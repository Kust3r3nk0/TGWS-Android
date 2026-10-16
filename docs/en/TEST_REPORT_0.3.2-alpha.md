# 0.3.2-alpha validation — 16 October 2026

Android 15+, versionCode 8; source `1d16f94cd06a21cf118cf53878a25672f6cb045d`.

- Local Wrapper JVM/Lint/debug/instrumentation APK tasks passed.
- [Core CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38858140036): 181 JVM cases, no failures/errors/skips; Lint zero errors, seven UseKtx suggestions.
- 31 CI gate script tests and four publication guard tests passed.
- 60-second WSS/TCP soak: 26,245 packets, 7,176 sessions, 59 resets, max sessions 4/pool 3.
- 60-second native H2 soak: 5,535 packets, 1,112 sessions, 59 resets, max sessions 4/HTTP calls 5.
- [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38858140039): 23 actual cases each on API 35/36/37, zero failures/errors/skips. All 69 identities matched source annotations; five archive SHA-256 digests matched GitHub. PR merge tree `5a84167ed5df1ee62f370d5611bfdb2354666465` matches source.

Liquid Glass beta is off by default, including missing/invalid stored values. A native test
covers enabling, Activity recreation/persistence, actual light/dark rendering, visible title/status
glyph pixels, disabling and restoring the normal background. Protected configuration and DNS
remain identical. Transparent surfaces explicitly retain theme foreground colors.

Seven native screenshots per API: home-light/dark, settings-dark, glass-disabled/settings/light/dark.
Glass light/dark and settings were visually inspected. These are emulator/software-GPU captures.
The native shader samples a procedural backdrop; arbitrary underlying UI is not captured/refracted.
No added dependencies/permissions, continuous animation or network-core changes.

[Debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.2-alpha-debug/TGWS-Android-0.3.2-debug.apk): 32202384 bytes, min35/target37, original applicationId.
SHA-256 `d599e19e29ce201e9577eb89d8297c0d9a9a337e411c561ca1797a7ebe90ce3e`; preserved debug certificate `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`. Payload matches the checked CI APK before signing.

Owner physical validation pending: real GPU appearance/performance/battery, new Telegram/carrier,
external CF/Worker/H2 and IPv6-only E2E. Existing Worker cap remains 16 MiB/session.
Debug signing is not production signing or a stable release gate.

[Publication workflow 38859005807](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38859005807)
succeeded. The [GitHub prerelease](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.2-alpha-debug)
is not a draft; tag targets the checked source and published APK digest matches the SHA above.
[Owner Drive copy](https://drive.google.com/file/d/1NODXWBqZpo0Xhdp4K5Q1467F5iACFoOE/view?usp=drivesdk)
was verified by metadata; sharing unchanged.
