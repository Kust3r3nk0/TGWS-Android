# 0.2.0-alpha verification

15 October 2026 UTC. Android 15+/minSdk 35, versionCode 5.
[Policy](RELIABILITY_0.2.md) · [Russian](../TEST_REPORT_0.2.0-alpha.md).

The full JDK 17 Wrapper command passed. Final source: **139 JVM tests**,
zero failures/errors/skips; Lint **0 errors / 3 existing UseKtx warnings**.
Debug and instrumentation APKs built. There are **14 required Android tests**,
including five start → two leases/pool → stop cycles and worker cleanup checks.
All original Flowseal v1.11.2 vectors and tests are retained.

An extended local TCP/TLS/WSS soak ran **180 seconds**: **76,799 reply-verified
packets**, **20,992 completed sessions**, 179 network resets and 193 expected
reset disconnects. WSS/TCP attempts: 5,925/15,848. Maximum sessions: 4/8;
ready/in-flight pool: 3/4. There were 4,237 prepared WSS leases; peak successful
WSS/TCP sessions were 4/4. This uses an independent local Java AES-CTR peer,
ef/ee/dd, fragmentation, multiple packets per session and injected WSS failures.
The report passed `verify_soak.py`. The full command with this soak passed for the final source; CI repeats
a 60-second soak for the exact release commit.

**11 Node relay tests**, **22 CI script tests**, **4 publication guards** and
actionlint passed. CI rejects short/cached mismatched, empty, skipped, failing
or incomplete soak results and exceeded resource bounds.

GitHub build/60-second soak and **14 tests per API 35/36/37.0** are pending push;
actual downloaded JUnit reports must pass before debug publication.

Physical Telegram 0.2.0, prolonged public Wi-Fi/LTE, an external relay and
carrier reachability remain **unverified**. Earlier Wi-Fi reports do not validate
this version. TCP/relay pooling, active-session reuse, seamless handover, Fake TLS,
full Cloudflare and HTTP/2 are not included. Debug signing is preserved from
0.1.2/0.1.3; the stable release gate remains open.
