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
