# Changelog

## v1.0.0 (build 10007)

- Added persistent in-app language selection for English, Russian, Turkish, Arabic and Persian; English is the default.
- Added right-to-left layout direction for Arabic and Persian without changing global device language.
- Localized the language settings, primary navigation, More workspaces, Control Hub tabs and the Project Studio interface using Android resource qualifiers.
- Added direct Language tab in the Control Hub. Existing chats, projects, stored keys and theme settings are preserved when selecting a new language.
- Added Android instrumentation tests for locale resources, directionality and preference persistence.
- Other legacy screens may retain English text until their strings are migrated. No automatic machine translation is claimed.
- Public release name remains V1.0.0; only internal Android build code increased.

### Previous build 10006

- Introduced a touch-friendly Project Studio with per-project app-private workspaces, starter Android/Compose templates, source browser, text editor and project briefs.
- Added user-initiated public GitHub/ZIP project imports, bounded ZIP validation, ZIP export and deliberate AI project planning through the existing provider/9Router.
- Added real Android instrumentation coverage for Studio project persistence, ZIP round trip, traversal and decompression limits.
- Moved News into the More workspace selector to make Studio available from the main navigation while preserving news access and existing Chat navigation.
- No cloud build worker is configured: project APK builds, unattended coding and publishing are **not** represented as working capabilities. Existing app GitHub Actions remain isolated from untrusted user workspaces.
- Kept public version V1.0.0 and existing signing identity.

### Previous build 10005

- Fixed client-side image upload blocking for authenticated 9Router model routes with unknown image support.
- Explicitly advertised text-only routes remain blocked, and unknown vision capability is clearly labeled.
- Updated the stable signed APKs in place without changing the public version.

### Previous build 10004

- Added an optional authorized HTTPS economic calendar and news feed connection.
- Added daily and weekly event views, manual refresh, and 15-minute in-screen refresh.
- Added 15-minute advance reminders for high-impact economic events with Android exact-alarm status.
- Kept verified official publisher news feeds and explicit source status.

Live calendar data requires the app owner to configure a licensed or otherwise authorized feed.
