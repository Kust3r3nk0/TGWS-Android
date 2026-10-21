# TGWS-Android 0.5.0 — Smart Connection Update

Android 16+ / API 36+, versionCode 21. Debug Pre-release, not Production.
Smart Routing is opt-in; existing settings, MTProto secret, Keystore, transport
mode, Premium Minimal tabs and themes are retained.

## New features

- Automatic, manual and primary/backup routing for new sessions only.
- Automatic, Wi-Fi, mobile and custom profiles: transport permissions, primary,
  backups, adaptive recovery and power policy. Automatic selection uses network
  kind without collecting SIM/carrier identifiers. Configure in Settings →
  Transport → Smart Connection with the proxy stopped.
- Connection Health: measured setup, attempts/failures/drops, selection reasons,
  active DC/media and session duration; insufficient evidence is shown explicitly.
- Live Traffic: actual rates, byte totals, sessions and reset/drop markers.
- Optional outage/recovery/fallback/configuration notifications in diagnostics settings.

## Fixes

- Network changes mark and close old sessions before invalidating the factory;
  stale results cannot affect a new profile or network generation.
- Duplicate own/Worker endpoints share cooldown and cannot consume two dial attempts.
- Final byte counters survive stop; the chart does not retain a stale running rate.

## Optimization

Passive, bounded evidence: 128 samples / 10 minutes, two-minute half-life. Scores
use DC/media, inbound bytes, useful duration, setup and remote failures. Socket
setup alone does not prove Telegram reachability or message delivery.
Demand-driven recovery exploration runs at most once/minute, or once/5 minutes
in power saving. Saver disables unused WSS warm-up. Existing RouteBackoff,
single recovery admission, Retry-After and total setup deadlines still apply.
Disabling adaptive growth retains minimum overload protection. Active streams
never migrate or replay processed MTProto bytes.
Traffic keeps 60 points, at most once/second; long gaps have no synthetic rate.
No extra network polling or chart drawing off-screen. Optional outage alerts
wait 30 seconds, repeat no more than once/5 minutes and report recovery only
with inbound progress. Mandatory foreground notifications remain unchanged.

## Technical

Legacy five-line settings remain byte-compatible until profiles are explicitly
changed. Profiles use the existing encrypted transport record without replacing
keys or secrets. DIRECT allows WSS/TCP; relay-only remains strict. Smart automatic
mode supports configured Worker/CF/own relay and direct WSS/TCP; dummy CF endpoints
are unnecessary. HTTP2 remains limited to supported media/DC with configured CF.
Incompatible manual choices fail as configuration errors without hidden fallback.
Health quality needs five conclusive inbound-data/failure observations, not five
socket handshakes. Success ratios do not count Telegram message acknowledgements.
Schema 3 JSON adds bounded typed health/selection data, never private endpoints.

## General information

No Worker protocol change: a fixed 0.4.2 Worker needs no redeploy. Upgrade older
Workers using the 0.4.2 instructions, retaining token/domain. APK installation
does not deploy a Worker. Limits remain six sessions/isolate, 16 MiB total/session,
4 MiB + 4 B / 64-message queue. Use the compatible stream-backpressured relay/
for large media. Device Telegram/HD/mobile/background behavior is owner-tested;
automatic tests do not certify it. Multi-VPS/Media Optimizer belong to 0.6.0.

## Installation and acceptance

Install over a matching-signer APK without uninstalling. Stop on signature
mismatch. Verify 0.5.0/code 21 and retained secret/port/relay/theme. First test
legacy mode, then enable Smart and configure Wi-Fi/mobile profiles. Exercise
AUTO/MANUAL/FAILOVER, an unavailable primary with an allowed backup, Wi-Fi↔LTE,
messages and SD/HD/video/large media in both directions, restart/rotation and
30–60 minutes in background. MANUAL/relay-only must not use forbidden transports.
Check honest health/traffic and optional notifications without short-outage spam.
Stop must release all sessions. Share only version/mode/scenario/result and the
redacted diagnostic JSON privately in chat, never tokens/private URLs/device data.
