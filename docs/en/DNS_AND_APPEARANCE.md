# 0.3.4 DNS, network and interface

[Русский](../DNS_AND_APPEARANCE.md). Android 16+.

Bottom navigation separates Home (proxy status/start/stop, Telegram, network and diagnostics)
from App & MTProxy settings. Existing port/secret/relay/Fake TLS/CF/Worker/H2 formats and mode
remain intact. Save proxy settings without starting the service; switching tabs retains drafts.
Secrets remain absent from savedInstanceState. The top-menu button toggles light/dark; settings
also offer Android system theme. The preference persists separately from protected proxy data.
Colors, surfaces, cards and navigation use Material 3.

## System DNS (0.3.4)

Built-in Cloudflare DNS is **removed** from the UI, application code and runtime dependencies.
Upgrade removes only the obsolete public `dns` preference (including malformed types), preserving
ports, secrets, encrypted endpoints/lists, themes and effects. Historical strict local TLS DoH tests
remain under `app/src/sharedTest/` and never ship in the user APK; CI inspects DEX for regressions.

Name resolution uses the active Android Network's resolver, including system Private DNS and
network-provided DNS64 answers. No Android DNS/APN/SIM settings or hidden public resolver are
changed. An unavailable system Private DNS is configured by the owner in Android settings.
DNS64/CLAT depends on the network; numeric Telegram routes still require IPv4/CLAT. Full
IPv6-only compatibility is not claimed.

TCP connection attempts interleave address families while retaining the OS's first preference,
stagger by 250 ms, allow two simultaneous attempts and cap answers at eight. The shared dial
pool has eight workers/32 queued tasks; DNS has two/eight. Total deadlines, loser cleanup and
stale-network checks remain. Single-address paths need no extra worker. HTTP/2 retains OkHttp.
This covers the Happy Eyeballs connection phase, not a complete RFC 8305 implementation.

DNS cannot resolve IP/domain blocks or network allowlists. See [network compatibility](NETWORK_COMPATIBILITY_0.3.4.md).
Configured Worker/Cloudflare transports are preserved. Physical connectivity awaits owner testing.

Home automatically observes Wi-Fi/cellular/other and IPv4/IPv6/dual-stack/unknown even while
the proxy is stopped. These are active-interface addresses, excluding loopback/link-local;
Android validation and captive portal are separate. Configuration does not prove reachability
of every remote server. NAT64/Private DNS/routes remain in diagnostics. The UI monitor stops
in background; the running foreground service retains its own monitor. No polling or new wake
locks. DNS/address details remain local and are excluded from anonymous export.

[Automated report](TEST_REPORT.md). Live Cloudflare, carrier/Telegram and physical
UI validation await the owner.

## Liquid Glass beta (0.3.2)

App & MTProxy settings → Appearance → Liquid Glass. Off by default, including upgrades.
The choice persists immediately, also while the proxy is running. Disabling restores normal
opaque Material 3 surfaces. Android RuntimeShader renders a static blue/teal procedural backdrop,
frosted tint, edge refraction and highlights. Text and controls remain crisp in both themes.
It does not capture/refract arbitrary underlying UI or feed secrets into shaders. No animation loop,
network calls, extra permissions or proxy/DNS changes. Physical performance/battery validation pending.

## Transparency effect (0.3.3)

Settings → Appearance → Transparency effect. Independent, persistent and off by default;
works while the proxy runs. Enable alone or with Liquid Glass; toggling one leaves the other
unchanged. Existing themes/effect choices survive upgrades.

Native premultiplied alpha composites panel fills over the real app backdrop; text/buttons stay
opaque. The root window remains opaque: no capture of wallpaper, other apps or secrets.
This is an Android beta appearance inspired by Liquid Glass, not Apple's system technology
or arbitrary underlying-content refraction. No continuous animation/network changes/new
permissions/dependencies. Real GPU appearance/performance/battery validation pending.
