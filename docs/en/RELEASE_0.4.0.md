# 0.4.0 production preparation

Status: candidate, not a published Stable release. Baseline: merged PR #19 / 0.3.10-alpha. Android API 36+, compile 37.0, versionCode 17. Native networking is preserved.

UI is split into MainActivity navigation, ProxyFormModel in-memory sensitive drafts, AppScaffold per-page saved scroll, DashboardScreens and SettingsScreens. Update Center shares theme tokens; its Repository, Worker, Downloads and Installer are reused. DiagnosticHistory accepts only typed events and numbers, bounds memory to 128 events/32 samples and never persists logs. Setup time is not Telegram ping. Uptime uses monotonic time; its UI timer stops with lifecycle/navigation. Active probes remain explicit.

Without credentials `assembleRelease` builds an **unsigned, non-distributable candidate**. PR CI checks identity, minSdk, debuggable=false and an 80 MiB budget. Debug signing never substitutes production signing.

Create and securely back up a separate trusted Release keystore outside Git. Configure required owner reviewers for GitHub environment `production-signing` before adding Secrets:

- `TGWS_RELEASE_KEYSTORE_BASE64`
- `TGWS_RELEASE_STORE_PASSWORD`, `TGWS_RELEASE_KEY_ALIAS`, `TGWS_RELEASE_KEY_PASSWORD`
- `TGWS_RELEASE_CERT_SHA256`: independently verified certificate fingerprint, 64 HEX without colons.

Local Gradle uses `TGWS_RELEASE_STORE_FILE` instead of Base64. Never share passwords or keys in chat/logs. Manual **Signed production candidate (no publication)** runs Release tests/Lint/build and verifies APK integrity, expected certificate, identity and no debug certificate. It uploads APK, manifest, RU/EN notes/changelog, source commit, verification and SHA256SUMS only as Actions artifacts. It creates no tag/Release and does not merge the PR.

Without a verified certificate rotation lineage, a new independent Release key cannot update an existing Debug installation or inherit its Keystore. Android rejects the signature mismatch even with the same applicationId. No automatic uninstall or guaranteed data migration is offered. A user may explicitly retain their link/secret and re-enter configuration after knowingly changing installations. The instrumented 0.3.10→0.4.0 `install -r` regression uses the same temporary CI signature on both APKs; it does not prove Debug→Release migration.

Owner tests the supplied compatible Debug APK: three tabs/back/scroll/drafts, themes/fonts/effects, start/stop/background, Wi-Fi/mobile/switching, Telegram SD/HD photos, video messages and large media in both directions, recovery and updating. Keep feedback private and free of secrets/addresses.

Stable gate: passing automatic API 36/37 checks, real screenshots, trusted signed APK and future signature continuity, physical acceptance including HD media and separate owner approval. Only then publish ordinary Release/tag `v0.4.0`.
