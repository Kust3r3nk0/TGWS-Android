# Автоматические проверки для разработчиков

Используйте JDK 17, Android SDK и Gradle Wrapper проекта:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
python3 -m unittest discover -s .github/release -p 'test_*.py'
```

CI запускает инструментальные проверки API 36/37 и установку обновления без сброса данных. Протокольные тесты используют локальные peers и закреплённые векторы Flowseal v1.11.2; они не требуют Telegram-аккаунта. TLS и проверки секретов не отключаются.

Личные отчёты, сведения о телефонах и операторах в приложении/репозитории не публикуются. Обратная связь обсуждается в чате разработки. [Обязанности](TGWS-16-017_PHYSICAL_TESTING.md).
