# Stable release criteria

0.3.5-alpha fixes reproducible credential and session defects. Stable publication requires successful automated checks, owner Telegram/message/media/reconnection and 30–60 minute screen-off testing, working cellular/problem Wi-Fi routes, and trusted production signing with an upgrade policy from debug builds.

TGWS-16-016 remains conditional: the app has no embedded WebView; emulator provider inventory does not certify latest Stable/Beta or One UI. Physical testing belongs exclusively to the owner under TGWS-16-017.

The owner reported 2529 rejected headers on 0.3.4 and suspects a secret change after updates. This has not been proven. SECRET_OR_TRANSPORT is checked before dialing upstream. Found and fixed separately: prefixed-secret truncation, exporting unsaved drafts, and silent regeneration after partial credential-record loss. CI now checks APK replacement over 0.3.4 without uninstalling between versions.

Install updates using the preserved signing certificate. Removing the application or clearing its data creates a fresh installation. If Telegram retains an outdated entry, disable it and add the current link from the app. Never include secrets in reports.

Worker retains its 16 MiB session budget until verifiable flow control exists. PR #16 diagnostics and a larger test-only budget do not establish reliable real Cloudflare HD uploads.

Automated 0.3.5 gate passed: 203 JVM tests, zero Lint errors, 33 Android cases and an in-place 0.3.4 upgrade preserving secret/Keystore/settings on each API 36/37. See the [checked report](TEST_REPORT_0.3.5-alpha.md). Physical and production-signing gates remain open.
