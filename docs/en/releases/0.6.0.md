# 0.6.0 — Relay & Performance Update

Android 16+ / API 36+, version 0.6.0, code 22. Debug Pre-release, not Production.
Device Telegram/HD/mobile behavior is owner-tested; local CI is not certification.

## Relay Manager

Settings → Transport → VPS Relay Manager, with the proxy stopped. Add up to eight
unique origin WSS endpoints, private labels, primary and explicitly permitted
backups. Apply each edit to the list, then save using the top card. Enabling the
manager is explicit. Existing single URL, secrets, keys, profiles and themes are
retained; disabling restores legacy selection. URLs are masked and reveal uses
FLAG_SECURE. Drafts survive rotation in memory, never plaintext saved state.

MANUAL uses only the primary; FAILOVER tries primary then selected backups;
AUTOMATIC ranks those same authorized nodes from passive Smart Routing evidence.
The manager works in relay-only and automatic transport modes. DIRECT stays
WSS/TCP. Smart permissions and MANUAL/FAILOVER priorities remain enforced. Total
setup deadline, backoff and endpoint pressure remain; automatic setup admits at
most four non-TCP dial attempts, so eight saved nodes need not all be tried in one
request. Duplicate own/Worker endpoints share pressure admission. No active
MTProto migration, payload mutation or replay.

Explicit per-node TLS/WSS checks send no MTProto and do not prove Telegram.
One check at a time, each node no more than once/30 seconds; timeout, leaving,
backgrounding, edit and network change cancel/close resources. Results show
measured setup and typed issue/HTTP status. No constant polling. Diagnostics use
numeric node slots, never private labels, URLs or tokens. Optional fallback alerts
distinguish origin nodes of the same transport.

## Media and server fixes

Media permission excludes incompatible nodes. Optional origin preference applies
only to new permitted media sessions in automatic routing; disabled by default.
Encrypted file sizes are unknown in advance. MTProto bytes remain ordered/unmodified.
Node relay refreshes write-stall deadlines at each <=16 KiB completed write, rather
than an entire <=4 MiB message. Stream backpressure/message limits remain. Closing
capacity stays reserved until native TCP/client/WS closure; shutdown also closes
pre-Upgrade/TLS sockets. Local bridge tests transfer 20 MiB in each direction;
a separate encrypted Kotlin MTProto/TLS/WSS test checks 24 MiB each way. These are
local peers, not real Telegram acceptance.

Worker is unchanged: six sessions/isolate, 16 MiB total/session, bounded queue.
A fixed 0.4.2 Worker needs no redeploy. Use a compatible origin relay for large
transfers; the APK neither deploys a server nor removes Worker limits.
[Deploy and upgrade](VPS_RELAY.md).

## Compatibility and dependencies

PRs #27–30: WorkManager 2.12.0, Core KTX 1.19.1, JSON-java 20260814 (JVM tests only),
setup-java 6.1.0 pinned by SHA. Toolchain/SDK/min36 unchanged; no new runtime
libraries, embedded WebView/DNS/VPN/SSH/wake locks. Existing bounded demand-driven
warm pool and Saver remain. Old five/six-line records stay byte-compatible; an
optional seventh line stores nodes in the existing AES-GCM record with unchanged
AAD/key. Combined plaintext <=32 KiB; largest configurations/tampering are tested.

## Device acceptance

Install over a matching-signer Debug without uninstalling. Verify retained settings
and code 22. Test legacy first, then enabled primary/backups in MANUAL/FAILOVER/AUTO,
an unavailable primary, no forbidden direct route, explicit probe/cancel, Wi-Fi↔LTE,
messages, SD/HD/photos/video/large files both ways, background 30–60 minutes,
start/stop/reopen/rotation/edit/delete. Treat Worker BYTE_LIMIT separately. Share only
version/mode/step/direction/result and reviewed safe JSON in chat, never private
node labels/URLs/tokens/device data. Quick Settings Tile remains planned pending
separate FGS/device checks; no decorative switch is added.
