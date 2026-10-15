# Maintainer checklist

Android CI builds a debug APK and runs JVM tests and lint. Validate the app on real Android 15+ devices before claiming working connectivity or a stable release. Enable Issues, Dependabot alerts, and private vulnerability reporting. Consider branch protection after CI succeeds.

Recommended topics: android, kotlin, jetpack-compose, telegram, mtproto, websocket, open-source. Do not release signed packages or claim production readiness before proper verification.

The owner authorized debug GitHub Pre-releases on 15 October 2026. Publish only after mandatory CI and API 35–37 checks, clearly label debug signing, include APK/SHA256/build metadata/test results, and keep the stable release gate. Repository integration cannot access Actions secrets (HTTP 403); persistent CI signing requires owner configuration. Never commit signing keys.

Debug releases use the existing workspace key outside Git (`/workspace/private-signing/` in this environment). Compare certificate fingerprints before subsequent publication. If the key is unavailable, do not promise in-place updates. Never upload keys/backups as release assets. CI may use a different temporary certificate.
