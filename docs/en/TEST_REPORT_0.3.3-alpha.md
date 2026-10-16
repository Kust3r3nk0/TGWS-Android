# 0.3.3-alpha validation — 16 October 2026

Android 15+, versionCode 9; source `a8020a6984f5dcc1d7b1496cb1e05d03feeca2fe`.

- Full local Wrapper JVM/Lint/debug/instrumentation APK tasks passed.
- [Core CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38860603795): 181 JVM cases, no failures/errors/skips; Lint zero errors, eight UseKtx suggestions.
- CI gate scripts: 31 tests; publication guards: four passed.
- 60-second WSS/TCP soak: 26,034 packets, 7,112 sessions, 59 resets, max sessions 4/pool 3.
- 60-second native H2 soak: 5,565 packets, 1,120 sessions, 58 resets, max sessions 4/HTTP calls 4.
- [Android matrix](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38860603657): 25 actual cases each on API 35/36/37, zero failures/errors/skips. All 75 identities matched source annotations and five archive SHA-256 digests matched GitHub. PR merge tree `9a743a3b1c28357346aa17f148fbc29850537b7b` matches source.

A native test changes the real parent backdrop from red to green; panel pixels follow in both
themes, with and without Liquid Glass. Opaque fills/simulated gradients cannot pass. Another
native test covers default off/corrupt-type fallback, persistence/recreation, independent switches,
light/dark glyph visibility, disabling and identical protected configuration/DNS. Earlier tests retained.

Eleven PNGs per API: prior seven plus transparency-settings/light/dark/combined. Light/dark
transparency and settings with both switches enabled were visually inspected. Emulator/software-GPU captures.

[Debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.3-alpha-debug/TGWS-Android-0.3.3-debug.apk): 32222864 bytes, min35/target37, original applicationId, versionCode 9.
SHA-256 `f8bc57f405452512050c9a9ab6cda38b1c2eb19fae3ec2468c1deaf1f1a1903f`; preserved debug certificate `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`. Payload matches the checked CI APK before signing.

Native premultiplied alpha affects panel fills, not text/buttons. Root window stays opaque;
no capture of wallpaper/other apps/secrets. No new dependencies/permissions/network changes
or continuous animation. Android beta appearance inspired by Liquid Glass, not Apple's system
technology/arbitrary UI refraction. Physical appearance/performance/battery, new Telegram/carrier,
external CF/Worker/H2 and IPv6-only E2E pending. Worker remains limited to 16 MiB/session.
Debug signing is not a production release gate.
