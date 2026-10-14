# Testing 0.1.0-alpha

Baseline: Flowseal/tg-ws-proxy **v1.11.2**, commit
`307a384ab7f522e34bb1a045884d0bd90f40bee5`. [Русский](../TESTING.md).

## Automated checks

Use JDK 17, Android SDK `platforms;android-37.0` and `build-tools;36.0.0`.
Gradle, AGP and Compose compiler versions are pinned. Do not replace them
to bypass a failing check.

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Golden vectors must come from the unchanged functions in the pinned upstream
with controlled randomness. Python belongs only in the test environment,
never in the APK. Compare regenerated vectors with the committed data instead
of overwriting expected output when a test fails.

JVM tests cover cryptography, framing, the local listener, bounded fallback
and a local WebSocket server. They cannot establish reachability of public
Telegram servers from the user's network.

## Android

Test API 34, 35, 36 and 37 where a stable system image is available.
Instrumentation requires a connected device or emulator:

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

CI also runs `python3 .github/scripts/verify_android_tests.py`, requiring entries
for all 10 tests instead of relying only on Gradle's exit code. Empty reports,
skips, duplicates and failures/errors fail the job. Test the gate and emulator
entry points with `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`.


| Scenario | Expected observation |
|---|---|
| First launch | Secret hidden; listener does not start automatically |
| Start and stop | Loopback port opens and is released; notification removed |
| Background and return | Service remains active; UI shows actual state |
| Repeated service start | No duplicate listener or workers |
| Occupied port | Clear error; foreground service exits |
| No internet | Local availability differs from remote sessions; bounded attempts |
| Invalid secret | Configuration rejected; no automatic replacement |
| Rotation and reopen | Port, hidden secret and service state remain correct |
| Lost Keystore key | Visible recovery error; ciphertext preserved until explicit action |
| Wi-Fi / mobile loss | Old sessions closed; Telegram may create new sessions |

The `specialUse` foreground service represents a user-started persistent
local proxy with an ongoing notification. This app is not a VPN. Store
distribution requires a separate review of the store's foreground-service rules.

## Physical Telegram E2E — release gate

Use a physical Android 14+ device and the official Telegram app.

1. Install the APK and record its SHA-256 and build commit.
2. Start the proxy and open its local `tg://proxy` link using the app button.
3. Enable this proxy in Telegram and verify an established connection.
4. Send and receive a text message.
5. Briefly disconnect and restore networking; verify reconnection.
6. Test Wi-Fi and mobile internet separately.
7. Stop the proxy; verify sessions end and the port is released.

Record:

```text
UTC date:
Git commit:
TGWS-Android version: 0.1.0-alpha
APK SHA-256:
Device / Android / API:
Official Telegram version:
Network type (no personal addresses):
Proxy connection: PASS / FAIL / NOT RUN
Send text: PASS / FAIL / NOT RUN
Receive text: PASS / FAIL / NOT RUN
Network recovery: PASS / FAIL / NOT RUN
Stop and port release: PASS / FAIL / NOT RUN
Notes (no secrets or message contents):
```

Until this scenario passes, do not claim a verified working proxy or create
a GitHub Pre-release meeting the specification. The debug APK is signed with
a debug key and is a test artifact. A distributable release needs a trusted signature.
