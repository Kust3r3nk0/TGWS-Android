# 0.3.4-alpha validation — 16 October 2026

Android 15+, versionCode 10. Final CI evidence will be recorded before publication.
Removed built-in Cloudflare DNS without losing other configuration; retained test-only DoH
fixtures, added the APK gate, bounded IPv4/IPv6 connection racing/migration regressions and
safe DNS/TCP/TLS/deadline classification. Native WebView applicability and owner handoff included.

Final local/CI/API 35–37 checks pending. Physical carrier/Wi-Fi/Telegram/One UI/WebView Stable/Beta
validation belongs to the owner and remains pending. The owner reports previous failures in all
three modes; exact previous version/SHA/failure stage are unknown. Carrier access restoration is
not confirmed by these changes. [Research and limits](NETWORK_COMPATIBILITY_0.3.4.md).
