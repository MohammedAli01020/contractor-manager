# Document Center — Design Specification

Status: **Draft for product-owner review**  
Scope: tenant-facing document navigation, generated business documents, attachments, statements, PDF output, and auditability. This is a product design specification; it does not claim that document storage or PDF generation is implemented.

## 1. Purpose

The Document Center gives the contractor one place to find, preview, save, print, and share documents that support the company's operational and financial activity.

It is an organized view of existing source records, not a second accounting system. A document must never create a second expense, receipt, payment, advance, stock movement, or journal entry merely because it is generated, printed, or attached.

## 2. Document categories

### 2.1 Transaction documents

- **Collection receipt:** generated from a confirmed customer receipt/collection transaction.
- **Payment voucher:** generated from a confirmed cash/bank payment, with the payment purpose and linked obligation where applicable.
- **Driver advance voucher:** generated from a recorded advance movement; the advance remains separate from operating expenses.
- **Expense supporting document:** uploaded receipt/invoice/photo/PDF linked to the expense record. The system may also generate an internal expense summary, but it must not present that summary as a supplier-issued tax invoice.
- **Supplier/customer supporting documents:** uploaded files linked to the relevant party and transaction.
- **Inventory documents:** receipt, issue, transfer, return, and stocktake-adjustment documents linked to the corresponding stock movement.
- **Shift work summary:** an operational summary of the shift, driver, equipment, date, quantities/notes, and status; it is not automatically an invoice or revenue record.

### 2.2 Statements

- Driver statement: wages earned, advances, payments, and outstanding balance.
- Customer statement: invoices/charges, collections, credits, and balance where customer accounting is enabled.
- Supplier statement: bills/expenses, payments, credits, and balance where supplier accounting is enabled.
- Cashbox/bank statement: receipts, payments, transfers, and derived balance.
- Inventory movement statement: item and warehouse movement history.

Statements are generated views over source records for a selected period or as-of date. They do not post new transactions.

### 2.3 Management and accounting reports

Reports such as Profit and Loss, Trial Balance, General Ledger, equipment summaries, inventory reports, and the monthly close package remain in Reports/Accounting. The Document Center may provide convenient links to their saved PDF copies, but must not create a competing report definition.

### 2.4 Tax invoices

Legally compliant tax invoices are a separate product capability. They require confirmed jurisdiction-specific legal fields, numbering, tax treatment, and any applicable authority integration. An ordinary receipt, payment voucher, expense attachment, shift summary, or period-close report must never be labeled a tax invoice unless that capability has been separately designed and validated.

## 3. Source-of-truth and linking rules

Every generated document must reference its source record(s), tenant, document type, generation version, and creation timestamp.

- A receipt is generated from an existing posted/confirmed receipt transaction; generating it does not receive money again.
- A payment voucher is generated from an existing payment transaction; printing it does not pay the obligation again.
- An advance voucher references the advance movement; it does not create an expense.
- An expense attachment belongs to the expense record; uploading it does not create another expense.
- An inventory document references one stock movement; it does not create another movement.
- A driver/customer/supplier statement is calculated from ledger/source records and never stores a manually editable balance.
- If one expense is linked to both an equipment and a shift, the same document and source transaction are shown in both relevant contexts but counted once.

The document list should open the source transaction, and the source transaction should expose its associated documents.

## 4. Document lifecycle

Proposed lifecycle:

1. **Available** — generated from a valid source record or uploaded and linked.
2. **Superseded** — a newer document version was generated after a permitted correction; the older version remains inspectable.
3. **Voided** — the underlying source document/transaction was voided through an authorized workflow. The document remains in history and clearly shows its void status.

Do not silently replace a generated document. Re-generation creates a new version with a reason/context where relevant. Uploaded originals should be preserved; replacement or removal must follow permission and audit rules.

A document's status is not a substitute for the financial status of its source transaction. For example, printing a payment voucher does not mean the payment has cleared unless the source transaction says so.

## 5. Numbering and identifiers

- Give every document a stable internal identifier.
- Human-readable numbering may be configurable by tenant and document type, with a clear uniqueness scope.
- Numbering must be concurrency-safe across multiple application instances and retries.
- Do not reuse an issued number after voiding or superseding a document.
- Internal receipt/voucher numbering must be visibly distinguished from any legally regulated tax-invoice numbering.
- Number format, reset policy, fiscal-year scope, and legal requirements remain subject to accounting/product confirmation before implementation.

## 6. PDF, printing, and sharing

For generated documents and statements, support:
- in-app preview;
- PDF generation with Arabic/RTL text and correct Arabic shaping;
- save/download to the device;
- print using the device's available print flow;
- share through the Android share sheet;
- optional email delivery to tenant-configured recipients where appropriate.

PDFs should include the company identity configured by the tenant, document title/type, human-readable number, dates, relevant party, amount/currency where applicable, source reference, and a clear status. Include page numbers for multi-page statements.

A generated PDF is a representation of the source record at a point in time. It must not become a new financial source of truth.

## 7. Attachments and file security

- Support common evidence formats such as PDF and image files, subject to explicit file-size/type limits.
- Validate file type and size on the server; do not trust the filename or client-provided MIME type alone.
- Store files privately; do not expose permanent public URLs for financial documents.
- Authorize every preview/download/share request against tenant and document permissions.
- Use expiring access mechanisms where external sharing is supported.
- Record upload actor/time, original filename, content type, size, and a content checksum.
- Preserve original uploaded evidence. Any removal/retention process must be permission-controlled and auditable.
- Protect against path traversal, malicious files, and cross-tenant access.
- Define backup, retention, and recovery policy before production launch.

## 8. Mobile information architecture

Suggested entry: **More → Documents** (or a primary navigation destination only if usage testing justifies it).

The Document Center home should offer:
- search by document number, party, source reference, and description;
- filters for document type, date range, status, and related party;
- recent documents;
- grouped categories: Transaction documents, Statements, Reports;
- clear empty states and upload/generate actions only where permitted.

A document details screen should show:
- preview and metadata;
- source transaction link;
- related party/equipment/shift/warehouse links where applicable;
- version and status history;
- actions allowed to the current user: download, print, share, email, or upload supporting evidence.

Use large touch targets, Arabic-first labels, RTL layouts, and concise status explanations.

## 9. Permissions and audit

Permissions must distinguish viewing, uploading, generating, downloading, sharing/emailing, voiding, and managing document settings. Do not assume every user who can view a shift can download all financial attachments.

Audit at minimum:
- document generation and version creation;
- file upload and authorized removal;
- download/print/share/email request where technically observable;
- void/supersede action and reason;
- permission or numbering-setting changes.

Tenant isolation applies to document metadata, source links, files, generated PDFs, and archive queries.

## 10. Relationship to monthly close

The monthly close package is an immutable, versioned report package governed by the Accounting Period Close specification. The Document Center may expose a link to that package and its archived PDFs.

Closing a period must not automatically duplicate every attachment into a second file store. The close snapshot should preserve report outputs and the identifiers/versions of supporting records needed to reproduce the review. Retention or legal archiving requirements for original attachments must be defined separately.

If a period is reopened and re-closed, retain the prior close package as superseded history. Do not silently overwrite it.

## 11. Acceptance criteria

- Generating or printing a document never posts a financial or stock transaction.
- Every generated document links to a valid source record and tenant.
- Uploaded evidence is private and access-controlled.
- Repeated generation, retries, and concurrent requests cannot issue duplicate document numbers or duplicate source transactions.
- Voided and superseded documents remain traceable.
- Statements reconcile to source records and clearly show the selected date range/as-of date.
- Arabic/RTL PDF output is readable and printable on common Android devices.
- Users cannot access another tenant's documents by guessing IDs or URLs.
- Tax invoices are not represented as implemented until legal and technical requirements are separately approved.
- The Document Center is a unified index over source documents, not a duplicate accounting ledger.

## 12. Decisions still requiring confirmation

1. Which transaction document types are required in the first release versus later phases.
2. Whether tenant-specific numbering formats are needed initially, and their reset/uniqueness scope.
3. Attachment size, file-type, and retention limits.
4. Whether external share links are allowed or sharing must remain through downloaded files/email.
5. Which roles may download sensitive driver, customer, supplier, and financial documents.
6. Whether customer/supplier statements are in the first release or wait for those accounting workflows.
7. Applicable tax-invoice requirements and whether/when a separate compliant invoicing module is in scope.

These decisions must be reconciled with backend source records, storage capabilities, accounting policy, and local legal requirements before implementation.
