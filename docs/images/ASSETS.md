# Визуальные материалы репозитория

[English](../en/IMAGE_ASSETS.md).

## Логотип и обложки

Исходный [логотип владельца](tgws-logo.jpg) сохранён без изменений. Обложки RU/EN используют тот же PNG-символ, что и приложение (`app/src/main/res/drawable-nodpi/tgws_symbol.png`), встроенный без изменения байтов. Обложки — самостоятельные SVG без внешних шрифтов, JavaScript или сетевых ресурсов; номер версии вынесен в README, чтобы изображение не устаревало.

## Иконки технологий

Иконки Kotlin, JavaScript, Python, Bash, Android, Jetpack Compose, Gradle и GitHub Actions взяты из [Devicon v2.17.0](https://github.com/devicons/devicon/tree/54cfe13ac10eaa1ef817a343ab0a9437eb3c2e08), commit `54cfe13ac10eaa1ef817a343ab0a9437eb3c2e08`. Локальные исходные SVG сохранены без изменения; [лицензия Devicon](tech/LICENSE-devicon.txt) приложена. Названия и знаки принадлежат соответствующим владельцам; партнёрство не заявляется. Значок `</>` для AGSL — условная иллюстрация кода, а не официальный логотип.

`tgws-stack.svg` объединяет эти векторные элементы и подписи. Встроенные id градиентов имеют префиксы, чтобы избежать пересечений.

| Иконка | SHA-256 оригинального SVG |
|---|---|
| [kotlin](tech/kotlin.svg) | `5053c2e1288582d1ac2cfc1e807eabb7f6059b1aa2f7d04e5cf00063682a2cbe` |
| [javascript](tech/javascript.svg) | `0656ff65fc8eeacda5c78d7f9ffe91ec1eb919db64f56e0b7dcd460af4bbd36c` |
| [python](tech/python.svg) | `71493b4a732f0532b3a03a7a95da3ee11a65766367c8303b901ffbe7bf91a3a2` |
| [bash](tech/bash.svg) | `8e9b1f859331c8d11571e77b1371c5d7ac7e380abfcd2f31160ed81dc37de403` |
| [android](tech/android.svg) | `d6c8aa6c2b94c90ff02c1b7e89f9cf863d54db59b459b8b287c19bfb8e664c41` |
| [jetpackcompose](tech/jetpackcompose.svg) | `aaf2741b2e2c655142264aba92d7c6805b549a34eab2a01e4f26f7e623749d18` |
| [gradle](tech/gradle.svg) | `2caf72625f88b8062f38ee4bed0193f5fe910b6ec73951e239584d55c1bde55a` |
| [githubactions](tech/githubactions.svg) | `3a2524052ccf301d1a239f20cfef98d655e060c51cc29c5e94c867913899a47e` |

## Снимки Android 16+

Снимки **0.3.5-alpha** из [device CI 39187502586](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39187502586), source `fb72b67775b6dff5a71d804ec596850963b409a6`; по 33 теста на API 36/37. Файлы скопированы побайтово, без ретуши/сжатия. Английская локализация, software GPU; эффекты показаны включёнными, по умолчанию выключены. Это не физический Telegram E2E.

| Файл | API / artifact | Оригинал | SHA-256 |
|---|---|---|---|
| [home-light.png](0.3.5/home-light.png) | 37 / `12159016676` | `transparency-light.png` | `593af30a64032dc580b9a4533a70cf98e5f461d73336fef8b46569e00162fe72` |
| [home-dark.png](0.3.5/home-dark.png) | 36 / `12158608352` | `transparency-dark.png` | `ca0f0456878ab2c732e53ab01138dcad146f2c9f6a0fd12e3f7ebf819b559fa2` |
| [settings-glass.png](0.3.5/settings-glass.png) | 37 / `12159016676` | `transparency-combined.png` | `ebc5d0df342b36be8f0a454f81abb3d645d3d8af0694180b494a794ec1e25bb9` |

## Update Center

- `docs/images/update-center/update-download-legacy.png`: декоративная иллюстрация, созданная по утверждённому владельцем первому концепту; не содержит экранов или текста UI. Фирменный логотип переиспользуется.
- `app/src/main/res/drawable/ic_update_*.xml`: Google Material Symbols, Apache-2.0; исходные URL указаны в каждом файле. Цвет задаёт Compose. [Лицензия](../licenses/MaterialSymbols-Apache-2.0.txt).

Снимки `update-center/*-api36.png` получены из настоящего Compose UI в Actions 39348216792 (commit 5f33810), скопированы без изменений. Состояние доступного релиза использует только тестовые данные, не включённые в обычный APK. Подробности: [отчёт](../UPDATE_CENTER_VERIFICATION.md).

## Premium Minimal 0.4.0

`ic_premium_*.xml` — Google Material Symbols Outlined, Apache-2.0, commit `49d4db35df873165d6bd6ba09b063c7dafbac2f4`. Точные исходные URL сохранены в XML; [лицензия](../licenses/MaterialSymbols-Apache-2.0.txt). Фирменный `tgws_symbol` не изменялся.

В `ic_premium_*.xml` удалён AppCompat-атрибут `colorControlNormal`; геометрия Google сохранена, tint задаёт Compose. Зависимость AppCompat не добавляется.

Снимки 0.4.0 из [CI 39374027242](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39374027242), source `83e8703532991da78b519e1efcf1ac1e168faa0b`, API 36 artifact `12243305738` (ZIP SHA-256 `7ab81c34f4b6ec8ed2b3154b7c6bfb92c93e9200ded9618d7cfe3dd7417b08d1`). 56 тестов прошли на каждой API 36/37. PNG скопированы без редактирования. Главные три экрана — настоящий запущенный listener без удалённых сессий; Update Center использует явные test fixtures 0.4.1-alpha/Current, без demo mode в APK.

| Файл | Исходный PNG | SHA-256 |
|---|---|---|
| [0.4.0/home-dark.png](0.4.0/home-dark.png) | `premium-home-dark.png` | `c94533c626a1195589e0068426c32a61aadcf76206e12e8cb7da3d544d0c21e0` |
| [0.4.0/diagnostics-dark.png](0.4.0/diagnostics-dark.png) | `premium-diagnostics-dark.png` | `4890ecebfb2cf2d66cdfd92fc62e295c5cd4773a26368125b64db4a15acda088` |
| [0.4.0/settings-dark.png](0.4.0/settings-dark.png) | `premium-settings-dark.png` | `7719ff1d3f527697f568d34c73ea642f223ff9db526827857e2b01ab9c139d26` |
| [0.4.0/home-light.png](0.4.0/home-light.png) | `premium-home-light.png` | `19adfc75f7f51425f262401a9e053191798d74e0ad2a90f3c14fd06064a49019` |
| [0.4.0/diagnostics-light.png](0.4.0/diagnostics-light.png) | `premium-diagnostics-light.png` | `0028dac577c696ce66b00168decdc6c69d91ba55510980b1fce817cfb3303d60` |
| [0.4.0/settings-light.png](0.4.0/settings-light.png) | `premium-settings-light.png` | `88ddbd764229f0b77ea3b12f33eae302ce0974c5e377a304e518350b7b7ed19f` |
| [0.4.0/updates-center-dark.png](0.4.0/updates-center-dark.png) | `updates-center-dark.png` | `9166ddcc4a495cfd14ef938fa161363193a3bc1189b7f7ab65d263366262bb24` |
| [0.4.0/updates-settings-dark.png](0.4.0/updates-settings-dark.png) | `updates-settings-dark.png` | `5cb187bfb92ed2acb8ce22f465309922603646c6655bd63e70ecd33d4805813d` |
| [0.4.0/updates-current-light.png](0.4.0/updates-current-light.png) | `updates-current-light.png` | `b61a7964747c7ba55c72991e47c35cd22617d02c367bc89cf0c4690fc3b9a360` |
| [0.4.0/updates-settings-light.png](0.4.0/updates-settings-light.png) | `updates-settings-light.png` | `6130c125c2ee8ec5fe437e0b09ac1734ebfa2c4f82c98d63301e2ccb2445e0c1` |

## Диагностика 0.4.1

`0.4.1/journal-dark.png` и `0.4.1/webview-dark.png` — неизменённые снимки настоящего приложения из CI API 36, source `41e34af33828eb7f264c5f9d5243cdb00480a2ab`, artifact `12286452994`. Не генеративные изображения. [Сценарии, SHA-256 и ограничения](../VERIFICATION_0.4.1.md). Исходный логотип `tgws_symbol` сохранён.
