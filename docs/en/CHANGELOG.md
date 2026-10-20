# Changelog

## 0.4.2 — Relay Stability Fix

## Important

TGWS-Android 0.4.2 — Relay Stability Fix. Android 16+, code 19, test candidate.
Update **both the APK and your existing Cloudflare Worker**. This is not a
Production acceptance claim.

## Fixes

- Early post-Upgrade disconnects enter bounded route backoff. A permit has one
  terminal result; only actual inbound progress resets runtime failure history.
- HTTP endpoint overload shares cooldown across DCs, with bounded Retry-After
  and one recovery probe.
- Peer Close code/category survive a failed echo. Normal client EOF performs
  bounded WS Close before raw socket release.
- Network invalidation closes active WSS/TCP scopes, including TLS setup.
- Worker failure before its read loop releases stream locks; admission waits
  for both TCP and WS closure. Binary mode/handlers precede accept.
- One failed session does not hide another live session on the same route.

## Technical

- Schema 3/journal records numeric HTTP Upgrade, allowlisted Worker category,
  cooldown and individual session termination/elapsed/byte/Close metadata.
- 503/CAPACITY, 503/CONFIGURATION, 502/UPSTREAM_CONNECT and 504/UPSTREAM_TIMEOUT
  are distinct. No response bodies, URLs, tokens or arbitrary peer text are logged.
- CI supplies a Worker module, checksum and source/checked commit metadata.
- Existing UI, settings, secrets, TLS checks and system DNS remain intact.

## General information

Worker limits remain 4 sessions/isolate, 4 MiB + 4 byte queue, 64 messages,
16 MiB/session across both directions. BYTE_LIMIT/IDLE_TIMEOUT remain explicit.
Large media can still reach the budget. Automated regressions do not certify
external Cloudflare or Telegram stability on a device.

## 0.4.1 — extended diagnostics

Test update based on the 0.4.0 candidate / PR #22. Android 16+, version code 18. Application ID, protected configuration and keys are preserved. No production publication.

## New features

- Categorized INFO / WARNING / ERROR journal: service, storage/configuration, network, DNS, TLS, TCP, WebSocket, relay, HTTP/2, routes, MTProto clients, local checks and system WebView.
- Combined severity/category filters, first/last occurrence times and repeat counts; up to 128 in-memory events and 32 actual setup measurements.
- Route details: DC/media, transport, stage, attempts, measured DNS/TCP/TLS/HTTP times, close code and safe close category. Missing measurements stay unknown.
- Cancellable one-shot loopback TCP check without sending a secret; it does not authenticate MTProto or confirm Telegram connectivity.
- Observation-based troubleshooting suggestions and explicit preview/copy/sharing of redacted JSON reports.

## Fixes

- Reconnecting no longer displays timings from the previous attempt.
- Wrapped native connection failures retain their DNS/TLS/TCP category through bounded, cycle-safe cause traversal.
- Route failures, closures, repeated failures and handshake rejection reasons are retained.
- Cancelling a local check closes its socket; stale completion cannot reset a newer check indicator.

## Optimization

- Repeated warnings coalesce within 10 seconds. Logcat emits occurrences 1, 2, 4, 8…; distinct successful setup measurements remain distinct.
- Lazy journal list, manual checks only and foreground-only provider package monitoring.

## Technical

Selected WebView provider/package/version and changes are recorded without loading an engine, adding WebKit or creating web UI. No page/renderer exists in TGWS and other apps’ logs are inaccessible. Channel is inferred only for known package names; latest Stable/Beta is never assumed. Future numeric versions are supported without a hardcoded version threshold.

Report schema 2 excludes secrets, relay/IP/DNS addresses, carrier and messages. Read-only FileProvider scope adds only the report cache directory. TLS, system DNS, native transports, Keystore and Update Center remain intact; built-in Cloudflare DNS stays removed.




## 0.4.0 — Premium Minimal candidate

Android 16+ stable candidate. Production publication requires a trusted Release signature and owner approval. A test Debug APK is not a Production release.

## Features
- Home, Diagnostics and Settings tabs with separate connection, transport, appearance and update pages.
- Service uptime, traffic/session counters and actual transport setup measurements.
- Optional memory-only log bounded to 128 events and 32 samples, without secrets or addresses.

## Fixes
- In-memory settings drafts survive Activity recreation without plaintext secrets in savedInstanceState.
- Back navigation returns from subpages without stopping the proxy.

## Performance
- Diagnostics timer runs only while its screen is open and lifecycle started; no unsolicited active probes.
- Bounded history, no log persistence and no shader when visual effects are disabled.

## Technical
- Shared Premium Minimal tokens, original tgws_symbol and light/dark adaptation including Update Center.
- versionCode 17 and minSdk 36; native transports, system DNS and update verification preserved.
- Separate Release build without debug signing fallback. PR CI verifies unsigned candidates; manual signing workflow checks the pinned trusted certificate.

## Important
- Different signing keys cannot update one another despite sharing an applicationId. A new Release key cannot inherit the Debug installation or its Keystore. No automatic uninstall or seamless migration is offered.
- Transport setup time is not Telegram ping. External relay availability depends on the network.
- Owner performs physical testing; private feedback is not published. Previously reported HD-media behavior still needs verification on the actual route.

## 0.3.10-alpha

- Native Update Center, Stable/Preview channels, WorkManager, persistent options and check results.
- Cancellable progress downloads, SHA-256 and full APK/rotation verification before Android-confirmed installation.
- Documented release manifest/headings and UI/lifecycle/0.3.9 replacement-install regressions.

## 0.3.9-alpha — network fixes

- Enabling Fake TLS keeps plain Obfuscated2 with the same secret compatible with Flowseal. TLS-looking random init bytes are authenticated as Obfuscated2 first; Fake TLS rejections have their own category.
- A large MTProto packet remains one WebSocket message, fragmented at 64 KiB. Actual write progress renews deadlines; stalled writes still expire. Ping/Pong can interleave fragments.
- Partial-message download deadlines renew only after 64 KiB of data, with a hard lifetime of five read windows. Control frames and tiny slow drips cannot renew the budget.
- Suspension/resume and per-app block/unblock of the same Android network clear old connections, DNS, pool and backoff. TCP_USER_TIMEOUT bounds unacknowledged data to 30 seconds; the local listener remains available for Telegram reconnects.
- VersionCode 15, Android 16+, unchanged application ID, signing certificate and protected settings. No personal reports. The existing Worker 16 MiB/session limit remains; this update does not remove every external network restriction.

## 0.3.8-alpha — privacy

- Remove personal validation reports, device/carrier details, About report and build metadata copies.
- Preserve automated tests, settings and application ID. Network/media issues remain under active investigation.

## 0.3.7-alpha — reporting update

- Superseded by the privacy update; its debug APK was withdrawn.

## 0.3.6-alpha — Android 16+ only

- Correct the RU/EN About label to Android 16+; APK minimum remains API 36.
- Remove retired platform reports, release-note files and images from the current tree; preserve Git history. Use verified API 36/37 screenshots and update SVG labels/instructions.
- CI checks minSdk, localized branding, resources, documentation and the emulator matrix. Android regressions check both locales and the actual About screen.
- Upgrade regression now uses the SHA-pinned Android 16+ 0.3.5 baseline, preserving credentials, Keystore and settings.
- versionCode 12, unchanged applicationId/debug certificate. Transports and Worker limits are unchanged.

## 0.3.5-alpha — credential continuity and session reliability

- Decode pasted `dd`/`ee` secrets without truncating the key; preserve the Fake TLS domain.
- Persist configuration before sharing a Telegram link or secret; stop export on Keystore failure.
- A missing secret record with existing settings/key now requires explicit recovery instead of generating a replacement.
- Empty local TCP probes are quiet; truncated handshakes and timeouts are classified separately, including Fake TLS.
- Fix concurrent collection snapshots during service stop and network cancellation.
- Clear current session errors on stop and ignore late callbacks. Keep rejection counters as last-run history.
- Add UI, Keystore, lifecycle and in-place 0.3.4 APK upgrade regressions.
- Integrate PR #16 media-route traffic and sanitized WSS close categories; retain the Worker 16 MiB production budget. Normal close is recorded without a false session failure.
- Stable release still requires owner physical validation, usable cellular routes and trusted production signing.

All notable changes will be documented in this file. Versions of this Android port do not correspond directly to upstream Flowseal releases.

## 0.3.4-alpha — 2026-10-16

- Removed built-in Cloudflare DNS, its selector and production DoH dependency. Migrate the obsolete preference to system DNS without changing other settings.
- Bounded TCP Happy Eyeballs: IPv4/IPv6, 250 ms stagger, two in-flight attempts, total deadline and loser cleanup.
- Android DNS cancellation releases the waiting worker; network loss cancels queries.
- Retained historical DoH regressions as test-only fixtures; added connection/migration regressions and an APK DEX gate.
- Preserved configured Worker/CF/H2 routes and strict TLS; documented DNS/IP/SNI/captive portal/IPv6/allowlist options with primary sources.
- TGWS-16-016/017: native UI without WebView, CI provider inventory and owner handoff workflow.
- Physical carrier/Wi-Fi/Telegram validation pending; no universal bypass claim.

## Infrastructure — 2026-10-16 (no application version change)

- TGWS-16-016: recorded native/no-WebView applicability, future Stable/Beta feature/security/recovery requirements and pending physical matrix. No unnecessary WebKit dependency or hidden engine.
- TGWS-16-017: permanent owner-only phone testing, no ADB/device requests; source/tree SHA, CI URL and report template in successful APK artifacts.
- CI native declaration guard and allowlisted emulator provider/device/OS metadata before/after tests. Absence/unknown/AOSP differ; inventory does not certify latest Stable/Beta. Original test failures cannot be masked by metadata failures.

## [0.3.3-alpha] — 2026-10-16 (debug)

- Separate Transparency effect switch: actually translucent cards/navigation over the app backdrop, independently or alongside Liquid Glass.
- Off by default, persists separately from Liquid Glass and protected proxy settings; applies without restarting the service. Text and controls remain opaque.
- Premultiplied alpha in native RuntimeShader; root window stays opaque. No capture of other apps/wallpaper, extra permissions/dependencies or continuous animation.
- Native UI tests cover changing parent backdrop compositing, both themes/effect combinations, text visibility, persistence/disabling and unchanged MTProxy/DNS.
- versionCode 9, same applicationId/debug key. Beta appearance inspired by Liquid Glass; physical appearance/battery validation pending.

## [0.3.2-alpha] — 2026-10-16 (debug)

- Optional Liquid Glass beta: frosted panels/navigation, blue/teal backdrop, edge highlights and refraction in both themes. Settings → Appearance → Liquid Glass.
- Off by default, including upgrades. Persists separately from protected proxy configuration and applies immediately without restarting the service.
- Native Android RuntimeShader; no new dependency, screen capture or continuous animation loop. Refraction samples a procedural backdrop, not arbitrary content behind panels.
- Native UI test covers default off, actual rendering/text visibility, light/dark, Activity recreation, disabling/restoring normal appearance and unchanged proxy/DNS settings.
- versionCode 8, existing applicationId/debug key; physical appearance/performance validation pending. App remains alpha.

## [0.1.2-alpha] — 2026-10-15 (debug)

- Owner logo in the app/README, adaptive launcher icon and monochrome notification icon.
- Installed label TGWS-Android without Alpha; versionCode 3, alpha stage retained in version metadata.
- Safe handshake rejection categories and counter separate from service/network errors; validation and routes preserved.
- Updated persistent instructions/specification baseline/roadmap; explicitly labelled debug Pre-releases are authorized.

## [0.1.1-alpha] — 2026-10-15 (test build)

### Fixed
- Healthy WSS sessions no longer share one absolute 60-second receive budget. Control traffic renews the wait; an idle peer receives a Ping with a bounded Pong deadline.
- Partial-message deadlines and memory bounds remain enforced; an unanswered heartbeat or wrong Pong cannot keep a dead peer open.
- Buffer WebSocket headers/masks/payload before TLS writes and enable TCP_NODELAY.
- Increase MTProto inactivity limit from 60 to 300 seconds; handshake/connect/write deadlines remain unchanged.
- Clear obsolete fallback errors on a successful new connection. Show failures in other sessions as historical events when a transport is working; display separate WSS/TCP session counts.
- Increment versionCode to 2 and derive CI artifact version from versionName.

### Verification and limits
- Version 0.1.1-alpha requires a new physical-device test; all causes of real disconnections have not been established. See the [Проверки для разработчиков](../TESTING.md).

## [0.1.0-alpha] — 2026-10-14 (source snapshot, unreleased)

### Added

- Local MTProto server, obfuscated2/AES-CTR, direct WebSocket and TCP fallback code.
- Foreground service, settings store with Android Keystore and first JVM tests.
- Open-source repository scaffolding, MIT attribution, contributor docs and CI definition.

### Limitations

- Debug APK and automated tests passed CI; physical-device Telegram E2E and full upstream feature parity remain pending.
- Fake TLS, Cloudflare transports, media HTTP/2 and robust mobile network support are pending.

### Fixed during alpha preparation

- Validated MTProto handshake/framing and independent AES-CTR streams; reproducible byte-level Flowseal v1.11.2 vectors added.
- Strict RFC 6455 masked/RSV/continuation/length/Upgrade/Ping/Pong/Close checks with bounded I/O and preserved TLS verification.
- Separate WSS redirects and TCP routes; bounded fallback, sessions and workers with owned sockets closed on stop/network loss.
- Idempotent foreground service startup and distinct local/remote state.
- Hidden secrets, explicit Keystore recovery, validated Telegram links and complete RU/EN Android string resources.
- SHA-pinned CI actions, checksums/build metadata/reports, instrumentation sources and a separate device workflow.
- CI rejects empty/incomplete Android reports and checks the actual emulator API; fixed dependent shell command execution and API 37.0 AVD metadata, with 16 CI script tests.
- Version remains `0.1.0-alpha`; physical Telegram E2E is not run, so the release gate prevents a tag and Pre-release.

[Русская версия](../../CHANGELOG.md)
