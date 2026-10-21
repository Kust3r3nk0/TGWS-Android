# 0.4.2 — Relay Stability Fix

Android 16+ / API 36+, versionCode 20. Built from `feature/0.4.1-diagnostics`.
The existing UI, encrypted settings, protocol routes and TLS checks remain intact.

PR #24 was merged into the source branch during this work. Its six-session pilot
and admission regression are preserved; this update does not raise the limit to
eight. Other memory/byte/queue bounds stay unchanged. CI does not certify the pilot
in an external Cloudflare runtime.

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

The automatic mode previously extended the configured 25-second setup budget to
40 seconds: four 8-second WSS attempts could delay TCP for 32 seconds. All attempts
now share the configured deadline, reserving one attempt (at most half the total)
for permitted TCP fallback. A regression stalls both Workers and verifies that
additional CF probes cannot consume this reservation. Separate overload/cooldown
regressions verify that independent DC/TCP routes still work without waiting.
Relay-only mode still forbids direct fallback; no payload is replayed.

Incomplete client headers (`INVALID_LENGTH`) now receive an incomplete-header
suggestion rather than a wrong-secret suggestion. Unknown format/DC and actual
authentication failures are distinguished. Existing truncated plain/Fake TLS,
DNS/network generation, one-use warm lease, timeout, reset and lifecycle tests
remain enabled. Smart ranking/profiles are deferred to 0.5.0.

Revision 2 uses versionCode **20** so it can update published 0.4.2/code 19.
The old tag/APK is preserved; this candidate is `v0.4.2+rev.2-debug`.

The bounded journal/JSON schema 3 includes numeric Upgrade status, fixed categories,
retry delay, per-run session sequence, termination reason, elapsed time, byte
counts and validated WS Close code/category. It excludes URLs, tokens, endpoints,
payloads and exception text. BYTE_LIMIT/IDLE_TIMEOUT remain explicit session outcomes.
The core-to-UI session-close queue also retains at most 128 events and posts one bounded batch,
so a blocked main thread cannot accumulate an unlimited session-event backlog.

## Update your existing Worker

1. Download `TGWS-Cloudflare-Worker-0.4.2.zip` from the Debug prerelease, or
   `TGWS-Cloudflare-Worker-<checked SHA>` from the successful PR Android CI.
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

Limits stay **6 sessions/isolate**, **4 MiB + 4 bytes queued**, **64 queued messages**,
and **16 MiB/session summed in both directions**. A slot stays occupied until both
WS and TCP closure are confirmed. Missing close confirmation is not bypassed by a
timer. Worker WebSocket.send lacks drain/backpressure; large media may still reach
BYTE_LIMIT and require a suitable origin relay or a future flow-control protocol.

The existing [Node.js WSS relay](MOBILE_NETWORKS.md) provides stream backpressure
for large transfers. It remains user-configured; automatic migration of an active
MTProto session is not supported.

Local/CI success is not external Cloudflare or Telegram E2E acceptance. The owner
tests message transfer, start/stop, background operation, network changes and media
on a device after updating **both** components. Report only build/category/HTTP/Close
metadata; keep device/operator/private configuration details out of public reports.

Official references: [WebSockets](https://developers.cloudflare.com/workers/runtime-apis/websockets/),
[TCP sockets](https://developers.cloudflare.com/workers/runtime-apis/tcp-sockets/).

For an installed 0.4.2/code 19, download revision 2 manually: the existing Update
Center only offers newer semantic versions. Android accepts code 20 over code 19
with a matching signer. This remains a Preview release.
