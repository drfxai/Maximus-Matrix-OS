# Changelog

## v1.0.0 (build 10010)

- Added a floating animated jump-to-latest message control, visible when the reader scrolls away from newest chat messages; automatic following pauses during history browsing.
- Added Copy and user-initiated Download for assistant text and suggested files, private local reactions, and a generating pulse. Raw model tool markup is labeled as an **unexecuted proposal**, never falsely shown as a successful operation.
- Enhanced verified publisher News with an additional BIS RSS source, Atom parsing, publisher-supplied HTTPS images with safe origin/size rules, category illustrations when no photo exists, and retry when no verified articles are available.
- Added size-bounded image sampling and normalization for Vision uploads (JPEG/PNG/WebP/GIF first frame/BMP and HEIC/HEIF on compatible Android decoders). Unavailable TIFF/SVG decoders and provider vision support are **not** misrepresented.
- Added regression coverage for tool drafts, feed parsing, authentic media and Android image processing. Preserve all build 10009 Smart Combo and checkpoint work.
- Public versionName remains 1.0.0; versionCode increments to 10010.

## v1.0.0 (build 10009)

- Added an opt-in Smart Combo free-text routing mode. Authenticated model discovery must explicitly report zero prompt/completion fees and a published context window before a route can be selected.
- Linked deterministic Smart Combo selection and local quota admission to live chat inference. Only one request is dispatched; ambiguous post-dispatch failures are never automatically replayed.
- Model discovery and capability gates remain conservative; model and account quota limits are shown as unknown unless the upstream provider reports them. Documented OpenAI/Groq rate headers can supply dated RPM/RPD/TPM evidence. Successful streams without final token totals remain estimated, never relabeled as exact. Paid/unknown-price routes are excluded from automatic mode.
- Added Project Studio's app-private content-hashed checkpoints and user-authorized resume planning. Source contents are not silently uploaded, and checkpoints do not grant execution permissions.
- Automated cloud execution, true account-balance telemetry, and cross-device transactional quotas are not yet configured; Remote Runtime remains discovery-only.
- Public version remains V1.0.0; versionCode advances to 10009 to support in-place upgrades.

## v1.0.0 (build 10008)

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
