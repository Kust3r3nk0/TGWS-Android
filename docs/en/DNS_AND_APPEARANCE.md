# 0.3.1 DNS, network and interface

[Русский](../DNS_AND_APPEARANCE.md). Android 15+.

Bottom navigation separates Home (proxy status/start/stop, Telegram, network and diagnostics)
from App & MTProxy settings. Existing port/secret/relay/Fake TLS/CF/Worker/H2 formats and mode
remain intact. Save proxy settings without starting the service; switching tabs retains drafts.
Secrets remain absent from savedInstanceState. The top-menu button toggles light/dark; settings
also offer Android system theme. The preference persists separately from protected proxy data.
Colors, surfaces, cards and navigation use Material 3.

Stop the proxy, then select **Cloudflare DNS over HTTPS** under settings. The saved choice
applies at the next proxy start. **System DNS remains the default/migration choice.**

- RFC 8484 POST `application/dns-message` to `https://cloudflare-dns.com/dns-query`.
- Bootstrap IPv4 `1.1.1.1` / `1.0.0.1`, IPv6 `2606:4700:4700::1111` / `2606:4700:4700::1001`.
- A/AAAA, up to eight route answers; 65,535 B response cap, six-second HTTP call,
  6.5-second DNS wait, dispatcher four. Numeric route addresses do not require DoH.
- Numeric bootstrap avoids system DNS; normal certificate and hostname verification remains.
  Sockets capture the active Android Network. Stop/network changes cancel HTTP and waiting
  DNS calls, discard old cache/pool and reject late results. No silent system fallback.

This affects TGWS only: no Android DNS/Private DNS/APN change, VPN or other-app DNS interception.
Cloudflare receives the TGWS domain names after explicit provider selection; queries/replies
are not logged. DNS does not remove IP blocking. Carrier DNS64 with A-only destinations may
require System DNS/XLAT on IPv6-only networks; Cloudflare does not invent carrier NAT64 prefixes.
[Provider addresses](https://developers.cloudflare.com/1.1.1.1/ip-addresses/) and
[DoH documentation](https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/).
Independent client, no affiliation with Cloudflare.

Home automatically observes Wi-Fi/cellular/other and IPv4/IPv6/dual-stack/unknown even while
the proxy is stopped. These are active-interface addresses, excluding loopback/link-local;
Android validation and captive portal are separate. Configuration does not prove reachability
of every remote server. NAT64/Private DNS/routes remain in diagnostics. The UI monitor stops
in background; the running foreground service retains its own monitor. No polling or new wake
locks. DNS/address details remain local and are excluded from anonymous export.

[Automated report](TEST_REPORT_0.3.1-alpha.md). Live Cloudflare, carrier/Telegram and physical
UI validation await the owner.
