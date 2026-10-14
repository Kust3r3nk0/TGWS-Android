# История изменений

Все значимые изменения проекта будут фиксироваться здесь. Формат основан на Keep a Changelog; версия — в духе Semantic Versioning.

## [0.1.0-alpha] — 2026-10-14
### Добавлено
- Первый публичный исходный код Android-порта на Kotlin.
- Интерфейс на Jetpack Compose.
- Первичная реализация локального MTProto-прокси, WSS и TCP fallback.
- Foreground Service, настройки и Android Keystore.
- Модульные тесты, документация, MIT License и конфигурация CI.

### Ограничения
- Debug APK собирается в Android CI; запуск на физическом устройстве и работа с Telegram пока не проверены.
- Fake TLS, Cloudflare-маршруты и HTTP/2 пока не перенесены.
- Подписанные APK и стабильные релизы отсутствуют.

[English version](docs/en/CHANGELOG.md)
