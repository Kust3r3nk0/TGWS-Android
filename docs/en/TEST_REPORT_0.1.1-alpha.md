# 0.1.1-alpha verification

Date: 15 October 2026, UTC. [Русский](../TEST_REPORT_0.1.1-alpha.md).

## User test of the previous build

The user confirmed text send/receive and downloads through **0.1.0-alpha** on
Android 16, Telegram 13.0.2, Wi-Fi, with brief disconnections and WSS/TCP error
messages. Device model and the installed APK commit are unconfirmed; the offered
CI artifact used `c1ef979`. No independent developer physical-device test was run.

## Reproduced defects and fixes

- Live control traffic, idle heartbeat and clearing obsolete fallback errors
  failed regression tests before the fixes; these tests now pass.
- WSS used one absolute 60-second receive deadline, including control frames.
  Successful control frames now renew the wait. After 60 seconds of silence a
  masked Ping (8 random bytes) requests a matching Pong within 10 seconds.
  No response closes the socket. Heartbeat does not enter the MTProto stream.
- Partial frame/fragmented-message assembly remains bounded to 60 seconds;
  control traffic cannot extend it. Close retains its own deadline.
- MTProto inactivity is bounded to 300 seconds; WebSocket heartbeat does not
  count as MTProto payload activity. Other deadlines and the 8-client limit remain.
- Buffer WebSocket writes by at most 16 KiB before TLS, and enable TCP_NODELAY.
  Certificate/hostname verification, cryptography and routes are preserved.
- A new established connection clears obsolete session errors. Failures in other
  sessions appear as historical events while a transport works; local failures
  remain errors. Separate WSS/TCP counters show the actual transport.

## Automated checks

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
```

Local: **113 JVM tests, zero failures/errors/skips**, **16 CI script tests**,
Lint and both APKs passed. Actionlint validates both workflows; RU/EN have 52
matching string keys. Existing tests are preserved; the silent-peer negative test
now checks Ping followed by closure when no Pong arrives.

The API 34–37 matrix executes 10 instrumentation tests per API and requires actual
JUnit records. Results: [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/workflows/android-ci.yml)
and [Android device tests](https://github.com/Kust3r3nk0/TGWS-Android/actions/workflows/android-instrumented.yml).

## Remaining validation

Whether **0.1.1-alpha** eliminates the real disconnections, long-running stability,
mobile data, Wi-Fi/LTE transitions and all WSS/TCP failure causes are unverified.
Local tests do not establish Telegram reachability from the user's network.
Repeat on the user's device: send/receive/download, several minutes idle, another
exchange, background/return and Wi-Fi loss/recovery.

Debug signing only; no trusted distribution release, tag or Pre-release is made.
[Historical 0.1.0-alpha report](TEST_REPORT.md).
