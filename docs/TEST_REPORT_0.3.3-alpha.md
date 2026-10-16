# Проверки 0.3.3-alpha — 16 октября 2026

Android 15+, versionCode 9; код `a8020a6984f5dcc1d7b1496cb1e05d03feeca2fe`.

| Проверка | Результат |
|---|---|
| Полный локальный Wrapper: JVM/Lint/Debug APK/Android test APK | Успешно |
| [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38860603795) | Успешно; 181 JVM, 0 failures/errors/skipped |
| Android Lint | 0 ошибок, 8 рекомендаций UseKtx |
| CI gate-скрипты / publication guards | 31 / 4 теста прошли |
| WSS/TCP soak | 60 с, 26 034 пакета, 7 112 сессий, 59 сбросов; max sessions 4, pool 3 |
| Native H2 soak | 60 с, 5 565 пакетов, 1 120 сессий, 58 сбросов; max sessions 4, HTTP calls 4 |
| [Android matrix](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38860603657) | По 25 case на API 35/36/37; 0 failures/errors/skipped |

Все 75 Android case identities сверены с исходными `@Test`, без дубликатов и пропусков.
SHA-256 пяти архивов совпали с GitHub. PR merge tree
`9a743a3b1c28357346aa17f148fbc29850537b7b` совпадает с проверяемым кодом.

## Эффект прозрачности

Настройки приложения и MTProxy → Оформление → «Эффект прозрачности». По умолчанию
выключен, включая отсутствие/некорректный тип настройки. Сохраняется отдельно от Liquid Glass,
применяется сразу без перезапуска прокси. Переключатели не выключают друг друга.

Новый native-тест меняет реальный фон родителя карточки с красного на зелёный. Пиксели
внутри панели меняются вместе с ним в обеих темах, с Liquid Glass и без него. Непрозрачная
заливка или имитация градиентом этот тест не проходит. Используется premultiplied alpha
только заливки; текст/кнопки и основное окно остаются непрозрачными.

Другой UI-тест проверяет default off, пересоздание Activity/сохранение, самостоятельный
и совместный режимы, читаемость по пикселям заголовка/состояния, отключение и неизменность
защищённых настроек и DNS. Прежние тесты Liquid Glass/MTProto/сетей сохранены.
В device-артефактах по 11 реальных PNG: прежние 7 и новые `transparency-settings`,
`transparency-light`, `transparency-dark`, `transparency-combined`. Просмотрены светлая/тёмная
прозрачность и настройки с обоими включёнными эффектами; текст и контролы читаемы.
Это эмуляторы с software GPU, не физические устройства.

## Debug APK

[Скачать](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.3-alpha-debug/TGWS-Android-0.3.3-debug.apk).
Размер 32222864 байт; minSdk 35 / targetSdk 37 / versionCode 9 / versionName 0.3.3-alpha.
ApplicationId прежний. APK получен из указанного успешного CI, подписан прежним debug-ключом;
содержимое ZIP побайтово совпадает с CI APK до подписи.

- SHA-256 APK: `f8bc57f405452512050c9a9ab6cda38b1c2eb19fae3ec2468c1deaf1f1a1903f`.
- SHA-256 debug-сертификата: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.

Бета-оформление Android в стиле Liquid Glass. Основное окно непрозрачно: обои, другие
приложения и секреты не захватываются. Нет новых разрешений/зависимостей, сетевых изменений
и постоянной анимации. Полное преломление произвольного UI/технология Apple не заявляются.

Проверка владельцем на физическом устройстве: ожидается. Реальные GPU/батарея, Telegram,
мобильный оператор, внешние CF/Worker/H2 и IPv6-only не подтверждены этой версией.
Прежний Worker сохраняет лимит 16 MiB/сессию. Debug-подпись не заменяет production release gate.

[Публикация 38861469403](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38861469403)
успешна. [GitHub Pre-release](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.3-alpha-debug)
не draft, tag указывает на проверяемый код, digest опубликованного APK совпадает с указанным выше.
[Копия на Drive владельца](https://drive.google.com/file/d/1N7LLzk-77OSpj9_S7a1HTrIgkiShDo-6/view?usp=drivesdk)
проверена по имени, MIME и размеру; настройки доступа не менялись.
