# Diagnostics 0.4.1

Open **Diagnostics**. Service and transport counters describe actual observations; a listening local port does not confirm Telegram connectivity.

To report a problem without a computer: enable recording in Settings → Diagnostics, reproduce it, open **Full event log**, combine severity/category filters, then preview the report and explicitly copy or share its JSON file through Android.

INFO describes normal events. WARNING describes an individual client/route failure or unvalidated network. ERROR describes a local service/configuration/storage failure. One client event does not stop other sessions. Repeated warnings coalesce; JSON includes first/last times and counts. Safe typed logcat output uses `TGWSDiagnostics` with repeat limits.

History is bounded to 128 in-memory events and 32 actual setup measurements. Disabling recording clears it and stops journal output without stopping the proxy/counters. Process death clears it too. Copies already shared with another app cannot be removed from that app.

The one-shot **local port check** connects only to the selected `127.0.0.1` port without sending a secret. It has a 1.5-second timeout and closes its socket on cancellation. Success does not authenticate MTProto or measure Telegram latency. Editing the port clears the previous result. The existing manual route check separately tests configured remote transports; unavailable timings remain unknown.

System WebView diagnostics read selected provider/version/channel metadata without starting an engine or pages. Refresh occurs on foreground return, selected package changes and explicit request. Other apps’ logs are inaccessible. Known package names indicate channels but never certify latest Stable/Beta.

Report schema 2 includes TGWS/API versions, typed service/network/route events, counters and provider metadata. It excludes secrets, keys, relay/IP/DNS addresses, Private DNS hostname, carrier, messages and raw exception texts/stacks. Up to four reports (256 KiB each) live in cache. A separate non-exported report provider grants only an explicit read-only URI; the APK installer provider cannot access report files. Android or subsequent exports evict older cache files. Settings and Keystore remain unchanged.
