# 0.4.2 — Relay Stability Fix

Android 16+ / API 36+, versionCode 19. Built from `feature/0.4.1-diagnostics`.
The existing UI, encrypted settings, protocol routes and TLS checks remain intact.

The audit found missing Upgrade status/reason, early post-Upgrade failures outside
backoff, lost peer Close metadata when the echo failed, abrupt normal client EOF,
active sockets outside factory cancellation, a Worker reader-lock leak before its
read loop started, and admission released before upstream TCP finished closing.
Regression tests exercise each case with controlled clocks and local peers.

The client shares endpoint cooldown across DCs on HTTP 403/404/429/503, observes
bounded Retry-After and allows one recovery probe. Early remote failures before
incoming data preserve exponential route backoff; socket setup alone no longer
resets it. There is no MTProto replay or unlimited retry. Stop/network/deadline
still abort immediately; normal client EOF performs a bounded WS close first.
One failed session does not mark another live session's route unreachable.

The bounded journal/JSON schema 3 includes numeric Upgrade status, fixed categories,
retry delay, per-run session sequence, termination reason, elapsed time, byte
counts and validated WS Close code/category. It excludes URLs, tokens, endpoints,
payloads and exception text. BYTE_LIMIT/IDLE_TIMEOUT remain explicit session outcomes.

## Update your existing Worker

1. Download `TGWS-Cloudflare-Worker-<checked SHA>` from the successful PR Android CI.
   Check `build-info.json` source commit and `SHA256SUMS` against `worker.js`.
2. In the Cloudflare dashboard open your **existing** Worker → Edit code. Replace
   the entire module with `worker.js`, without Markdown fences, and Deploy.
3. Preserve the Worker name, domain/routes and existing secret `TGWS_RELAY_TOKEN`.
   Do not regenerate the Android MTProto secret or expose the private URL.
   Keep observability/Workers Logs disabled because the path carries credentials.
4. Alternatively run `npm ci --ignore-scripts --no-audit --no-fund`, `npm test`,
   `npm run build`, then `npm run deploy` in `cloudflare-worker/` using your account.
5. Install the same-signer 0.4.2 Debug APK over 0.4.1; do not uninstall first.
   Stop and restart the proxy. Preserve the local port, secret and relay settings.

Updating the APK does not deploy the Worker. The historical `worker-v0.1.0-alpha`
module lacks these fixes. Old Worker versions remain protocol-compatible but lack
new HTTP categories: 503/CAPACITY (Retry-After 2s), 503/CONFIGURATION,
502/UPSTREAM_CONNECT and 504/UPSTREAM_TIMEOUT.

Limits stay **4 sessions/isolate**, **4 MiB + 4 bytes queued**, **64 queued messages**,
and **16 MiB/session summed in both directions**. A slot stays occupied until both
WS and TCP closure are confirmed. Missing close confirmation is not bypassed by a
timer. Worker WebSocket.send lacks drain/backpressure; large media may still reach
BYTE_LIMIT and require a suitable origin relay or a future flow-control protocol.

Local/CI success is not external Cloudflare or Telegram E2E acceptance. The owner
tests message transfer, start/stop, background operation, network changes and media
on a device after updating **both** components. Report only build/category/HTTP/Close
metadata; keep device/operator/private configuration details out of public reports.

Official references: [WebSockets](https://developers.cloudflare.com/workers/runtime-apis/websockets/),
[TCP sockets](https://developers.cloudflare.com/workers/runtime-apis/tcp-sockets/).
