# 0.3.0-alpha validation

16 October 2026 UTC. [Русский](../TEST_REPORT_0.3.0-alpha.md).
Android 15+ / minSdk 35, versionCode 6. **Owner physical-device validation: pending.**

Implemented: Fake TLS HMAC/records/ee links, direct/relay-only/Auto CF, bounded Worker →
CF → TCP routing/backoff, compatible CF media H2, active Android Network DNS/sockets and
cleanup, route/network/traffic diagnostics with actual phase timings, anonymous export,
four carrier reference cards and encrypted-settings migration. [Transports](TRANSPORTS_0.3.md).

Local automated results: **176 JVM cases**, zero failures/errors/skips, preserving the original
139 cases and pinned Flowseal v1.11.2 commit `307a384ab7f522e34bb1a045884d0bd90f40bee5`.
New upstream Fake TLS/HTTP-media byte vectors; real local strict TLS/ALPN H2, multiplexing,
256 KiB flow control, backpressure/cancel, RST/GOAWAY and negative HTTP/ALPN/size cases.
Fixture trust is test-client-only; production TLS verification remains strict.

Both local soaks ran **180 seconds** and were independently checked from JUnit:

| Metric | TCP/TLS/WSS | Full native MTProto → core → TLS/H2 |
|---|---:|---:|
| Completed packets | 77,695 | 14,501 |
| Completed sessions | 21,249 | 2,839 |
| Network resets | 179 | 178 |
| Expected reset disconnects | 187 | 640 |
| Peak native sessions / bound | 5 / 8 | 4 / 8 |
| Peak WSS / TCP / H2 | 4 / 4 / — | — / — / 4 |
| Peak warm WSS / bound | 3 / 4 | — |
| Peak HTTP calls / bound | — | 4 / 16 |
| Peak test-process heap | 118,258,104 B | 115,862,512 B |

18,523 HTTP requests in the H2 soak. These are local fixtures without Telegram accounts;
heap includes the fixture and is not phone APK memory/battery. Network resets create fresh
native cipher sessions without replay. CI requires actual 60-second runs of both soaks.
Also passed: 28 CI/AVD-script tests, four publication guards, 11 Node relay tests,
15 Worker tests and actionlint. Source contains **19 required Android cases** per API:
existing lifecycle/Keystore plus migration/tamper, monitor stop/optional permissions,
real local H2 and full Fake TLS → core → TLS/H2.

## Build and CI

Verified application source: `7bb77b5354e7ae4e6c2274ab85315025a166bcf0`.
Documentation may be newer; application/build inputs remain identical.

- Complete JDK17 Wrapper checks: **BUILD SUCCESSFUL**, 176 passing JVM cases,
  Lint zero errors / three existing UseKtx warnings, debug and instrumentation APK assembled.
- [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38828242098): **success**,
  176 JVM cases, zero failed/error/skipped, Lint zero errors and both measured 60-second soaks.
- [Android matrix](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38828242138): **success**,
  API35 **19**, API36 **19**, API37.0 **19**. All **57** actual JUnit records were downloaded
  and checked for required identities, duplicates, missing cases and failures/skips.
- Artifact ZIP SHA-256 digests match GitHub. PR merge tree equals source. Every signed-APK
  ZIP entry matches CI payload. Test p12/CA is absent from the app APK.
- Manifest retains applicationId, minSdk35/target37, versionCode6 and `TGWS-Android` label.

CI TCP/WSS soak: 60 s, 26,022 packets, 7,108 completed sessions, 59 resets, 61 expected
reset disconnects, 1,445 warm leases; peak sessions4/8, pool3/4, WSS/TCP3/4.
H2: 60 s, 5,479 packets, 1,106 completed sessions, 58 resets, 189 expected disconnects,
6,938 HTTP requests; peak native/H2 4/8, HTTP calls5/16. Resource bounds passed.

Retained debug certificate:
`26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.
Signed APK SHA-256: `b40411bc922725aeac585adb6402e12ff2065d5e92fa999d37d763cd8db1e58b`.
This is not production signing. The publisher rechecks exact source, both CI/JUnit/soaks,
certificate and APK payload before creating the
[debug Pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.0-alpha-debug).

## Physical acceptance

**NOT TESTED:** 0.3.0 Telegram, external CF/Worker/relay, MegaFon/MTS/Beeline/T2 cellular,
real IPv6-only/DNS64/XLAT/dual-SIM/roaming and 30–60 minute screen-off. The owner will
perform available checks later; this does not block a checked debug Pre-release.
Earlier Android16/Telegram13.0.2/Wi-Fi and 0.2.0 6 ms reports do not validate this version.
Local Fake TLS does not bypass IP filtering. CF/H2 requires a compatible external server.
The experimental Worker keeps its 16 MiB/session budget, potentially interrupting large
transfers. No automatic remote-list download, seamless migration or test DC support is
claimed. Stable release gates remain open. [RU-01–14 matrix](RU_MOBILE_OPERATORS.md).
