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

## Снимки 0.3.3-alpha

[Проверенный device CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/38860603657), код `a8020a6984f5dcc1d7b1496cb1e05d03feeca2fe`; 25 тестов на каждой API 35/36/37. Файлы скопированы побайтово из артефактов, без ретуши и сжатия. Английская локализация, software GPU. Это демонстрация включённых эффектов (по умолчанию выключены), не подтверждение физического устройства или Telegram E2E.

| Файл | API / artifact | Оригинал | SHA-256 |
|---|---|---|---|
| [home-light.png](0.3.3/home-light.png) | 35 / `12028244444` | `transparency-light.png` | `a73d20be29da7ee1de63fc42e6e3d91c9ac4a8a1807de952be1094fa36b27ec2` |
| [home-dark.png](0.3.3/home-dark.png) | 36 / `12028914195` | `transparency-dark.png` | `25f19faa188adaa03f7ad0d38aa6fa4cd2b4a7cedfdb3d4d33620676003cc38a` |
| [settings-glass.png](0.3.3/settings-glass.png) | 37 / `12029291120` | `transparency-combined.png` | `863ae4e1dd6524c43db3059017027b23d6f51bfd1f01a780b50d79e5e5b8f653` |
