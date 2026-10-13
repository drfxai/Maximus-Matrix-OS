# Changelog

## v1.0.0 (build 10009)

- Added assistant-message Copy, Download and persistent local reactions with animated generation state and accessible action buttons; added a floating, animated jump-to-latest control that appears only when older messages are being viewed, preserving scroll position during new messages.
- Converted raw model-generated tool proposals into clearly labeled not-executed cards, without falsely claiming that files or tools ran. Exported suggested file contents remain user-controlled.
- Expanded official publisher News feeds (Fed, ECB and BIS), added RSS and Atom parsing with verified publisher-host media, and image-aware cards with neutral fallback and explicit retry/error state; preserve verified-source-only policy and offline cache.
- Introduced sampled multi-format image import, normalization to bounded JPEG, and platform-decoder support for animated GIF first frames, HEIF/HEIC when supported, PNG/JPEG/WebP/BMP. SVG/TIFF still require a supported converter; large inputs have explicit limits.
- Added parser/security and emulator image-processing regression tests. No fictitious news, execution results or unsupported vision-model claims.
- Public version remains 1.0.0; internal versionCode 10009.

### Previous build 10008

- Introduced authenticated Remote Runtime discovery with HTTPS-only, redirect-rejecting Android client and endpoint-bound bearer tokens encrypted by Android Keystore.
- Added a Windows/Linux Python gateway that binds to loopback, requires a strong token and returns a versioned capability declaration.
- Replaced Project Studio's Cloud Execution placeholder with a Worker connection panel, encrypted save/forget controls and a discovery-only status.
- Added tests for unsafe endpoints, unauthorized access, command blocking and Android credential storage; remote builds remain disabled.
- Public version remains V1.0.0; internal Android versionCode advanced to 10008.

### Previous build 10007

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
