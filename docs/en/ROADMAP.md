# TGWS-Android · Development roadmap

Current minimum: **Android 15 / API 35**, per owner instruction on 15 October 2026. Historical reports remain unchanged. Follow the [specification baseline](../DEVELOPMENT_BASELINE.md).

## 0.2.0-alpha — reliability and compatibility

- [x] Preserve pinned Flowseal v1.11.2 vectors and strict MTProto/WebSocket handshakes
- [x] Bounded fresh direct-WSS pool, single-use leases and pre-init Ping/Pong
- [x] Age rotation without perpetual idle refill; close/cancel on network change and stop
- [x] Per-route 1–30 second backoff, one half-open probe and no payload replay
- [x] Extended local TCP/TLS/WSS soak with ef/ee/dd and network resets
- [ ] Prolonged real Wi-Fi/LTE and physical Telegram 0.2.0 E2E
- [ ] Extend vectors when upstream is separately upgraded; this version retains v1.11.2

[Policy and limits](RELIABILITY_0.2.md) · [Actual results](TEST_REPORT_0.2.0-alpha.md).

## 0.1.3-alpha — alternate route for IP-filtered networks

- [x] Configurable Worker-compatible WSS relay, explicit relay-only mode
- [x] Encrypted URL, strict TLS, relay session counts and bounded DNS
- [x] Standalone relay with fixed DC routes, path access key and stream backpressure; local TLS/TCP tests
- [ ] Reachable external deployment and physical cellular Telegram E2E

Prioritized at the owner's request; not full Cloudflare support or a guaranteed bypass.
[Setup and limits](MOBILE_NETWORKS.md).

## 0.1.2-alpha — branding and diagnostics
- [x] Owner logo, adaptive icon and app label without Alpha
- [x] Android 15+ / minSdk 35; API 35–37 CI
- [x] Safe handshake rejection categories and counter distinct from listener failures
- [x] Instrumented check of installed icon, label and minSdk
- Publish checked debug APKs as GitHub Pre-releases with checksums and commit; retain the stable release gate.

## 0.1.1-alpha — WSS stability
- [x] Ping/Pong and partial-message deadlines
- [x] Buffered TLS writes and TCP_NODELAY
- [x] WSS/TCP counts and recovery from obsolete fallback errors
- [x] User reported Telegram working; 6–8 ms is client-reported, not independently measured
- [ ] Complete repeated E2E and long-running network tests

**Objective:** functional compatibility with the Flowseal TG-WS-Proxy baseline, with Android-native runtime, UI, and power/network lifecycle.

This is a plan, not a statement that a release has shipped.

## v0.1.0-alpha — Initial native implementation

- [x] Kotlin Android project structure and Compose UI code
- [x] MTProto obfuscated2/AES-CTR initial implementation
- [x] Direct WS/WSS and TCP fallback source implementation
- [x] Local foreground service and Keystore-backed settings code
- [x] JVM test sources for crypto and packet splitting
- [x] Green CI: Android build, unit tests and lint (device validation still pending)
- [x] Deterministic byte-level Flowseal v1.11.2 vectors
- [x] Strict WebSocket/TLS/MTProto and malformed packet checks
- [x] Bounded fallback, I/O deadlines, cancellation and safe diagnostic categories
- [x] RU/EN resources, hidden secrets and explicit Keystore recovery
- [x] Android instrumentation sources and API 34–37 CI matrix
- [x] Execute all 10 instrumentation tests on each API 34–37 emulator (see test report)
- [ ] Manual UI and network scenarios on a physical device
- [ ] Device and Telegram end-to-end smoke test

## v0.3.0 — Extended transports
- [x] Experimental Worker server template for the existing relay client; local tests/bundling, 16 MiB/session budget
- [ ] Worker deployment, cloud runtime and Telegram E2E on Megafon Moscow region; full media flow control

- [ ] Fake TLS `ee-secret` and masking
- [ ] Cloudflare proxy fallback and domain selection
- [ ] Cloudflare Worker fallback, HTTP 429 and retry policies
- [ ] Configurable datacenter routing

## v0.4.0 — HTTP/2 media and mobile networks

- [ ] HTTP/2 multiplexing and flow control
- [ ] Replay semantics and bounded memory buffers
- [ ] IPv4/IPv6 dual stack, IPv6-only validation
- [ ] Wi-Fi ↔ mobile handover, battery and reconnection testing

## v1.0.0 — First stable release gates

- [ ] Upstream feature parity or explicitly documented intentional deviations
- [ ] Android 15+ validation on emulators and physical ARM64 hardware
- [ ] Security audit; CI unit and integration test coverage
- [ ] Signed APK/AAB builds, update process, release notes and SBOM

Issues/PRs should link to the relevant milestone above.

[Русская версия](../../ROADMAP.md)
