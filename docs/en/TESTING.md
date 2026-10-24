# Automated developer checks

Use JDK 17, Android SDK and the project Gradle Wrapper:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
python3 -m unittest discover -s .github/release -p 'test_*.py'
```

CI runs API 36/37 instrumentation and update installation without clearing data. Protocol tests use local peers and pinned Flowseal v1.11.2 vectors without a Telegram account. TLS and authentication checks stay enabled.

## 0.4.2 — Stability & Recovery

Regressions cover server/backoff, connector/factory/warm pool, route diagnostics,
WebSocket, handshake advice, journal and Android lifecycle: DNS/TCP/TLS/Upgrade/
Close, incomplete headers, deadlines/backoff, stale network results, cancellation,
stop/restart, independent fallback after two Worker failures and setup time
reserved for TCP. CI retains both actual 60-second MTProto and HTTP2 soaks,
API 36/37 and verified install-over-existing settings/Keystore preservation.

With Node.js 22+, run `npm ci --ignore-scripts` and `npm test` in both `relay/`
and `cloudflare-worker/`. Worker `npm run build` is a dry run, not deployment.
Worker tests check both-direction byte limits, bounded byte/message queues,
write/connect deadlines, stream locks and admission until both resources close.
Its 16 MiB lifetime cap is retained. [Owner acceptance and next stages](SMART_CONNECTION_UPDATE.md).

Personal reports, phone and carrier details are not published in the app/repository. Feedback stays in development chat. [Responsibilities](PHYSICAL_TESTING.md).

## 0.5.0 Smart Connection

ConnectionProfiles/SmartRouting tests cover legacy codec, permissions, bounded
decay, generation reset, controlled probes and saver. SmartConnectionNetworkTest
passes real encrypted MTProto ef/ee/dd over local TCP and TLS/WSS with DC/media
and old-network closure. Server tests cover strict manual/probe/fallback, duplicate
endpoints and no dummy CF. Traffic/alert tests use controlled clocks. Android
tests check encrypted profiles/Keystore across recreation, honest empty health
and opt-in actual notifications.

[0.5.0](SMART_CONNECTION_0.5.0.md)

## 0.6.0

RelayConfigurationTest: legacy codec, Unicode, limits, duplicates, permissions, edit/delete.
TransportRecordCodecTest: largest combined encrypted configuration and tampering.
MtprotoServerTest: strict managed relay-only/manual/backup and media permissions;
SmartRoutingTest: independent nodes of one transport. ManagedRelayNetworkTest: real
encrypted 24 MiB per direction over local TLS/WSS with independent client CTR.
RelayManagerInstrumentedTest: CRUD/persistence/recreation/Keystore/secure drafts.
Node tests: bounded progress, close admission, 20 MiB each way, pre-Upgrade shutdown
and bounded environment configuration. [Acceptance](RELAY_PERFORMANCE_0.6.0.md).

## 0.6.1 — Connection Recovery

Controlled MtprotoServer regressions cover two blocked direct media aliases before a configured Worker, four failed Cloudflare candidates before a Worker, and strict MANUAL transport permissions. Existing stop/network-change/overload/media regressions remain. [Device acceptance](CONNECTION_RECOVERY_0.6.1.md).

## 0.6.2

NetworkAutoConfigTest checks encrypted fragmented req_pq/resPQ, nonce rejection, profile preservation and route permissions. AutoConfigInstrumentedTest checks offline controls, Activity recreation and retained settings. Existing large encrypted media and stop regressions cover the inbound counter fix.

## 0.6.3

LocalSocksTest: fragmented SOCKS replies, binary data, unavailable proxy without direct dialing, cancellation, invalid greeting. LocalDpiInstrumentedTest: preference preservation across Activity recreation, unchanged secret and corrupt preference fails closed. Real DPI bypass requires owner testing with a separately running engine.
