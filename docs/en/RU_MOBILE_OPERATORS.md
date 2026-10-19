# Russian carrier reference and validation

[Русский](../RU_MOBILE_OPERATORS.md). 0.3.0-alpha / Android 16+.
**Owner physical-device validation: pending.**

| Carrier | Primary PLMN | Reference APN | Reference login/password |
|---|---|---|---|
| MegaFon | 25002 (additional reference 25014) | internet | Use SIM/Android configuration; do not invent values |
| MTS | 25001 | internet.mts.ru | mts / mts |
| Beeline | 25099 | internet.beeline.ru | beeline / beeline |
| T2 | 25020 | internet | Empty |

These are human-readable references from the owner specification, not automatic configuration.
The app never changes APN/DNS/SIM/radio/tariff. Beeline's IPv4 example is not evidence of
universal IPv6 absence. Actual active data-SIM and registered network are distinct, optional
fields. Unknown, permission-denied, MVNO, MNP, roaming and extra PLMNs do not break routing.
A brand name containing “5G” does not establish NR. The transport core has no carrier-brand rules.

Source links were supplied in the 15 October 2026 owner specification; current settings for
a particular SIM were not independently confirmed. See the [complete source links](../RU_MOBILE_OPERATORS.md#источники).

## RU-01–14 matrix

Each physical result below is for 0.3.0-alpha. Model coverage is limited and does not emulate
an actual carrier. NetworkViewTest repeats network-change/loss/recovery handling for all
four reference cards; protocol/address-family tests exercise the carrier-independent core.

| ID | Scenario | Automated scope | MegaFon | MTS | Beeline | T2 |
|---|---|---|---|---|---|---|
| RU-01 | LTE | Local listener/transport; no LTE radio | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-02 | 2G/3G/LTE+/NR | Reported radio mapping/unknown | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-03 | IPv4 | Local IPv4 TCP/TLS/H2 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-04 | Dual-stack | Bounded IPv6 failure → IPv4 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-05 | IPv6/DNS64 | OS-provided IPv6 model; no carrier NAT64 deployment | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-06 | No compatible route | Bounded failure/diagnostics | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-07 | Wi-Fi/cellular handover | Four-card callback model and native-session reset soaks | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-08 | Loss/recovery | Late callbacks, cancellation, DNS/backoff cleanup | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-09 | DNS/TLS/Worker failure | Strict local TLS/relay-only and DNS failures | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-10 | Text/media/files | Local ef/ee/dd/H2/media bytes; no official Telegram | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-11 | 30–60 min screen off | Short local soaks/lifecycle only; long screen-off NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-12 | Dual-SIM/eSIM | Active data-SIM presence/wrong-SIM/unknown model | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-13 | Roaming/MVNO | Separate registered/SIM PLMN and unknown mappings | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| RU-14 | Transport modes | Routing/config and real local H2 tests | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |

Earlier owner reports of direct-path failure on MegaFon/Mega5G in Moscow region and stable
Wi-Fi/8443 on 0.2.0 do not establish cellular Worker reachability, DPI/CGNAT cause or new-version
results. N/A means a known unavailable condition, not a missing result.

The owner tests available SIMs; buying every carrier SIM is unnecessary. Report version,
commit/APK SHA, Android/model, carrier/optional region, active SIM (no IDs), actual access/family,
transport mode, RU scenario, expected/actual behavior, PASS/FAIL/NOT TESTED/N/A and duration.
Use the anonymous export preview. Never publish secrets, token URLs, SIM IDs or Telegram content.
