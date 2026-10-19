# Owner verification — 0.3.6-alpha

Owner questionnaire answers, 19 October 2026. **Partially verified by the owner, with reported failures.** Physical results are separate from CI/emulator checks.

Samsung, Android 16 / One UI 8.5; exact model and Telegram version await clarification. Carrier label: Мега5G (earlier identified by the owner as Megafon, Moscow region). Direct and relay-only modes were used; relay implementation/version is unspecified.

| Scenario | Owner result |
|---|---|
| Upgrade keeps secret/port/relay settings | Passed |
| About says Android 16+ | Passed |
| Repeated start/stop | Failed; exact symptom not yet specified |
| Text send and receive | Passed |
| Standard photos / voice messages | Work; direction and per-mode breakdown unspecified |
| HD photos / video messages | Fail; direction, mode and cause unspecified |
| 1–5 MiB file | Not tested |
| Wi-Fi and cellular | Both work, with failures in some areas while travelling |
| Wi-Fi ↔ cellular recovery | Requires manual proxy restart |
| Sustained operation | Sometimes brief reconnects, sometimes requires manual restart; exact duration unspecified |
| Themes / Liquid Glass / transparency | Work and persist |

A correlation with particular cell towers is an owner observation, not a proven cause. Client failure, route stalls and carrier restrictions remain to be separated. No universal reachability claim follows.

This does not verify HTTP/2, Fake TLS, Auto CF or specific WebView channels. There is no embedded WebView; renderer/page APIs and unimplemented Live Updates/Quick Settings are not failed app tests. Basic One UI 8.5 scenarios above were tested, not the entire platform.

Next: reproduce start/stop and network recovery failures with regressions, then isolate HD/video failures by direction and route. The Worker 16 MiB cap remains a hypothesis for an individual failure, not a diagnosis.

0.3.7 carries this versioned report in the app/docs; it does not claim fixes or new physical validation. [Structured report](../owner-validation.json) · [Automated 0.3.6 results](TEST_REPORT_0.3.6-alpha.md).
