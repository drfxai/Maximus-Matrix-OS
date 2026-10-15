# Smart Combo: five-language provider evidence and explicit cost consent

This is a UI/localization patch, not a pricing or routing-policy relaxation.

## Scope
- AI Engine / API Control displays structured Smart Combo status and provider check outcomes in English, Persian, Arabic, Turkish and Russian.
- Provider names and model identifiers remain exact machine identifiers; do not translate them.
- The app distinguishes configured accounts, verified zero-cost catalog offers, missing catalog price/context evidence, unavailable authenticated catalog, and missing chat capabilities.
- Chat's no-free-route recovery and manual-provider switching approval are translated; the user must tap the confirmation button to leave free-only mode. Switching never sends/retries an existing chat turn.
- Estimated *context capacity* remains distinguished from verified account quota/credit. Published zero catalog price is not proof of available capacity.
- Release versionName stays V1.0.0. No secrets, provider API tokens, generated model output or private usage data are added to resource files.

## Validations
- Run locale catalog parity checker, unit tests, Android lint, debug build, and instrumented locale tests.
- Switch language in Settings to fa, ar, tr, ru, en; open API Engine then Chat. Check selected RTL direction (fa/ar) and ensure reasons and approval dialogs are localized.
- With no saved provider key, verify the UI explains no configured accounts.
- With key and nonfree/unknown pricing catalog, verify **no automatically paid request** and display of missing price evidence.
- With verified zero cost but missing context, display the correct reason and do not route.
- With unavailable catalog or failed authentication, show unavailable without claiming that the key is invalid.
- With valid catalog zero-price offer, show the count and caveat that remaining quota is unknown.
- Select direct-provider mode and verify the explicit charge warning is displayed; pressing Cancel does not send requests or disable free-only. Confirming disables free-only without sending chat messages.
- Validate state persistence and app-language changes after Activity recreation.

## Remaining work
- Older Chat token inspector labels, API budget labels and many unrelated screens contain hardcoded English text.
- Full UI language parity (all five locales) remains issue #36; this stage must not be marketed as complete app-wide localization.
- Physical Android 15 Persian speech recognition and Mobile Harness phone-native APK builds need device testing.
