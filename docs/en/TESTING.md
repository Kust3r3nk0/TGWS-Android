# Testing TGWS-Android

Baseline: Flowseal/tg-ws-proxy **v1.11.2**, commit
`307a384ab7f522e34bb1a045884d0bd90f40bee5`. [Русский](../TESTING.md).

## Automated checks

Use JDK 17, Android SDK `platforms;android-37.0` and `build-tools;36.0.0`.
Gradle, AGP and Compose compiler versions are pinned. Do not replace them
to bypass a failing check.

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Golden vectors must come from the unchanged functions in the pinned upstream
with controlled randomness. Python belongs only in the test environment,
never in the APK. Compare regenerated vectors with the committed data instead
of overwriting expected output when a test fails.

JVM tests cover cryptography, framing, the local listener, bounded fallback
and a local WebSocket server. They cannot establish reachability of public
Telegram servers from the user's network.

## Android

Test API 36 and 37 where a stable system image is available.
Instrumentation requires a connected device or emulator:

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

CI also runs `python3 .github/scripts/verify_android_tests.py`, requiring entries
for all current tests (25 in 0.3.3) instead of relying only on Gradle's exit code. Empty reports,
skips, duplicates and failures/errors fail the job. Test the gate and emulator
entry points with `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`.


| Scenario | Expected observation |
|---|---|
| First launch | Secret hidden; listener does not start automatically |
| Start and stop | Loopback port opens and is released; notification removed |
| Background and return | Service remains active; UI shows actual state |
| Repeated service start | No duplicate listener or workers |
| Occupied port | Clear error; foreground service exits |
| No internet | Local availability differs from remote sessions; bounded attempts |
| Invalid secret | Configuration rejected; no automatic replacement |
| Rotation and reopen | Port, hidden secret and service state remain correct |
| Lost Keystore key | Visible recovery error; ciphertext preserved until explicit action |
| Wi-Fi / mobile loss | Old sessions closed; Telegram may create new sessions |

The `specialUse` foreground service represents a user-started persistent
local proxy with an ongoing notification. This app is not a VPN. Store
distribution requires a separate review of the store's foreground-service rules.

## Physical Telegram E2E — separate owner validation

The owner uses a physical Android 16+ device and official Telegram. These checks do not block
a debug Pre-release after automated validation; stable release acceptance still requires E2E.

1. Install the APK and record its SHA-256 and build commit.
2. Start the proxy and open its local `tg://proxy` link using the app button.
3. Enable this proxy in Telegram and verify an established connection.
4. Send and receive a text message.
5. Briefly disconnect and restore networking; verify reconnection.
6. Test Wi-Fi and mobile internet separately.
7. Stop the proxy; verify sessions end and the port is released.

Record:

```text
UTC date:
Git commit:
TGWS-Android version: 0.3.1-alpha
APK SHA-256:
Device / Android / API:
Official Telegram version:
Network type (no personal addresses):
Carrier / country (cellular):
Mode: direct / WSS relay (do not publish credential-bearing URLs)
Proxy connection: PASS / FAIL / NOT RUN
Send text: PASS / FAIL / NOT RUN
Receive text: PASS / FAIL / NOT RUN
Network recovery: PASS / FAIL / NOT RUN
Stop and port release: PASS / FAIL / NOT RUN
Notes (no secrets or message contents):
```

Until this scenario passes, do not claim a fully verified working proxy. The owner
authorized clearly labeled debug Pre-releases after automated checks; these do
not pass the stable release gate. Stable distribution requires a trusted signature.

## Relay and cellular checks

Run `npm ci --ignore-scripts --prefix relay && npm test --prefix relay` for local
TLS/WSS/TCP tests. These do not establish carrier reachability. Repeat physical
Telegram E2E separately in direct and configured relay-only mode with Wi-Fi off.
Record carrier/country and compare results. Check clearing the URL restores
direct mode, a failed relay has a bounded deadline with no direct fallback, and
Keystore key loss requires explicit URL recovery. [Relay setup](MOBILE_NETWORKS.md).

For the 0.2 soak, add `-PsoakSeconds=180` to the Wrapper command (1–3600 seconds; CI 60). [Policy and fixture](RELIABILITY_0.2.md).


## 0.3 automated scope and owner acceptance

**Owner physical-device validation: pending.** The owner runs Telegram/cellular/screen-off
checks separately; they do not block a checked debug Pre-release. No device/ADB access is required
for this development run. Stable release gates remain unchanged.

New tests cover pinned Fake TLS HMAC/records, cf_h2 byte vectors, real local strict TLS/ALPN H2,
concurrent streams, 256 KiB flow-control windows, bounded backpressure/cancellation, RST/GOAWAY,
HTTP/ALPN/oversize rejection and reused-connection timing. The native H2 soak exercises core
Obfuscated2 ef/ee/dd and media DC under four clients and resets. The original TCP/WSS soak remains.

```bash
./gradlew --no-daemon -PsoakSeconds=180 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 .github/scripts/verify_soak.py --seconds 180
python3 .github/scripts/verify_http2_soak.py --seconds 180
```

Both soaks execute the requested duration individually (CI: 60 seconds each). Gates require actual
passing JUnit cases, duration, measured summaries and bounded resources. Android now requires 22
cases on supported APIs: added encrypted-settings migration/tamper, network callback stop/permissions,
real local H2 and full Fake TLS → core → TLS/H2 integration. Fixture CA trust is test-client-only.
[Carrier model versus physical RU-01–14 matrix](RU_MOBILE_OPERATORS.md),
[transports](TRANSPORTS_0.3.md), [actual report](TEST_REPORT.md).


## 0.3.1 UI and DNS

CloudflareDnsTest exercises real local TLS/H2 DNS: A/AAAA, NXDOMAIN, malformed/HTML/
oversize/redirect/untrusted-certificate rejection, stop/stale callbacks without system fallback.
NetworkViewTest checks IPv4/IPv6/dual/unknown and excludes link-local. Android adds real DoH
and navigation/draft/save/recreate/theme/DNS/protected-secret checks. CI collects light/dark
Home and settings PNGs in Android reports. Owner physical carrier/Cloudflare/Telegram checks
remain separate.


## TGWS-16-016 / 017: applicability and handoff

Current production UI/transport has no embedded WebView. CI checks declared production
references/dependencies with `verify_native_ui.py`; this does not replace code/security review.
Before/after emulator tests `android-environment/*.json` records manufacturer/model/OS/API
and selected provider package/version, not latest-channel certification. It neither switches
providers nor instantiates an engine; null/unknown states are explicit. No personal device discovery.
See [WebView matrix](WEBVIEW_COMPATIBILITY.md), [owner workflow](PHYSICAL_TESTING.md),
and the populated `OWNER_HANDOFF.md` included with successful CI APK artifacts.
