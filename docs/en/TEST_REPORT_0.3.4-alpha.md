# 0.3.4-alpha validation — 16 October 2026

Android 15+, versionCode 10; source `9756be8d49838c1f7f90f415eb4a5948e101b988`.
Checked PR tree `730e2c9ef376d6c0c1b6b498376ef7091d9837ed` matches that source.

- Full local Wrapper: JVM/Lint/Debug APK/instrumentation APK passed.
- [CI 38974842759](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38974842759): 190 JVM, zero failures/errors/skips; Lint zero errors/eight existing UseKtx recommendations; relay tests passed.
- 50 CI script tests and four publication guards passed. DEX gate confirms retired DoH fixture/library/endpoint absent from six DEX files; native UI declaration audit covers 49 files.
- WSS/TCP soak: 60 s, 26,300 packets, 7,187 sessions, 59 resets; max sessions four/pool three.
- Native H2 soak: 60 s, 5,154 packets, 1,020 sessions, 59 resets; max sessions/HTTP calls four.
- [Device CI 38974842757](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38974842757): 26 exact source test identities each on API 35/36/37, all 78 without duplicates/failures/skips. Five artifact digests verified; 11 native PNGs per API. Inspected light/dark Home and settings showing system DNS with no Cloudflare selector.

Removed Cloudflare DNS without losing protected proxy/UI settings. Existing DoH regressions
remain in test-only Kotlin source sets, excluded from user APK. Seven new connection scenarios
cover broken preferred families, interleaving before the eight-answer cap, both failures,
cancellation/network invalidation, bounded retries and owner-closed sockets. Two tests cover
safe native setup classification/probe. The stalled relay test now expects accurate TIMEOUT
plus relay route, retaining cleanup/no-direct-fallback/no-init assertions.

TGWS-16-016/017: native Compose/MTProto, no embedded WebView; unnecessary WebKit not added.
Declaration auditing is not a complete reflection/binary audit or channel certification. Future
WebView integration requires the documented security/feature/recovery implementation.
CI captured before/after provider inventory without launching/switching a WebView:

| Android/API | Emulator manufacturer/model | Provider | Exact version, unchanged |
|---|---|---|---|
| 15/35 | Google / sdk_gphone64_x86_64 | com.google.android.webview | 124.0.6367.219 |
| 16/36 | Google / sdk_gphone64_x86_64 | com.google.android.webview | 133.0.6943.137 |
| 17/37 | Google / sdk_gphone64_x86_64 | com.google.android.webview | 145.0.7632.218 |

Stable channel is inferred from package name; `latest_verified=false` throughout. These image
versions **do not certify Latest Stable/Beta**. One UI is null/not applicable to these emulators.
Physical Stable/Beta/provider update/One UI validation belongs to the owner and remains pending.
Actions APK includes resolved OWNER_HANDOFF.md with version/source SHA/checked tree/run URL,
build-info, checksums/changelog/native-ui JSON. Device artifacts include before/after JSON.

Debug APK: version 0.3.4-alpha/code10/min35/target37, original applicationId/name. Signed with
preserved debug certificate; every ZIP payload byte matches passing CI. Size 32,186,000 bytes.

- APK SHA-256: `453c6676053c439709508a16024267f502f079b8ce7b8b8cc9952c2bd997eab8`.
- Certificate SHA-256: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.

[Debug pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.4-alpha-debug)
is published after these gates; it is not production signing or stable validation.

The owner reports previous failure in all three modes; exact previous build/SHA/stage unknown.
**Carrier/Wi-Fi/Telegram connectivity restoration is not confirmed.** DNS cannot overcome IP/SNI
blocks or allowlists; an accessible endpoint is necessary. Native IPv6-only/CLAT/external
transports/long-running operation require owner tests. ECH/full control H2 absent; Worker cap
remains 16 MiB. [Research and options](NETWORK_COMPATIBILITY_0.3.4.md).
