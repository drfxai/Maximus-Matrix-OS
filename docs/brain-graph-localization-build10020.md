# Brain Graph five-language localization — internal build 10020

Public version remains **V1.0.0**. This patch translates the interactive Brain graph's navigation and control surface in five languages: English, Persian, Arabic, Turkish and Russian.

## Implementation

- Add 43 shared resource keys with identical names and format placeholders in all five locales.
- Replace hardcoded action names for Signals, Vision, News, Alerts, Chat, Agent Hub, Data Center, Control Hub and AI Gateway with resource IDs; preserve existing tap callbacks and tool permission gates.
- Translate displayed labels for 16 graph groups while retaining the canonical English `LiveNode.group` values as internal filter/group identities. Search accepts translated group names *and* original identifiers, including Persian/Arabic text.
- Translate graph search headings, module counts, connection counts, mesh explanation, top/front/side anatomical camera views, rotation and filtering controls, clear-selection descriptions and live READY/PLANNING/EXECUTING/ATTENTION/CONFIRM states.
- Localize screen reader labels on key graph actions. App brand, untrusted model output, user content, provider names, raw technical IDs and code must not be modified.
- Internal `versionCode` is increased to **10020** for user-data-preserving signed upgrade. Public `versionName` stays **1.0.0**.

## Release gates

CI must validate string-key parity, `%1$d` placeholder parity, Kotlin compilation, JVM tests, Android lint, both instrumentation emulator suites, the same permanent release certificate, an in-place upgrade from the existing V1.0.0 build and published APK/source SHA-256 hashes.

## Still outstanding

- Descriptions of individual graph nodes and some specialized anatomical labels are still English. They are static product taxonomy, not user model content, and require a separate review.
- Other older UI modules, error details, some worker-generated reports and platform provider screens still have untranslated labels; **complete application localization is not yet claimed** (issue #36).
- Persian microphone transcription accuracy and voice availability depend on the device speech recognition/TTS engines. No offline ASR engine or guaranteed 24/7 cloud worker is added here.
- Real ARM64 device acceptance remains necessary in addition to green emulators.

This release introduces no additional Android privileges or secrets and must not bypass per-action permission prompts.
