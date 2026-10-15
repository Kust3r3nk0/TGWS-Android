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

Проверенный commit: `328564b2ec6a8956a5ca4d7f12d44841b4ba85e1`; merge commit CI `c2614878a653fb25476fc9de2594594dc0ba2245` имеет ту же Git tree.

- [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38758903208): **success**, 126 JVM, 11 Node.js, Lint и оба APK.
- [Android device tests](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38758903121): **success**. Скачанные JUnit проверены обязательным gate: **13/13 на каждом API 35, 36, 37.0**, всего 39 выполнений, 0 failures/errors/skipped.
- Регрессионный тест длительной передачи падает со старым тайм-аутом реле и проходит после исправления; лимит применяется к каждой записи, а не общей длительности передачи.

Debug APK: `TGWS-Android-0.1.3-debug.apk`, тот же debug-сертификат, что 0.1.2.
Все ZIP entries совпадают побайтово с финальным CI APK; изменён только блок подписи.
SHA-256: `827887c64b1ce7b7edee5391dc021be30f31d51de83c8917e2a9c971cd4250b7`.
[Debug Pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.1.3-alpha-debug).
Это не production-подпись и не подтверждение мобильного E2E.

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
