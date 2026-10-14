# Persian voice and five-language app QA — V1.0.0

## Speech recognition
- The on-screen speech-to-text microphone uses Android `RecognizerIntent` and now explicitly passes the user-selected language `fa-IR` (or one of English, Arabic, Turkish and Russian) instead of `Locale.getDefault()`. Voice choice follows the **app language** by default and can be overridden independently in Chat.
- The speech-to-text result is shown in the composer for correction by default; optional **Talk** mode auto-sends recognized text and speaks the model reply. The user must intentionally tap the microphone for each turn. There is **no always-listening background microphone**.
- Android must provide a recognizer that actually supports `fa-IR`. This app cannot install or certify a third-party recognizer. If no recognizer exists, the app must display a localized availability message and preserve draft text.
- Speech output uses the `fa-IR` on-device TTS voice when selected. A phrase composed only of Arabic-shared characters (e.g. `خوشحالم`) must not be pronounced by the English voice. Explicit Arabic selection still takes precedence.
- If the installed Android text-to-speech engine does not have a Persian voice, the app shows an **Install speech language** option that invokes Android's TTS data installation. Actual language availability and voice quality require **physical-device** validation.
- The separate **Record voice note** action attaches raw audio; provider models that do not support raw audio cannot be claimed to transcribe it. Use the speech-to-text microphone for provider-independent dictation.

## Localization
- App language choices: en, fa, ar, tr, ru. The MainActivity locale and RTL direction follow the selected language, and switching language recreates the Activity. Audio input defaults to the same language.
- Android has a complete five-locale resource-key inventory (validated by `.github/scripts/check-ui-locales.py`). Core Chat, Mobile Harness, API usage, diagnostics, Data Center, Studio, Hindsight, Local Model Hub, Voice and Vision labels now use translated resources.
- **Known incomplete item:** not every legacy hardcoded label, error message, diagnostic event, accessibility description, or dynamic API description uses a resource yet. The audit identifies these. Do NOT claim full app-wide linguistic coverage until the full hardcoded-label migration and five-locale screenshot/accessibility audit are finished.
- Do not translate user-authored messages, code, AI responses or provider secrets just to change the UI locale. Their content is independent from the app language.

## On-phone Android SDK pinned archive

Authoritative Mobile Harness release asset SHA-256 from `runtime-2026.09.4`:
- Linux core, size 72,185,773: `df0cf7251c74f82d424231e3804114a4ca66b16130eea9abab11e220dc7ac012`
- Android SDK/Gradle, size 569,652,007: `01bea058ebcb17416d1eb08c0211b3782da3228eb3c8348eb3719f2d61dd3ec6`

The previous Android SDK constant omitted several hex characters and rejected intact downloads. The code now stores a single authoritative pin manifest, with JVM regression assertions for exact size/hash. The installer continues to reject all files failing the complete 64-hex SHA-256. The first ~68 MiB (Linux core) already downloaded on a device may be reusable if its hash matches. No provider/shared secrets are placed in the APK.

## Device acceptance checklist
1. Install the same-signed internal 10017 upgrade over 10016 without clearing the app or speech preferences.
2. Ensure 3 GiB free space and a stable network. Tap Mobile Harness > Resume. First archive hash should pass, Android SDK archive should download and verify, then private filesystem extraction can begin. If any step fails, keep the exact diagnostic text; do not disable integrity controls.
3. Tap Verify tools; run a trusted sample Gradle Debug APK build and validate exported APK SHA-256. This remains a physical ARM64 test requirement.
4. Select Persian under language settings, confirm app RTL; confirm voice picker shows Persian, dictate `سلام، حالت چطوره؟` into editable Persian script, manually send, toggle Talk and dictate another prompt, and verify text response plus Persian spoken reply if TTS data is installed.
5. Check English/Turkish/Russian/Arabic language switching in the same session. Confirm model names, code snippets, user chat messages and secret fields are never machine-translated.
6. Confirm app captions and Talk controls expose semantic labels to accessibility services.

Keep public `versionName=1.0.0`. Release update requires CI, permanent signature, emulator tests and in-place upgrade success.
