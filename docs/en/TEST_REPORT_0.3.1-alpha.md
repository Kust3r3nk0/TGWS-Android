# 0.3.1-alpha validation

16 October 2026 UTC. [Русский](../TEST_REPORT_0.3.1-alpha.md). Android 15+, versionCode7.
**Owner physical-device validation: pending.**

Material 3 Home/settings tabs, independent save, persistent light/dark/system theme and
menu toggle; automatic network/IP families even while stopped, excluding link-local-only
false positives. System DNS remains default; optional Cloudflare DoH has strict TLS,
A/AAAA, bounded responses/time and cancellation. [Behavior](DNS_AND_APPEARANCE.md).

Local DNS tests use real strict TLS/H2: both families, no system bootstrap/fallback,
NXDOMAIN/malformed/HTML/size/redirect/untrusted-certificate failures and stop/stale replies.
A hanging DNS waiter was fixed with owned Dns.Call cancellation and future completion.
An existing malformed-MTProto test now awaits finally-close with a finite deadline rather
than racing local EOF; protocol checks remain intact.

Complete local Wrapper: **BUILD SUCCESSFUL**, 181 JVM cases, zero failed/error/skipped,
Lint zero errors / three existing UseKtx warnings, debug and instrumentation APK assembled.
Actual CI JUnit results are pending. Existing176 cases remain;
five new JVM cases give181. Android source requires22 cases, adding navigation/persistence,
theme and real local DoH. CI captures light/dark Home and settings for visual verification.
API35/36/37 are not marked PASS until actual JUnit is checked.

Physical device, public Cloudflare over a carrier, cellular and new-version Telegram:
**NOT TESTED**. A local peer verifies client behavior, not carrier access to1.1.1.1.
No Android DNS/APN/SIM changes, IP-bypass guarantee or carrier DNS64 synthesis. The
experimental Worker retains16MiB/session. The retained debug key is not production signing.
