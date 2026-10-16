# 0.3.1-alpha validation

16 October 2026 UTC. [Русский](../TEST_REPORT_0.3.1-alpha.md).
Android 15+, minSdk 35, targetSdk 37, versionCode 7.
**Owner physical-device validation: pending.**

Material 3 Home/settings navigation preserves proxy configuration and supports saving
without starting. Light/dark/system preferences persist; a menu button switches themes.
Wi-Fi/cellular/other and IPv4/IPv6/dual-stack update even while stopped, excluding
link-local-only false positives. These describe the interface, not remote reachability.
System DNS stays default. Optional app-scoped Cloudflare DoH uses strict TLS, A/AAAA,
numeric bootstrap, bounded replies/time and explicit cancellation. No Android DNS/APN/SIM
changes. [Details](DNS_AND_APPEARANCE.md).

## Verified evidence

Application source: `38617572147d59b6aea521a99ce94b30dbd8a742`. Documentation may be newer; application/build inputs match.

- Complete local Wrapper/JDK 17: **BUILD SUCCESSFUL**, 181 JVM cases, zero failed/error/skipped,
  Lint zero errors / six UseKtx suggestions, debug and instrumentation APK assembled.
- [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38855362494): **SUCCESS**, actual 181 passing JVM cases, lint/APK and two
  60-second soaks independently checked from JUnit.
- [Device matrix](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38855362508): **SUCCESS**, API 35: **22**, API 36: **22**, API 37.0: **22**.
  All **66** identities verified with no missing, duplicate, failing or skipped cases.
  Existing 176 JVM and 19 Android cases remain.
- All five artifact ZIP SHA-256 values verified against GitHub; merge tree matches source.
  Every signed-APK ZIP entry matches CI bytes. No test p12/keystore/Python files in APK.

Real local TLS/H2 DNS tests cover both families, no system bootstrap/fallback,
NXDOMAIN/malformed/HTML/oversize/redirect/untrusted-certificate responses and cancellation/stale
network results. Native Android DoH is tested as well. Cancelling owned Dns.Call completes
waiting futures. An existing malformed-MTProto test now awaits finally-close with a deadline
instead of racing local EOF; protocol assertions remain intact.

CI TCP/WSS soak: 60 s, 26505 packets, 7251 sessions,
59 resets, 60 expected disconnects; peak native sessions
4/8 and warm pool 2/4. H2: 60 s, 4954 packets,
988 sessions, 59 resets, 194 expected disconnects,
6291 HTTP requests; peak native sessions 4/8 and HTTP calls
4/16. Gates verify transport activity, duration and resource bounds.
Peers are local; Telegram accounts are not used.

## UI verification

Tests cover draft navigation, explicit save without service start, Activity recreation,
theme/DNS persistence and unchanged encrypted secret when changing public preferences.
Stable UI Automator 2.4.0 avoids the hidden API removed in Android 17 used by Espresso 3.7.
Public accessibility page-scroll actions account for restored viewport position.
No tests/assertions were disabled.

Visual inspection found light system bars with the dark theme on API 35. Standard AndroidX
edge-to-edge, explicit SystemBarStyle and native-window background synchronization fix it.
Screenshots assert the actual page and
both system-bar backgrounds and wait for a stable rendered theme/Material colour transition.
Test-only shell-owned Download files survive harness uninstall; no app storage permission
was added. Final API 36 screenshots below are unedited CI PNGs, English emulator locale.
All 166 RU/EN string keys match.

<img src="../images/0.3.1/home-light.png" width="240" alt="Light Home" />
<img src="../images/0.3.1/home-dark.png" width="240" alt="Dark Home" />
<img src="../images/0.3.1/settings-dark.png" width="240" alt="Settings" />

## Debug package and limits

ApplicationId `io.github.kust3r3nk0.tgwsandroid`, label `TGWS-Android` and previous
debug certificate `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe` remain. This is not production signing.
Signed APK SHA-256: `26755af5f67875e5668c9d9ac7d7b0f165db7e7de4fbaed63866d831a6e0c363`. Publication rechecks source/CI/JUnit/soaks/certificate/payload.

Physical device, public Cloudflare over a carrier, cellular and new-version Telegram:
**NOT TESTED**. Earlier Wi-Fi results do not validate this version. No IP-block bypass
guarantee or carrier DNS64 synthesis; IPv6-only may need system DNS/XLAT. The experimental
Worker retains 16 MiB/session. The stable production release gate remains open.

One API 36 dark-Home capture retains mixed system-icon colours after switching.
Page/bar backgrounds and app controls are checked; physical Android 16 system-icon
contrast needs separate validation. Vendor-device visual perfection is not established.
