# TGWS-Android 0.4.0 candidate verification

App source `83e8703532991da78b519e1efcf1ac1e168faa0b`, checked tree `595a5a0bb90a98eb39e56e1522c25d475b1e8e37`, checked merge `a1ea00125444292eee94e594e7a38dd673a93dba`. [Complete file list, evidence and screenshots (RU)](../VERIFICATION_0.4.0.md).

252 JVM tests; 56 instrumented tests per API 36 and 37; zero errors/failures/skips. Debug/Release lint/build, actual 60-second MTProto/H2 soak and 0.3.10→0.4.0 identical-signature install-r preserving ciphertext, Keystore, proxy/appearance/update settings passed. 79 Python guards + 6 publication tests passed. CI run IDs: 39374027261 and 39374027242.

Debug APK SHA-256 `592a7b878b593805d821dfd4449971d96d27e3c85407cf70bb78c1487a639884`; preserved debug certificate `26877620e16c5b2680023fc1fc6a285214b2e06dc998366731d9bcacd11c9fbe`; 35329844 bytes. [Download test build](https://github.com/Kust3r3nk0/TGWS-Android/releases/tag/v0.4.0-debug). APK payload matches successful CI exactly.

Native Premium Minimal screens reuse the original logo and all existing network/settings/update components. Sensitive drafts use an in-memory ViewModel; histories are bounded and free of addresses/secrets. Transport setup is not Telegram ping. Images are actual byte-for-byte emulator screenshots, not generated mockups; available-update fixtures are clearly test-only.

Stable remains gated by a separate trusted Release key, verified signed Production APK, physical acceptance including HD media and owner approval. No debug certificate is substituted for production. Different independent certificates require an explicitly tested migration/rotation process; no automatic uninstall or Keystore preservation claim. [Signing and testing instructions](RELEASE_0.4.0.md).
