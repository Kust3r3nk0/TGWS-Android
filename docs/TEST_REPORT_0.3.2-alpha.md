# Проверки 0.3.2-alpha — 16 октября 2026

Android 15+, versionCode 8. Проверяемый код: `1d16f94cd06a21cf118cf53878a25672f6cb045d`.

| Проверка | Фактический результат |
|---|---|
| Локальный Gradle Wrapper: JVM, Lint, Debug APK и Android test APK | Успешно |
| [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38858140036) | Успешно; 181 JVM, 0 failures/errors/skipped |
| Android Lint | 0 ошибок; 7 рекомендаций UseKtx |
| Скрипты обязательных gates / publisher guards | 31 / 4 теста прошли |
| Локальное WSS/TCP испытание | 60 с; 26 245 пакетов, 7 176 сессий, 59 сбросов; max sessions 4, max pool 3 |
| Локальное native H2 испытание | 60 с; 5 535 пакетов, 1 112 сессий, 59 сбросов; max sessions 4, max HTTP calls 5 |
| [Android matrix](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38858140039) | API 35: 23; API 36: 23; API 37: 23; 0 failures/errors/skipped |
| APK | Собран в CI, подписан прежним debug-ключом; содержимое ZIP совпадает с CI до подписи |

Реальные JUnit identities всех 69 Android cases сверены с исходными `@Test`; пропусков,
дубликатов или подмены результата количеством нет. SHA-256 пяти архивов сверены с GitHub.
PR merge tree `5a84167ed5df1ee62f370d5611bfdb2354666465` совпадает с проверяемым кодом.

## Liquid Glass · бета

Настройки приложения и MTProxy → Оформление → Liquid Glass. По умолчанию выключен,
включая отсутствие ключа после обновления и повреждённый тип настройки. Выбор применяется
сразу, сохраняется отдельно от MTProxy и не требует остановки прокси.

Новый native UI-тест проверил исходное выключенное состояние, включение, сохранение после
пересоздания Activity, светлую/тёмную отрисовку, видимые пиксели текста заголовка и состояния,
отключение и возврат исходного фона. Защищённые настройки и DNS до/после совпали.
На прозрачных поверхностях явно заданы цвета текста соответствующей темы.

В каждом device-артефакте находятся семь реальных PNG: `home-light`, `home-dark`,
`settings-dark`, `glass-disabled`, `glass-settings`, `glass-light`, `glass-dark`.
Glass light/dark и экран настройки просмотрены; текст и элементы управления читаемы.
Это снимки эмуляторов с программным GPU, а не физического устройства.

Эффект использует нативный RuntimeShader: общий процедурный фон, матирование и преломление
по краям панелей. Произвольное содержимое под карточками не захватывается и не преломляется.
Нет новых зависимостей/разрешений, постоянной анимации или изменений сетевого ядра.

## Тестовый APK

[Скачать Debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.2-alpha-debug/TGWS-Android-0.3.2-debug.apk).
Размер: 32202384 байт; minSdk 35, targetSdk 37, applicationId прежний.

- SHA-256 APK: `d599e19e29ce201e9577eb89d8297c0d9a9a337e411c561ca1797a7ebe90ce3e`.
- SHA-256 debug-сертификата: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.
- Production-подпись и стабильный release gate не заявляются.

## Осталось проверить

Проверка владельцем на физическом устройстве: ожидается. Производительность и батарея
Liquid Glass на реальном GPU не измерены. Новые Telegram E2E, Wi-Fi/мобильная сеть,
внешние CF/Worker/H2 и IPv6-only не подтверждены этой версией. Более ранние сообщения
владельца о рабочем Telegram не подменяют проверку 0.3.2. Worker сохраняет лимит 16 MiB/сессию.

Публикация: [workflow 38859005807](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38859005807)
успешен; [Pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.2-alpha-debug)
не draft. Tag указывает на проверяемый commit, digest опубликованного APK совпадает с указанным выше.
[Копия на Google Drive владельца](https://drive.google.com/file/d/1NODXWBqZpo0Xhdp4K5Q1467F5iACFoOE/view?usp=drivesdk)
проверена по имени, MIME и размеру; доступ к файлу не менялся.
