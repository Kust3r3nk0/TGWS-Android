# 0.3.0-alpha transports

[Русский](../TRANSPORTS_0.3.md). Android 15+. **Owner physical-device validation: pending.**
Local automated results do not establish public server or carrier reachability.

Stop the proxy before editing. Migration preserves the 0.2 port, secret, relay URL and
direct/relay-only mode. Advanced settings have a separate Keystore AES-256-GCM record;
decryption failure requires explicit recovery, never a silent reset.

| Mode | Allowed routes |
|---|---|
| Direct | Existing Telegram WSS → TCP; IP blocking can affect both. |
| Relay only | Only the configured Worker-compatible WSS relay; no direct fallback. |
| Automatic Cloudflare | Configured Workers → compatible CF Proxy → direct TCP. Use relay-only to forbid direct access. |

CF Proxy, Worker and standalone relay need different server setups. No embedded external
credentials or working endpoints are provided. Configure up to eight CF base domains and
eight Worker URLs, one per line. `proxy.example` yields `kwsN.proxy.example/apiws`;
`wss://worker.example/<key>/apiws` appends `?dst=<pinned-DC-IP>&dc=<DC>` to the supplied path.
Our Worker requires `<key>/apiws`; do not enter a query manually. Examples are placeholders, not working nodes.
Importing the existing relay into Workers is an explicit button action.

Auto tries at most two Workers and two CF domains, at most four non-TCP attempts plus
TCP, eight seconds each and a 40-second overall setup budget. H2 and WSS count separately.
Failed routes rotate with 1–30 second backoff. Setup may send a fresh relay init; user
packets are never replayed and routes never change mid-session. Lists are validated as
a whole on explicit save. There is no automatic remote-list download or hidden source.

An optional Fake TLS domain enables upstream v1.11.2 ClientHello HMAC, ServerHello and
record camouflage on **Telegram → local listener**. Links use `ee` + 32 HEX secret +
HEX domain; no domain uses `dd`. Import accepts raw/dd/ee secrets and local `tg://proxy`
or `https://t.me/proxy` links with server `127.0.0.1`. Invalid local hellos close without
upstream's camouflage forwarding. Remote WSS/H2 always use authenticated TLS and hostname
verification. Local camouflage does not create SNI fronting or remove an IP block.

H2 is opt-in for configured CF media routes only: real ALPN `h2`, `HEAD /api` accepting
2xx/405/501, then MTProto packet-body `POST /api` matching `cf_h2.py`. Arbitrary DCs and
ordinary WSS Workers are not H2 endpoints. Setup failure can fall back before payload;
RST/GOAWAY/IO after payload closes the native session without retry/replay.
Each native session has independent CTR, at most two pending requests and 4 MiB of queued
request bodies, one response buffer up to 4 MiB. The shared dispatcher caps eight requests;
OkHttp handles H2 flow control. Cancellation wakes blocked operations. Maximum eight native
sessions. HTML, bad lengths, oversized replies and wrong TLS/ALPN fail predictably.

DNS and sockets capture one active Android Network per attempt. Address-family fallback is
bounded; AAAA/DNS64/NAT64/464XLAT rely on OS/network support, never fabricated IPv6 addresses.
Default-network/DNS/link changes cancel old DNS/HTTP/sockets and reset sessions/pool/backoff.
Telegram creates new sessions; seamless handover is not claimed. Captive portal and Android
validation status remain distinct; lack of validation alone does not prove CF unreachable.
Monitoring uses callbacks, no periodic reconnect loop or additional wake locks.

Diagnostics show listener/network, optional active data-SIM and registered PLMN, IP families,
DNS/Private DNS/NAT64, actual resolved A/AAAA answers (up to eight, local display only),
DC/media/routes/stages/errors/time/attempts and Telegram-side traffic. Reused H2 connections
show unknown DNS/TCP/TLS phases/answers rather than fabricated measurements.
Actual DNS/TCP/TLS/HTTP setup measurements are not Telegram ping; reused phases are unknown.
An explicit probe opens up to three transport handshakes, no MTProto packets, once per 30 s.
Export requires preview and copy; it excludes URLs/domains/IP/DNS/PLMN/SIM IDs/secrets.
Normal `READ_BASIC_PHONE_STATE` supports optional radio reporting; no dangerous telephone
permission is requested and unavailable fields remain unknown.

[Standalone relay](MOBILE_NETWORKS.md) and [Worker phone guide](CLOUDFLARE_WORKER.md) remain
separate server components. The experimental Worker still has its **16 MiB/session budget**,
which may interrupt large transfers; H2 does not remove it. CF Proxy needs your own compatible
`kwsN.<base>/apiws` and `/api` server; pointing at an arbitrary CF hostname is insufficient.
Public CF/Worker E2E, cellular access and long screen-off behavior await the owner.
[Carrier references and physical matrix](RU_MOBILE_OPERATORS.md).
