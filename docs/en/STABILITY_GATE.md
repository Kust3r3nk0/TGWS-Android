# Stable release gate

[Owner 0.3.6 report](OWNER_VALIDATION_0.3.6.md), Android 16 / One UI 8.5: **partial verification with failures**. CI does not override physical results.

Passed: text send/receive, standard photos/voice (directions unspecified), settings and appearance persistence. Failed: repeated start/stop, HD/video messages, automatic network recovery and sustained stability. Both Wi-Fi/cellular work with travelling failures. The 1–5 MiB file was not tested.

Basic One UI 8.5 scenarios are confirmed, not every OEM feature. No embedded WebView: renderer/page tests are not applicable. Current Stable/Beta channels were not identified and cannot be claimed certified.

Stable release requires targeted fixes, owner regression of failed scenarios, and trusted production signing. [Automated 0.3.7 report](TEST_REPORT_0.3.7-alpha.md) remains separate from phone evidence. 0.3.7 updates reporting, not network/media behavior. The Worker 16 MiB cap is not established as the cause of the reported media failure.
