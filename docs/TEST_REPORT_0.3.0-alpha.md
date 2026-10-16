# Проверка 0.3.0-alpha

16 октября 2026 UTC. [English](en/TEST_REPORT_0.3.0-alpha.md).
Android 15+ / minSdk 35, versionName `0.3.0-alpha`, versionCode 6.
**Проверка владельцем на физическом устройстве: ожидается.**

## Реализовано в коде

Fake TLS HMAC/records/ee links, separate direct/relay-only/Auto CF, Worker → CF → TCP
с ограниченными попытками/backoff, H2 только для совместимого CF media `/api`.
Активный Android Network для DNS/сокетов, отмена/очистка при смене сети, диагностика
с реальными фазами и обезличенным экспортом, четыре операторские карточки, Keystore migration.
[Конфигурация и ограничения](TRANSPORTS_0.3.md) · [Матрица RU-01–14](RU_MOBILE_OPERATORS.md).

## Локальные автоматические проверки

**176 JVM-тестов**, 0 failures/errors/skipped. Прежние 139 тестов сохранены.
Эталон v1.11.2 `307a384ab7f522e34bb1a045884d0bd90f40bee5` не обновлён;
новые Fake TLS и HTTP-media goldens получены из закреплённых функций upstream.
Настоящий локальный TLS/H2: multiplexing/CTR, flow-control windows (256 KiB), backpressure,
cancel, RST_STREAM и GOAWAY, отказ ALPN/status/HTML/размеров. Нет тестового TLS bypass
в приложении: локальный CA доверен только специальному тестовому клиенту.

Оба локальных soak выполнялись по **180 секунд**, без ошибок; статистика проверена
по JUnit независимыми gate-скриптами, а не только по Gradle exit:

| Метрика | TCP/TLS/WSS | Полный native MTProto → core → TLS/H2 |
|---|---:|---:|
| Пакеты | 77 695 | 14 501 |
| Завершённые сессии | 21 249 | 2 839 |
| Искусственные сетевые сбросы | 179 | 178 |
| Ожидаемые разрывы при сбросах | 187 | 640 |
| Peak native sessions / лимит | 5 / 8 | 4 / 8 |
| Peak WSS / TCP / H2 sessions | 4 / 4 / — | — / — / 4 |
| Peak подготовленных WSS / лимит | 3 / 4 | — |
| Peak HTTP calls / лимит | — | 4 / 16 |
| Peak heap процесса JVM | 118 258 104 B | 115 862 512 B |

Во время H2 soak выполнено 18 523 HTTP-запроса. Peer локальный, аккаунт Telegram
не используется. Метрики JVM heap включают стенд/MockWebServer и не являются расходом
батареи или замером памяти APK на телефоне. При reset Telegram-модель создаёт новые
криптосессии, пакеты старой сессии не переигрываются.

Пройдены **28 тестов CI gate/AVD-скриптов**, **4 publication guards**, **11 Node relay**,
**15 Worker** и actionlint. Два CI gate требуют по 60 с реального soak и проверяют
наличие транспорта/reset, длительность, границы ресурсов и отсутствие skipped/failed.

В исходниках **19 обязательных Android-тестов**: прежний lifecycle/branding/Keystore,
новые migration/tamper, network stop/optional permissions, реальный локальный H2 и
полный Fake TLS → native core → TLS/H2. Фактический прогон — отдельная CI-матрица ниже.

## Сборка и GitHub CI

Полная Wrapper-команда с JDK 17, `-PsoakSeconds=180`, JVM/Lint/Debug APK/instrumentation APK:
**BUILD SUCCESSFUL**. Lint — 0 errors / 3 прежних UseKtx warnings; Debug APK и instrumentation APK собраны.
CI и Android API35/36/37.0 ожидаются;
до проверки фактических JUnit они не обозначаются PASS.

Публикация debug APK выполняется только после успешных обоих CI для одного исходного commit.
Публикационный workflow повторно сверяет CI/JUnit/оба soak/подпись и каждый ZIP entry APK
с исходным CI APK. Сохраняется прежний debug-сертификат
`26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`;
это не production signing. Исходный commit и APK checksum входят в build-info/SHA256SUMS релиза.

## Физическое устройство и ограничения

Telegram 0.3.0, внешние CF Proxy/Worker/собственное реле, мобильный МегаФон/МТС/билайн/T2,
реальные IPv6-only/DNS64/XLAT/dual-SIM/roaming и 30–60 минут screen-off — **NOT TESTED**.
Владелец выполнит доступные проверки позже; это не блокирует проверенный debug Pre-release.
Ранее сообщённые Android 16 / Telegram 13.0.2 / Wi-Fi и 6 мс в 0.2.0 не подтверждают новую версию.

Fake TLS маскирует локальный transport, не заменяет настоящий удалённый TLS и не снимает
блокировку IP. Для CF/H2 нужен совместимый внешний узел. Экспериментальный Worker
сохраняет лимит 16 MiB/сессию и может прерывать большие загрузки. Списки настраиваются
явно, автоматического интернет-обновления нет. Бесшовная миграция и тестовые DC не заявляются.
Стабильный release gate не закрыт; 1.0.0 не объявляется.
