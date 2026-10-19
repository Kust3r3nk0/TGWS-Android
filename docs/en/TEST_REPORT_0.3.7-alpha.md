# 0.3.7-alpha verification

Reporting update based on the [owner 0.3.6 answers](OWNER_VALIDATION_0.3.6.md). Native network core unchanged; reported failures are not claimed fixed. No separate physical 0.3.7 validation is claimed.

## Verified build

- Android 16+, minSdk 36, versionName `0.3.7-alpha`, versionCode 13.
- Source: `6eaaa68a42a923ec62c78c5c6dae6815e52dc35c`.
- Checked PR tree: `8969074b712f7dee62b3e95780b5f8e60705deee`. Later report changes leave application/build inputs unchanged.
- [Android CI](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39193702691), [API 36/37 tests](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39193702410), and [publication guards](https://github.com/Kust3r3nk0/TGWS-Android/actions/runs/39193702405): successful.

## Automated results

| Check | Result |
|---|---|
| Full Wrapper command, debug and instrumentation APKs | Passed locally and in CI |
| JVM JUnit | 203, no failures/errors/skips |
| Android Lint | 0 errors, 8 existing UseKtx warnings |
| CI Python / publication guards | 64 / 5 tests passed |
| Node relay / Cloudflare Worker | 11 / 17 passed; Worker bundle checked |
| Android JUnit | 35 on each API 36 and 37, no failures/errors/skips |
| UI screenshots | 13 PNGs per API; visible `owner-validation-dark` report inspected on both |
| Upgrade 0.3.6 → 0.3.7 | Secret, ciphertext, Keystore and settings preserved on both APIs; install-replace and payload checks passed |
| Local WSS/TCP and HTTP/2 soak | 60 seconds each; automated resource limits passed |
| APK minimum OS / DNS | API 36 confirmed; no production Cloudflare DNS |

Verified all four Actions archive SHA-256 digests and actual JUnit entries, including `malformedLongSecretCannotBeTruncatedIntoValidSavedKey`. An obsolete run on an earlier source lost UI access in that test; nearby Binder overflow logs do not establish a cause. All assertions remain enabled and pass in the final matrix. This does not prove a network bug fixed.

There is no embedded WebView; renderer/page scenarios are inapplicable. CI provider inventory does not certify latest Stable/Beta: API 36 has `133.0.6943.137`, API 37 has `145.0.7632.218`, package `com.google.android.webview`. These are AOSP emulators, not the owner's Samsung/One UI device.

## Installable debug APK

`TGWS-Android-0.3.7-debug.apk`: 32,210,576 bytes.

SHA-256: `bc6fadfb50d0d224789a26ec45cbc9f72b14556d3d8ba6bdbff2ffc5c31ebdb2`.

Certificate SHA-256: `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`. Preserved debug key; APK payload matches CI byte for byte before signing. Verified using `apksigner`; not production signing.

## Open issues

The owner confirmed text, standard photos/voice, settings and appearance persistence in 0.3.6. An Obfuscated2 error during start/stop was reported (UI source and listener stop unconfirmed), alongside HD/video-circle failures in direct and relay modes, manual network recovery and long-session instability. Media direction and travel-related failure causes remain unknown. A 1–5 MiB file was not tested. These results do not automatically certify the new build.
