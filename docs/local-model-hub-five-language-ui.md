# Local Model Hub — complete screen localization (V1.0.0)

## Scope

This is a user-visible text / five-locale pass on the *existing inventory-only* Local Model Hub screen, using Android string resources for `en`, `fa`, `ar`, `tr` and `ru`. All 14 newly added keys have matching placeholder signatures across locales.

- Localizes the inventory-only/offline warning, registered file count, import result, human-reviewable errors, size, GGUF/LiteRT-LM header disclaimers and forget-reference confirmation.
- Removes displaying persistent English-only model format probe notes (`LocalModelEntry.note`) in the UI, while retaining that metadata in private device storage.
- Preserves SAF scoped read permission and the user's original file; forgetting a reference must not delete a model.
- A GGUF file header is **not** proof that its weights load successfully. The `.litertlm` suffix is only a filename hint. Inference, vision, audio and Hugging Face downloads remain unsupported.
- Suppresses potentially sensitive raw ContentResolver/third-party exception strings in the user-facing status. Raw technical debugging should be opt-in and scrubbed separately.

## Verification and remaining work

- Android XML resource parity checker, Kotlin unit tests, release lint, debug build and both emulator suites must pass on the PR's exact head SHA. Signed release requires the existing permanent key and Android upgrade tests; public `versionName=1.0.0` must not change.
- Physical-device RTL and screen-reader checks needed on a real Android 15 device and lower API target. Translation coverage for the entire app is a broader issue (#36).
- Native llama.cpp / LiteRT-LM inference, model weights license review and verified downloads are separate issue #22; do not market this inventory as runnable offline AI.
