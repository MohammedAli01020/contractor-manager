# Collection Receipt and Payment Voucher — Mobile Design Specification

Status: **Draft for product-owner review**  
Parent: [Document Center](../documents/document-center.md)  
Scope: Arabic-first Flutter workflows for viewing and generating a collection receipt or payment voucher from an already recorded financial transaction. This specification does not implement posting, payment execution, or PDF generation.

## 1. Product rule

A receipt or voucher is evidence of a financial transaction that already exists and has been confirmed by the backend. Creating, previewing, downloading, printing, or sharing the document must never create or repeat the financial transaction.

The mobile UI must make the source transaction and its status visible before offering document actions.

## 2. Entry points

Users may reach these documents from:
- the source transaction details screen (preferred primary entry);
- Document Center search/recent documents;
- the relevant customer, supplier, driver, cashbox, or bank account activity.

When launched from a source transaction, the screen receives its identifier and loads the authoritative details from the backend. It must not trust editable client-supplied amounts or party names.

## 3. Collection receipt flow

### 3.1 Eligibility

Show **Generate collection receipt** only when the source collection/receipt transaction is confirmed/posted and the current user has permission to view and generate its document.

For pending, failed, cancelled, reversed, or voided source transactions:
- do not offer a normal valid receipt;
- explain the source status;
- if policy allows, offer access to the historical/voided document with a clear status watermark/label.

### 3.2 Receipt preview fields

The preview should display, where applicable:
- company name and configured contact details/logo;
- document title: **Collection Receipt / إيصال قبض**;
- unique internal receipt number;
- issue date/time;
- received-from party name and party type;
- amount and currency, both numeric and readable text where supported;
- payment method and destination cashbox/bank account;
- source reference and related invoice/obligation, if any;
- short description/purpose;
- collector/recorded-by identity where appropriate;
- transaction status and document version.

Do not invent missing values. Hide optional fields or show an explicit unavailable marker according to the document template policy.

### 3.3 Actions

Available actions according to permission:
- Preview PDF.
- Save/download PDF.
- Print.
- Share using the Android share sheet.
- Email to an explicitly configured recipient, if tenant email delivery is enabled.

Do not show a generic **Confirm receipt** action on the document screen; confirmation belongs to the source transaction workflow.

## 4. Payment voucher flow

### 4.1 Eligibility

Show **Generate payment voucher** only when the source payment transaction is confirmed/posted and the user has the required permissions.

The voucher must clearly distinguish:
- a recorded payment from a payment request or draft;
- a completed payment from a failed/reversed/voided payment;
- a partial settlement from full settlement where the source obligation supports partial payment.

### 4.2 Voucher preview fields

The preview should display, where applicable:
- company name and configured contact details/logo;
- document title: **Payment Voucher / سند صرف**;
- unique internal voucher number;
- issue date/time;
- paid-to party name and party type;
- amount and currency, numeric and readable text where supported;
- payment method and source cashbox/bank account;
- purpose/description;
- linked obligation, expense, wage, advance, or other source reference;
- payment reference/transfer reference if present;
- recorded-by identity and document version;
- source transaction status.

A driver advance voucher must identify the advance as an advance movement, not label it as a wage payment or operating expense. A wage payment voucher should link to the driver's wage obligation/settlement. The UI must not infer one from the other.

## 5. Shared mobile screen structure

Suggested screen sequence:

1. **Source transaction details** — status, party, amount, date, and source reference.
2. **Document preview** — a phone-readable preview of the receipt/voucher.
3. **Document actions** — Save PDF, Print, Share, and Email where configured.
4. **Document history** — generated versions, superseded/voided state, creator, and timestamps.

Keep the primary action explicit, for example **View receipt** or **View payment voucher**. If a document already exists, open the latest valid version by default while keeping older versions accessible through history.

### Status presentation

- **Valid** — source transaction remains valid and document is current.
- **Superseded** — a newer version exists; preserve access to the older copy.
- **Voided** — source/document was voided through an authorized workflow; show a prominent void status.
- **Unavailable** — source status or permission does not allow document generation.

Never use color alone to communicate status; include readable Arabic labels and icons/text.

## 6. Arabic and PDF layout

- Arabic-first, RTL layout in Flutter.
- Arabic shaping and mixed Arabic/Latin identifiers must render correctly.
- Currency and numeric formatting follow tenant locale/currency configuration.
- The PDF must remain legible on a typical Android phone and on A4 print output.
- Avoid splitting the document title, amount, party name, or signature/acknowledgment area awkwardly across pages.
- A document may include an optional recipient acknowledgment/signature area, but a printed signature does not itself change the transaction status.

## 7. Permissions and audit

Separate permissions for:
- viewing the source transaction;
- viewing its document;
- generating a new document version;
- downloading/printing/sharing;
- emailing to configured recipients.

Audit generation, version creation, void/supersede actions, and delivery requests/results. Access checks are tenant-scoped and enforced by the backend.

## 8. Error and edge cases

- If the source transaction is not confirmed, explain that the document is unavailable until confirmation.
- If PDF generation fails, preserve the source transaction and offer retry; do not duplicate posting.
- If email delivery fails, keep the generated document in the in-app archive and show delivery failure separately.
- If the user loses connectivity, do not claim generation or delivery succeeded. Show locally available previously downloaded files only if their security policy permits.
- Concurrent generation requests must not issue conflicting/duplicate numbers; backend idempotency and uniqueness are authoritative.
- If the source is reversed after a document was issued, retain the document and mark it voided/superseded with a link to the reversal; do not delete it.
- A document must not imply cash has been received or paid if the source transaction is only pending.

## 9. Acceptance criteria

- Generating a receipt/voucher never posts or repeats a financial transaction.
- Amount, party, date, method, and source reference come from authoritative source data.
- A receipt is not available for an unconfirmed collection.
- A payment voucher is not available for an unconfirmed payment.
- Driver advances and wage settlements are clearly distinguished.
- Superseded/voided documents remain accessible with clear status.
- Arabic/RTL preview and PDF are readable on Android and A4 print.
- Document generation and delivery failures do not mutate the source transaction.
- Tenant isolation and permissions are enforced server-side.

## 10. Decisions still open

1. Whether to include a configurable recipient acknowledgment/signature area in the first release.
2. Whether internal document numbering is sequential per tenant and document type or uses a simpler unique reference initially.
3. Whether the first release supports email from the app or only PDF save/print/share.
4. Which company identity fields are mandatory on internal receipts/vouchers.
5. Whether a paper copy may show a handwritten signature field without a separate in-app signature workflow.

Do not begin implementation until these choices are reviewed and the backend's receipt/payment source models and statuses are confirmed.
