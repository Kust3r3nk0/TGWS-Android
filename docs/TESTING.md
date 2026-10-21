# Автоматические проверки для разработчиков

Используйте JDK 17, Android SDK и Gradle Wrapper проекта:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
python3 -m unittest discover -s .github/release -p 'test_*.py'
```

CI запускает инструментальные проверки API 36/37 и установку обновления без сброса данных. Протокольные тесты используют локальные peers и закреплённые векторы Flowseal v1.11.2; они не требуют Telegram-аккаунта. TLS и проверки секретов не отключаются.

## 0.4.2 — Stability & Recovery

Целевые регрессии: `MtprotoServerTest`, `RouteBackoffTest`, `NetworkConnectorTest`,
`NetworkTransportFactoryTest`, `WarmConnectionPoolTest`, `RouteDiagnosticsTest`,
`RawWebSocketTest`, `DiagnosticAdviceTest`, журнал и Android lifecycle.
Проверяются DNS/TCP/TLS/Upgrade/Close, неполный handshake, тайм-ауты и backoff,
отмена/поздний результат старой сети, stop/restart, независимый fallback при отказе
двух Worker, общий deadline с резервом TCP, bounded очереди и сохранение настроек.

Серверная часть (Node.js 22+): `npm ci --ignore-scripts` и `npm test` отдельно
в `cloudflare-worker/` и `relay/`; `npm run build` Worker — dry run без deploy.
Worker-тесты проверяют очередь/число сообщений/байты в обоих направлениях,
write/connect deadlines, закрытие/stream locks и admission до подтверждения
закрытия обоих ресурсов. Предел 16 МиБ не снимается.

CI также выполняет реальные 60-секундные локальные MTProto/TLS/TCP и HTTP2 soak
с сетевыми сбросами, проверяет исходные JUnit-записи, API 36/37 и установку поверх
закреплённого APK. Эти результаты не подтверждают HD-медиа/Telegram в реальной сети.
[Физическая приёмка и последующие этапы](SMART_CONNECTION_UPDATE.md).

Личные отчёты, сведения о телефонах и операторах в приложении/репозитории не публикуются. Обратная связь обсуждается в чате разработки. [Обязанности](TGWS-16-017_PHYSICAL_TESTING.md).

## 0.5.0 Smart Connection

ConnectionProfilesTest/SmartRoutingTest проверяют legacy codec, разрешения,
затухание/границы, generation reset, единичную пробу и saver. SmartConnectionNetworkTest
передаёт реальные зашифрованные MTProto ef/ee/dd через локальные TCP и TLS/WSS,
с media/DC и закрытием старой сети. Server tests покрывают manual/probe/strict
fallback, одинаковые endpoints и отсутствие dummy CF. TrafficHistoryTest и
ConnectionAlertPolicyTest используют управляемые часы без sleep/polling сети.
SmartConnectionInstrumentedTest проверяет сохранение профиля/Keystore при Activity
recreation, честное отсутствие измерений и opt-in Android notifications.

[0.5.0](SMART_CONNECTION_0.5.0.md)
