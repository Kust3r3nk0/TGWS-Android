# TGWS-16-017 — owner physical testing

P0. [Full workflow (RU)](../TGWS-16-017_PHYSICAL_TESTING.md).

Codex owns implementation, optimization, unit/integration/CI/Lint/builds, Debug APK artifacts,
version/commit/checksum/changelog handoff and fixes followed by automated regression checks.
The owner installs/tests personal phones: Telegram messages/downloads/latency/stability,
Wi-Fi/cellular, SNI/Fake TLS/Cloudflare, actual One UI, implemented Live Updates/Quick Settings
and WebView Stable/Beta environments. Unsupported features are not marked passed.

Do not seek/request ADB or access to the owner's smartphones. CI uses emulators.
After passing automated gates publish the authorized debug artifact/prerelease, provide the
Actions link, exact version/source/tree SHA and known limits, then await owner feedback.
Physical confirmation does not block debug publication; a stable release still needs the full
acceptance gate and trusted production signature. Passing CI/build is not physical validation.

Successful CI ZIPs include `OWNER_HANDOFF.md`, `build-info.json`, checksum and changelog.
PR source and checked merge/tree SHA can differ. CI debug signing can differ from the preserved
prerelease certificate; prefer the checked prerelease for updates and do not automatically delete
settings to bypass signature mismatch. Signing keys are never uploaded.

Reports can be filled on the phone using the GitHub issue form. Include exact app/build,
manufacturer/model/OS/One UI/Telegram/provider package/version, network/mode, reproduction and
observed behavior. Do not send secrets/keys/private relay URLs/messages/SIM/IMEI/serial/full configs.
Review anonymous diagnostic exports before sharing. No invented fixes for unclear failures:
keep them open with environment-specific evidence, implement a targeted fix and retest.

Statuses: not tested / passed / failed / unsupported / not applicable. Until the owner confirms:
**Owner physical-device validation pending.** Current native TGWS has no embedded WebView;
provider inventory is not a browser component/channel certification.
