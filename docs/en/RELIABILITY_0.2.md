# 0.2.0-alpha reliability

The GitHub 0.2.0 plan covers reference vectors, strict handshakes, pools, rotation,
controlled retry, cancellation and load testing. Flowseal v1.11.2 remains pinned;
dependencies, package ID and Android 15+/minSdk 35 are unchanged.

Only **unused** direct WSS sockets enter the pool after TLS/Upgrade, before MTProto
initialization. Each lease is consumed once; active/completed MTProto sessions are
never returned. Maximum: one connection per IP/hostname, four globally and two
background connect workers with a bounded queue. Default DC/media mappings stay
unchanged. Warmup follows successful client demand, not listener startup.

Idle age is at most 30 seconds, checked on rotation and checkout. Expiry closes a
socket without perpetual idle refill. Checkout verifies a Ping with a fresh nonce
and matching Pong before MTProto init; early data, Close, invalid frames or timeout
discard it. A fresh attempt remains within the session's route deadline. Background
connects are capped at 8 seconds; ownership precedes connect/TLS. Network changes
discard idle/in-flight work and reject late results from the previous generation.

This native Android policy differs intentionally from upstream pool sizes, ages
and refill backoff. **TCP and custom relay connections are not pooled**: the 0.1.3
relay expects MTProto initialization within 10 seconds, incompatible with storing
an unused connection for 30 seconds.

Setup failures receive per-route monotonic cooldowns: 1, 2, 4, 8, 16, then up to 30
seconds. After cooldown one half-open probe is allowed; success resets the route.
An old failure cannot overwrite newer success; a network change resets cooldowns.
Direct WSS → TCP and relay-only isolation remain intact. There is no sleeping
client worker, infinite retry loop or payload replay after AES-CTR has advanced.
Telegram handles reconnection of active sessions; four cipher states remain
independent per session.

Tests cover pool limits, single-use leases, expiry, canceled/late resources, Pong,
early data rejection, backoff and network reset, preserving all existing vectors.
The soak uses real local TCP and TLS/WSS, an independent Java AES-CTR peer, four
clients, ef/ee/dd, multiple packets/session, fragmented input, injected WSS failures
and network resets. It verifies reply bytes and resource bounds, logging numeric
statistics only. It does not connect to Telegram.

Run the full Wrapper command with `-PsoakSeconds=180` for an extended run. The
default is 2 seconds; CI uses 60; supported values are 1–3600.
[Actual results](TEST_REPORT_0.2.0-alpha.md). Physical-device, prolonged public
Wi-Fi/LTE, external relay and carrier tests remain unverified.
