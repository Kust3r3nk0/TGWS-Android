# Cloudflare Worker 0.1.0-alpha

Отдельный экспериментальный серверный компонент для проверки маршрута через
Cloudflare. **Это не новый APK и не развёрнутый внешний узел.**

Скачайте `worker.js`, вставьте в редактор своего Cloudflare Worker и задайте
Secret `TGWS_RELAY_TOKEN`. Локальная установка Node.js не требуется.
[Полная инструкция](https://github.com/Kust3r3nk0/TGWS-Android/blob/main/cloudflare-worker/README.md).
APK TGWS-Android 0.2.0 уже совместим через поле «WSS-реле».

SNI/Host — настоящий домен вашего Worker, не `cloudflare.com`. TLS-проверка
сохраняется. Приложены исходный модуль, лицензия MIT, результаты 15 тестов,
commit/build metadata и SHA-256. Модуль собран и протестирован CI этого commit.

**16 MiB суммарно на сессию**, затем закрытие и возможное переподключение:
пока это проба достижимости/текста, не полноценный media relay. Подходит ли
этот путь в конкретной сети, зависит от доступности узла. Нужны аккаунт
владельца, deploy, реальный Telegram и мобильная сеть. Обход не гарантируется.

English: experimental server module, not an APK or deployed endpoint. Download
`worker.js`, deploy in your own Cloudflare account with the access-token secret.
16 MiB/session budget can interrupt downloads; cellular/Telegram E2E unverified.
