# 0.3.4 network compatibility

[Русский](../NETWORK_COMPATIBILITY_0.3.4.md). Research: 16 October 2026.

Cellular and Wi-Fi networks can restrict route reachability. Identify the failing stage; DNS changes alone do not restore blocked routes.

The app now uses the active Android Network's resolver, system Private DNS and supplied DNS64
answers. It removes only the obsolete DNS preference; encrypted configuration and configured
Worker/CF/H2 endpoints remain. Historical DoH tests are test-only, excluded from the APK.
TCP interleaves families before the eight-answer cap, preserves the first OS preference, staggers
attempts by 250 ms and permits two in flight. A shared pool is capped at eight workers/32 queued
tasks, with total deadlines, loser cleanup and stale-network rejection. Single addresses use no
extra worker. This is the connection phase of Happy Eyeballs, not a complete RFC 8305 engine.
DNS/TCP/TLS/deadline errors are distinguished from HTTP Upgrade errors; no private details are logged.

| Cause | Available option and limits |
|---|---|
| Unreachable built-in Cloudflare DNS | Removed in 0.3.4; no alternate public DNS is silently installed. |
| Failing system Private DNS | Owner checks Android settings; the app respects system policy. |
| Broken IPv4 or IPv6 | Bounded TCP concurrency tries the alternate family; neither family being reachable cannot be repaired by DNS. |
| IPv6-only/NAT64/DNS64 | Use network-provided answers for relay domains; do not invent a NAT64 prefix. Numeric Telegram routes need IPv4/CLAT; full IPv6-only support remains unverified. |
| Telegram IP block | An accessible own/trusted WSS relay on 443 using existing `relay/`, with valid TLS; local port changes cannot change the remote IP. |
| workers.dev/Cloudflare edge block | A custom domain may help name filtering, not blocked edge IPs; accessible independent infrastructure may be necessary. No automatic unknown public relays. |
| WSS filtered, HTTPS allowed | Existing compatible H2 `/api` covers media only; full control-channel H2 is not implemented. |
| SNI filtering | A real reachable domain/correct certificate/server is required. Arbitrary `cloudflare.com` SNI does not create routing or hide IPs. Local Fake TLS does not automatically disguise the outbound relay path. |
| ECH | Future native TLS/configuration work, absent today; ordinary SNI substitution or WebView is not ECH. IPs remain visible. |
| Captive portal/no internet/network allowlist | Owner completes Android Wi-Fi login; an accessible permitted endpoint is necessary. No universal connectivity guarantee. |
| Worker download cap | Existing template closes sessions above 16 MiB; use own backpressured relay for long transfers. This is not DNS. |
| TLS interception/name/certificate error | Repair server/network; verification stays enabled. |

No new external infrastructure is provisioned: building an APK does not create a reachable relay.
After installing the preserved-certificate Debug pre-release, keep existing port/secret/relay;
no new MTProto or Worker token is needed. Test cellular with Wi-Fi off, repeat on affected Wi-Fi,
run the bounded explicit route probe, record safe categories/version/SHA/network/date, then test
bidirectional text, downloads and screen-off stability. A handshake probe is not Telegram E2E.
Use the [Проверки для разработчиков](../TESTING.md), without endpoints/keys/SIM IDs/messages.

Primary sources, read via Firecrawl; GitHub provides repository/CI evidence:
[Android DnsResolver](https://developer.android.com/reference/android/net/DnsResolver),
[Android Network](https://developer.android.com/reference/android/net/Network),
[RFC 8305](https://www.rfc-editor.org/rfc/rfc8305),
[DNS64 requirements](https://developers.google.com/speed/public-dns/docs/dns64),
[Worker TCP restrictions](https://developers.cloudflare.com/workers/runtime-apis/tcp-sockets/),
[ECH requirements](https://developers.cloudflare.com/ssl/edge-certificates/ech/).
Google DNS and ECH are not added. This covers principal failure classes; it is not exhaustive
or evidence of the cause in the owner's carrier network.
