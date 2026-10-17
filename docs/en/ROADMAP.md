# TGWS-Android · Development roadmap

Current minimum: **Android 16 / API 36**, per owner instruction on 17 October 2026. Android 14/15 are no longer supported; prior CI evidence and reports remain historical. Follow the [specification baseline](../DEVELOPMENT_BASELINE.md).


## 0.3.5-alpha — credential continuity and session reliability

- Decode pasted `dd`/`ee` secrets without truncating the key; preserve the Fake TLS domain.
- Persist configuration before sharing a Telegram link or secret; stop export on Keystore failure.
- A missing secret record with existing settings/key now requires explicit recovery instead of generating a replacement.
- Empty local TCP probes are quiet; truncated handshakes and timeouts are classified separately, including Fake TLS.
- Clear current session errors on stop and ignore late callbacks. Keep rejection counters as last-run history.
- Add UI, Keystore, lifecycle and in-place 0.3.4 APK upgrade regressions.
- Stable release still requires owner physical validation, usable cellular routes and trusted production signing.

## 0.3.4-alpha — network compatibility

- [x] Remove Cloudflare DNS from APK/UI, system DNS migration without configuration loss
- [x] TCP IPv4/IPv6 interleaving, 250 ms stagger, two attempts, bounded resources/deadlines
- [x] Blackhole/refusal/cancellation/network-change regressions, migration and APK gate; previous tests preserved
- [x] [Network failure research and options](NETWORK_COMPATIBILITY_0.3.4.md); strict TLS preserved
- [x] Wrapper/CI: 190 JVM, zero Lint errors, 26 cases each API 35/36/37; APK/metadata verified
- [x] [0.3.4 Debug pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.4-alpha-debug), preserved certificate/checksum/reports
- [ ] Owner's physical carrier/problematic Wi-Fi validation

## TGWS-16-016 / 017 — WebView applicability and physical ownership

- [x] Audit: native Compose/MTProto, no embedded WebView; no unnecessary WebKit dependency
- [x] CI declaration guard, emulator provider inventory before/after and regression gate tests
- [x] Owner-only phone testing, handoff/report template with version/source/tree SHA and CI URL
- [x] CI tooling verified, including exact before/after provider metadata and resolved owner handoff
- [ ] Owner Android 16 Stable/Beta physical native-app regression
- [ ] One UI 8.5 Stable/Beta when available; One UI 9.0 when an environment exists
- [ ] If a real WebView UI is added: feature guards, renderer recovery, security/performance and mandatory channel tests

[WebView](WEBVIEW_COMPATIBILITY.md) · [Physical testing](PHYSICAL_TESTING.md).

## 0.3.3-alpha — transparency effect

- [x] Independent persistent appearance switch, off by default
- [x] Actual translucent cards/navigation while text remains opaque
- [x] Both themes, standalone and with Liquid Glass
- [x] Native backdrop compositing, visibility and persistence tests
- [x] Final CI: 181 JVM, zero Lint errors, 25 Android cases each on API 35/36/37; native PNGs inspected
- [x] [Checked 0.3.3 debug prerelease](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.3-alpha-debug) with preserved certificate, checksum and reports
- [ ] Owner physical appearance/performance validation

## 0.3.2-alpha — Liquid Glass beta

- [x] Optional glass panels/navigation in light and dark themes
- [x] Persistent appearance switch, off by default, separate from MTProxy configuration
- [x] Native UI test for actual rendering, persistence and disabling
- [x] Final CI: 181 JVM, zero Lint errors, 23 Android cases each on API 35/36/37; native PNGs checked
- [x] [Checked debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.2-alpha-debug) with preserved certificate, checksum and reports
- [ ] Owner physical appearance/performance validation

## 0.3.1-alpha — interface and DNS

- [x] Final CI: 181 JVM and 22 Android cases each on API 35/36/37, actual light/dark screenshots

- [x] Two Material 3 tabs, separate settings and save without starting
- [x] Persistent light/dark/system themes and top-menu toggle
- [x] Optional Cloudflare DoH with A/AAAA, strict TLS and cancellation; preserve System DNS
- [x] Automatic Wi-Fi/cellular and IPv4/IPv6/dual-stack on Home, including while stopped
- [x] DNS/network tests, Android UI/theme/persistence and CI screenshots
- [ ] Owner physical UI/DNS/cellular/Telegram validation

[Behavior and limits](DNS_AND_APPEARANCE.md) · [Report](TEST_REPORT_0.3.1-alpha.md).

## 0.3.0-alpha — extended transports and Android

This stage merges the former 0.3.0 and **all 0.4.0 items**. [Owner specification (RU)](../TZ_0.3.0-alpha.md).
Checks below refer to implementation and automated validation, not physical acceptance.

- [x] Fake TLS HMAC hello/response, `ee` link import/export and record framing; pinned v1.11.2 vectors
- [x] Separate direct / relay-only / automatic CF; Worker → CF Proxy → TCP, bounded rotation/backoff without replay
- [x] Validated `kwsN.<base>` domains and Worker URLs; encrypted settings and 0.2 migration
- [x] Compatible CF media HTTP/2 `/api`: ALPN, multiplexing, flow control/backpressure, RST/GOAWAY and cancellation
- [x] Route/network/session/traffic diagnostics, actual measured phases, explicit probe and anonymous export preview
- [x] IPv4/dual-stack, active Android Network AAAA/DNS64/XLAT support and bounded family fallback
- [x] Wi-Fi ↔ cellular cleanup: cancel old DNS/HTTP/sockets, reset pool/backoff and create fresh native sessions
- [x] Callback-based power behavior, no demand-free perpetual warmup or additional wake locks
- [x] MegaFon/MTS/Beeline/T2 reference cards; active data-SIM versus registered PLMN and unknown/denied telemetry
- [x] Android 15/16/17 instrumentation, Keystore migration and real local TLS/H2/Fake TLS integration
- [x] Preserve standalone relay and experimental Worker server components with local tests
- [ ] External CF/Worker → Telegram E2E; physical IPv6-only, handover and 30–60 minute screen-off tests
- [ ] Owner physical RU-01–14 matrix on available SIMs; not a debug Pre-release blocker
- [ ] Full experimental Worker flow control replacing its current 16 MiB/session budget

[Transports and limits](TRANSPORTS_0.3.md) · [Carrier matrix](RU_MOBILE_OPERATORS.md) · [Report](TEST_REPORT_0.3.0-alpha.md).
No automatic remote-list refresh is enabled: configuration changes are explicit. Seamless migration and test DCs are not claimed.

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
- [ ] Android 16+ / minSdk 36; verify the updated API 36–37 CI matrix in a fresh run
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

## v1.0.0 — First stable release gates

- [ ] Upstream feature parity or explicitly documented intentional deviations
- [ ] Android 16+ validation on emulators and physical ARM64 hardware
- [ ] Security audit; CI unit and integration test coverage
- [ ] Signed APK/AAB builds, update process, release notes and SBOM

Issues/PRs should link to the relevant milestone above.

[Русская версия](../../ROADMAP.md)
