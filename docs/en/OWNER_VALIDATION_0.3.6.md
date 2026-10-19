# Owner verification — 0.3.6-alpha

Owner questionnaire answers, 19 October 2026. **Partially verified by the owner, with reported failures.** Physical results are separate from CI/emulator checks.

Samsung Galaxy A16, Android 16 / One UI 8.5, Telegram 13.0.3, clarified by the owner. Carrier label: Мега5G (earlier identified by the owner as Megafon, Moscow region). Direct and relay-only modes were used; relay implementation/version is unspecified.

| Scenario | Owner result |
|---|---|
| Upgrade keeps secret/port/relay settings | Passed |
| About says Android 16+ | Passed |
| Repeated start/stop | Owner sees “Ошибка Obfuscated2”; message source and actual listener shutdown not established |
| Text send and receive | Passed |
| Standard photos / voice messages | Work; direction and per-mode breakdown unspecified |
| HD photos / video messages | Fail in both direct and relay-only; direction and cause unspecified |
| 1–5 MiB file | Not tested |
| Wi-Fi and cellular | Both work, with failures in some areas while travelling |
| Wi-Fi ↔ cellular recovery | Requires manual proxy restart |
| Sustained operation | Sometimes brief reconnects, sometimes requires manual restart; exact duration unspecified |
| Themes / Liquid Glass / transparency | Work and persist |

A correlation with particular cell towers is an owner observation, not a proven cause. Client failure, route stalls and carrier restrictions remain to be separated. No universal reachability claim follows.

This does not verify HTTP/2, Fake TLS, Auto CF or specific WebView channels. There is no embedded WebView; renderer/page APIs and unimplemented Live Updates/Quick Settings are not failed app tests. Basic One UI 8.5 scenarios above were tested, not the entire platform.

Next: reproduce start/stop and network recovery failures with regressions, then isolate the shared HD/video path and transfer direction. The Worker 16 MiB cap alone cannot explain direct-mode failure.

0.3.7 carries this versioned report in the app/docs; it does not claim fixes or new physical validation. [Structured report](../owner-validation.json) · [Automated 0.3.6 results](TEST_REPORT_0.3.6-alpha.md).

Release reference: owner identified version 0.3.6-alpha. Published source `71fd2a2b886bdb423ed5823d5152c8d3967f2349`, checked tree `556fd3ea62df8eb1e7ebb435846e5ce1efc74b10`, APK SHA-256 `063373a78944f23265fcee73558d7c5e5a0c2a8b08199ced920faac0fdc75cab`. These are release metadata; no separate installed-file checksum verification was reported.
