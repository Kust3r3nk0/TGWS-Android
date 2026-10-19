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

- `app/src/main/res/drawable-nodpi/update_download_glass.png`: декоративная иллюстрация, созданная по утверждённому владельцем первому концепту; не содержит экранов или текста UI. Фирменный логотип переиспользуется.
- `app/src/main/res/drawable/ic_update_*.xml`: Google Material Symbols, Apache-2.0; исходные URL указаны в каждом файле. Цвет задаёт Compose. [Лицензия](../licenses/MaterialSymbols-Apache-2.0.txt).
