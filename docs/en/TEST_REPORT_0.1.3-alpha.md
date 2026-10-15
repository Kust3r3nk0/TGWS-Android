# 0.1.3-alpha verification

15 October 2026 UTC. Android 15+, minSdk 35, versionCode 4.
Optional Worker-compatible WSS relay-only routing, encrypted URL and bounded DNS.
Original direct routes remain the default. The standalone relay is outside the APK.

The full Wrapper command (JVM, Lint, Debug APK and instrumentation APK) passed
with JDK 17. **126 JVM tests** passed with zero failures/errors/skips, including
existing Flowseal vectors and new relay crypto/routes, GCM/AAD, deadlines, DNS
and real local TLS path/query tests. Lint: **0 errors**, 3 existing UseKtx warnings.
**11 Node.js tests** passed: real TLS/WSS/TCP bytes, fragments, authentication,
certificate trust, capacity, payload size and connect/idle/stalled-write deadlines.
All **16 CI script tests**, **4 publication guard tests** and actionlint passed.

Verified source: `328564b2ec6a8956a5ca4d7f12d44841b4ba85e1`; CI merge `c2614878a653fb25476fc9de2594594dc0ba2245` has the same Git tree.
[Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38758903208) passed: 126 JVM, 11 Node, Lint and both APKs.
[Device CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38758903121) passed. Downloaded JUnit reports satisfy the required gate:
**13/13 tests per API 35, 36, 37.0**, 39 executions, zero failures/errors/skips.
The long-transfer regression fails with the old relay timer and passes after the fix.

[Debug Pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.1.3-alpha-debug).
The APK preserves every final CI ZIP entry byte and uses the same debug certificate
as 0.1.2. SHA-256: `827887c64b1ce7b7edee5391dc021be30f31d51de83c8917e2a9c971cd4250b7`.
This is not production signing or proof of a mobile E2E connection.

**Not verified:** external relay deployment, carrier/country, physical cellular
Telegram E2E or a working IP-filtering bypass. Earlier user-reported Wi-Fi success
does not validate this route. A reachable external relay and actual send/receive,
downloads and reconnect tests are required. No full Cloudflare implementation,
Fake TLS, HTTP/2, IPv6-only or seamless handover is claimed.

[Relay setup](MOBILE_NETWORKS.md) · [Russian report](../TEST_REPORT_0.1.3-alpha.md).
