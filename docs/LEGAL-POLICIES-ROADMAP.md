# Contractor Manager — Legal Policies & Terms Roadmap

**Status:** Planning draft only — not a final legal contract, and not evidence of legal compliance.
**Product:** Contractor Manager, a commercial multi-tenant software service for construction companies.
**Target initial market:** Egypt.
**Branch:** `feature/platform-owner-activation`.

## Purpose

Prepare the product's legal and customer-facing documents before commercial launch. The documents must match the actual product, data flows, subscription model, hosting, support practices, and security controls. Do not publish this planning draft as binding terms without review by a qualified Egyptian lawyer.

## Required documents

1. **Terms of Use / Acceptable Use Policy** — who may use the service, account security, authorized use, prohibited conduct, suspension, service availability, customer responsibilities, and complaint/contact channels.
2. **Privacy Policy** — data categories, purposes and legal basis, disclosures/processors, retention, security, data-subject rights, contact details, and applicable cross-border transfers.
3. **Software License / SaaS Agreement** — limited, non-transferable right to use the service; no transfer of source-code ownership; permitted users and tenants; restrictions on copying, resale, reverse engineering where legally enforceable; and treatment of customer data.
4. **Subscription, Billing and Cancellation Policy** — plan, price, billing period, renewal, grace period, late payment, operational restrictions, cancellation, refunds/credits, and payment-review timelines.
5. **Customer Data Processing and Security Terms** — allocation of controller/processor responsibilities, authorized processing, confidentiality, security measures, subprocessors, incident communication, export, return and deletion.
6. **Backup, Retention, Export and Deletion Policy** — backup frequency and tested restore targets once actually defined, retention periods, export formats, deletion process, and exceptions for legal obligations.
7. **Support, Maintenance and Updates Policy** — support channel and hours, severity definitions, planned maintenance, release/update policy, supported client versions, and limits of support.
8. **Intellectual Property Notice** — ownership of original software, documentation, branding and other original assets; third-party and open-source components remain subject to their own licenses.
9. **Acceptable Use / Security Rules for Customer Accounts** — no credential sharing beyond authorized use, unauthorized access, tenant-boundary bypass, malware, unlawful data, abuse, or attempts to disrupt the service.

These may eventually be combined into a shorter customer agreement plus separate privacy and operational policies, but each topic must be covered clearly.

## Product principles to preserve

- A customer receives a license/right to use the service under the agreed plan; the agreement must not imply that the customer owns the source code.
- Customer companies remain responsible for the accuracy and lawful collection of information they enter, subject to applicable law and the service provider's own obligations.
- Tenant data must remain isolated. Platform Owner access should be limited to documented operational needs and should not mean unrestricted routine access to each customer's personal or financial records.
- Do not claim encryption, uptime, backup frequency, recovery time, deletion deadlines, certifications, or security capabilities unless they are implemented, verified, and operationally maintained.
- Expiry of a subscription must follow the documented product decision: after the agreed grace period, operational actions may be restricted, while login/renewal/support access and customer data are preserved. Final terms must explain this clearly.
- No receipt image is stored by the app in the planned first manual-payment flow; WhatsApp is only a communication channel, and a screenshot alone is not proof of payment.
- Terms and privacy notices should be presented to the customer at the appropriate point in onboarding, with a recorded version and acceptance timestamp where legally appropriate.
- Policy versions and acceptance records must be auditable. Material changes should have a documented notice/acceptance process.
- The company must have a working contact method for privacy requests, support complaints, security incidents, and legal notices before launch.

## Privacy and legal review — Egypt

Before launch, review the product's actual data practices against Egypt's Personal Data Protection Law No. 151 of 2020 and the applicable executive regulations, including Ministerial Decree No. 816 of 2025. Determine with qualified local counsel whether licensing/permits, controller/processor roles, a data protection officer, records of processing, consent/notice wording, retention, incident procedures, and cross-border-transfer requirements apply to the specific business and hosting arrangement.

Do not assume that merely publishing a privacy policy satisfies statutory requirements. The legal analysis must consider which entity operates the service, what personal data is processed, where it is stored, which vendors receive it, and the actual purposes of processing.

## Decisions required before drafting binding customer terms

- Legal name and address of the service provider, and whether the service will be sold by an individual or registered business.
- Support and legal-notice email/address.
- Production hosting provider, hosting country/region, backups, logs, and third-party processors.
- Exact customer data fields, purposes, retention periods, and deletion/export workflow.
- Final subscription price, billing-period arithmetic, renewal/cancellation rules, refunds/credits, and policy for pending payment reviews.
- Actual backup frequency, retention, recovery objectives, and tested restore procedure.
- Support hours, response targets (if any), maintenance notice, and incident notification process.
- Whether the service is B2B-only and whether any consumer customers may use it.
- Applicable dispute resolution, governing law, and contract language, to be set with counsel.
- Inventory of third-party and open-source components and their licenses.

## Release gate

Do not label the policies "final" or launch paid access until:
- product behavior and policies agree;
- actual data flows and subprocessors have been inventoried;
- the required operational controls exist and have been tested;
- the documents have been reviewed by qualified Egyptian legal counsel;
- the customer-facing versions, policy versioning, and acceptance records are in place.

**Important:** This document is a product/legal planning checklist, not legal advice, a finalized policy, or proof that Contractor Manager complies with any law.
