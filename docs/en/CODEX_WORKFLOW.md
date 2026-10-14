# Codex + GitHub Actions: TGWS-Android development from a specification

This guide describes a **single Codex development task** followed by GitHub Actions verification and, if successful, a downloadable debug APK.

> Codex implements the specification and runs available tools; GitHub Actions **only** tests and builds the changes. Creating an issue or adding a specification to this repository does not launch Codex by itself. A running agent with repository access is required.

## 1. Prepare

1. Open `Kust3r3nk0/TGWS-Android` in Codex Cloud or in a local Codex CLI/IDE environment.
2. Grant the agent appropriate repository permissions. Local builds require JDK 17, the Android SDK and the included Gradle Wrapper.
3. For routine implementation, start with **GPT-6.1 Sol, medium reasoning, normal speed** if these options are available. Increase reasoning for complex transport issues after reviewing results.
4. Work on a separate branch. [AGENTS.md](../../AGENTS.md) defines persistent project instructions (in Russian).

## 2. Prompt to send with a specification

```text
Implement the attached TGWS-Android specification in one maximally autonomous pass.
Follow AGENTS.md and the existing architecture.
After a brief inspection of relevant files, begin coding; do not repeatedly scan the whole repository.
Continue through authorized steps without unnecessary confirmation pauses.
Add relevant tests, fix discovered issues, and run:
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
Keep the work on a separate branch and open a PR if possible.
Do not create a release, merge to main, or claim device testing unless it actually happened.
Report implemented requirements, actual verification results, APK/CI location, and remaining issues.
[Paste specification below]
```

A five-hour Codex quota window **does not guarantee five hours of uninterrupted execution**. Usage limits, environment failures and required approvals can interrupt the task. These instructions improve repeatability, not completion guarantees.

## 3. CI and APK artifacts

The existing [Android CI](../../.github/workflows/android-ci.yml) runs for pull requests targeting `main`, pushes to `main`, and manual dispatch. On success it runs JVM tests, Android Lint, builds `:app:assembleDebug`, and uploads the **`TGWS-Android-debug-apk`** artifact.

To download:

1. Open a PR containing the Codex changes and wait for Android CI.
2. Open the workflow's [Actions page](https://github.com/Kust3r3nk0/TGWS-Android/actions/workflows/android-ci.yml).
3. Select the correct run and check its conclusion.
4. Under **Artifacts**, download **TGWS-Android-debug-apk** and extract its ZIP.
5. The archive contains the debug APK if the build and upload succeeded.

A debug APK is **not a production-signed release**, and passing CI does **not** prove real Telegram connectivity. Run separate integration and on-device tests.

## 4. Failures and limits

- For test, lint or compilation failures, provide the failed workflow run/logs to Codex and fix the root cause on the same branch.
- Distinguish broken build environments from application defects; do not invent passing test results.
- If quota runs out, resume from saved commits and outstanding requirements when access becomes available.
- Fully unattended initiation requires a separately configured agent trigger and permissions; this workflow does not run a language model.

[Русская инструкция](../CODEX_WORKFLOW.md).
