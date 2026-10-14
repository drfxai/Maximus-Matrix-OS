# V1.0.0 / Build 10019 — Persian Speech and Voice Acceptance

Status: Code and automated release verification are distinct from real device language support.

## New behavior

- Chat Dictate requests an explicit Android speech-recognition locale based on the chosen voice language. By default, voice language follows the interface; an explicit override remains user-controlled.
- Android recognizers can ignore requested locales. For Persian (fa-IR), Arabic (ar-SA), or Russian (ru-RU), a result in an incompatible writing system is kept in the editable input but is **never auto-sent** in Talk mode. If several alternatives exist, a compatible script candidate is preferred. Script compatibility does not prove transcription accuracy.
- Brain uses the same safety gate for dictated text and can optionally speak newly completed non-error assistant replies using Android TextToSpeech. It does not speak old messages merely because the user enables playback.
- A Persian speech request or an opt-in spoken Brain reply passes the effective selected locale into the model's voice-response system instruction.
- Missing Android Persian TTS voice shows an installation prompt instead of falling back silently to English.
- ElevenLabs Voice Studio UI is translated into en/fa/ar/tr/ru and an explicitly requested sample uses the user's voice language, separately from app UI language. ElevenLabs requests may spend account allowance; there is never an automatic paid switch.

## Privacy and user consent

- The microphone is only activated through a user-visible Android speech recognizer or explicit voice recording control.
- TTS playback is opt-in on the Brain home screen. It stops when the app goes into the background.
- No new Android permissions or hidden screen capture, background microphone, accessibility automation, provider credentials or cloud recording features are added.
- For security, Android action requests still require explicit user confirmation; speech recognition never directly bypasses that gate.

## Real physical device verification required

1. Install Build 10019 over Build 10018 without clearing user data; verify same app ID and signer.
2. Set **Settings → App language → فارسی**, then in Chat **Speech → Follow app language**. Inspect visible text and RTL alignment; some legacy panels remain untranslated and are tracked in issue #36.
3. Ensure the device actually has a recognizer supporting Persian. Dictate «سلام، امروز هوا چطوره؟» using the Chat microphone and verify the editable transcript is genuine Persian. Repeat with Chat Talk enabled: verify only compatible-script results can auto-send.
4. On a recognizer that ignores fa-IR and returns Latin transliteration, verify the transcript stays editable with a clear Persian warning and **no automatic LLM request**.
5. In Brain, dictate a Persian question, check text, and press Send. Enable the speaker icon and verify new replies are voiced in fa-IR if an Android Persian TTS voice is installed. Pause/leave the screen and verify playback stops. Missing voice must display the install-language prompt.
6. In Chat, set a manual English speech override, switch the app language, then verify the override persists until reset to Follow App Language.
7. Switch among fa, ar, tr, ru and en; verify locale-specific warning text, correct speech picker state, and that existing chats/keys/checkpoints survive recreation.
8. If using ElevenLabs, verify a sample speaks in the selected speech language and no synthesis occurs until explicitly approved. Review actual provider credit changes.
9. Test on Samsung ARM64 Android 15 and at least one second Android version/recognizer provider. Emulators do not establish microphone or TTS voice availability.

## Still outstanding

- Guarantee of working Persian ASR when no installed recognizer supports it (requires a separately vetted optional on-device STT engine or explicitly authorized cloud provider).
- Complete app-wide five-language resource migration across legacy screens. Untranslated UI literal inventory is tracked in issue #36.
- Fully unattended hands-free live audio conversation and 24/7 autonomous background agents; neither is enabled by this release.
- Physical-device on-phone Gradle PRoot execution: separate issue #24.

## Release rules

- Public versionName remains V1.0.0; internal versionCode 10019 is required for a same-certificate in-place update.
- Stable release assets must not be replaced unless JVM tests, all locale resource checks, Android lint, both emulator suites and signed upgrade identity pass with the permanently provisioned signing key.
