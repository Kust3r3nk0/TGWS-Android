# TGWS-16-016 — WebView compatibility

P1, Android 16+ AOSP / One UI 8.5/9.0. App minimum is Android 16 (API 36); only API 36 and newer are supported.
[Full specification and matrix (RU)](../TGWS-16-016_WEBVIEW.md).

Current 0.3.4-alpha uses **no embedded WebView**: Compose/Material 3 UI, external Telegram
Intent, native Kotlin/socket/TLS/OkHttp transports. Do not add AndroidX WebKit or create a
hidden engine merely to satisfy a version checkbox. Renderer/page recovery and feature
checks are not applicable; this is not certification of latest Stable/Beta or One UI.

CI guards production declarations and declared dependencies (`verify_native_ui.py`), not
arbitrary reflection/binaries. A new WebView/JS bridge requires explicit contract review.
If a real embedded UI is introduced, use the specification's stable `androidx.webkit:webkit:1.17.1`
only after checking availability/stability/compatibility; no prerelease AndroidX by default.
Detect provider/version with `WebView.getCurrentWebViewPackage()`, handle absence, guard
features using `WebViewFeature.isFeatureSupported()` or startup-feature checks, and handle
renderer death with safe instance recreation/state recovery. Keep JS off unless required,
restrict navigation/local file access, retain TLS/Safe Browsing and never expose proxy secrets.
Measure RAM/CPU/startup/battery; MTProto payload and sockets remain native, outside WebView/JS.

Required owner matrix: Android 16 Stable/Beta; One UI 8.5 Stable/Beta when available;
One UI 9.0 Stable/Beta when a test environment exists. Dev is optional, Canary experimental.
This matrix applies when an embedded WebView component is introduced. Current renderer/page scenarios are not applicable.

Before/after emulator runs CI saves allowlisted environment JSON, including selected provider
metadata. AOSP/unknown/unavailable differ; package naming does not prove latest channel version.
No raw dumps, serial/fingerprint/SIM/IP/config are retained. Collector only addresses a CI
emulator; it does not instantiate/switch WebView, certify channels, or inspect personal phones.

## Provider diagnostics in 0.4.1

A native system-provider card and change journal use only `android.webkit.WebView.getCurrentWebViewPackage()`. [Android documents](https://developer.android.com/reference/android/webkit/WebView#getCurrentWebViewPackage()) that this call does not load WebView. No embedded UI, WebKit dependency, renderer or page is created. Future provider versions are read as metadata without a hardcoded version threshold.

Refresh runs on foreground entry, selected package changes and explicit action. Missing provider and query failure are distinct. Known package names identify Stable/Beta/Dev/Canary, never certify latest versions; AOSP/OEM stays system/unknown. Other applications’ logs are inaccessible.

The native guard permits only the exact reviewed metadata statement in its adapter and display string values. Engine creation, imports, other calls, bridges and WebKit dependencies remain blocked by regression tests.
