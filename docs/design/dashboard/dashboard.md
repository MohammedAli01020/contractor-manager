# Dashboard — Design Specification

**Status:** Draft / not yet approved  
**Platform:** Flutter mobile app  
**Design baseline:** [Design System v1.0](../design-system/v1.0.md)

## 1. Purpose

Give the contractor a quick, trustworthy overview of current financial position and operational activity, with clear shortcuts to frequent tasks.

## 2. Proposed page structure

1. **Company context and header**
   - Company/project identity where applicable.
   - Notifications entry.
   - User/account entry.
2. **Greeting and page title**
   - Short Arabic greeting.
   - Clear title such as "ملخص النشاط".
3. **Period selector**
   - Today / this week / this month / custom period only if supported by the backend.
4. **Financial summary**
   - Cash balance.
   - Bank balance (separate from cash).
   - Revenue for selected period.
   - Expenses for selected period.
   - Net result only when the backend definition is available and clearly labelled.
5. **Operational summary**
   - Completed shifts.
   - Active shifts.
   - Equipment count/status.
   - Pending actions requiring attention.
6. **Quick actions**
   - Add shift.
   - Record expense.
   - Record advance.
   - Inventory issue/receipt where permitted.
7. **Recent activity**
   - Date/time.
   - Transaction/activity type.
   - Description and related driver/equipment where relevant.
   - Amount and status where applicable.

## 3. Important semantic rules

- Do not combine cash and bank balances into a single number unless a clearly labelled total is intentionally provided in addition.
- Revenue and expenses are period totals, not account balances.
- Net result must use the backend's actual accounting definition and must not be described as final profit when adjustments remain.
- Driver advances must remain distinguishable from operating expenses.
- Equipment expenses should show their equipment association when present.
- An estimated cost must not appear as a posted expense.
- Recent activity must not imply that a pending or failed operation was successfully posted.

## 4. Prototype behavior

The initial prototype is for layout and navigation review only.

- Period selector changes the displayed prototype period label; it does not query live data.
- Navigation items open placeholder sections to demonstrate information architecture.
- Sample amounts and counts are illustrative only and must never be interpreted as actual tenant data.
- No API calls, backend writes, or Flutter source changes are part of this prototype.

## 5. Responsive and RTL requirements

- Arabic RTL layout by default.
- Financial amounts remain readable and consistently aligned.
- Cards stack on narrow screens; avoid squeezing four financial cards into unreadable columns.
- Important actions remain reachable without excessive scrolling.
- Long company names and large amounts must not overflow.

## 6. Acceptance checklist

- [ ] Header and company context are clear.
- [ ] The period filter is understandable.
- [ ] Cash/bank balances are not confused with revenue/expenses.
- [ ] Net result label matches backend semantics.
- [ ] Quick actions reflect actual permissions and available workflows.
- [ ] Recent activity distinguishes advances, expenses, income, and non-financial events.
- [ ] Loading, empty, offline, and error states are specified.
- [ ] RTL and small-screen behavior reviewed on Android.
- [ ] Product owner explicitly approves the screen.

## 7. Open decisions

- Which five destinations should appear in primary navigation?
- Should the dashboard show a combined cash + bank total in addition to separate balances?
- Which pending actions deserve a dashboard alert?
- Which summary metrics are available from the current backend without adding new endpoints?
