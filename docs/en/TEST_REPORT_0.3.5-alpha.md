# 0.3.5-alpha verification

19 October 2026. Combined PR #15 and #16. `0.3.5-alpha`, versionCode 11, Android 16+ / minSdk 36, unchanged applicationId.

- Source: `fb72b67775b6dff5a71d804ec596850963b409a6`.
- Checked PR tree: `2f562b42c6819d6fdc518139f7cb5230efd2193f`.
- Passing [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39187502581), [API 36/37 matrix](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39187502586), [Worker checks](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39187502588).

| Check | Observed result |
|---|---|
| Full Wrapper build / JVM | 203 tests, zero failures/errors/skips; debug and instrumentation APKs built |
| Android Lint | Zero errors; 8 existing UseKtx warnings |
| Android 16 / API 36 | All 33 expected JUnit cases; no failures/skips; 11 screenshots |
| Android 17 / API 37 | All 33 expected JUnit cases; no failures/skips; 11 screenshots |
| In-place 0.3.4 → 0.3.5 upgrade | Both APIs preserve secret, ciphertext, Keystore, port, relay, mode, theme and effects |
| Local WSS relay / Worker | 11 / 17 tests; Worker bundle dry-run without deployment |
| CI scripts / publication guards | 56 / 5 Python tests |
| Protocol reference | Existing tests retained; deterministic Flowseal v1.11.2 MTProto/Fake TLS fixtures rechecked byte-for-byte |
| DNS / TLS | APK guard passed across 6 DEX files; built-in Cloudflare DNS absent; strict TLS retained |

Downloaded CI ZIP digests, actual JUnit identities/counts, zero failures/skips, screenshots and metadata were checked. The two manual upgrade phases are additional to the 33 JUnit cases. Upgrade uses a SHA-pinned baseline and one temporary CI signer for old/new/test APK copies, with every ZIP payload byte unchanged. No data is cleared between the two installs. Fixture apps are removed only after upgrade verification, before the ordinary Android suite. The preserved publishing key never enters CI.

Separate 60-second local soaks: WSS/TCP — 26,046 packets, 7,113 sessions, 59 resets; HTTP/2 — 5,357 packets, 1,078 sessions, 58 resets. Resource and completion limits passed. These are local peers, not a Telegram/carrier E2E.

## APK

[Debug pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.5-alpha-debug) · [APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.5-alpha-debug/TGWS-Android-0.3.5-debug.apk).

- Size: 32,206,480 bytes.
- SHA-256: `d658209c94f1f9e8fccf7ef24f2a629f3a5569becc8329e138859baf900f9164`.
- Certificate SHA-256: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.
- Preserved debug key; apksigner verified the signature and every ZIP entry matches final CI. This is not production signing.

## Limits and owner handoff

TGWS-16-016: native Compose/MTProto; no embedded WebView. Before/after emulator inventory recorded `com.google.android.webview`, API 36 `133.0.6943.137`, API 37 `145.0.7632.218`, both `latest_verified=false`. This does not certify current Stable/Beta or One UI compatibility.

TGWS-16-017: physical Telegram, Wi-Fi/LTE, One UI and Stable/Beta checks belong to the owner and remain pending for 0.3.5. Install over the previous debug release without deleting data. Verify the current Telegram link, text/SD/HD, network recovery and 30–60 minutes with the screen off. Share only a redacted diagnostic export with route counters and close categories, never secrets or private relay addresses.

The phone's 2,529 rejections have no proven root cause; reproducible credential divergence and false-diagnostic paths were fixed. Carrier reachability is not guaranteed. The production Worker retains its 16 MiB/session limit; 24 MiB is test-only. HD upload and external transports require physical E2E confirmation. [Stable release gate](STABILITY_GATE.md).
