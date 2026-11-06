# WF-08C — Mobile measured-basis correction

Status: implementation in progress; stacked on WF-08B and not merged.
Parent: `feature/wf08b-measured-correction` at `3b090e4b3eafdc1d0db790c56c4a3e76ed404468`.
Branch: `feature/wf08c-measured-correction-mobile`.

## Scope

Expose the WF-08B measured correction API in the existing Flutter completed-shift correction screen without changing SHIFT behavior or compensation identity.

## UI rules

- SHIFT keeps its editable driver-wage input and existing zero-wage behavior.
- HOUR displays the frozen hourly rate and previous actual hours; the user enters only the corrected positive hours.
- QUANTITY displays the frozen unit rate, unit code, and previous actual quantity; the user enters only the corrected positive quantity.
- HOUR input supports at most 2 decimal places and API precision bounds; QUANTITY supports at most 3 decimal places and API precision bounds.
- For measured bases, do not send `driverWage`; send only the applicable measurement. The backend remains the authority for the calculated wage.
- Show the expected new wage and frozen rate in the confirmation dialog before submission.
- Keep reason, expected version, idempotency key, audit warning, and reversal/replacement explanation.
- Do not expose compensation rate, basis, unit, currency, agreement identity, or worker identity as editable controls.

## Validation

- Widget tests cover HOUR and QUANTITY payloads and assert that no client-supplied `driverWage` or irrelevant measurement is sent.
- Existing SHIFT tests continue to cover zero wages and required correction reason.
- Flutter CI runs with Flutter 3.3.3, full test suite, and analyzer on this branch and its stacked PR base.
- No offline-first implementation is included; this remains a future stage after core stabilization.

## Safety

- This PR is stacked on WF-08B. Do not retarget it to integration until WF-08B is integrated.
- No PR merge, `main` change, or production migration is authorized.
