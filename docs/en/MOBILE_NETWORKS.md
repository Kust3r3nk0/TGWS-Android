# Mobile networks and a WSS relay

> Current 0.3.4 behavior: [network diagnosis](NETWORK_COMPATIBILITY_0.3.4.md). Version 0.3+ requires explicit Relay-only mode plus its URL; a nonempty field alone does not select it. Built-in Cloudflare DNS is removed.


An [experimental Cloudflare Worker template](CLOUDFLARE_WORKER.md) is available
for testing without a VPS. It needs your Cloudflare account and works with APK
0.2.0. SNI is the actual Worker name, not `cloudflare.com`. Endpoint reachability depends on the active network. A **16 MiB per-session** safety budget may interrupt
large downloads; this is a route/text test template, not a stability claim.

Direct mode uses Telegram IPs for both WSS and TCP fallback. If a carrier blocks
these destinations, changing the loopback port or switching to direct TCP does
not provide another reachable destination. A timeout alone does not establish
the cause: compare Wi-Fi/mobile and record the carrier/country.

Version 0.1.3-alpha adds an optional **relay-only** mode:
Telegram → loopback TGWS → TLS/WSS:443 → your relay → Telegram DC:443.
You need a trusted external relay reachable from that network. No third-party
relay is embedded. DNS changes and TLS alone cannot solve IP filtering, and
networks restricted to an allowlist may block the relay too.

Stop TGWS, enter `wss://your-domain/<random-key>/apiws` in **WSS relay**, then
start it. This example is not a working endpoint. Only WSS on port 443 with a
DNS hostname is accepted. Local Telegram proxy settings can stay the same.
The nonempty field selects relay only, without direct fallback; clear it to
restore original direct WSS/TCP. Check the separate relay session count, then
send/receive/download using official Telegram with Wi-Fi disabled.

The URL is masked, encrypted using Keystore AES-256-GCM with distinct AAD and
excluded from Activity saved state and diagnostics. Do not publish URLs containing
access keys. Explicit recovery after key loss requires re-entering the relay URL.
Certificate and hostname validation remain enabled. Each relay attempt is bounded
to 8 seconds. Network changes close sessions; Telegram can reconnect.

The wire contract follows Flowseal v1.11.2 `proxy/bridge.py::_cfproxy_worker_fallback`:
`/apiws?dst=<DC_DEFAULT_IPS[dc]>&dc=<dc>`, first binary message is the 64-byte
relay initialization, then the Obfuscated2 stream. DC 1–5/203 and absolute media
DCs are supported. A protected path prefix is an extension for your own relay.
An existing compatible Worker can use `/apiws`. An ordinary website or Worker
without WebSocket-to-Telegram-TCP forwarding will not work.

The standalone [Node.js relay](../../relay/) runs outside the APK. Install Node.js
22+, run `npm ci --omit=dev --ignore-scripts` in `relay`, configure
`TGWS_RELAY_TOKEN` (32–64 random HEX characters), `TGWS_TLS_KEY` and
`TGWS_TLS_CERT` using private host files, then `npm start`. It listens on 443;
configure port permissions, service supervision, firewall and certificate renewal.
See [Russian deployment instructions](../MOBILE_NETWORKS.md) for commands.
The relay validates the path key before dialing, allows only fixed Flowseal
IP/DC pairs, bounds sessions/frames/deadlines and uses stream backpressure.
No packet contents or request URLs are logged. The path key and local MTProto
secret are separate credentials.

Cloudflare Proxy in front of an origin is a possible separate deployment using
your domain/account. Keep origin TLS valid, select **Full (strict)** and enable
WebSocket support. A reachable Cloudflare edge IP is still required. Relay code
and configuration do not imply a successful deployment or a working carrier route.
No Fake TLS, domain fronting, pools or automatic public-relay discovery is added.

Local JVM tests cover route/crypto preservation, URL/GCM, bounded failure with no
direct fallback and real TLS Upgrade. Local Node tests cover real TLS/WSS/TCP,
bidirectional bytes, fragments, credentials, trust, capacity and timeouts. Android
tests cover encrypted URL persistence and explicit recovery. **External relay to
Telegram, Cloudflare deployment and physical mobile-network E2E remain unverified.**
Record device, carrier/country, APK version/commit and send/receive/download/reconnect
results before claiming a working mobile route.
