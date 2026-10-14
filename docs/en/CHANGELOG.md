# Changelog

All notable changes will be documented in this file. Versions of this Android port do not correspond directly to upstream Flowseal releases.

## [0.1.0-alpha] — 2026-10-14 (source snapshot, unreleased)

### Added

- Initial Kotlin/Compose Android project with Android 14 minimum target.
- Local MTProto server, obfuscated2/AES-CTR, direct WebSocket and TCP fallback code.
- Foreground service, settings store with Android Keystore and first JVM tests.
- Open-source repository scaffolding, MIT attribution, contributor docs and CI definition.

### Limitations

- Debug APK builds in CI, but device testing, end-to-end Telegram connectivity, and full upstream feature parity are still pending.
- Fake TLS, Cloudflare transports, media HTTP/2 and robust mobile network support are pending.

### Fixed during alpha preparation

- Validated MTProto handshake/framing and independent AES-CTR streams; reproducible byte-level Flowseal v1.11.2 vectors added.
- Strict RFC 6455 masked/RSV/continuation/length/Upgrade/Ping/Pong/Close checks with bounded I/O and preserved TLS verification.
- Separate WSS redirects and TCP routes; bounded fallback, sessions and workers with owned sockets closed on stop/network loss.
- Idempotent foreground service startup and distinct local/remote state.
- Hidden secrets, explicit Keystore recovery, validated Telegram links and complete RU/EN Android string resources.
- SHA-pinned CI actions, checksums/build metadata/reports, instrumentation sources and a separate API 34–37 device workflow.
- Version remains `0.1.0-alpha`; physical Telegram E2E is not run, so the release gate prevents a tag and Pre-release.

[Русская версия](../../CHANGELOG.md)
