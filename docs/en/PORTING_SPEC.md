# TGWS-Android porting specification — summary

**Goal:** independent native Android 14+ client based on the protocol behavior of Flowseal/tg-ws-proxy v1.11.2. The full technical specification is currently maintained [in Russian](../../PORTING_SPEC_RU.md).

Architecture: Kotlin / Jetpack Compose, foreground connection service, MTProto obfuscated2 and AES-CTR, direct WebSocket, TCP fallback, Cloudflare Proxy/Worker, Fake TLS, HTTP/2 media transport, secure settings, diagnostics.

Acceptance gates: first build and unit tests, then interoperability test vectors, malformed-frame fuzz cases, network switching and battery tests, and real Telegram end-to-end verification.

**Current status:** 0.1.0-alpha initial source only; Cloudflare, Fake TLS, H2, full IPv6 support and production readiness are pending.
