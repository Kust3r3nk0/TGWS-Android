# TGWS Update Center

Starting with candidate 0.4.0, Update Center uses shared graphite/blue Premium Minimal tokens. Repository/Worker/Installer and release-note categories remain intact. Stable is the default channel. [Release signing and migration](RELEASE_0.4.0.md).


[Русский](../UPDATE_CENTER.md). Native Compose / Material 3, Android 16+. Open **Settings → App updates**.

The center checks published official GitHub Releases, not commits. It shows actual release versions and notes. APK URLs come exclusively from the release assets; links in notes are never update sources.

Stable is the default channel. Tap the Update channel row in the information card to explicitly select Preview (Alpha / Debug). Both SemVer and versionCode must increase, the Android minimum must match the APK, and its signing identity must be compatible with the installed app.

Automatic checks use one unique WorkManager periodic task. Intervals: 15 minutes, 1/3/6/12 hours, daily or weekly; default 6 hours. Timing is approximate and subject to Android constraints. Disabling automatic checks cancels the task. Notifications are separate, permission/channel-aware and deduplicated per release. Manual checks remain available independently of the schedule, subject to a 60-second minimum and persisted HTTP 403/429 backoff.

Wi-Fi-only affects APK downloads, not release checks. Downloads bind DNS and sockets to one active Android Network. Unrelated Wi-Fi networks do not qualify; mixed Wi-Fi/cellular VPN transport is treated conservatively, without bypassing the VPN.

## Release contract

Assets must include the APK, `SHA256SUMS` and `update-manifest.json`:

```json
{
  "schema": 1,
  "package_name": "io.github.kust3r3nk0.tgwsandroid",
  "version_name": "0.3.10-alpha",
  "version_code": 16,
  "min_sdk": 36,
  "channel": "PREVIEW",
  "apk": "TGWS-Android-0.3.10-debug.apk"
}
```

Channels are `STABLE` or `PREVIEW`. Stable excludes prereleases and Debug APKs. Generate this manifest from Gradle using `.github/scripts/create_update_manifest.py`; the checked Debug publisher does so automatically and includes the file in SHA256SUMS. Legacy releases without metadata are not offered: versionCode is never guessed from a tag. Each check examines at most 100 published entries and 8 newer candidates.

Release notes use exact second-level headings: `## Important`, `## Features`, `## Fixes`, `## Performance`, `## Technical`, `## General`. Russian equivalents are documented in the [Russian guide](../UPDATE_CENTER.md). Empty categories are omitted. Unknown/unstructured content stays General; prose is never classified by keywords. Long text expands. Markdown is rendered as text, not executable links or a WebView.

## Installation and persistence

Update starts a cancellable download with byte progress. Limits and published SHA-256 are checked, then Android's `apksig` verifies the complete APK cryptographically. Package name, actual versionName/versionCode/minSdk and installed signer identity must match. Forward v3 rotation requires a verified lineage granting installed-data capability. A checksum alone never establishes trust.

Only then is the unknown-source permission requested when necessary. Return and tap Continue installation; Android asks for confirmation. No silent installation or uninstallation is performed. A narrow read-only FileProvider exposes verified files only. Cached bytes are reverified after process recreation and before installer handoff; process restoration does not automatically open the installer.

Update preferences are separate from proxy credentials, Keystore, routes, relay and appearance. The automated replacement-install regression checks their preservation as well as update options.

To check on a phone, install over the previous compatible APK, exercise both screens/themes, persist an interval across restart and check manually with automation off. When a newer signed release exists, exercise cancellation/retry, Wi-Fi-only, permission and installer confirmation, then verify preserved proxy configuration. Personal feedback stays in chat.

One UI behavior and WorkManager timing depend on firmware restrictions. API 36/37 emulator screenshots come from the actual native UI; the available-update scenario in Compose tests uses a deterministic fixture, not a claim of a public future release. There is no embedded WebView or WebKit dependency.
