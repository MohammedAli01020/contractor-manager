# WF-08C — Mobile measured-basis correction

Status: implementation complete; automated CI passed on the current implementation head; manual Android device validation remains; stacked on WF-08B and not merged.
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
- Flutter CI on the implementation head `ce224a99e13227f487982bd74a976f30d5bb8a72` passed for both push run [42254959722](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42254959722) and PR run [42254969635](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42254969635): Flutter 3.3.3 setup, dependency resolution, full test suite, and analyzer all succeeded.
- Manual Android device validation remains before phase acceptance.
- No offline-first implementation is included; this remains a future stage after core stabilization.

## Safety

- This PR is stacked on WF-08B. Do not retarget it to integration until WF-08B is integrated.
- No PR merge, `main` change, or production migration is authorized.


## Manual Android acceptance checklist

Run against an isolated WF-08B/WF-08C test environment and a tenant containing completed shifts. Do not use production data.

- [ ] Open a completed HOUR shift; verify the frozen hourly rate and previous hours are visible.
- [ ] Enter a valid positive hour value with up to 2 decimal places; verify the preview uses the frozen rate.
- [ ] Try blank, zero, negative, more than 2 decimal places, and above-maximum values; submission must be blocked with a clear validation message.
- [ ] Confirm the correction; verify the audit/reversal explanation is readable and the request completes once.
- [ ] Reopen the shift; verify the new hours and calculated wage are displayed, while the hourly rate remains unchanged.
- [ ] Repeat for QUANTITY; verify the frozen unit rate and unit code, and allow up to 3 decimal places only.
- [ ] Verify invalid quantity values are blocked and the new calculated wage matches the server response.
- [ ] Confirm SHIFT correction still exposes the existing wage field and retains its existing zero-wage behavior.
- [ ] Verify no measured correction UI exposes editable rate, basis, unit, currency, agreement, or worker identity.
- [ ] Verify no duplicate correction is created by repeated taps or a retry after a delayed response; confirm the idempotency behavior.
- [ ] Capture device model, Android version, app build/commit, test tenant, and pass/fail notes. Do not include credentials or personal worker data in evidence.

Phase acceptance requires all applicable checks to pass on the real device. Any mismatch blocks WF-08D progression until fixed and revalidated.
