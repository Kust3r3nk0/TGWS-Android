# Проверка 0.1.3-alpha

15 октября 2026, UTC. [English](en/TEST_REPORT_0.1.3-alpha.md).

## Изменения

Android 15+ / minSdk 35, versionName `0.1.3-alpha`, versionCode 4.
Выбираемый WSS relay-only маршрут по Worker-контракту Flowseal v1.11.2,
защищённое сохранение URL и bounded DNS. Прежние прямые маршруты по умолчанию
не изменены. Добавлен самостоятельный сервер реле вне APK.

## Локальные результаты

`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
с JDK 17 и существующим Wrapper: **BUILD SUCCESSFUL**.

- **126 JVM-тестов**, 0 failures/errors/skipped. Включают прежние побайтовые
  Flowseal-векторы; URL, AES-GCM/AAD, DC/media через реле без изменения CTR,
  отказ и deadline без прямого fallback, bounded/uninterruptible DNS,
  настоящий локальный TLS Upgrade с защищённым путём и dst/dc.
- Android Lint: **0 errors**, 3 прежних `UseKtx` warnings.
- **11 Node.js тестов**, настоящий локальный TLS/WSS → TCP echo, двусторонние
  байты/фрагменты, недоверенный сертификат, ключ пути до TCP dial, размеры,
  capacity, connect/idle/stalled-write deadlines.
- **16 тестов CI-скриптов**, **4 теста publication guards**; actionlint пройден.
- Debug APK и instrumentation APK собраны. В исходниках **13 обязательных
  инструментальных тестов**, включая encrypted relay URL, key loss и corruption.

## GitHub CI

После push должны пройти Android CI и все 13 инструментальных тестов на каждом
API 35/36/37.0. Результаты будут дополнены по фактическим JUnit, без подмены
выполнения на устройстве фактом сборки. До этого debug-предрелиз не публикуется.

## Что не подтверждено

Внешнее реле не развёрнуто. Нет предоставленного endpoint, оператора/страны и
физического прогона мобильной сети. Успех Wi-Fi в 0.1.0/0.1.1 по сообщению
владельца не проверяет новый транспорт. **Рабочий обход IP-фильтрации и внешний
relay → Telegram E2E не заявляются.** Требуется собственный доступный узел,
официальный Telegram, отправка/получение/загрузка и повторное подключение.

Fake TLS, полный Cloudflare Proxy/Worker с пулами, HTTP/2, IPv6-only и бесшовная
миграция не реализованы. Node.js сервер не относится к Android APK; его локальные
тесты не заменяют аудит и эксплуатационные испытания внешнего сервиса.

[Настройка и ограничения реле](MOBILE_NETWORKS.md).
