# Premium Minimal — native Android visual QA

final result: passed

Scope: app-owned Compose UI. Reference: owner attachment 1924.jpg (three-screen Premium Minimal), original branding attachment 1925.jpg. No generated screen captures, device frames or replacement logo. Runtime: real API 36/37 emulator UI, source 83e8703, CI 39374027242. Screenshots in docs/images/0.4.0 are unchanged copies of the verified API 36 artifact 12243305738.

Checked Home, Diagnostics, Settings and Update Center in dark/light themes. Graphite background #0B1016, surfaces #151D27, blue #2387FF, original tgws_symbol, rounded 16–24 dp cards/buttons and consistent Material Symbols are present. The light adaptation uses opaque text with appropriate contrast. Standard system bars are readable and content consumes native insets once. Existing Liquid Glass/transparency rendering tests pass.

Functional QA: 56 actual instrumented cases per API include tab navigation, retained input/scroll, Activity recreation, live listener continuity, update actions, permissions/installer, theme/effects and 160% font checks. Long content uses a native scroll container; scrolling to actions is verified. There are no nested page scrollers or screen-level fixed-height clipping. Native dialogs scroll separately.

Accessibility color token contrast (WCAG formula): dark body/surface 15.83, dark secondary/surface 9.04, dark primary/surface 4.84, dark button 4.85; light primary/white 5.89, secondary/white 7.22. Settings rows and button targets remain at least 48 dp. No text is replaced by an image.

Differences intentionally required by the task: the supplied original logo replaces the mockup logo; no invented country route, server, ping or Telegram availability. Empty/unknown states replace decorative sample data. Transport setup time is labelled distinctly from Telegram ping. The available-update screenshot is a documented test fixture, not an offered production release.

No P0/P1/P2 defects reproduced in the checked native screens after fixes. P3: content can extend below the first viewport on compact displays and large fonts; normal vertical scrolling keeps actions available. Pixel-percentage fidelity is not measured. Physical device/One UI behavior and signed production installation remain owner release-gate checks, outside emulator visual certification.
