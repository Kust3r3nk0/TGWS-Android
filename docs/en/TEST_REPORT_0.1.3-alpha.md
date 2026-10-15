# 0.1.3-alpha verification

15 October 2026 UTC. Android 15+, minSdk 35, versionCode 4.
Optional Worker-compatible WSS relay-only routing, encrypted URL and bounded DNS.
Original direct routes remain the default. The standalone relay is outside the APK.

The full Wrapper command (JVM, Lint, Debug APK and instrumentation APK) passed
with JDK 17. **126 JVM tests** passed with zero failures/errors/skips, including
existing Flowseal vectors and new relay crypto/routes, GCM/AAD, deadlines, DNS
and real local TLS path/query tests. Lint: **0 errors**, 3 existing UseKtx warnings.
**10 Node.js tests** passed: real TLS/WSS/TCP bytes, fragments, authentication,
certificate trust, capacity, payload size and connect/idle/stalled-write deadlines.
All **16 CI script tests**, **4 publication guard tests** and actionlint passed.

There are **13 required instrumentation tests**. GitHub CI and execution on API
35/36/37.0 are pending push and will be recorded from actual JUnit reports before
publishing the debug Pre-release.

**Not verified:** external relay deployment, carrier/country, physical cellular
Telegram E2E or a working IP-filtering bypass. Earlier user-reported Wi-Fi success
does not validate this route. A reachable external relay and actual send/receive,
downloads and reconnect tests are required. No full Cloudflare implementation,
Fake TLS, HTTP/2, IPv6-only or seamless handover is claimed.

[Relay setup](MOBILE_NETWORKS.md) · [Russian report](../TEST_REPORT_0.1.3-alpha.md).
