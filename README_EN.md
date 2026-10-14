# TGWS-Android

**Unofficial, open-source Android 14+ port of [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy).** Developed under **Makarov Lab's 3.0™**.

> **Pre-release warning:** This is an early **0.1.0-alpha source snapshot**, not a verified functional build or a full feature-parity port. Android build and live Telegram compatibility are not yet validated. Do not rely on it as your sole means of communication.

## Goal

Build a native Kotlin / Jetpack Compose app exposing a local MTProto WebSocket proxy. This app is **not a device-wide VPN**. It targets Android API 34+ and will be developed towards feature parity with the upstream project's WebSocket, TCP fallback, Fake TLS, Cloudflare Proxy/Worker and HTTP/2 transports.

## Current source snapshot

- Initial local MTProto listener, obfuscated2 and AES-CTR processing
- Initial WebSocket/WSS transport and TCP fallback
- Android foreground service, Compose UI and Android Keystore-backed settings
- Unit-test source for cryptography and packet framing

Not yet ported: Fake TLS, Cloudflare routes and worker pool, HTTP/2 media multiplexing, IPv6-only and network handover management, test datacenters, complete tests, and signed releases.

## Build prerequisites

Android Studio compatible with Android Gradle Plugin 9.4.0, JDK 17, Android SDK Platform 37, and Gradle 9.8.1. There is currently no committed Gradle wrapper JAR. With a locally installed Gradle 9.8.1, run `gradle :app:testDebugUnitTest :app:assembleDebug`. **Build success has not been verified yet.**

## Versions

Kotlin 2.4.21, Android Gradle Plugin 9.4.0, Gradle 9.8.1, minimum Android 14 / API 34, compile/target SDK 37, Java bytecode target 17. Python is used only as an upstream reference language; it is not included in the APK.

## Roadmap and community

- [Feature parity roadmap](docs/en/ROADMAP.md)
- [Contributing](docs/en/CONTRIBUTING.md)
- [Security disclosure](docs/en/SECURITY.md)
- [License](LICENSE) / [Attribution](docs/en/NOTICE.md)

Upstream baseline: Flowseal/tg-ws-proxy **v1.11.2**, commit `307a384ab7f522e34bb1a045884d0bd90f40bee5`.

This is an independent community port, not an official Flowseal Android release. MIT licensed; original Flowseal attribution is preserved.
