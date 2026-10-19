# Changelog

## 0.3.6-alpha — Android 16+ only

- Correct the RU/EN About label to Android 16+; APK minimum remains API 36.
- Remove retired platform reports, release-note files and images from the current tree; preserve Git history. Use verified API 36/37 screenshots and update SVG labels/instructions.
- CI checks minSdk, localized branding, resources, documentation and the emulator matrix. Android regressions check both locales and the actual About screen.
- Upgrade regression now uses the SHA-pinned Android 16+ 0.3.5 baseline, preserving credentials, Keystore and settings.
- versionCode 12, unchanged applicationId/debug certificate. Transports and Worker limits are unchanged; physical validation belongs to the owner.

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
- User reports 0.1.1-alpha working and Telegram showing 6–8 ms; no independent latency or complete E2E claim.

## [0.1.1-alpha] — 2026-10-15 (test build)

### Fixed
- Healthy WSS sessions no longer share one absolute 60-second receive budget. Control traffic renews the wait; an idle peer receives a Ping with a bounded Pong deadline.
- Partial-message deadlines and memory bounds remain enforced; an unanswered heartbeat or wrong Pong cannot keep a dead peer open.
- Buffer WebSocket headers/masks/payload before TLS writes and enable TCP_NODELAY.
- Increase MTProto inactivity limit from 60 to 300 seconds; handshake/connect/write deadlines remain unchanged.
- Clear obsolete fallback errors on a successful new connection. Show failures in other sessions as historical events when a transport is working; display separate WSS/TCP session counts.
- Increment versionCode to 2 and derive CI artifact version from versionName.

### Verification and limits
- The user confirmed text send/receive and downloads in **0.1.0-alpha**, Android 16, Telegram 13.0.2, Wi-Fi, with brief disconnections. This does not verify the update.
- Version 0.1.1-alpha requires a new physical-device test; all causes of real disconnections have not been established. See the [report](TEST_REPORT.md).

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
