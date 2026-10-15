# Experimental Cloudflare Worker relay

[Russian setup guide](../../cloudflare-worker/README.md).

Standalone server for the existing **WSS relay** field in Android 0.1.3/0.2.0;
no APK update is required. It connects a real Cloudflare edge to fixed Telegram
DC IPs from Flowseal v1.11.2. TLS SNI and HTTP Host are your actual
`your-worker.account.workers.dev` name, with normal certificate checks.
Substituting `cloudflare.com` cannot create a relay or change a blocked IP.
Workers domains/edge IPs can also be filtered.

Status: locally tested and bundled; **not deployed or tested with Telegram on
Megafon (Moscow/Moscow region)**. The filtering mechanism is unconfirmed. No
Cloudflare account or endpoint is connected to this environment.

Create your [Cloudflare account](https://dash.cloudflare.com/), verify email,
and review current terms, quotas and pricing. With Node 22+:

```bash
cd cloudflare-worker
npm ci --ignore-scripts --no-audit --no-fund
npm run build
```

Paste `dist/worker.js` into a new Worker in Workers & Pages. Add **Secret**
`TGWS_RELAY_TOKEN`: 32–64 random lowercase HEX characters. Keep it out of source,
screenshots, logs and chat. Deploy and enable the workers.dev address. Disable
Workers Logs/observability because the path contains the token; the provider
still processes metadata. Alternatively use local Wrangler:
`npx wrangler login`, `npx wrangler secret put TGWS_RELAY_TOKEN`, `npm run deploy`.

Stop the Android proxy, enter your actual domain/token, then start it. Example
only; this is not a working endpoint:

```text
wss://tgws-relay.account.workers.dev/<your-TGWS_RELAY_TOKEN>/apiws
```

Keep the local port and MTProto secret. Nonempty relay configuration selects
relay-only without direct fallback; the URL is encrypted in Android Keystore.

Limits: exact DC/IP pairs on TCP 443, token required, 64-byte first binary message,
four connections **per isolate**, 4 MiB + 4 byte queue, 64 queued messages,
6s connect / 10s init and write / 5 minute idle deadlines. Closing sockets retain
capacity until the platform close event. This is not an account-wide quota or
DDoS/billing protection; configure account limits separately.

**16 MiB total per session across both directions**, then Close 1008. Workers'
WebSocket send API has no drain/backpressure interface. This lifetime budget
bounds runtime-owned output buffering; it can cause reconnects and interrupt
large downloads. The template tests reachability/text, **not a claimed stability
fix or production media support**. For long transfers use the Node relay behind
Cloudflare Proxy with your domain/origin and Full (strict), or a future
flow-control protocol. Cloudflare can also close connections for its own limits.

Local tests cover fixed routing, URL/key rejection, actual loopback TCP bytes
through injected WebSocket/Response adapters, ordering, queue limits, failures,
timeouts, cleanup and byte budget. They do not establish cloud runtime or
Telegram E2E. Wrangler dry-run bundles without deployment; CI uploads the module
and actual JUnit report.

After deployment test Wi-Fi, then **disable Wi-Fi** and repeat official Telegram
text send/receive, reconnect and a small download. Record APK commit, Android,
operator, time and result without secrets/URLs. Displayed ping alone does not
establish the route. External deployment and Megafon E2E remain pending.
