# Проверки TGWS-Android 0.4.1

[English](en/VERIFICATION_0.4.1.md) · [Диагностика](DIAGNOSTICS.md) · [Изменения](releases/0.4.1.md).

Версия **0.4.1 / code 18**, Android 16+ / minSdk 36, прежний applicationId. Тестовое обновление поверх кандидата Premium Minimal; стабильный Production-релиз не публиковался. [PR #23](https://github.com/Kust3r3nk0/TGWS-Android/pull/23) основан на открытом PR #22.

## Сборка и источник

- Исходники APK: `41e34af33828eb7f264c5f9d5243cdb00480a2ab`.
- Проверенный merge CI: `8aadb9fa58cf562b97048491c08d3b7792f0e431`; tree `3fa800218e28a074610f712388fa4c8ca455f613`.
- [Основной CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39469515262), [API 36/37](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39469515372), [проверенная публикация](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39470969581) — успешно.
- [Debug APK](https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.4.1-debug/TGWS-Android-0.4.1-debug.apk): 35 510 147 байт.
- SHA-256: `21c04ccce090008b71c2625519358266a6bdcb23f6c4d9bfcd1061131df0cbeb`.
- Сохранён прежний Debug-сертификат: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`.

Все ZIP payload entries опубликованного APK совпали с APK успешного CI после переподписи. Публично загружены обратно и проверены 6 assets / 5 checksum: APK, build-info, BUILD, changelog и PREVIEW update-manifest. Сертификат повторно проверен `apksigner`. Данные и ключ подписи в репозиторий не добавлялись.

## Реально выполненные проверки

| Проверка | Результат |
| --- | --- |
| JVM / криптография / протокол / маршруты / updater / диагностика | 270, без ошибок и пропусков |
| Android API 36 и 37 | По 61, точный набор JUnit methods сверён с исходниками |
| Debug / Release Lint в CI | 0 ошибок; по 24 предупреждения |
| Локальная Wrapper-команда, AndroidTest APK, Release Lint/build | Успешно; локальный Debug Lint — 23 предупреждения |
| Native UI, minSdk, отсутствие встроенного Cloudflare DNS, публичная metadata | Успешно |
| Python guards / publisher | 80 / 6, успешно |
| Собственное WSS-реле / Worker | 11 / 17, без ошибок |
| MTProto soak | 60 с, 25 493 пакета / 6 958 сессий, максимум 4 сессии / 3 в пуле / 6 peer sockets |
| HTTP/2 soak | 60 с, 5 209 пакетов / 1 043 сессии, максимум 5 HTTP calls |
| Обновление APK с 0.3.10-alpha на code 18 | API 36/37: секрет, **шифротекст**, Keystore, proxy/relay/transport/UI/update settings сохранены |

Первый запуск сервиса сохраняет форму с новым GCM nonce, сохраняя значения. Новые Android-тесты отдельно проверяют равенство настроек до/после явного Start, затем неизменность шифротекста при диагностике, пересоздании Activity и несохранённом редактировании порта. Проверка upgrade использует одинаковую временную CI-подпись на обеих версиях и не подменяет проверку доверенной Production-подписи.

Новое покрытие: фактический WebView provider сверяется с Android API; локальный TCP-check не передаёт байты/секрет и не порождает ложный отказ handshake; занятый порт создаёт реальную ошибку, фильтры переживают recreation, очистка не меняет настройки; JSON корректен, обезличен, read-only; APK installer отвергает файлы из отдельного каталога отчётов; отключение записи работает через UI; изменение порта сбрасывает старый результат.

Unsigned Release-кандидат проверен: `debuggable=false`, minSdk 36, 27 335 664 байта, SHA-256 `72a7caf80b74cf33e157709683036b206bf77c3985ec3c20a5cdf9d1c97b184b`. Production-подпись не подтверждена, пакет не распространяется как Production.

## Реальные снимки

Из 29 PNG на каждом API в документацию скопированы без изменений два снимка API 36, artifact `12286452994` (ZIP SHA-256 `d8c5bdbdb9d00c8cb02bdbe6fc003773d8f0e691edf12df86aead5a803683cd5`). Это реальное приложение, не генеративный макет.

| Снимок | Сценарий | SHA-256 PNG |
| --- | --- | --- |
| [Журнал](images/0.4.1/journal-dark.png) | Намеренно занят локальный порт, фильтр ERROR / SERVICE | `e1a7b78fdd3a70d56ee07e6db5283384edcaeb53afb541d8dff278614d0e4645` |
| [WebView и локальная проверка](images/0.4.1/webview-dark.png) | Реальный listener и установленный provider | `54ee5c0d0174a4bd049c1c6788be847ecd346a5e612f1562c20728dbfafb6bd6` |

Установленные версии WebView эмуляторов: API 36 — `133.0.6943.137`, API 37 — `145.0.7632.218`. Они совпадают с независимой CI-инвентаризацией. Это **не сертификация Latest Stable/Beta**. Браузерный движок не запускается, у TGWS нет страниц/renderer; их логи и журналы других приложений недоступны.

## Что изменено и как проверить

[Полный diff](https://github.com/Kust3r3nk0/TGWS-Android/pull/23/files). Основные файлы: `DiagnosticHistory`, `DiagnosticLog`, `DiagnosticReport`, `DiagnosticJournalUi`, `DiagnosticToolsModel/Ui`, `LocalListenerCheck`, `WebViewStatus/ProviderReader`, `ProviderPackageMonitor`, `DiagnosticExport`, `MainActivity`, `ProxyService`, `ProxyState`, `core/RouteDiagnostics`, отдельный FileProvider/paths, RU/EN ресурсы, тесты и CI guards. Протокольная криптография и транспорты не переписывались.

Установите Debug APK поверх прежней сборки с тем же сертификатом, **без удаления приложения**. Проверьте запуск/остановку, работу Telegram и медиа, смену сети, фильтры журнала, локальный check, отключение записи и предпросмотр JSON. После штатного обновления/смены WebView-провайдера вернитесь в приложение и сравните сведения. [Инструкция без компьютера](DIAGNOSTICS.md).

Реальную доступность сетей, HD-медиа и поведение OEM/свежих Stable/Beta подтверждает владелец; личные результаты не публикуются. История только в памяти; диагностический TCP-check не подтверждает Telegram. Отдельный доверенный Release-ключ и одобрение Production по-прежнему необходимы.

Дополнительная регрессия тестового окружения: драйвер UiAutomator подключается до создания первого Compose-дерева, чтобы API 37 не сохранял устаревшие virtual IDs. Assertions навигации и сохранности секретов не отключались, тайм-ауты не увеличивались. Это изменение AndroidTest, а не кода опубликованного APK; результаты последнего прогона доступны в checks PR.
