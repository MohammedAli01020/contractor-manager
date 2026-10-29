# Flutter Manual Smoke Test — 2026-10-29

## Scope

This report records the manual smoke tests the project owner reported completing successfully on a physical Samsung SM A525F running Android 14 (API 34).

## Environment

- App: Flutter mobile app in `mobile/`
- Device connection: Android Wireless Debugging / ADB
- Flutter device ID observed by the owner: `192.168.2.195:35119`
- API base URL used for the run: `http://192.168.2.172:8081`
- Runtime define supplied: `--dart-define=API_BASE_URL=http://192.168.2.172:8081`

The command used was:

```bat
flutter run -d 192.168.2.195:35119 --dart-define=API_BASE_URL=http://192.168.2.172:8081
```

The IP addresses above are local-network values from this test session and are not production configuration.

## Manual results reported by the owner

| Check | Result |
|---|---|
| Flutter detects the physical Android device | Passed |
| App launches on the physical device | Passed |
| Sign-in and initial data loading | Passed |
| Add a driver and confirm it appears in the list | Passed |
| Equipment screens and tested actions | Passed |
| Shifts screens and tested actions | Passed |
| Close and reopen app / session persistence | Passed |

## Evidence and limitations

- Results above are based on the project owner's report during the session; they were not independently executed by CI or by this report author.
- This is a manual smoke-test record, not evidence that `flutter analyze`, `flutter test`, Android release builds, or production signing have passed.
- The test used a private LAN HTTP URL. Before production deployment, configure an environment-appropriate HTTPS API URL and verify release signing.
- Keep the API URL configurable; do not hardcode the local machine's IP address as a production default.

## Suggested follow-up

1. Run `flutter analyze` and `flutter test` in the mobile project and record actual output.
2. Add a repeatable Flutter CI job when the supported Flutter/Dart toolchain is agreed.
3. Continue the financial foundation work on its feature branch, preserving tenant isolation and avoiding changes to `main` without explicit approval.
