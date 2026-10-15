# Five-locale AI Engine token and budget screen

This is a focused UI migration building on typed Smart Combo evidence and manual-provider cost consent.

- All visible labels on API Engine: account and provider identification, live versus estimated token usage, streaming estimate, provider rate headers, local budget fields, endpoints and plan labels are translated into English, Persian, Arabic, Turkish and Russian.
- API brand names, model IDs and user-entered strings stay untouched.
- The **estimated context remaining** metric is not presented as verified free API credits; provider account quota must come from actual provider evidence.
- Both explicit zero-price catalog eligibility and user confirmation for potentially charged provider selection remain enforced by the existing deterministic Smart Combo engine.
- Switching app language recreates the UI while retaining saved provider keys; no token values, secrets or chat histories are translated.
- Instrumented Android tests validate important pricing and budget labels across five locales and verify numeric format placeholders.
- Further localization of Chat inspector and unrelated screens remains in issue #36; speech recognition and on-phone APK building require physical device acceptance.

Public versionName stays V1.0.0. The release shall not be changed unless unit tests, lint, emulator suites, permanent signing and in-place upgrade pass.
