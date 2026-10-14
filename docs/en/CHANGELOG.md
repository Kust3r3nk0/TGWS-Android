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

[Русская версия](../../CHANGELOG.md)
