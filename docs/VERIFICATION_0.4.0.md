# Проверки TGWS-Android 0.4.0 — кандидат

Исходный коммит приложения: `83e8703532991da78b519e1efcf1ac1e168faa0b`. Проверенное дерево CI: `595a5a0bb90a98eb39e56e1522c25d475b1e8e37`; merge-коммит CI: `a1ea00125444292eee94e594e7a38dd673a93dba`. Документация и неизменённые PNG снимков могут быть добавлены последующим коммитом; код APK соответствует указанному source.

- [Проверенная Debug-публикация](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39375050990), 6 публичных артефактов и 5 контрольных сумм.
- [PR #22](https://github.com/Kust3r3nk0/TGWS-Android/pull/22), открыт без merge/production publication.
- [Core CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39374027261) — 252 JVM-теста, без ошибок/пропусков, Debug/Release Lint и сборка обоих вариантов.
- [API 36/37 CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39374027242) — 56 обязательных записей JUnit на каждой ОС, без ошибок/пропусков. Список сопоставлен с методами исходников.
- Lint в итоговом CI: 0 ошибок, 24 предупреждения в каждом варианте (рекомендации KTX/зависимостей); локально 23/24. Проверки не отключались.
- 79 Python CI guard tests и 6 publisher tests прошли. Worker/собственное TLS/WSS реле проверены локальными CI-тестами без внешнего развёртывания.
- Фактические 60-секундные MTProto и HTTP/2 soak-тесты: 26072 / 5314 пакетов; лимиты ресурсов проверены, не заменены успешным exit code.
- Настоящий install-r **0.3.10-alpha → 0.4.0/code 17** на обеих API с одинаковой временной CI-подписью сохранил secret, исходный ciphertext, Keystore, port/relay/transport, темы/эффекты и update options.
- Локально выполнена полная Wrapper-команда плюс assembleDebugAndroidTest/lintRelease/assembleRelease. Подписанный Production workflow не выполнялся: доверенный Release-ключ не предоставлен.

## Результат

Три вкладки, отдельные подразделы всех настроек, оригинальный логотип, Premium Minimal в светлой/тёмной темах и Update Center с прежней криптографической проверкой APK. Чувствительный черновик сохраняется в ViewModel, а не plaintext bundle. Журнал ограничен 128 событиями/32 setup-замерами в памяти. Uptime использует monotonic clock; таймер только в открытой активной диагностике. Никаких фоновых активных ping/probe.

[Архитектура и Release gate](RELEASE_0.4.0.md). Обновление не изменяет MTProto/WSS/TCP ядро, TLS validation или системный DNS.

## Исправленные воспроизводимые дефекты

- Полная dd/ee-вставка секретов и домена Fake TLS сохранена при новом UI; ошибочная длинная строка не обрезается в валидный ключ. Проверки JVM + Android.
- Изменение черновика и Activity recreation не заменяют сохранённый секрет; явное копирование сначала сохраняет точные данные.
- Прокрутка вкладок сохраняется при переходе в Update Center и возврате; Back не останавливает сервис.
- Неправильная AppCompat tint-ссылка стандартных векторов исправлена без добавления зависимости; цвета задаёт Compose.
- Сжатая организация настроек/реле, действия доступны при крупном шрифте 160%, обеих темах и эффектах.
- Release verifier отвергает неполный ZIP/DEX, повреждения, неверную идентичность/версию/сертификат, Debug-подпись и debuggable APK.

## Проверяемый Debug APK

[Скачать](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.4.0-debug). Версия 0.4.0, versionCode 17, minSdk 36. Размер 35329844 байт. SHA-256:

`592a7b878b593805d821dfd4449971d96d27e3c85407cf70bb78c1487a639884`

Сертификат Debug: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`. Каждый ZIP payload byte совпадает с APK успешного CI; подпись проверена apksigner. Установить поверх прежнего Debug APK можно только при совпадении сертификата. При ошибке несовместимости не удалять приложение автоматически.

## Реальные изображения и визуальная сверка

[Главная](images/0.4.0/home-dark.png) · [Диагностика](images/0.4.0/diagnostics-dark.png) · [Настройки](images/0.4.0/settings-dark.png), светлые варианты в той же папке. Это побайтовые PNG реального приложения на эмуляторе из указанного CI; locale RU. Работает настоящий локальный listener, удалённых сессий нет. Изображения Update Center/крупного шрифта используют явные UI-test fixtures, не поставляемые как демонстрационный режим. Фикстура доступного обновления 0.4.1-alpha не означает опубликованный релиз.

Сопоставлены утверждённый Premium Minimal и реальные кадры; токены, иерархия, оригинальный логотип, три вкладки, поля и системные отступы проверены. Географические маршруты и ping из макета не имитируются. Текст и CTA адаптируются к локализации/крупному шрифту; длинные разделы прокручиваются. Числовой процент пиксельного совпадения не заявляется.

## Ограничения и приёмка

Stable **не опубликован**. Нужны доверенный отдельный Release-ключ, подписанный и проверенный Production APK, физическая приёмка (включая HD-медиа) и отдельное одобрение владельца. Новый независимый сертификат без проверенной цепочки ротации не обновляет Debug-установку; сохранение Keystore при удалении не обещается. Выполненный CI install-r проверяет одинаковый сертификат.

Физический сценарий: установить совместимый APK; проверить Home/Diagnostics/Settings, темы/эффекты/шрифт, start/stop/фон, Wi-Fi/mobile и смену сетей, Telegram SD/HD фото, видеосообщения и большие медиа в обоих направлениях, восстановление и Update Center. Обсуждать результаты в чате, без секретов и личной информации. Снятие ограничений сетей и исправление внешнего HD E2E этой UI-сборкой не заявляются.

## Изменённые файлы

- [.github/release/publish.py](../.github/release/publish.py)
- [.github/release/test_publish.py](../.github/release/test_publish.py)
- [.github/scripts/check_apk_upgrade.py](../.github/scripts/check_apk_upgrade.py)
- [.github/scripts/test_release_apk.py](../.github/scripts/test_release_apk.py)
- [.github/scripts/test_update_manifest.py](../.github/scripts/test_update_manifest.py)
- [.github/scripts/verify_release_apk.py](../.github/scripts/verify_release_apk.py)
- [.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml)
- [.github/workflows/release-candidate.yml](../.github/workflows/release-candidate.yml)
- [AGENTS.md](../AGENTS.md)
- [CHANGELOG.md](../CHANGELOG.md)
- [README.md](../README.md)
- [README_EN.md](../README_EN.md)
- [ROADMAP.md](../ROADMAP.md)
- [SECURITY.md](../SECURITY.md)
- [app/build.gradle.kts](../app/build.gradle.kts)
- [app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/BrandingInstrumentedTest.kt](../app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/BrandingInstrumentedTest.kt)
- [app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/CredentialsInstrumentedTest.kt](../app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/CredentialsInstrumentedTest.kt)
- [app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/NavigationThemeInstrumentedTest.kt](../app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/NavigationThemeInstrumentedTest.kt)
- [app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/PremiumNavigationInstrumentedTest.kt](../app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/PremiumNavigationInstrumentedTest.kt)
- [app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/UpdateSystemInstrumentedTest.kt](../app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/UpdateSystemInstrumentedTest.kt)
- [app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/UpdateUiInstrumentedTest.kt](../app/src/androidTest/java/io/github/kust3r3nk0/tgwsandroid/UpdateUiInstrumentedTest.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/AppPreferences.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/AppPreferences.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/AppScaffold.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/AppScaffold.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/AppTheme.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/AppTheme.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/DashboardScreens.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/DashboardScreens.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/DiagnosticHistory.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/DiagnosticHistory.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/MainActivity.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/MainActivity.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyFormModel.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyFormModel.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyService.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyService.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyState.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyState.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyText.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/ProxyText.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/SettingsScreens.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/SettingsScreens.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/SettingsValidation.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/SettingsValidation.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/TransportUi.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/TransportUi.kt)
- [app/src/main/java/io/github/kust3r3nk0/tgwsandroid/UpdateCenterUI.kt](../app/src/main/java/io/github/kust3r3nk0/tgwsandroid/UpdateCenterUI.kt)
- [app/src/main/res/drawable/ic_premium_content_copy.xml](../app/src/main/res/drawable/ic_premium_content_copy.xml)
- [app/src/main/res/drawable/ic_premium_hub.xml](../app/src/main/res/drawable/ic_premium_hub.xml)
- [app/src/main/res/drawable/ic_premium_link.xml](../app/src/main/res/drawable/ic_premium_link.xml)
- [app/src/main/res/drawable/ic_premium_monitor_heart.xml](../app/src/main/res/drawable/ic_premium_monitor_heart.xml)
- [app/src/main/res/drawable/ic_premium_route.xml](../app/src/main/res/drawable/ic_premium_route.xml)
- [app/src/main/res/drawable/ic_premium_send.xml](../app/src/main/res/drawable/ic_premium_send.xml)
- [app/src/main/res/drawable/ic_premium_share.xml](../app/src/main/res/drawable/ic_premium_share.xml)
- [app/src/main/res/drawable/ic_premium_signal_cellular_alt.xml](../app/src/main/res/drawable/ic_premium_signal_cellular_alt.xml)
- [app/src/main/res/values-en/premium.xml](../app/src/main/res/values-en/premium.xml)
- [app/src/main/res/values-en/strings.xml](../app/src/main/res/values-en/strings.xml)
- [app/src/main/res/values/premium.xml](../app/src/main/res/values/premium.xml)
- [app/src/main/res/values/strings.xml](../app/src/main/res/values/strings.xml)
- [app/src/test/java/io/github/kust3r3nk0/tgwsandroid/DiagnosticHistoryTest.kt](../app/src/test/java/io/github/kust3r3nk0/tgwsandroid/DiagnosticHistoryTest.kt)
- [app/src/test/java/io/github/kust3r3nk0/tgwsandroid/SecretInputTest.kt](../app/src/test/java/io/github/kust3r3nk0/tgwsandroid/SecretInputTest.kt)
- [design-qa.md](../design-qa.md)
- [docs/RELEASE_0.4.0.md](../docs/RELEASE_0.4.0.md)
- [docs/UPDATE_CENTER.md](../docs/UPDATE_CENTER.md)
- [docs/VERIFICATION_0.4.0.md](../docs/VERIFICATION_0.4.0.md)
- [docs/en/CHANGELOG.md](../docs/en/CHANGELOG.md)
- [docs/en/IMAGE_ASSETS.md](../docs/en/IMAGE_ASSETS.md)
- [docs/en/RELEASE_0.4.0.md](../docs/en/RELEASE_0.4.0.md)
- [docs/en/ROADMAP.md](../docs/en/ROADMAP.md)
- [docs/en/UPDATE_CENTER.md](../docs/en/UPDATE_CENTER.md)
- [docs/en/VERIFICATION_0.4.0.md](../docs/en/VERIFICATION_0.4.0.md)
- [docs/en/releases/0.4.0.md](../docs/en/releases/0.4.0.md)
- [docs/images/0.4.0/diagnostics-dark.png](../docs/images/0.4.0/diagnostics-dark.png)
- [docs/images/0.4.0/diagnostics-light.png](../docs/images/0.4.0/diagnostics-light.png)
- [docs/images/0.4.0/home-dark.png](../docs/images/0.4.0/home-dark.png)
- [docs/images/0.4.0/home-light.png](../docs/images/0.4.0/home-light.png)
- [docs/images/0.4.0/settings-dark.png](../docs/images/0.4.0/settings-dark.png)
- [docs/images/0.4.0/settings-light.png](../docs/images/0.4.0/settings-light.png)
- [docs/images/0.4.0/updates-center-dark.png](../docs/images/0.4.0/updates-center-dark.png)
- [docs/images/0.4.0/updates-current-light.png](../docs/images/0.4.0/updates-current-light.png)
- [docs/images/0.4.0/updates-settings-dark.png](../docs/images/0.4.0/updates-settings-dark.png)
- [docs/images/0.4.0/updates-settings-light.png](../docs/images/0.4.0/updates-settings-light.png)
- [docs/images/ASSETS.md](../docs/images/ASSETS.md)
- [docs/images/update-center/update-download-legacy.png](../docs/images/update-center/update-download-legacy.png)
- [docs/releases/0.4.0-debug.md](../docs/releases/0.4.0-debug.md)
- [docs/releases/0.4.0.md](../docs/releases/0.4.0.md)
