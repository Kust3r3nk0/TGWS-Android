# Проверки 0.3.7-alpha

Обновление статусов по [ответам владельца для 0.3.6](OWNER_VALIDATION_0.3.6.md). Сетевое ядро не меняется; зарегистрированные сбои не объявлены исправленными. Физическая проверка 0.3.7 отдельно не заявляется.

## Проверенная сборка

- Android 16+, minSdk 36, versionName `0.3.7-alpha`, versionCode 13.
- Source: `6eaaa68a42a923ec62c78c5c6dae6815e52dc35c`.
- Проверенное PR-дерево: `8969074b712f7dee62b3e95780b5f8e60705deee`. Последующие изменения отчёта не меняют приложение и сборочные входы.
- [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39193702691) — успешно.
- [Android API 36/37](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39193702410) — успешно.
- [Publication guards](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39193702405) — успешно.

## Автоматические результаты

| Проверка | Результат |
|---|---|
| Полная Wrapper-команда, Debug APK и instrumentation APK | Пройдено локально и в CI |
| JVM JUnit | 203, без failures/errors/skips |
| Android Lint | 0 ошибок, 8 прежних предупреждений UseKtx |
| CI Python / publication guards | 64 / 5 тестов, пройдено |
| Node relay / Cloudflare Worker | 11 / 17 тестов, пройдено; Worker bundle проверен |
| Android JUnit | По 35 на API 36 и 37, без failures/errors/skips |
| Снимки UI | По 13 PNG; `owner-validation-dark` просмотрен на обеих API, текст отчёта виден |
| Установка обновления 0.3.6 → 0.3.7 | На обеих API сохранены секрет, ciphertext, Keystore и настройки; install-replace и payload checks пройдены |
| Локальные WSS/TCP и HTTP/2 soak | По 60 секунд, автоматические ограничения ресурсов пройдены |
| Минимальная ОС APK / DNS | API 36 подтверждён; production Cloudflare DNS отсутствует |

Проверены SHA-256 четырёх архивов Actions и фактические JUnit-записи, включая `malformedLongSecretCannotBeTruncatedIntoValidSavedKey`. В устаревшем запуске другого source этот тест потерял доступ к UI; рядом в журнале было переполнение Binder, причинная связь не доказана. В финальной матрице все проверки включены и проходят; это не доказательство исправления сетевых сбоев.

WebView не встроен; renderer/page проверки неприменимы. Инвентаризация CI-провайдера не подтверждает актуальность Stable/Beta: API 36 — `133.0.6943.137`, API 37 — `145.0.7632.218`, пакет `com.google.android.webview`. Это AOSP-эмуляторы, не Samsung/One UI владельца.

## Установочный debug APK

Файл: `TGWS-Android-0.3.7-debug.apk`, 32 210 576 байт.

SHA-256: `bc6fadfb50d0d224789a26ec45cbc9f72b14556d3d8ba6bdbff2ffc5c31ebdb2`.

Сертификат SHA-256: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`. Сохранён прежний debug-ключ; содержимое APK побайтово совпадает с CI до подписи. Подпись проверена `apksigner`; это не production-релиз.

## Открытые проблемы

У владельца в 0.3.6 работают текст, обычные фото/голосовые, сохранение настроек и оформления. Сообщено «Ошибка Obfuscated2» при start/stop (источник и остановка listener не установлены), сбои HD-фото/кружков в direct и relay, ручное восстановление сети и нестабильность длительных соединений. Направление проблемных медиа и причина отказов в поездках неизвестны. Файл 1–5 МиБ не проверялся. Эти результаты не переносятся автоматически на новую сборку как полный pass.
