# 0.1.0-alpha test report

> 15 October update: the user confirmed exchange/downloads in 0.1.0-alpha on Android 16 / Telegram 13.0.2 / Wi-Fi, with disconnections. [Update report](TEST_REPORT_0.1.1-alpha.md). Historical results below describe 14 October.

Date: **14 October 2026, UTC**. [Русский](../TEST_REPORT.md).

**Release gate remains closed:** no physical Android 14+ device with official
Telegram is available. Live connection, text send/receive and reconnection have
**not been tested**. No tag or GitHub Pre-release is created. Version remains
`0.1.0-alpha`.

## Baseline checkpoint

Original `main`: `0874a6e4ef364e06badf7f9f1ad75b5d02e78686`.

- Unchanged checkout passed Wrapper `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  with JDK 17; **all 5 existing tests passed**.
- Original [successful GitHub CI run 38514171444](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38514171444).
- Baseline APK SHA-256:
  `c54e22259df1fba908c7aec27f1668cc0a7e37099210e3d1a305b46462473aa6`.
- Package/applicationId, history and existing tests are preserved.

## Defects found and addressed

| Original defect | Change and validation |
|---|---|
| Masked/RSV/invalid continuation frames accepted | Strict parser and positive/negative local RFC 6455 tests |
| Substring Upgrade checks; incomplete Close handshake | HTTP/token/Accept/subprotocol and Close code/UTF-8 validation |
| Potential indefinite reads/writes | Bounded setup/read/write/idle deadlines and owned socket cancellation |
| Invalid framing silently forwarded raw | Invalid/oversized/truncated data rejected on WSS and TCP; bounded buffers |
| No independent crypto oracle or input validation | 36 upstream handshake/relay/cipher vectors, NIST AES-CTR and isolation tests |
| Same IP for WSS and TCP | Separate DC 2/4 upstream WSS redirects and TCP routing table |
| In-flight connect sockets unowned; stop ahead of native listener closure | Resource ownership before blocking I/O; refused connect after stop regression |
| Repeated ACTION_START recreated server | Idempotent service and accepted-client preservation test |
| Silent secret replacement on Keystore failure | Ciphertext preserved, visible error and explicit recovery |
| Visible secret, hardcoded strings and core/UI coupling | Hidden/reveal controls, RU/EN resources, typed core events and main-thread UI |
| Inaccurate unsigned debug artifact and missing provenance | Pinned action SHAs, accurate artifact label, checksum/build metadata/reports |

## Reference coverage

Flowseal **v1.11.2**, commit `307a384ab7f522e34bb1a045884d0bd90f40bee5`.
The generator executes unchanged upstream `_try_handshake`, `_generate_relay_init`,
`_build_crypto_ctx`, `CryptoCtx` and `MsgSplitter`, verifies the commit and clean
`proxy/` files and records source SHA-256 hashes. Regeneration was byte-identical.

Coverage includes `ef`/`ee`/`dd`, normal/media DC 1–5 and preserved DC 203,
relay init, four AES-CTR streams, chunk boundaries and quick-ack framing.
Reserved prefixes, test DCs, invalid sizes and truncated EOF are intentionally
rejected more strictly than upstream. [Reproduction](../../tools/README.md).

## Modified project checks

Official stack: **Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.1, JDK 17,
compileSdk 37.0, targetSdk 37, minSdk 34**. Local builds use verified official
JDK/SDK artifacts and the environment's trusted network CA; TLS remains enabled.

Final mandatory command:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Tested code and CI configuration: `450ae9ed69b40cbba940ab11363603079686ce73`.
The complete local run passed: **104 tests, 0 failures/errors/skips**.

| Group | JVM tests |
|---|---:|
| MTProto / AES-CTR / relay | 7 |
| PacketSplitter | 8 |
| TelegramRoutes | 5 |
| WebSocket / TLS | 50 |
| Listener / fallback / cancellation | 22 |
| State model | 5 |
| Settings / GCM / Telegram links | 7 |

Lint: **0 errors, 4 warnings** (`MissingApplicationIcon` — 1, `UseKtx` — 3).
No checks, TLS or tests were disabled. A Lint internal crash during concurrent
source edits did not reproduce after source freeze; the complete final run passed.

Debug APK and the APK containing **10 instrumentation tests** were built.
Debug APK signature verified with `apksigner` (v2). Local APK SHA-256:
`8b1bb5dd59b6449c2c6eabad330c44ed14ea1b136671e5cb3a222e535d8c5a56`.
Artifacts include `SHA256SUMS`, `build-info.json`, changelog and JVM/Lint reports.
Compiling instrumentation does not establish Android execution.

A clean checkout of `450ae9e` passed the same complete command. The public
localhost TLS fixture is tracked only as a test resource and is absent from the
APK. **16 CI script tests** additionally cover complete JUnit reports, AVD setup,
and stopping on a wrong device API or Gradle failure.

GitHub JVM/Lint/APK and 16 CI script tests: [successful run 38627799074](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38627799074)
at `450ae9e`. Downloaded JUnit reports confirm all 104 JVM tests.

### Android verification integrity

Previous API 34, 35 and 36 artifacts contain **10 passing tests each**.
The apparently successful API 37 run contained no JUnit tests: the emulator
restarted system services, and Gradle returned success after APK installation
failed. This result **does not count** as device verification.

The workflow now checks actual `ro.build.version.sdk` and requires JUnit entries
for every instrumentation test with no errors or skips. Empty, incomplete or
duplicated reports fail CI. For minor-versioned API 37.0, AVD target metadata is
corrected only after checking the installed system image path; the supported
`-gpu software` backend is used. Entry points run in single processes because
the emulator action executes separate script lines in separate shells. These
fixes do not alter the application or relax test acceptance criteria.

Repeated Android matrix: [successful run 38627804320](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38627804320),
commit `450ae9e`. All downloaded JUnit artifacts pass the same strict gate:

| Emulator | Executed | Errors / skips | Artifact ID |
|---|---:|---|---|
| Android 14 / API 34 | 10 | 0 / 0 | 11924757599 |
| Android 15 / API 35 | 10 | 0 / 0 | 11925800757 |
| Android 16 / API 36 | 10 | 0 / 0 | 11925436007 |
| Android 17 / API 37.0 | 10 | 0 / 0 | 11925980806 |

Covered: start/stop, repeated start, background/return and Activity recreation,
occupied port, malformed handshake and stopping during startup; AES-256 Keystore,
encrypted records, lost keys, corrupt settings and explicit recovery. These tests
do not exercise official Telegram, every visual UI scenario or real Wi-Fi/LTE
changes.

## Alpha limits

- Loopback `127.0.0.1` only, default port `1443`; **8** admitted clients including pending handshakes.
- MTProto payload **4 MiB**, WS message **4 MiB + 4 bytes**; one ciphertext packet buffered, no user payload logging.
- Handshake **10 s**, total setup **25 s**, WSS attempt **8 s**, write **10 s**, idle **60 s**.
  A WS message must complete within **60 s**; slow/idle connections require reconnecting.
- WSS only for upstream default DC 2/4 redirects; other DCs use TCP. DC 203 preserved, test DCs excluded.
  No domain fronting; certificate trust and hostname checks mandatory.
- Network replacement/loss closes sessions. Physical Wi-Fi/LTE, seamless migration and IPv6-only remain unverified.
- Fake TLS, Cloudflare, WS pools, balancing and HTTP/2 are planned.
- `specialUse` represents a user-started persistent local proxy; store acceptance has not been verified.
- Debug APK uses a debug signature. A trusted distribution signing key has not been provided or added to the repository.

Port-release checks require a refused TCP connect and a new bind using production's
`SO_REUSEADDR` policy. Kernel `TIME_WAIT` after an accepted session closes is not a live listener.

## Physical Telegram E2E

| Field | Result |
|---|---|
| TGWS-Android | 0.1.0-alpha |
| Device / Android / Telegram | Unavailable |
| Commit installed on physical device | Not applicable: no physical installation |
| Official Telegram connection | Not tested |
| Text send / receive | Not tested |
| Wi-Fi / LTE loss and recovery | Not tested |
| Real Telegram session termination | Not tested |

Scenario and report form: [TESTING.md](TESTING.md).
