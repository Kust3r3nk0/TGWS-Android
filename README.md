# TGWS-Android

[![Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Kust3r3nk0/TGWS-Android/actions/workflows/android-ci.yml)

[![Android](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/14)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Status](https://img.shields.io/badge/Status-0.1.0--alpha-orange)

**Независимый open-source порт [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) на Android 14+.** Разработка: **Makarov Lab's 3.0™**.

**[English README](README_EN.md) · [План разработки](ROADMAP.md) · [Проверки и Telegram E2E](docs/TESTING.md) · [Отчёт](docs/TEST_REPORT.md)**

> [!IMPORTANT]
> **Ранняя альфа-версия.** Это не готовый релиз, не подтверждённый рабочий прокси и не полная замена оригинального проекта. Android-сборка и базовые JVM-тесты прошли CI; реальное соединение с серверами Telegram пока **не проверено**. Не используйте эту версию как единственный канал связи.

## Как работает TGWS-Android

![Схема работы TGWS-Android: текущая реализация и план развития](docs/images/tgws-android-workflow.png)

*Схема показывает текущую архитектуру исходников 0.1.0-alpha и запланированные возможности версии 1.0. Изображение не означает, что все функции уже реализованы или протестированы.*

## О проекте

TGWS-Android — нативное Android-приложение на Kotlin и Jetpack Compose для запуска локального MTProto WebSocket-прокси. Планируется функциональная совместимость с сетевым ядром Flowseal TG-WS-Proxy, включая WebSocket, TCP fallback, Fake TLS, Cloudflare-маршруты и HTTP/2. Приложение **не является VPN** и не перехватывает системный трафик.

## Текущее состояние (0.1.0-alpha)

| Возможность | Статус |
|---|---|
| Obfuscated2, AES-CTR, relay initialization | Реализовано и протестировано: побайтовые JVM-векторы Flowseal v1.11.2 |
| Abridged, Intermediate, Padded Intermediate | Реализовано и протестировано: JVM, пакет до 4 МиБ, повреждённые длины отклоняются |
| Локальный listener и WSS → TCP fallback | Реализовано и протестировано: локальные интеграционные тесты; публичная сеть Telegram требует проверки |
| WebSocket framing, HTTP Upgrade, TLS | Реализовано и протестировано: локальные RFC 6455 / TLS тесты; реальные WSS-узлы требуют проверки |
| Foreground Service, реакция на потерю сети | Основной lifecycle протестирован на эмуляторах API 34–37; реальные Wi-Fi/LTE требуют проверки |
| Compose / Material 3, русский и английский UI | Реализовано, требует проверки на устройстве |
| Android Keystore AES-256-GCM | Реализовано и протестировано на эмуляторах API 34–37; утрата ключа требует явного восстановления |
| Реальное подключение официального Telegram | Запланировано; физическое устройство сейчас недоступно |
| Fake TLS (ee-secret) | Запланировано |
| Cloudflare Proxy, Worker и балансировка | Запланировано |
| HTTP/2 для медиатрафика | Запланировано |
| IPv6-only, миграция сетей, тестовые DC | Запланировано |
| Debug APK | Тестовый артефакт с отладочной подписью; не пользовательский релиз |
| Доверенно подписанный APK / GitHub Pre-release | Запланировано; release gate не пройден |

## Технологии

- **Android:** minSdk 34 (Android 14); compileSdk 37.0 / targetSdk 37.
- **Kotlin:** 2.4.21; **AGP:** 9.4.1; **Gradle Wrapper:** 9.8.1 с проверкой SHA-256.
- **UI:** Jetpack Compose / Material 3; Java bytecode target 17.
- **Без Python в APK:** Python из оригинального проекта используется только для эталонных тестов совместимости.
- **ABI:** APK формируется для совместимых Android-устройств, включая ARM64; отдельного NDK-кода пока нет.

Версии зафиксированы для воспроизводимости. Изменения зависимостей допускаются после проверки совместимости в CI.

## Сборка для разработчиков

1. Установите Android Studio с поддержкой AGP 9.4, JDK 17, Android SDK `platforms;android-37.0` и `build-tools;36.0.0`.
2. Клонируйте этот репозиторий и откройте его в Android Studio.
3. Для сборки в терминале используйте комплектный Gradle Wrapper: `./gradlew :app:assembleDebug` (Linux/macOS) или `gradlew.bat :app:assembleDebug` (Windows).
4. Основная проверка: `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
5. Сборка инструментальных тестов: `./gradlew --no-daemon :app:assembleDebugAndroidTest`; выполнение на устройстве: `./gradlew --no-daemon :app:connectedDebugAndroidTest`.

> [!NOTE]
> Подтверждены 104 JVM-теста, Lint, сборка APK и по 10 инструментальных тестов на эмуляторах API 34–37. [Результаты и CI](docs/TEST_REPORT.md). Реальное подключение официального Telegram не проверено.

CI запускается при push в `main`, Pull Request и вручную. Успешная проверка
публикует debug APK, `SHA256SUMS`, версию и commit, changelog; отчёты JVM/Lint
сохраняются отдельно. Actions и Wrapper закреплены по проверенным SHA.
Отдельный workflow при PR и вручную проверяет Android API 34, 35, 36, 37.0.

## Поведение альфы

Listener принимает только `127.0.0.1`; порт по умолчанию — `1443`. Запуск
сервиса выполняется действием пользователя. Открытый локальный порт не означает
подключение Telegram: экран отдельно показывает ожидающие и установленные
транспортные сессии. Счётчик не подтверждает отправку сообщения.

WSS использует обычную проверку доверия TLS и имени `kwsN.web.telegram.org`.
По умолчанию upstream задаёт WSS redirect `149.154.167.220` для DC 2 и 4;
остальные DC идут по прямому TCP. Media сначала пробует `kwsN-1`, затем `kwsN`.
Существующий DC 203 сохранён как отдельный TCP-маршрут. Тестовые DC `10001–10005`
отклоняются. Fronting с отключением проверки имени сервера не переносится.

Секрет скрыт по умолчанию. Отображение, копирование и открытие Telegram-ссылки
требуют действия пользователя; ссылка использует `dd` для Padded Intermediate.
Маркер transport `ee` поддерживается в базовом Obfuscated2, но Fake TLS с
`ee-secret` в эту версию не входит. Ciphertext сохранённого секрета не заменяется
молча при утрате ключа Keystore. Диагностика содержит только категории событий.

При смене сети существующие сессии закрываются; Telegram должен создать новые.
Бесшовной миграции нет. Полные критерии, ограничения и фактически выполненные
проверки приведены в [отчёте](docs/TEST_REPORT.md).

## Архитектура исходников

```text
app/src/main/java/io/github/kust3r3nk0/tgwsandroid/
├── MainActivity.kt        # Экран управления
├── ProxyService.kt        # Android foreground service
├── ProxyState.kt          # Состояние сервиса
├── SettingsStore.kt       # Keystore и настройки
└── core/
    ├── MtprotoCrypto.kt   # Обфускация и AES-CTR
    ├── MtprotoServer.kt   # Локальный listener, мост, TCP fallback
    ├── PacketSplitter.kt  # Пакетные границы MTProto
    └── RawWebSocket.kt    # WebSocket-клиент
```

## Совместимость с Flowseal

- Оригинальный проект: [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy).
- Эталонная версия: **v1.11.2**, commit `307a384ab7f522e34bb1a045884d0bd90f40bee5`.
- Мы не представляем и не аффилированы с официальными сопровождающими оригинала. Это независимая реализация, созданная на основе открытого кода и протокольного поведения проекта.
- Attribution и условия лицензирования: [LICENSE](LICENSE) и [NOTICE.md](NOTICE.md).

## Open Source

Код распространяется по лицензии **MIT**. Приветствуются сообщения об ошибках, тестирование и Pull Requests. Перед внесением изменений ознакомьтесь с [CONTRIBUTING.md](CONTRIBUTING.md) и [SECURITY.md](SECURITY.md).

**Разработчик / инициатор проекта:** Makarov Lab's 3.0™.  
**Репозиторий:** `Kust3r3nk0/TGWS-Android`.
