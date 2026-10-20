# TGWS-Android 0.4.1 verification

[Full report (RU)](../VERIFICATION_0.4.1.md) · [Diagnostics](DIAGNOSTICS.md).

Test update, version 0.4.1/code 18, Android 16+/minSdk 36, unchanged application ID. [PR #23](https://github.com/Kust3r3nk0/TGWS-Android/pull/23) depends on #22; neither was merged by Codex. No Production release.

APK source `41e34af33828eb7f264c5f9d5243cdb00480a2ab`; CI merge `8aadb9fa58cf562b97048491c08d3b7792f0e431`, tree `3fa800218e28a074610f712388fa4c8ca455f613`.

Passed [Core CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39469515262), [API 36/37](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39469515372), [checked publication](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39470969581):

- 270 JVM tests; 61 actual Android methods on each API, no failures/errors/skips.
- Debug/Release Lint: 0 errors, 24 warnings per CI variant; local Debug 23 warnings. Full local Wrapper/build/test APK and unsigned Release passed.
- Python guards 80, publisher 6, standalone relay 11, Worker 17.
- Actual 60-second MTProto soak: 25,493 packets/6,958 sessions; maxima 4 sessions/3 pool/6 peer sockets. H2: 5,209 packets/1,043 sessions, maximum 5 HTTP calls.
- Actual APK upgrade from 0.3.10-alpha to code 18 preserves secret, exact ciphertext, Keystore, proxy/relay/transport/appearance/update options on both APIs, using one temporary CI signature on both packages.

New tests independently compare provider metadata with Android, exercise real empty loopback connections and occupied port errors, filters/recreation, recording UI toggle, safe JSON/read grants/cache bounds and isolation from the APK installer. Start intentionally saves with a fresh GCM nonce; values are compared across Start, then ciphertext stays unchanged during diagnostics/recreation/unsaved editing.

[Debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.4.1-debug/TGWS-Android-0.4.1-debug.apk), 35,510,147 bytes, SHA-256 `21c04ccce090008b71c2625519358266a6bdcb23f6c4d9bfcd1061131df0cbeb`. Preserved debug certificate `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`. All APK ZIP payload entries match successful CI. All six public assets were downloaded back, five checksums validated and the APK certificate reverified. PREVIEW manifest: version041/code18/min36.

Unsigned Release: debuggable=false, 27,335,664 bytes, SHA-256 `72a7caf80b74cf33e157709683036b206bf77c3985ec3c20a5cdf9d1c97b184b`; no production signature or distribution claim.

Real [journal](../images/0.4.1/journal-dark.png) and [WebView/local check](../images/0.4.1/webview-dark.png) PNGs are unchanged copies from API 36 artifact 12286452994, one of 29 screenshots/API. PNG hashes and archive digest are in the Russian report. The error is an intentionally occupied local port. Installed emulator providers are 133.0.6943.137/API36 and 145.0.7632.218/API37, matching independent inventory, **not latest Stable/Beta certification**. No engine/pages/renderer is created; other apps’ logs are inaccessible.

[Changed files](https://github.com/Kust3r3nk0/TGWS-Android/pull/23/files). Install over a prior build with the same certificate without uninstalling. Check proxy/Telegram/media/network changes plus journal filters, recording toggle, local check, JSON preview/share and provider changes. Physical/OEM/latest-channel testing belongs to the owner; private reports are not published. In-memory history and a TCP check cannot prove Telegram. Trusted Production signing and separate approval remain required.

An additional harness fix connects UiAutomator before the first Compose tree, avoiding stale virtual IDs on API 37. Navigation/credential assertions remain active and timeouts were not increased. This changes AndroidTest only, not the published APK. Latest checks are attached to the PR.
