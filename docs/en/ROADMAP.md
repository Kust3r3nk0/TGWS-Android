# TGWS-Android · Development roadmap

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

## v0.2.0 — Reliability and protocol parity

- [ ] Connection-pool warmup, rotations, backoff and limits
- [ ] Extend protocol vectors when updating the upstream baseline
- [ ] Long-running load and network testing

## v0.3.0 — Extended transports

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
- [ ] Android 14+ validation on emulators and physical ARM64 hardware
- [ ] Security audit; CI unit and integration test coverage
- [ ] Signed APK/AAB builds, update process, release notes and SBOM

Issues/PRs should link to the relevant milestone above.

[Русская версия](../../ROADMAP.md)
