# 0.1.2-alpha verification

15 October 2026, UTC. [Русский](../TEST_REPORT_0.1.2-alpha.md).

- Owner logo: original JPEG in README, transparent symbol in the app, adaptive
  launcher icon and monochrome notification icon. Installed label **TGWS-Android**,
  versionName `0.1.2-alpha`, versionCode **3**.
- Minimum **Android 15 / API 35**; other toolchain versions unchanged. Matrix:
  API 35, 36, 37.0, **11 instrumentation tests per API**, including installed label,
  adaptive icon, minSdk and state after a rejected client header. Empty/incomplete
  JUnit reports fail verification.
- Rejected client headers have a separate counter/category: length, reserved
  prefix, secret/format or unsupported DC. No header bytes, secrets, keys or
  payloads are logged. Validation remains strict; listener/network errors remain
  visible and are not replaced by a rejected client.

Local complete Wrapper test/lint/debug/instrumentation build passed:
**117 JVM tests, zero failures/errors/skips**, including the original 36 handshake
reference vectors, rejection categories and state/error isolation. **16 CI script
tests** passed. See [GitHub Actions](https://github.com/Kust3r3nk0/TGWS-Android/actions)
for CI/device reports.

The user reports **0.1.1-alpha** working with Telegram showing 6–8 ms. The screenshot
shows zero established transports, one pending client and an earlier rejected
header; it does not identify the rejection cause/current traffic path or establish
independent RTT. The new APK needs another physical test. Full E2E, long-running
stability, mobile data and Wi-Fi/LTE recovery remain independently unverified.

The owner authorized a **debug Pre-release**, not a stable release claim. Debug
signing only; integration access to Actions secrets returns HTTP 403, so persistent
CI signing is not configured. Signing keys are not published.
