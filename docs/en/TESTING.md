# Automated developer checks

Use JDK 17, Android SDK and the project Gradle Wrapper:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
python3 -m unittest discover -s .github/release -p 'test_*.py'
```

CI runs API 36/37 instrumentation and update installation without clearing data. Protocol tests use local peers and pinned Flowseal v1.11.2 vectors without a Telegram account. TLS and authentication checks stay enabled.

Personal reports, phone and carrier details are not published in the app/repository. Feedback stays in development chat. [Responsibilities](PHYSICAL_TESTING.md).
