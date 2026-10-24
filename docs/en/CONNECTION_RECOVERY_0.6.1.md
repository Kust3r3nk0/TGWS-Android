# 0.6.1 — Connection Recovery

Android 16+ / API 36+, code 23. Debug Pre-release based on the published 0.6.0
and current main; not Production.

Two failures are reproducible with controlled transports: cold Smart media
selection can exhaust most of the 25-second deadline on two blocked direct WSS
aliases before a healthy configured Worker; four failing Cloudflare candidates
can consume every non-TCP admission before a configured Worker is tried.
Both regression tests fail before the fix. This does not establish the cause of
every device failure without that session's safe diagnostics.

Speculative WSS/CF/HTTP2 candidates reserve one existing admission and setup time
for a later permitted configured Worker or origin relay. TCP reservation, total
deadline, attempt cap, backoff and endpoint Retry-After remain. Known cooldowns
need no reservation. MANUAL/relay-only permissions remain strict; active MTProto
streams never migrate or replay bytes.

UI, encrypted settings, secret, Keystore, Android network DNS and strict TLS are
unchanged. Worker/Node server code is unchanged from 0.6.0; no redeploy is needed
for this fix. Worker still limits a session to 16 MiB.

Install over a compatible signed Debug APK without uninstalling. Check retained
settings, cold start, Telegram messages, automatic Worker/origin fallback behind
blocked direct routes, media DCs, manual/relay-only, Wi-Fi/mobile changes and stop.
Report the version, mode, listener state and safe diagnostic export if it still
fails. Never share relay URLs/tokens, proxy links, secrets or personal details.
Device Telegram/HD/long-running acceptance is performed by the owner; automated
tests do not prove device connectivity or remove external filtering.
