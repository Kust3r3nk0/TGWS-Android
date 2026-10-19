# Проверки 0.3.6-alpha

19 октября 2026. Android 16+ / minSdk 36, versionCode 12, прежний applicationId. Исправлена подпись минимальной ОС в RU/EN; удалены 34 устаревших платформенных файла из текущего дерева. История Git сохранена.

- Source: `71fd2a2b886bdb423ed5823d5152c8d3967f2349`.
- Проверенное дерево PR: `556fd3ea62df8eb1e7ebb435846e5ce1efc74b10`.
- [Android CI: успешно](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39190171801).
- [API 36/37: успешно](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39190171835).

| Проверка | Результат |
|---|---|
| Wrapper, JVM, APK/instrumentation | 203 теста, без failures/errors/skips; оба APK собраны |
| Android Lint | 0 ошибок, 8 прежних предупреждений UseKtx |
| Android 16 / API 36 | Все 34 ожидаемых JUnit-case, 0 ошибок/пропусков; 12 PNG |
| Android 17 / API 37 | Все 34 ожидаемых JUnit-case, 0 ошибок/пропусков; 12 PNG |
| Обновление 0.3.5 → 0.3.6 | На обоих API install-replace сохраняет секрет, ciphertext, Keystore, порт, реле, режим, тему и эффекты |
| Платформенный контракт | minSdk 36, RU/EN Android 16+, CI только поддерживаемых API; проверен manifest настоящего APK |
| Локальные relay / Worker | 11 / 17 тестов; bundle dry-run без развёртывания |
| Python CI / publication guards | 64 / 5 тестов |
| DNS / WebView | 6 DEX без встроенного Cloudflare DNS; UI/MTProto нативные, встроенного WebView нет |

Скачанные ZIP проверены по SHA-256; проверены реальные JUnit-имена/количество, отсутствие пропусков, 24 PNG, build metadata, handoff, environment before/after и upgrade JSON. Две upgrade-фазы не включены в число 34. Базовый Android 16+ APK 0.3.5 закреплён по SHA-256; временная CI-переподпись сохраняет все ZIP-байты. Между установками данные не очищаются, publishing key в CI не передаётся.

Локальные нагрузки по 60 секунд: WSS/TCP — 26 384 пакета, 7 214 сессий, 59 сбросов; HTTP/2 — 5 283 пакета, 1 033 сессии, 59 сбросов. Ограничения ресурсов и завершение проверены; это не внешний Telegram E2E.

## Проверенный экран

<img src="images/0.3.6/about-dark.png" width="280" alt="Android 16+: раздел About в 0.3.6-alpha" />

Побайтовая копия `about-dark.png`, API 36 / artifact `12158989722`, SHA-256 `6d630716c4bfa0f9964f2ca07665bbd28a6d8f4e1589adfb9b070a7778f5eccd`. API 37 также проверен. Android-регрессия отдельно проверяет RU и EN ресурсы и фактически видимый экран. Изображение не редактировалось.

## Debug APK

[Скачать](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.6-alpha-debug/TGWS-Android-0.3.6-debug.apk) · [Предрелиз](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.3.6-alpha-debug).

- Размер: 32 206 480 байт.
- SHA-256: `063373a78944f23265fcee73558d7c5e5a0c2a8b08199ced920faac0fdc75cab`.
- Сертификат SHA-256: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.
- Прежний debug-ключ; apksigner и побайтовое сравнение каждого ZIP-entry с CI успешны. Устанавливать поверх предыдущего debug APK без удаления данных. Это не production-подпись.

## Ограничения

TGWS-16-016: provider before/after — API 36 `com.google.android.webview` `133.0.6943.137`, API 37 `145.0.7632.218`; `latest_verified=false`, One UI не проверен. Инвентаризация не подтверждает актуальные Stable/Beta. Ненужный WebView/WebKit не добавлен.

TGWS-16-017: физические Telegram, Wi-Fi/LTE, One UI и Stable/Beta проверяет владелец; для 0.3.6 результаты ожидаются. Протоколы, TLS, настройки и лимит Worker 16 МиБ не менялись. Доступность операторских маршрутов и HD-upload не подтверждены этой сборкой. [Условия стабильного выпуска](STABILITY_GATE.md).
