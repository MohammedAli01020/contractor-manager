# Driver and Worker Ledger – Design Notes

Status: incremental implementation on `feature/driver-worker-ledger`; initial ledger/advance tests and the first posted-shift API guard tests passed. MySQL database-guard migration and direct-SQL integration tests are now added; CI verification is pending.
Base: `feature/equipment-expenses-accounting`.
This document does not change `main` and does not merge any pull request.

## Existing behavior verified

- `ShiftService.finish()` currently changes a shift from `IN_PROGRESS` to `COMPLETED`, stamps `finishedAt`, and records an audit event. It does not create an accounting journal or worker-ledger transaction.
- A shift stores a snapshot `driverWage` when the shift starts. Later changes to the driver's rate do not reprice that shift.
- `ShiftService.update()` currently permits updates to completed shifts, including driver, shift type, and wage. This must be handled before a completed shift can create an immutable payable.
- A completed shift can be cancelled and reinstated; reinstating a previously completed shift restores it as completed.
- Chart of accounts already contains 2100 (driver payables) and 5000 (driver wage expense).
- Journal lines support `driver_id`, allowing the general ledger to retain the driver dimension.

## Proposed first release

- Shift-based wage calculation only, using the persisted `Shift.driverWage` snapshot. Keep hourly pay as a future configurable wage model; do not infer hours from timestamps in this release.
- Recognize the wage once when a shift is completed:
  - Dr 5000 – Driver wage expense
  - Cr 2100 – Driver payables
  - Journal line carries `driver_id`; source identifies the shift.
- A dedicated worker ledger table now records earnings, payments and advances, linked to immutable journals.
- `GET /api/v1/drivers/{driverId}/ledger` returns the worker statement with running payable balance.
- `POST /api/v1/drivers/{driverId}/ledger/payments` settles earned payable from cash/bank.
- `POST /api/v1/drivers/{driverId}/ledger/advances` supports two explicit advance types: `AGAINST_EARNED` (reduces payable) and `RECOVERABLE` (records a worker-advance asset in account 1300).
- Recoverable advances are automatically offset against subsequent earned wages, up to the amount earned, with a separate balanced journal and ledger entry. Generic expense records are not used for driver advances or wage settlement.
- Every source event has a tenant-scoped idempotency key backed by a database unique constraint and is committed atomically with its journal.
- The worker ledger is append-only at the MySQL layer: UPDATE and DELETE are rejected by triggers. Corrections must be compensating entries, not row edits.
- Posted financial events are immutable. Corrections use explicit reversal/adjustment entries.
- Tenant scope is always taken from the authenticated user; actor IDs are never accepted from request bodies.
- Worker statement shows dated transactions, source shift/reference, debit/credit effect, paid amount and running balance.
- VIEWER can read; ADMIN/OPERATOR can create permitted financial transactions, subject to existing authorization conventions.

## Completion and editing safety

The persisted recognition marker is `shifts.driver_wage_journal_entry_id`, unique per tenant. The ledger also has a generated `earning_shift_id` with a unique tenant+shift index, plus a composite foreign key tying the ledger row to the same tenant and driver as its shift. The finish operation, wage-ledger transaction and journal posting share one database transaction.

A completed shift whose wage has been recognized must not silently change its financial/source fields. Backend validation rejects edits; MySQL triggers independently protect the persisted shift against direct SQL changes to date, type, site, driver, equipment, wage, price, equipment operating cost, start/finish timestamps, journal link or completion status. Notes remain editable. Deletion is blocked. Corrections require a dedicated reversal/adjustment workflow.

Cancelling a completed, wage-recognized shift must not erase the earned wage. It must require a reversal/correction workflow. Reinstating a completed shift must not post a second earning.

## Advances: distinguish two meanings

Do not assume every advance is the same accounting event:
1. Advance against already-earned payable: Dr 2100 worker payable / Cr cash or bank.
2. Recoverable advance before earning (employee receivable): Dr a dedicated worker-advances asset account / Cr cash or bank; when wages are earned and offset, Dr 2100 / Cr worker-advances asset.

The chart now includes worker-advances asset account 1300. Recoverable advances are Dr 1300 / Cr 1000 cash or 1010 bank; wage recovery is Dr 2100 / Cr 1300. A worker payment against payable uses Dr 2100 / Cr 1000 cash or 1010 bank.

## Test requirements

- Completing a shift creates one balanced wage journal and one ledger earning.
- Repeated finish/retry cannot create a duplicate earning or journal.
- Concurrent completion requests cannot double-post.
- Cancel/reinstate of recognized completed shifts cannot duplicate or erase earnings.
- Recognized shift financial/source edits and deletion are rejected by both Backend and MySQL triggers.
- Direct SQL attempts to edit/delete worker-ledger rows are rejected; duplicate shift earnings are blocked by a unique generated-column index.
- Partial and full worker payments reduce payable correctly; overpayment is rejected.
- Advance types post to the correct accounts and reconcile to the worker subledger.
- Tenant isolation, role boundaries, idempotency, rollback atomicity and concurrent payment safety.
- Integration tests must run against MySQL/Testcontainers, not only H2.

## Non-goals for the first release

- Do not merge driver/worker advances into generic expenses.
- Do not implement hourly wage calculation until the tenant wage model is configurable and explicitly selected.
- Do not introduce microservices.
- Do not merge PRs or modify `main` without explicit approval.

## Concurrent-user safety review

The current branch uses row-level pessimistic locks for state-changing operations on shifts, drivers, equipment and expenses, plus optimistic `@Version` checks for stale edits. Database unique indexes remain the final guard for active driver/equipment reservations, journal posting keys, reversals, idempotency keys and one earning per shift.

Existing MySQL integration race tests cover:
- Starting a shift while another request changes driver availability.
- Starting a shift while another request moves equipment into maintenance.
- Two payments attempting to overpay the same expense.

Added worker-ledger race coverage:
- Two simultaneous payments against one driver's earned payable balance must serialize on the driver row; only one may spend the same available balance.

Known follow-up checks before declaring concurrency coverage complete:
- Same idempotency key submitted simultaneously for expense creation and driver-ledger writes should return a stable idempotent response rather than an unexpected server error.
- Concurrent duplicate journal posting and reversal requests should be exercised against MySQL.
- Cash/bank account balance policy must be explicitly defined before enforcing a non-negative balance. Posting rows are append-only, but this is not equivalent to a cash-balance floor.
- Inventory concurrency is out of scope until inventory issue/receipt workflows are implemented.

A CI-discovered trigger defect was corrected: the posted-shift trigger now permits the one-time `IN_PROGRESS -> COMPLETED` transition and first `finished_at` value, while keeping the financial/source snapshot immutable afterward.

## Concurrency and database integrity review (2026-10-31)

- Existing integration coverage includes simultaneous identical journal postings, competing reversals of the same journal, concurrent payments against one expense (only one may succeed when their sum exceeds the expense), active-shift start/resource-state races, and row locks for driver/shift/equipment/expense mutations.
- Driver-ledger payment and advance commands lock the driver row before reading the running balance and posting the journal/ledger event. This serializes balance-changing commands for the same worker.
- Journal posting uses a tenant-scoped unique posting key; competing identical requests converge on the same journal, while conflicting payloads are rejected. Reversal uniqueness is protected by a tenant-scoped unique key.
- V23 adds MySQL triggers rejecting UPDATE/DELETE of posted journal headers and lines. Corrections must use reversal/adjustment journals.
- V18 already enforces unique tenant-scoped idempotency keys for expenses and expense payments. V20/V22 enforce worker-ledger request-key uniqueness and one EARNING row per shift.
- Important remaining boundary: the current model does not maintain a separately locked, authoritative cash/bank balance row. Cash/bank availability is therefore not yet enforced as a no-negative-balance rule across unrelated payment sources. Do not claim concurrent disbursements cannot exceed available cash/bank until a dedicated cash-account balance/ledger policy and concurrency tests are implemented. Bank overdraft policy must be explicit rather than assumed.
- V23 protects existing journal rows from mutation/deletion, but does not by itself prevent an additional journal line from being inserted later by privileged direct SQL. Production DB credentials should be least-privilege; a future posting-finalization design can close this remaining insert window without breaking the current atomic header+lines transaction.
- Optimistic `@Version` and row locks solve different problems: use optimistic version checks for stale edits, pessimistic row locks for balance/resource decisions, and database unique constraints as the final race barrier.
- CI is the verification gate for MySQL trigger behavior; do not treat these safeguards as production-verified until the MySQL integration workflow passes.

## Cash and bank balance protection – approved policy (2026-10-31)

### Business policy

- Cash accounts must never have a negative available balance.
- Bank accounts must never have a negative available balance in the first release.
- Overdraft is disabled in the first release. If introduced later, it must be an explicit per-bank-account setting with a defined limit and audit trail; it must not be enabled implicitly.
- A transaction that would make an account balance negative is rejected atomically. A failed posting must leave neither a journal nor a partial source/ledger mutation.

### Proposed technical approach (implementation pending)

- Keep the general ledger as the single accounting source of truth; do not introduce a separately maintained balance that can drift from journal lines.
- Serialize every posting that affects a cash/bank account by acquiring a database pessimistic write lock on the relevant tenant-scoped chart-account row(s), in ascending account ID order. Acquire these locks before reading balances. All posting paths must go through the central `JournalPostingService`.
- Calculate each cash/bank account's current debit-normal balance from its posted journal lines (debits minus credits for asset accounts), then apply the normalized journal's net effect. Reject any result below zero. Reversal journals are subject to the same check.
- Add repository support for locking tenant-scoped account rows and an efficient aggregate balance query. The implementation must handle journals with multiple cash/bank lines and net effects without double-counting.
- Identify cash/bank accounts explicitly and safely. The current baseline uses account codes 1000 (cash) and 1010 (bank); before supporting multiple physical cashboxes or multiple bank accounts, introduce explicit account classification/parent-leaf rules rather than relying indefinitely on names or loose code-prefix matching.
- Keep account locks and journal insertions in the same database transaction. Define and test a consistent lock order to reduce deadlocks. Preserve tenant isolation and existing posting-key idempotency behavior.
- Add MySQL integration tests with two independent transactions attempting different postings against the same cash/bank account at the same time. When their combined outflow exceeds the opening balance, exactly one may commit; the other must fail with a stable business conflict. Also test concurrent mixed inflow/outflow, exact-zero balance, reversals, multi-line journals, duplicate retries and transaction rollback.
- Do not treat a unit test or a single-threaded insufficient-funds test as proof of concurrency safety.

Implementation must begin only after reviewing every existing journal-producing workflow to verify that it uses the central posting service and determining the current account-selection contract for each cash/bank source.


## Journal-producing workflow audit (2026-10-31)

Reviewed the accounting-related service implementations on `feature/driver-worker-ledger`:

- `ExpenseService.create()`: posts the expense recognition journal through `JournalPostingService`; this is an accrual entry (expense debit / supplier payable credit) and does not move cash or bank.
- `ExpenseService.addPayment()`: records the payment source row and posts supplier payable debit / selected cash-or-bank credit through `JournalPostingService`. The source row and journal are in the surrounding Spring transaction.
- `DriverLedgerService.pay()`: posts worker payable debit / selected cash-or-bank credit through `JournalPostingService`.
- `DriverLedgerService.advance()`: posts either worker payable or recoverable worker-advance asset debit / selected cash-or-bank credit through `JournalPostingService`.
- `ShiftService.finish()`: posts the wage recognition journal (wage expense debit / worker payable credit) and, when applicable, a recoverable-advance offset journal (worker payable debit / worker-advance asset credit) through `JournalPostingService`. Neither journal moves cash/bank.
- No direct construction/persistence of `JournalEntry` or `JournalLine` was found in these reviewed business workflows. The central posting service owns journal header and line persistence.
- The currently identified cash/bank outflow sources are therefore supplier expense payments, worker payable payments and worker advances. They all submit a journal containing a credit line to the selected account; this is the enforcement point for the shared cash/bank balance floor.
- The account-selection contract is currently hard-coded to active asset accounts with code `1000` (cash) or `1010` (bank) in `ExpenseService` and `DriverLedgerService`. The baseline chart creates one account for each code per tenant. This supports the current single-cash/single-bank baseline, not multiple physical cashboxes or multiple bank accounts yet.
- `JournalPostingService.postNormalized()` currently validates account existence and active status, then saves the journal header and lines. It does not acquire account row locks or calculate cash/bank balances. This confirms the identified protection gap.
- Implementation must centralize cash/bank classification and balance-floor enforcement in `JournalPostingService`, rather than adding separate balance checks to each source workflow. The existing source-specific code restrictions should be retained until a deliberate multi-account model is designed.
- The source business methods are transactional and the posting service uses a REQUIRED `TransactionTemplate`; verify transaction propagation and rollback behavior in MySQL integration tests while adding account locks. Keep account locking, balance read, source row changes and journal persistence in one transaction.
- Audit scope: the accounting-related business service files in the current branch tree (expense, driver ledger, shift) and the central accounting domain/repositories. Re-run this audit whenever a new journal-producing module is introduced.



## Cash/bank balance floor implementation (2026-10-31)

Implemented on `feature/driver-worker-ledger`:

- `ChartAccountRepository.findCashOrBankByIdAndTenantIdForUpdate` obtains a tenant-scoped pessimistic write lock for asset accounts coded 1000 or 1010.
- `JournalPostingService` collects distinct journal account IDs and processes them in ascending order. It locks only cash/bank accounts, avoiding unnecessary serialization on shared payable or expense accounts.
- The balance is derived from journal lines as debit minus credit; the journal's complete normalized net effect is applied before deciding whether the resulting balance is negative.
- The aggregate balance query uses a MySQL locking read (`FOR UPDATE`) so it reads the latest committed balance after waiting for the account lock, rather than reusing an earlier repeatable-read snapshot from the caller's transaction.
- A negative resulting cash/bank balance is rejected as a business conflict (HTTP 409). This applies to outflows and reversals because validation runs for every new journal, including reversal journals.
- A MySQL integration test was added for two independent concurrent cash outflows of 80.00 against an opening balance of 100.00; expected outcome is exactly one successful posting and a final balance of 20.00.
- The implementation and test are committed, but are **not yet verified by Maven/CI**. Run the focused integration test and full backend suite before treating this protection as production-ready.
- Current scope is the baseline accounts 1000 and 1010 only. It does not yet introduce multiple cashboxes/bank accounts or overdraft configuration.
