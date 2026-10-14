# ElevenLabs Voice Studio — V1.0.0 integration

## Scope
- Optional, independently configured ElevenLabs **user-owned** account for natural speech output. It is NOT an LLM route and does not replace Smart Combo's free-first text inference.
- Android Control Hub > ElevenLabs Voice: enter key, verify subscription, load entitled voices, select a voice and an allowlisted synthesis model.
- Chat > any assistant response > Speak: if ElevenLabs has been explicitly selected, submit only the tapped response text (up to 2,500 characters) via HTTPS to api.elevenlabs.io. Otherwise speak with on-device Android TTS.
- Existing automatic spoken replies remain on Android TTS, never triggering implicit external/paid synthesis.
- An explicit Sample action synthesizes the short sample and may consume account credits.

## Supported models
- `eleven_multilingual_v2`: stable quality, multilingual; **not guaranteed to support Persian**.
- `eleven_flash_v2_5`: lower-latency model, multilingual; **not guaranteed to support Persian**.
- `eleven_v3`: multilingual including Persian; may differ in price and latency.
- This integration intentionally does not allow arbitrary model IDs, cloning, dubbing, or unconsented audio uploads. Model permissions are determined by the account and official API, not app assumptions.

## Credential and cost safety
- Android Keystore AES-GCM encrypts only the **user's own** API key. No shared/provider keys appear in source, release artifacts, or crash logs.
- Fixed HTTPS origin `https://api.elevenlabs.io`; HTTP redirects disabled; bounded 2.5K characters and 8MB MP3 replies; concurrent syntheses blocked within the Android app process.
- Before each generation the app calls `GET /v1/user/subscription`. It displays provider-reported `character_count` and `character_limit` separately from LLM tokens and applies a conservative 2x request-length allowance check.
- This check is NOT a guaranteed billing cap. ElevenLabs model prices/credit multipliers and use by other devices can change. To prevent overages, configure the account's hard spend/overage policy in ElevenLabs.
- No silent paid fallback and no automatic retry after a potentially charged POST, even if the download fails.
- Voice audio is held only in app-private temporary MP3 files deleted on playback completion, stop and screen disposal. Android backup is disabled by the application.
- The app cannot measure an account's usage perfectly live if another device changes the balance, so only provider snapshots are labeled verified.

## Verification
- JVM tests: voice ID/model/key allowlist and reported balance arithmetic.
- Android instrumentation: encrypted key cannot be found in plaintext preferences, persists across store recreation, and can be revoked.
- Release gates: Android unit/lint/emulator smoke, permanent signing certificate identity, and real in-place installation from the currently published V1.0.0.
- No paid ElevenLabs API requests are made by CI because no user key is provisioned to the test jobs.

## Known limitations
- TTS retrieves the complete bounded MP3 before playback; this is not yet low-latency chunked streaming conversation.
- Voice list is first-page API search (up to 50 returned voices), not all possible library/catalogue voices; the free plan's library access may be restricted.
- Free tier is not a guarantee of unlimited free speech; any quota or price claims must be checked against the user's own account and current provider terms.
- STT/Scribe, conversational websocket voice, and durable cross-device quota management are not enabled in this milestone.
