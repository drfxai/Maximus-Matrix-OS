# Maximus Matrix OS — Application Language Settings

## Supported languages

- English (default), Russian, Turkish, Arabic and Persian.
- Change the language via **More → Language** or **More → Control Hub → Language**.
- Language preference persists in app-private Android preferences. The current Activity is recreated on selection, without clearing conversations, API credentials, projects or theme preference.
- Arabic and Persian enable native right-to-left layout; English, Russian and Turkish remain left-to-right. The Android manifest permits RTL.

## Translation coverage

Android string resources provide translations for the language selector, primary navigation and workspace switcher, Control Hub tabs, and Project Studio's core interface and messages. Other legacy screens still contain hardcoded English technical copy; those strings remain English until migrated to Android resources. No runtime machine translation or unsupported claims of complete translation are made.

## Validation

Instrumentation verifies all five resource variants, RTL/LTR direction and saved-language persistence. Existing keyboard and credential/history tests remain mandatory. The public release remains V1.0.0; only Android versionCode increments for in-place upgrades.
