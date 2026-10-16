# Проверки 0.3.4-alpha — 16 октября 2026

Android 15+, versionCode 10. Исходники приложения:
`9756be8d49838c1f7f90f415eb4a5948e101b988`.
Проверенный PR merge tree `730e2c9ef376d6c0c1b6b498376ef7091d9837ed` совпадает с этим source.

| Проверка | Фактический результат |
|---|---|
| Полный локальный Wrapper: JVM, Lint, Debug APK, Android test APK | Успешно |
| [Android CI 38974842759](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38974842759) | Успешно; 190 JVM, 0 failures/errors/skipped |
| Android Lint | 0 ошибок, прежние 8 рекомендаций UseKtx |
| CI-скрипты / publication guards | 50 / 4 теста прошли |
| Локальное TLS/WSS→TCP реле | CI успешно выполнил Node.js тесты |
| WSS/TCP soak | 60 с; 26 300 пакетов, 7 187 сессий, 59 сбросов; max sessions 4 / pool 3 |
| Native HTTP/2 soak | 60 с; 5 154 пакета, 1 020 сессий, 59 сбросов; max sessions 4 / HTTP calls 4 |
| [Android-матрица 38974842757](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38974842757) | По 26 case на API 35/36/37; 0 failures/errors/skipped |
| APK DEX guard / декларации native UI | Прошли; DoH fixtures/library/endpoint исключены из 6 DEX, 49 production/declaration файлов без embedded WebView |

SHA-256 всех пяти архивов совпали с GitHub. Все 78 Android case identities сверены с текущими
`@Test`, без дубликатов и пропусков. По 11 native PNG на API; просмотрены Home light API35,
Home dark API36 и настройки DNS API35/37. Системный DNS показан, Cloudflare selector отсутствует;
темы/навигация/текст читаемы. Это эмуляторы с software GPU, не физические устройства.

## Изменённое поведение и регрессии

Встроенный Cloudflare DNS удалён из приложения и runtime-зависимостей. Миграция удаляет
только старое публичное поле `dns`, включая неизвестный/некорректный тип; Android-тесты
проверяют сохранность зашифрованной конфигурации, тем, Liquid Glass и прозрачности.
Прежние DoH-тесты не удалены: общий test-only source set для JVM и Android, локальные TLS fixtures.

Семейства TCP чередуются до cap 8 с сохранением первого предпочтения ОС, шаг 250 мс,
две попытки на соединение, общий deadline, ограниченные пул/очередь и закрытие проигравших.
Семь новых JVM-сценариев: blackhole предпочтительного IPv4/IPv6, много AAAA перед IPv4,
сохранение другого семейства до cap, отказ обоих, отмена/инвалидация, восемь отказов без цикла,
закрытие сокета владельцем. Прежние реальные локальные IPv4/IPv6/TLS тесты сохранены.
DNS/TCP/TLS/deadline отличаются от HTTP Upgrade; два теста проверяют классификацию и probe.
Прежний тест зависшего relay теперь проверяет точный TIMEOUT, relay-kind, закрытие, отсутствие
прямого fallback и отправленных init-байтов. Ожидание общей relay-ошибки было обновлено
из-за намеренного уточнения категории; проверки ресурсов и запрета fallback не ослаблялись.

## TGWS-16-016 / TGWS-16-017

Compose UI и MTProto нативные. WebView renderer/page/features не используются; WebKit без
необходимости не добавлен. Новый WebView UI потребует условных API, безопасности и renderer
recovery согласно [контракту](TGWS-16-016_WEBVIEW.md). Текущий guard проверяет исходные
декларации/зависимости, не является полной проверкой DEX/reflection или сертификацией каналов.

CI собрал сведения до/после без запуска/смены WebView и без персональных идентификаторов:

| Android / API | Производитель / модель эмулятора | Выбранный provider | Версия до = после |
|---|---|---|---|
| 15 / 35 | Google / sdk_gphone64_x86_64 | com.google.android.webview | 124.0.6367.219 |
| 16 / 36 | Google / sdk_gphone64_x86_64 | com.google.android.webview | 133.0.6943.137 |
| 17 / 37 | Google / sdk_gphone64_x86_64 | com.google.android.webview | 145.0.7632.218 |

Канал определяется по package name как Stable; `latest_verified=false` во всех записях.
Это версии образов, **не проверка актуальных Latest Stable/Beta**. One UI неприменим к этим
эмуляторам, поле null. Stable/Beta/One UI/обновление provider на смартфонах ожидаются от владельца.

APK-артефакт Actions содержит `OWNER_HANDOFF.md`, `build-info.json`, `SHA256SUMS`, changelog и
`native-ui.json`. Проверены отсутствие неразрешённых шаблонов, версия, source/checked tree SHA
и URL запуска. Device-артефакты содержат `android-environment/before.json` и `after.json`.
Физическое тестирование полностью за владельцем; Codex не ищет телефоны/доступ ADB.
[Порядок проверки](TGWS-16-017_PHYSICAL_TESTING.md).

## Debug APK и пределы подтверждения

versionName 0.3.4-alpha / versionCode 10 / minSdk 35 / targetSdk 37. Прежний applicationId и
название TGWS-Android. Payload переподписанного CI APK совпал побайтово с успешным CI.
Размер 32 186 000 байт.

- SHA-256 APK: `453c6676053c439709508a16024267f502f079b8ce7b8b8cc9952c2bd997eab8`.
- SHA-256 прежнего debug-сертификата: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.

[Debug Pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.4-alpha-debug)
опубликован после этих проверок. [Публикация 38975728048](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38975728048) успешна. Tag указывает на проверяемый source, Pre-release не draft; digest опубликованного APK совпадает с указанным выше. Это debug-подпись, не production-релиз.

[Копия для владельца на Google Drive](https://drive.google.com/file/d/15cbJNWZET2CCm4uyL92l4zaDb636Dpa6/view?usp=drivesdk) проверена по имени, MIME и размеру; настройки доступа не менялись.

Владелец сообщил об отказах предыдущей сборки **во всех трёх режимах**; точная предыдущая
версия/SHA и этап ошибки не указаны. Устранение отказов оператора/реального Wi-Fi/Telegram
новой сборкой **не подтверждено**. DNS не снимает блокировку IP/SNI/allowlist. Доступный внешний
endpoint, DNS64/CLAT, внешние CF/Worker/H2 и долговременная работа требуют проверки владельцем.
ECH/полный H2 control отсутствуют; Worker сохраняет лимит 16 MiB/сессию.
[Причины и варианты](NETWORK_COMPATIBILITY_0.3.4.md).
