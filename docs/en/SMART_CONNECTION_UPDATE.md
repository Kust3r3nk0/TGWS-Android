# Smart Connection Update — staged contract

Android 16+ / API 36 only. Preserve the native app, Premium Minimal, three tabs,
encrypted settings/Keystore and localhost listener. Device acceptance belongs to
the owner; do not publish device, carrier, token or private relay information.

## 0.4.2 — Stability & Recovery

Branch `feature/0.4.2-relay-stability`, [PR #26](https://github.com/Kust3r3nk0/TGWS-Android/pull/26).
Revision 2 is 0.4.2/code 20, tag `v0.4.2+rev.2-debug`; the previous code 19 release
and its tag are preserved. [Audit and Worker update instructions](RELAY_STABILITY_0.4.2.md).

Typed HTTP Upgrade/Close/session outcomes, endpoint cooldown, early-runtime
backoff, generation-safe cancellation and bounded cleanup are implemented.
Automatic setup no longer extends the total deadline for each candidate; a
bounded reservation protects permitted TCP fallback. Incomplete client headers
are not diagnosed as wrong secrets. Independent routes are not blocked by
another endpoint's cooldown. No MTProto payload is replayed.

Worker remains limited to six sessions/isolate, bounded queues and 16 MiB/session
summed in both directions: WebSocket.send provides no drain API. Use the existing
stream-backpressured [origin WSS relay](MOBILE_NETWORKS.md) for large transfers.

Install over a matching-signer APK without uninstalling and update the existing
Worker module without changing tokens/domains. Check settings persistence,
Wi-Fi and mobile messages, both network-switch directions, repeated start/stop,
30–60 minutes in the background, recovery, both media directions and resource
release. Verify controlled fallback when the primary route fails and no direct
fallback in relay-only mode. Separate expected Worker BYTE_LIMIT from unexpected
failures; test sustained large transfers on a suitable origin relay.

Share only version/code, mode, failing step/direction, outcome and a reviewed safe
diagnostic report. CI/local peers do not certify real Telegram/Cloudflare stability.

## 0.5.0 — Smart Connection Update · test update

PR #26 is merged and the owner authorized stage 0.5.0 on a separate branch.
See [implementation and acceptance](SMART_CONNECTION_0.5.0.md). Implement bounded, decaying route
statistics and automatic/manual/fallback selection only among user-authorized
transports compatible with DC/media. Add automatic/Wi-Fi/mobile/custom network
profiles, connection health (including insufficient data), bounded real traffic
charts that stop rendering when hidden, and optional de-duplicated outage/recovery
notifications. No aggressive probing or treating TCP setup as Telegram delivery.

## 0.6.0 — Relay & Performance Update · test update

Separate branch/PR after Smart Routing. Add multiple securely stored origin
relays, primary/backup selection and documented deployment from `relay/`, without
in-app SSH. Improve compatible media routing with ordered bytes, partial reads,
progress-aware deadlines and bounded backpressure; no replay/compression/packet
modification. Optimize FGS with callbacks and bounded workers, no speculative
warmup or unnecessary wake locks. Quick Settings requires API 36+ FGS checks first.

## Gates

Keep unit/integration, Lint, Debug, API 36/37, update-persistence and server tests.
Each stage provides a PR, reproducible APK, source SHA, checksum and limitations.
No built-in Cloudflare DNS, weakened TLS/auth/DC allowlist, VPN, hidden WebView
or Zapret. Never auto-merge. Stable requires trusted Release signing and explicit
owner device acceptance and publication approval.

[0.5.0 implementation, setup and acceptance](SMART_CONNECTION_0.5.0.md).

[0.6.0: implementation and acceptance](RELAY_PERFORMANCE_0.6.0.md).
