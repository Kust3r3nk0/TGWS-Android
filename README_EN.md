# TGWS-Android

<p align="center">
  <img src="docs/images/tgws-github-cover-en.svg" width="100%" alt="TGWS-Android — native MTProto proxy for Android, English cover" />
</p>

[![Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Kust3r3nk0/TGWS-Android/actions/workflows/android-ci.yml) [![Android](https://img.shields.io/badge/Android-16%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/16) [![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE) ![Status](https://img.shields.io/badge/Status-0.3.5--alpha-orange)

**[Download Debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.5-alpha-debug/TGWS-Android-0.3.5-debug.apk) · [Русский](README.md) · [Roadmap](docs/en/ROADMAP.md) · [Test results](docs/en/TEST_REPORT_0.3.5-alpha.md) · [Contributing](docs/en/CONTRIBUTING.md)**

**Unofficial, open-source Android 16+ port of [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy).** Developed under **Makarov Lab's 3.0™**.

> **0.3.5-alpha is a test build with the preserved debug signature.** CI passed 203 JVM tests, 33 Android tests each on API 36/37 and an in-place 0.3.4 upgrade preserving credentials/settings. Physical Telegram/carrier validation of this version is pending.

## Languages & technology

![TGWS-Android languages and tools: Kotlin, JavaScript, Python, Bash, AGSL, Android, Compose, Gradle and GitHub Actions](docs/images/tgws-stack.svg)

| Language | Role |
| --- | --- |
| **Kotlin** | Native Android application, MTProto core, Jetpack Compose UI and tests |
| **JavaScript · ES modules** | Standalone WSS relay and Cloudflare Worker in `relay/` and `cloudflare-worker/` |
| **Python 3** | Protocol reference vectors, CI verification and publishing; **not bundled in the APK** |
| **Bash** | Android instrumentation and emulator preparation in CI |
| **AGSL** | Embedded Android RuntimeShader for Liquid Glass/transparency in `LiquidGlass.kt` |

**Resources & configuration:** XML for AndroidManifest, icons and localization; Kotlin DSL
for Gradle; YAML/JSON for workflows/server configuration. Java provides JDK 17; this project
currently has no separate Java source files.

**Platform:** Android 16+, Jetpack Compose / Material 3, Kotlin Coroutines, OkHttp,
Android Keystore, Gradle and GitHub Actions. [Image sources & licenses](docs/en/IMAGE_ASSETS.md).

## Inside the app

<table>
  <tr>
    <td align="center" width="33%"><strong>Light theme</strong></td>
    <td align="center" width="33%"><strong>Dark theme</strong></td>
    <td align="center" width="33%"><strong>Appearance</strong></td>
  </tr>
  <tr>
    <td><img src="docs/images/0.3.3/home-light.png" alt="TGWS-Android 0.3.3: light Home screen with transparency" width="260" /></td>
    <td><img src="docs/images/0.3.3/home-dark.png" alt="TGWS-Android 0.3.3: dark Home screen with transparency" width="260" /></td>
    <td><img src="docs/images/0.3.3/settings-glass.png" alt="TGWS-Android 0.3.3: settings with Liquid Glass and transparency enabled" width="260" /></td>
  </tr>
</table>

Historical **0.3.3-alpha** CI captures (not the current support matrix): Android API 35/36/37, English locale, software GPU.
Beta effects are enabled for this demonstration; they are off by default. These captures
do not establish Telegram connectivity. [Sources and verified SHA-256](docs/en/IMAGE_ASSETS.md).

## Interface and system DNS

Transparency effect: a separate, persistent, off-by-default switch under Settings → Appearance. Cards and panels composite the real app backdrop; works with or without Liquid Glass. Text and controls stay opaque.

Liquid Glass beta: Settings → Appearance. Off by default; persistent frosted panels and navigation in both themes. Native procedural backdrop, no screen capture or continuous animation.

Material 3 now has **Home** and **App & MTProxy settings** bottom tabs. Light/dark/system
themes persist, with a quick theme toggle in the top menu. Proxy configuration is preserved
and can be saved separately without starting the service.

Built-in **Cloudflare DNS is removed**. The app uses the active Android network's DNS,
including system Private DNS and carrier DNS64 answers; other settings remain intact.
Bounded parallel IPv4/IPv6 TCP attempts stagger by 250 ms. Home automatically shows
Wi-Fi/cellular and address families even while stopped.
[DNS behavior](docs/en/DNS_AND_APPEARANCE.md) · [Network failures and options](docs/en/NETWORK_COMPATIBILITY_0.3.4.md).

## Architecture & roadmap

![TGWS-Android: current alpha architecture and planned 1.0 features](docs/images/tgws-android-workflow-en.svg)

*The diagram separates the current unverified alpha source snapshot from features planned for 1.0.*

## Goal

Build a native Kotlin / Jetpack Compose app exposing a local MTProto WebSocket proxy. This app is **not a device-wide VPN**. It targets Android API 36+ and will be developed towards feature parity with the upstream project's WebSocket, TCP fallback, Fake TLS, Cloudflare Proxy/Worker and HTTP/2 transports.

[Download 0.3.5 Debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.5-alpha-debug/TGWS-Android-0.3.5-debug.apk)

## 0.2.0 reliability

For a Cloudflare route without a VPS, see the [experimental Worker setup](docs/en/CLOUDFLARE_WORKER.md).
Your account is required; Megafon reachability is unverified. It has a 16 MiB
per-session budget and does not require a new APK or spoof `cloudflare.com`.

Bounded unused direct-WSS pool, pre-init Ping/Pong, age rotation and per-route
1–30 second backoff. Active MTProto sessions are never reused.
[Policy, soak and limits](docs/en/RELIABILITY_0.2.md).

## Mobile networks

If a carrier blocks Telegram IPs, direct WSS → TCP fallback can fail on both paths.
The app supports an optional **relay-only** route through your reachable
WSS endpoint. No external relay has been deployed or validated on a carrier yet.
[Setup, standalone server and limits](docs/en/MOBILE_NETWORKS.md).

## Current state (0.3.5-alpha)

| Component | Status |
|---|---|
| Obfuscated2, AES-CTR, relay initialization | Implemented and tested: byte-for-byte JVM Flowseal v1.11.2 vectors |
| Abridged / Intermediate / Padded Intermediate | Implemented and tested: JVM, 4 MiB packet bound and malformed length rejection |
| Loopback listener and WSS → TCP fallback | Implemented and tested locally; live Telegram network validation required |
| WebSocket framing, Upgrade and TLS | Implemented and tested with local RFC 6455 / TLS peers; live WSS validation required |
| Foreground Service and network loss | Basic lifecycle tested on API 36–37 emulators; real Wi-Fi/LTE requires validation |
| Compose / Material 3, Russian and English UI | Tested on API 36–37; physical validation pending |
| Keystore AES-256-GCM | API 36–37 including in-place 0.3.4 → 0.3.5 upgrade; missing keys/records require explicit recovery |
| Official Telegram physical-device E2E | Owner confirmed earlier text/SD; HD stalls on 0.3.4/LTE/Worker. New 0.3.5 needs validation |
| Debug APK | Debug-signed test artifact, not a distribution release |
| GitHub debug Pre-releases | Owner-authorized after CI, with debug signing and stated limitations |
| Trusted signed stable APK | Planned; full release gate not passed |
| Fake TLS and CF Proxy/Worker routing | Implemented and tested locally; external E2E unverified |
| HTTP/2 media `/api` | Real local TLS/ALPN/flow-control/multiplexing and RST/GOAWAY tests; needs compatible server |
| Network-bound DNS/sockets, IPv4/IPv6, network changes | Local/model tests; physical DNS64/XLAT and handover unverified |
| Network/route diagnostics and carrier reference | Actual phase timings, explicit probe, anonymous export preview; no APN/DNS/SIM changes |

[0.3 transports and configuration](docs/en/TRANSPORTS_0.3.md) · [Carrier reference/matrix](docs/en/RU_MOBILE_OPERATORS.md).
Seamless migration, test datacenters and full experimental Worker media flow control remain outside this release.

## Build prerequisites

Use Android Studio compatible with AGP 9.4.1, JDK 17, SDK `platforms;android-37.0`
and `build-tools;36.0.0`. The complete Gradle 9.8.1 Wrapper includes SHA-256 validation.

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --no-daemon :app:assembleDebugAndroidTest
```

Run `:app:connectedDebugAndroidTest` with a device or emulator. CI checks pushes to
`main`, Pull Requests and manual runs; successful builds publish the debug APK,
checksum, build version/commit and changelog, with JVM/Lint reports separately.
Action versions are pinned to verified commit SHAs. A separate PR/manual workflow
is configured to run instrumentation tests on API 36 and 37.0; verify new run results.
JVM tests, Lint and APK assembly are required checks. See [testing](docs/en/TESTING.md) and the
[update test report](docs/en/TEST_REPORT_0.3.5-alpha.md).

## Versions

Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.1, minimum Android 16 / API 36,
compile SDK 37.0, target SDK 37, JDK/bytecode target 17. Python is used only
as an upstream test reference and is not included in the APK.

## Alpha behavior

The listener binds only `127.0.0.1`, default port `1443`, after user action.
Local availability differs from pending and established transport sessions.
Neither an open port nor a session count proves Telegram message delivery.

TLS checks certificate trust and the `kwsN.web.telegram.org` hostname. Upstream
default WSS redirects use `149.154.167.220` for DC 2 and 4; other DCs use direct TCP.
Media tries `kwsN-1` before `kwsN`. Existing DC 203 remains a separate TCP route;
test DCs `10001–10005` are rejected. Fronting with disabled hostname verification
is excluded.

WSS checks an idle peer with Ping/Pong after 60 seconds instead of immediately
reconnecting. Partial-message assembly is bounded to 60 seconds, and MTProto
inactivity to 300 seconds. The UI shows WSS/TCP session counts and clears obsolete
fallback errors after a new connection succeeds.

Secrets are hidden by default; reveal, copy and link opening require user action.
Telegram links use `dd` for Padded Intermediate. An optional Fake TLS domain produces an `ee` + secret + HEX-domain link. This local record camouflage does not replace authenticated remote TLS or remove carrier IP blocking.
Lost Keystore keys never silently overwrite stored ciphertext. Diagnostic events
contain categories only. Network changes close sessions so Telegram can reconnect;
seamless migration is not supported.

## Roadmap and community

- [Feature parity roadmap](docs/en/ROADMAP.md)
- [Contributing](docs/en/CONTRIBUTING.md)
- [Security disclosure](docs/en/SECURITY.md)
- [License](LICENSE) / [Attribution](docs/en/NOTICE.md)

Upstream baseline: Flowseal/tg-ws-proxy **v1.11.2**, commit `307a384ab7f522e34bb1a045884d0bd90f40bee5`.

This is an independent community port, not an official Flowseal Android release. MIT licensed; original Flowseal attribution is preserved.

## Codex development and CI artifacts

To implement a specification with Codex, follow the [English Codex workflow](docs/en/CODEX_WORKFLOW.md) and [project agent instructions](AGENTS.md). The existing GitHub Actions workflow runs JVM tests, Android Lint and debug build checks for pull requests to `main`; after a successful run, download the `TGWS-Android-<version>-debug-<commit>` ZIP under **Actions → Android CI → Artifacts**. A successful CI run is not proof of real Telegram connectivity or production readiness.


## WebView and owner testing

Current UI is native Compose: no embedded WebView or WebKit dependency. Provider inventory
in CI does not certify latest Stable/Beta. [WebView applicability](docs/en/WEBVIEW_COMPATIBILITY.md)
and [owner physical-testing workflow](docs/en/PHYSICAL_TESTING.md) define responsibilities.
Successful CI artifacts include version/source/tree SHA and `OWNER_HANDOFF.md`; phone,
One UI, carrier, Telegram and Stable/Beta results are confirmed by the owner.
