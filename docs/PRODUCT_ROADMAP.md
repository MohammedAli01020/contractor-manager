# Contractor Manager — Product Roadmap & Decisions

**Repository:** `MohammedAli01020/contractor-manager`  
**Planning branch:** `fix/active-shift-concurrency`  
**Status:** Planning document. Proposed features below are not implemented unless explicitly marked as existing.  
**Purpose:** Keep product decisions, unresolved questions, phase order, and verified phase status in one version-controlled source of truth.

**Document roles:** This file owns the product-wide roadmap and decisions. Detailed implementation checklists live under [`docs/phases/`](phases/); the root `README.md` is for setup and running the app.

---

## 1. Rules for all future work

1. Inspect the current code, migrations, tests, and branch before proposing edits; do not assume a feature is absent without checking.
2. Preserve existing business rules and data. Do not remove functionality without discussion and explicit approval.
3. Never edit a Flyway migration that may already have been applied. Add a new migration instead.
4. Preserve tenant isolation in API/service logic and database relationships.
5. Avoid destructive Git/Docker commands and do not discard local changes.
6. Before coding, explain the intended behavior, affected files, schema/API impact, and tests. Submit a reviewable patch before merging.
7. Do not merge to `main`, push to another branch, or perform a release without explicit approval.
8. Mark each roadmap item as **Not started / In progress / Implemented / Tested** only after checking the actual repository and test evidence.

## 2. Current repository baseline — verified from the planning branch

The branch currently contains a working foundation for:
- Flutter Android client and Spring Boot 2.7.18 / Java 11 backend, MySQL, Docker Compose and Flyway.
- Tenant registration, username/password login, access JWT and rotating refresh tokens, logout, `/auth/me`, and change-password endpoint.
- Refresh-token hashes stored rather than raw refresh tokens; refresh tokens are tenant/user-bound in the database.
- Tenant-scoped drivers, equipment, sites and shifts; shift records snapshot prices/wages/operating costs.
- Backend checks shift resource ownership through tenant-scoped queries and composite database foreign keys in the inspected migrations.
- Flutter version in `mobile/pubspec.yaml` is currently `0.1.0+1`; current Dart constraint is `>=2.18.0 <3.0.0`.
- The current `users` table has globally unique `username`, nullable `phone`, no email column in the inspected migration, and the only existing role is `ADMIN`.
- The current login request accepts `username` and `password`. Tenant registration accepts company name, full name, username, password, and optional phone.
- `drivers` has its own optional contact phone and is currently a separate record from `users`. There is no `DRIVER` role in the inspected role enum.

This baseline is not a claim that every endpoint or tenant-isolation case has been exhaustively audited. Recheck relevant code and tests before each implementation phase.

## 3. Product and technical decisions agreed in discussion

### 3.1 Driver account is optional
- A contractor can create and use a driver record without creating a login account.
- Later, the contractor may initiate account activation for an existing driver; do not create a duplicate driver record.
- A driver account uses a separate `DRIVER` role and least-privilege permissions. The backend—not just the Flutter UI—must enforce access to that driver's own permitted information.
- Driver account access is read-only initially: own shifts/history and any specifically approved earnings/advances/payment information. No ability to edit shifts, rates, advances, or company records.
- Multi-tenant driver identity/linking is deferred until a real product need is established. Do not build multi-tenant memberships in the first release without revisiting the identity model and migration implications. Keep driver records tenant-scoped and separate from login identities; do not duplicate a login/password per tenant.

### 3.2 Login identifier: proposed target
- The preferred user experience is phone number + password, with a stable internal `userId`; the phone is not a primary key.
- Normalize phone numbers on the backend to a canonical international representation, e.g. `+201012345678`. Accept reasonable Egyptian input formats and Arabic/English digits; Egypt is the initial default country.
- Use a phone-number library such as Google libphonenumber for numbering-plan validation and normalization. This does **not** prove that the number is active or owned by the user.
- Do not require the company code at every login by default. The backend determines the company/tenant associated with the authenticated user. A company reference code, if used for support or invitations, is not a credential and grants no access.
- Do not implement the login migration until the current globally unique username model and existing accounts have a safe migration plan.
- For the first release, the login phone number should be unique globally after normalization. This applies to login identities, not blindly to driver contact phones.
- A user's internal ID remains stable. Changing a phone used for login must be a separate secure flow; changing a driver's contact phone must not silently change login credentials.
- The first release assumes one login user belongs to one tenant. Revisit this before implementation if multi-company access becomes necessary.

### 3.3 Email and password recovery: proposed target
- Require an email address for every account that can log in (including a driver login account); a driver record without a login account does not need an email.
- Verify email ownership before treating it as a trusted recovery channel. Email should be globally unique for login/recovery in the first release to keep account recovery unambiguous; revisit if a more flexible identity model is needed.
- Initially use a secure, single-use, expiring email recovery link rather than paid SMS OTP.
- Add rate limits, generic responses that do not reveal whether an account exists, token invalidation, and a documented refresh-session revocation policy.
- Phone ownership verification via SMS OTP is a future capability, potentially for activation, phone changes, or sensitive actions—not every login.
- Email delivery provider and costs remain undecided.

### 3.4 App updates and early APK distribution
- Initial Android distribution will be an APK shared through Google Drive; this is a temporary distribution approach, not a permanent update infrastructure.
- Backend should expose update metadata: latest version/build, minimum supported version/build, optional vs mandatory policy, release notes, and download URL.
- Flutter checks update metadata through the existing REST/Dio connection when opening the app and at an appropriate resume/check interval.
- Optional update: inform the user and allow “Later”.
- Mandatory update: block normal use when below the minimum supported version and provide the update action.
- The backend must also reject obsolete client versions for sensitive/incompatible APIs where required; hiding the UI alone is not a security control.
- Test the actual Google Drive link on Android: it must lead to a usable APK download rather than an inaccessible preview page.
- Keep the Android application ID/package name and signing key stable so updates install over the existing app. Test upgrade from the previous APK and ensure local data is preserved.
- APK installation still requires Android's user confirmation and any required permission to install from that source; do not promise silent installation.
- In-app update messages do not require WebSocket. Push notifications when the app is closed can be added later with Firebase Cloud Messaging (FCM), if needed.

### 3.5 Subscriptions, expiry and manual payment

#### Launch decisions
- Launch with **one plan at a fixed price and monthly billing only**. The actual price and usage limits have not been decided.
- Design the subscription model so annual plans and plans based on company size can be added later without rebuilding the subscription system. Do not implement all future plans at launch.
- Keep the concepts of **Plan**, **Billing Period**, **Subscription**, and **Payment Request** separate:
  - **Plan:** included features and usage limits, potentially based on users/equipment.
  - **Billing period:** monthly at launch; annual may be added later.
  - **Subscription:** tenant's selected plan, start/end dates, status, and entitlement.
  - **Payment request:** amount, reference, period/plan requested, status, review outcome, and audit history.
- Grace period: **3 days after subscription expiry**.
- Early renewal: add the new period to the current subscription end date so the customer does not lose remaining paid days. Define and test month-end/date arithmetic before implementation.
- After the grace period, block operational actions server-side while still allowing login and access to renewal, payment, and support screens. Preserve tenant data; do not delete it because a subscription expired.
- The exact rule for a payment request submitted before expiry/grace end but still awaiting admin review is unresolved. Define a fair, bounded policy before implementation; do not create an indefinite automatic extension.
- Monthly date calculation remains undecided: fixed 30-day periods vs calendar-month periods. Decide this before implementing billing dates.

#### Manual payment flow for the first paid pilot
1. Contractor selects the available plan/period and creates a payment request in the app.
2. The system records tenant, plan, amount, period, status, a unique reference (e.g. `PAY-1042`), and timestamps.
3. The app opens a prepared WhatsApp message containing the request reference and payment details. The contractor manually attaches and sends the receipt image in WhatsApp; the app does not automatically send the image.
4. Do not upload/store receipt images on the application server in the first release. WhatsApp is a communication channel, not proof of payment.
5. A Platform Admin reviews the request and verifies actual receipt in the bank/wallet/provider account. A screenshot alone is not sufficient proof.
6. Platform Admin confirms or rejects the request. Record the actor, time, outcome, and rejection reason where applicable.
7. Backend activates/extends the subscription only after confirmation, records the resulting dates, and prevents duplicate processing of the same request.
8. Keep payment requests, payment verification, and subscription entitlements separate. Use server-side authorization and an audit trail for all decisions.

- Proposed request states include pending payment, awaiting review, activated/accepted, rejected, and cancelled; finalize exact transitions and names during design.
- WhatsApp is the only planned receipt-notification channel for the first release, but the architecture should allow an alternative channel later.
- No WhatsApp Business API or payment gateway is required for the first release.
- Later, evaluate an Egyptian payment gateway (for example Paymob/Fawry) after checking business eligibility, supported methods, fees, settlement, refunds, and recurring-payment support. Use sandbox first.
- Activate paid access automatically only from a trusted provider confirmation/webhook, not a client redirect or uploaded receipt.
- Whether recurring auto-renewal is required remains undecided; manual renewal is the recommended first pilot.

### 3.6 Financial, purchases and inventory principles
- Preserve the agreed equipment net result formula:
  **Equipment net = shift revenue − driver wages attributable to those shifts − recognized equipment expenses.**
- Driver wage is deducted once from equipment net and appears as a driver payable; it must not be deducted a second time from the driver's balance.
- Separate purchase classification, accounting treatment, and destination/beneficiary. A single invoice may contain stock items, direct equipment expenses, and general company items.
- Buying stock is not the same as consuming it. Recognize stock cost according to the chosen inventory policy when issued/consumed, and avoid charging it again.
- Posted financial/inventory transactions should not be silently deleted or edited. Use audited reversal/correction/cancellation flows.
- Every financial movement needs tenant, reference, status, timestamp, actor, and an audit trail.
- Do not implement purchasing/inventory before agreeing on valuation (weighted average/FIFO), warehouses, negative-stock policy, supplier payment behavior, and cancellation rules.

### 3.7 إنشاء الشركات وإدارة الحسابات — قرار معتمد
- **صاحب النظام (Platform Owner):** هو الوحيد الذي ينشئ الشركات (Tenants) في المرحلة الحالية، ويُنشئ أو يحدد حساب المدير الأول للشركة، ويتحكم في تفعيل الشركة أو إيقافها.
- **مدير الشركة (ADMIN):** يستطيع إنشاء وإدارة المستخدمين داخل Tenant الخاص بشركته فقط، ولا يملك صلاحية إنشاء شركة جديدة أو الوصول إلى بيانات الشركات الأخرى.
- **أدوار الموظفين:** يجب فصل دور المدير عن أدوار الموظفين؛ لا يجوز منح كل مستخدم جديد دور `ADMIN` تلقائيًا. تُحدد صلاحيات كل دور قبل تنفيذه.
- **التسجيل الذاتي للشركات مغلق حاليًا.** إنشاء Tenant يجب أن يكون محميًا بصلاحية صاحب النظام، وليس متاحًا لزائر أو لمدير شركة.
- **مستقبلًا:** يمكن لصاحب النظام فتح التسجيل الذاتي عبر إعداد مركزي قابل للتحكم، بعد تجهيز مسار تسجيل مضبوط واختبارات الصلاحيات وعزل البيانات ومنع إساءة الاستخدام. فتحه مستقبلًا لا يمنح مديري الشركات صلاحية إنشاء الشركات.
- **متطلبات التنفيذ:** فصل إنشاء Tenant عن إنشاء مستخدم داخل Tenant؛ عدم الوثوق بأي `tenantId` يرسله العميل لتحديد نطاق الصلاحيات؛ إضافة صلاحية واضحة لصاحب النظام؛ مراجعة أدوار المستخدمين؛ واختبار منع مدير الشركة من إنشاء Tenant أو الوصول إلى Tenant آخر، واختبار رفض التسجيل العام عندما يكون مغلقًا.
- **حالة القرار:** معتمد من صاحب المنتج. **حالة التنفيذ البرمجي:** لم يُنفذ بهذا التوثيق وحده، ويجب تحديثها فقط بعد تعديل الكود وتشغيل الاختبارات والتحقق من النتائج.


### 3.8 دفتر السائقين والإقفال الشهري — قرار معتمد
- يجب أن يكون لكل سائق كشف حركات مالي واضح يدعم الرصيد الافتتاحي، أجور الورديات المكتملة، السلف، الخصومات المعتمدة، المكافآت/البدلات، المدفوعات الفعلية، التسويات والتصحيحات المعتمدة.
- الرصيد الختامي للسائق في الفترة يصبح رصيده الافتتاحي للفترة التالية، من دون إعادة إنشاء أو تكرار الحركات الأصلية. يجب أن يوضح الكشف هل الرصيد مستحق للسائق أم مديونية عليه وفق اصطلاح إشارة ثابت وواضح.
- لا تُعدّل الحركات المالية المرحلة بصمت ولا تُحذف نهائيًا؛ تستخدم عمليات تصحيح/عكس موثقة مع المستخدم والوقت والسبب.
- الإقفال الشهري يسجل الفترة، حالة الإقفال، المستخدم المعتمد، وقت الاعتماد، أرقام/أرصدة الإقفال، وأي إعادة فتح مع سبب وصلاحية خاصة. يجب أن تكون عملية الإقفال والترحيل آمنة من التكرار عند إعادة الطلب أو انقطاع الاتصال.
- لا يجوز إقفال فترة مع وجود تعارضات أو بيانات غير مكتملة غير معتمدة؛ يعرض النظام استثناءات واضحة للمراجعة قبل الاعتماد.
- **حالة القرار:** معتمد على مستوى متطلبات المنتج. **حالة التنفيذ:** لم يُنفذ بعد؛ يُعاد التحقق قبل بدء المرحلة المالية.

### 3.9 حسابات المعدات والملكية وتسوية الملاك — قرار معتمد
- يدعم النموذج المعدات المملوكة للشركة والمعدات المملوكة لطرف/شريك آخر، من دون إنشاء مسارين منفصلين لتسجيل الورديات والمصروفات. نوع الملكية وبيانات المالك/الشريك وطريقة التسوية تُسجل عند الحاجة.
- القاعدة التشغيلية المتفق عليها: **صافي تشغيل المعدة = قيمة الورديات المكتملة المستحقة للمعدة − أجور السائقين المرتبطة بهذه الورديات − مصروفات المعدة المعترف بها**.
- أجرة السائق تُخصم مرة واحدة فقط من نتيجة تشغيل المعدة، وتظهر كمستحق في دفتر السائق؛ لا تُخصم مرة ثانية من رصيد السائق.
- يجب فصل نتيجة التشغيل عن التدفقات النقدية وعن حساب مستحقات المالك/الشريك. مستحق المالك يتحدد وفق عقد/طريقة التسوية الفعلية ولا يُفترض تلقائيًا أنه يساوي صافي التشغيل.
- مصروف المعدة يؤثر على الفترة التي يخصها وفق السياسة المعتمدة سواء دُفع فورًا أو بقي مستحقًا. حالة السداد مستقلة: غير مدفوع، مدفوع جزئيًا، مدفوع بالكامل. سداد مصروف سابق لا يُسجل كمصروف جديد في فترة السداد.
- المصروف يمكن ربطه بمعدة مباشرة أو بورديّة محددة. يجب منع احتساب المصروف نفسه مرتين، خصوصًا عند صرف مخزون للمعدة مستقبلًا.
- تُحفظ أسعار الوردية وأجور السائق وتكلفة التشغيل الموجودة كقيم تاريخية للوردية؛ لا تُحذف الحقول الحالية `defaultShiftPrice` أو `operatingCostPerShift` أو ما يقابلها في الـAPI. المصروفات الفعلية تُسجل منفصلة عن تكلفة التشغيل التقديرية لتجنب الخصم المزدوج.
- **حالة القرار:** معتمد على مستوى متطلبات المنتج. **حالة التنفيذ:** لم يُنفذ بعد؛ لا تُعد هذه البنود دليلًا على وجود وظائفها في الكود.

### 3.10 تصنيفات المصروفات وصلاحيات المستخدمين — قرار معتمد مبدئيًا
- تصنيفات مصروفات المعدات قابلة للإضافة والتعديل والتفعيل/التعطيل من المستخدم المخوّل. تعطيل أو إعادة تسمية تصنيف لا يغيّر الوصف التاريخي للمعاملات القديمة؛ لا يُسمح بحذف تصنيف مرتبط بحركات مالية.
- يبدأ النظام بتصنيفات شائعة مثل السولار/الديزل، الزيوت والشحوم، الصيانة، قطع الغيار، وتصنيف مخصص، مع السماح بتوسعتها دون تعديل الكود لكل تصنيف جديد.
- يجب فرض الصلاحيات في الـBackend، وليس فقط بإخفاء الأزرار في Flutter. التعديلات المالية والإقفالات والتصحيحات تتطلب صلاحيات محددة وسجل تدقيق.
- يجب التمييز بين **Platform Owner** الذي يدير إنشاء الشركات على مستوى المنصة، وبين مدير الشركة والمستخدمين داخل الـTenant. لا نستخدم اسم `OWNER` دون حسم نطاقه لتجنب الخلط بين مالك المنصة ومالك الشركة/المعدة.
- نموذج الأدوار المستهدف مبدئيًا: `PLATFORM_OWNER` لإدارة المنصة وإنشاء الشركات؛ `ADMIN` لإدارة شركته ضمن حدود الـTenant؛ `OPERATOR` للعمليات المصرح بها؛ `VIEWER` للقراءة والتقارير؛ و`DRIVER` اختياري لاحقًا وفق متطلبات حساب السائق الواردة أعلاه. تُراجع الأدوار الحالية واختبارات الصلاحيات قبل أي migration أو تغيير في JWT.
- **حالة القرار:** المتطلبات أعلاه معتمدة مبدئيًا للتصميم؛ الأدوار النهائية وتفاصيل كل صلاحية يجب توثيقها قبل التنفيذ.

### 3.11 Phase status and execution documents
The roadmap below is the only source of truth for phase order and overall status. Detailed files explain scope and acceptance criteria; they must not create a competing roadmap. Status describes phase-level acceptance, not whether some related code already exists.

| Phase | Overall status | Execution plan |
|---|---|---|
| 0 — Stabilize and verify the existing core | In progress — re-verify current branch and refresh test evidence before acceptance | [PHASE-00-CORE.md](phases/PHASE-00-CORE.md) |
| 1 — Master data and shift usability | In progress — compare existing implementation with the phase exit criteria | [PHASE-01-MASTER-DATA.md](phases/PHASE-01-MASTER-DATA.md) |
| 2 — Financial foundation and monthly close | Not started | [PHASE-02-FINANCIALS.md](phases/PHASE-02-FINANCIALS.md) |
| 3 — Purchases, suppliers and inventory | Not started | Tracked in this roadmap until a detailed plan is needed |
| 4 — Reports and exports | Not started | Tracked in this roadmap until a detailed plan is needed |
| 5 — Authentication redesign and optional driver accounts | Not started as a redesign phase | [PHASE-05-AUTHENTICATION.md](phases/PHASE-05-AUTHENTICATION.md) |
| 6 — Version/update policy and APK pilot | Not started | Tracked in this roadmap until a detailed plan is needed |
| 7 — Subscription management and manual-payment pilot | Not started | Tracked in this roadmap until a detailed plan is needed |
| 8 — Payment gateway and push notifications | Not started | Tracked in this roadmap until a detailed plan is needed |

**How to update status:** change a phase status only after inspecting the current branch and recording evidence. "Implemented" means the scoped changes exist; "Tested" additionally requires actual test results. A file existing, a task being discussed, or a test merely being present is not evidence that a phase passed. Record the next action/blocker in the phase plan or the relevant roadmap phase section.

## 4. Step-by-step delivery plan

### Phase 0 — Stabilize and verify the existing core
**Status:** In progress; current branch state and latest test evidence must be refreshed before marking this phase tested/accepted.  
**Detailed plan:** [PHASE-00-CORE.md](phases/PHASE-00-CORE.md).  
**Goal:** establish a reliable base before adding new modules.
- Review the current branch, local changes, API contracts, Flyway migrations, and test coverage.
- Finish and test shift lifecycle (create, list/filter, update, cancel/reinstate where supported), concurrency, driver/equipment availability, optimistic locking/version conflicts, and tenant isolation.
- Run backend tests and Flutter analyze/tests; record actual outcomes.
- Resolve defects before expanding financial scope.
**Exit criteria:** agreed shift workflows pass tests; no known tenant-crossing access; no unreviewed changes are lost.

### Phase 1 — Driver/equipment master data and shift usability
**Status:** In progress; compare the current implementation with the exit criteria before accepting the phase.  
**Detailed plan:** [PHASE-01-MASTER-DATA.md](phases/PHASE-01-MASTER-DATA.md).  
- Complete driver/equipment management and clear unavailable/inactive states with reasons and current shift context where applicable.
- Confirm rates/defaults are copied into each shift as historical snapshots.
- Improve date selection and driver/equipment selection UX without breaking the current Flutter/Dart compatibility.
**Exit criteria:** contractor can manage master data and run the agreed shift workflow reliably.

### Phase 2 — Financial foundation, driver ledger, equipment expenses and monthly closing
**Status:** Not started.  
**Detailed plan:** [PHASE-02-FINANCIALS.md](phases/PHASE-02-FINANCIALS.md).  
- Define and test the exact accounting rules first, including sign conventions for positive/negative balances and the period in which each transaction is recognized.
- Implement driver ledger movements: opening balance, shift wages, advances, approved deductions, bonuses/allowances, actual payments, adjustments and reversals.
- Implement configurable equipment expense categories and expense entries that can be linked to an equipment or a specific shift; support unpaid, partially paid and fully paid status.
- Model equipment ownership (company-owned or third-party/partner-owned) and, when applicable, owner/partner settlements separately from operating result and cash movement.
- Implement monthly period close: reconciliation preview, exceptions, approval metadata, immutable closing snapshot, opening balances for the next period, and controlled reopen/correction flow.
- Make posting, payment and close operations idempotent; prevent duplicate wage/expense/payment posting and enforce tenant isolation and audit history.
- Preserve existing shift price/wage/operating-cost snapshots and avoid double-counting estimated operating costs, actual expenses, driver wages or inventory consumption.
- Separate operational profit from cash flow and supplier/owner liabilities.
**Exit criteria:** sample cases reconcile for driver balances, equipment operating result, owner/partner amounts due, unpaid liabilities, monthly carry-forward, corrections and retries; no double deduction or duplicated transaction.

### Phase 3 — Purchases, suppliers and inventory
- Design suppliers, purchase invoices and invoice lines, payment status, stock items/units, warehouses (if required), stock movements and issue-to-equipment flows.
- Decide stock valuation, negative stock, invoice posting and reversal policies before migrations.
- Support mixed invoices with different treatment per line.
- Add idempotent posting and tests against duplicate invoice/stock/expense entries.
**Exit criteria:** inventory quantities and financial ledgers reconcile for purchase, issue, return, cancellation and retry cases.

### Phase 4 — Reports and exports
- Equipment, driver and supplier/owner statements; daily/monthly/custom date ranges; opening balance, movements, totals, closing balance and payment status.
- Monthly close reports, period-to-period comparisons, unreconciled items and unpaid/partially paid liabilities.
- PDF/Excel exports after the underlying ledgers and close process are stable.
- Restrict each report to authorized tenant and role.
**Exit criteria:** report totals match ledger totals and approved closing snapshots for test datasets.

### Phase 5 — Authentication redesign and optional driver accounts
**Status:** Not started as a redesign phase; current auth endpoints are baseline only.  
**Detailed plan:** [PHASE-05-AUTHENTICATION.md](phases/PHASE-05-AUTHENTICATION.md).  
- Design the identity and tenant-membership model before changing schema.
- Multi-tenant driver identity/linking is deferred until a real need appears; revisit the model and migration impact before supporting it.
- Define account activation, phone ownership verification, and recovery for users without email if the requirements change.
- Add email field/verification and secure password recovery.
- Plan safe migration from globally unique usernames to normalized unique login phones, including duplicate/missing phone handling and existing users.
- Add DRIVER role and backend-enforced read-only endpoints; ensure driver can only see their permitted records.
- Add tests for tenant isolation, role escalation, account linking, duplicate phone formats, disabled accounts, and revoked sessions.
**Exit criteria:** old accounts have a safe migration path; no driver can access another driver's or tenant's data.

### Phase 6 — Version/update policy and Google Drive APK pilot
- Define app version/build scheme and compatibility policy.
- Add backend update metadata and endpoint, plus Flutter optional/mandatory update UX.
- Publish a signed APK to Drive and verify the download flow on real Android devices.
- Test optional deferral, mandatory blocking, minimum-version API enforcement, upgrade install, signing continuity and local-data retention.
- Document a rollback/release checklist and who can publish a release.
**Exit criteria:** update behavior works end-to-end from a prior installed APK, not just in a mocked response.

### Phase 7 — Subscription management and manual-payment pilot
- Implement the agreed launch scope: one fixed-price plan, monthly billing, 3-day grace period, early-renewal extension, and manual payment review.
- Design subscription data to support future annual billing and company-size-based plans without implementing those plans at launch.
- Decide the monthly date arithmetic, exact plan usage limits, final price, and policy for payment requests awaiting review near expiry.
- Add subscription/payment-request states and audit trail, with server-side entitlement enforcement.
- Implement contractor request flow and Platform Admin confirmation/rejection.
- Test duplicate requests, wrong amount, expired subscriptions, early renewal, grace-period boundaries, delayed review, cancellations/refunds, and repeated confirmation.
**Exit criteria:** paid access changes only through a trusted, audited server-side action; expired tenants retain data and can still reach renewal/support.

### Phase 8 — Payment gateway and push notifications (later)
- Research provider eligibility and fees; implement sandbox integration and verify webhooks/signatures/idempotency.
- Automate subscription activation only after trusted confirmation.
- Add FCM only if external push is needed while app is closed. WebSocket is not required for ordinary update checks or payment status polling.

## 5. Decisions to settle in focused discussions

Do not guess these choices during implementation. Discuss and record them here before the related phase:
1. What secure activation/recovery route is available if a driver login account cannot use verified email?
2. Should username remain as a temporary login fallback during migration to phone login?
3. What is the precise policy when a payment request is submitted before expiry/grace end but admin review is delayed?
4. Should monthly billing use fixed 30-day periods or calendar-month periods?
5. What final price and usage limits will apply to the initial fixed-price plan?
6. Which inventory valuation method, warehouse model, and negative-stock policy will be used?
7. Should purchases by tenant admin be posted directly while employee purchases require approval? The current proposal is yes, with later configurable approval rules.
8. Which email provider and sender domain will be used?
9. What is the release approval and APK signing-key backup procedure?
10. Is annual billing or company-size-based pricing needed at launch, or should both remain future options? The current decision is to defer both.

## 6. Working agreement

For each phase:
1. Discuss requirements and edge cases.
2. Inspect relevant code and migrations on the current branch.
3. Propose files, API/schema changes, and tests.
4. Get approval before implementing.
5. Produce a reviewable patch and run relevant tests.
6. Review the diff and test evidence before any merge/release.
7. Update this document only when a decision is actually agreed or implementation status is verified.

**Important:** This roadmap records intent and verified baseline observations. It does not claim that proposed features have been implemented.

