# ТЗ: полноценный Android-порт TG-WS-Proxy

**Основа:** https://github.com/Flowseal/tg-ws-proxy, эталон v1.11.2, SHA 307a384ab7f522e34bb1a045884d0bd90f40bee5.

**Базовые правила:** Android minSdk 34; compile/target API 37; Kotlin 2.4.21; AGP 9.4.0;
Gradle 9.8.1; Java bytecode 17. Только стабильные релизы. Python 3.15.0 разрешён
только для эталонных проверок исходного проекта, не в APK. Сохранять MIT attribution.

## Объём функций для релиза 1.0

1. MTProto obfuscated2: dd-secret, abridged/intermediate/secure, DC 1–5/203, media, test DC.
2. Fake TLS: ee-secret; корректная проверка ClientHello и таймштампа, masking fallback.
3. WebSocket: RFC6455, WSS, TLS verification, контролируемый fronting, ping/pong, fragmentation.
4. Pools: per-DC/per-media, background warmup, rotation, max age, backoff, shutdown.
5. CF fallback: обновление и валидация списка доменов, пользовательские домены.
6. CF Worker: заданные пользователем Worker; HTTP 429, rate limit, expiring exclusion.
7. HTTP/2 for media: session multiplexing, flow control, packet replay, backpressure,
   bounded reply buffer; parity with `cf_h2.py` and `h2_transport.py`.
8. TCP fallback, custom DC IP mapping, ipv4/ipv6 dual stack, mobile network handovers.
9. Notification controls, always-visible connection state, persistent user preferences,
   localized RU/EN interface, dark/light theme, logs with automatic redaction.
10. Tests and CI: local JVM unit tests, emulator API 34/35/36/37 instrumentation,
    packet vectors cross-checked against upstream, regressions under packet loss.

## Phases

### Stage A: project foundation
- Produce reproducible stable builds, pin dependency versions.
- Add automated formatting, unit tests, Android lint, SCA, dependency audits.
- Add source license, security review checklist and traceability mapping.

### Stage B: protocol compatibility
- Port complete crypto/handoff logic from `proxy/tg_ws_proxy.py`, `bridge.py`.
- Generate deterministic Python reference vectors and verify Kotlin byte-for-byte.
- Write TCP and WS loopback integration tests with controlled frame splitting.
- Test malformed MTProto packets, 0-byte payloads, oversized packets, cancellation.

### Stage C: routes and fallback
- Port `proxy/pool.py`, `balancer.py`, `config.py`, `fake_tls.py`.
- Validate TLS hostname/certificate behavior for direct and fronted routes.
- Port CF Worker/CF fallback; fix unreachable handling of HTTP 429.

### Stage D: HTTP/2 parity
- Implement HTTP/2 transport with bounded streams and buffers.
- Match timeout/replay semantics from `proxy/cf_h2.py`.
- Verify media upload/download under slow networks and mobile handover.

### Stage E: Android product quality
- ConnectivityManager callbacks and IPv6-only handling.
- Foreground service with Android 14–17 compatibility; stop/cancel controls.
- Secure secrets via Keystore; user-configurable diagnostics and redacted logs.
- UX controls and performance/battery testing on physical devices.

### Stage F: release gates
- Android Gradle build success, static analysis clean, no high severity dependencies.
- Run upstream parity suite and device tests. Verify reconnect and throughput.
- Produce signed APK/AAB and SBOM; clear alpha warnings only after parity.

## Definition of Done

Release 1.0 may be called a full port only if all upstream functions are implemented
or any intentional exclusions are documented, tested, and accepted. No mock proxy,
no placeholder HTTP/2, no claim of supported features without passing end-to-end tests.
