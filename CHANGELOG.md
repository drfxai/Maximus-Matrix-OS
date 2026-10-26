# Changelog

## V1.0.0 — internal build 10037

- Chat command center: a slim live header shows the active provider, model and the agent that is answering, an exact count of context tokens left, and a live output-token counter and speed while a reply streams. The conversation gets the freed space; voice, REA and workspace controls move into the Tools panel.
- New Models & providers switcher: animated provider cards with key status, a searchable model list with capability tags, context size and measured response time, filters (responsive, vision, reasoning, free) and persona chips. Switching to a provider with a saved key loads its models immediately.
- Smart key entry: pasting a key detects the provider, verifies the key by listing models and then tests which models answer (one tiny request each, can be turned off), showing the fastest working models first.
- Smart Combo failover: if the free model fails, hits a rate limit or quota, or does not start answering within 25 seconds before any text appears, the same message goes to the next verified free model automatically (up to three models) and Chat shows which model took over. The header shows the model that is ready next and its standby. A reply that breaks off mid-stream is kept and can be finished with Continue.
- Public version 1.0.0, package identity and signing certificate are preserved; the Android build number increases for an in-place update.

## V1.0.0 — internal build 10035

- Pine Studio local engine: `ta.pvt`, `ta.nvi`, `ta.pvi`, `ta.wad`, `ta.iii` and `ta.wvad` now run in the chart preview instead of being rejected, with a unit test against the Pine reference definitions.
- Public version 1.0.0, package identity and signing certificate are preserved; the Android build number increases for an in-place update.

## V1.0.0 — internal build 10034

- Large projects: Pine drafts, imports and the local engine accept sources up to 1,000,000 characters (about 20k lines); Work edits text files up to 2 MB.
- New full-screen code editor for Pine Studio and Work with find/replace, go-to-line, a live cursor position and a local syntax check; code views in Chat, Pine Studio and Work are virtualized so 10k+ line sources scroll smoothly.
- Work reviews now use a real line diff, so an edit deep in a large file shows exactly where it happened.
- AI revisions of large Pine drafts send an outline plus the relevant excerpts and ask for targeted section changes; a partial reply can no longer overwrite a whole draft.
- Smart Combo reserves up to 16k output tokens for code (falling back to smaller budgets when routes are tight), and Claude models get 8k output for code.
- Chat uses edge-to-edge width with more space between turns, and long pastes collapse into previews.
- Fixes: the Brain no longer recomposes its whole screen on every orbit frame, and Pine parsing and draft lookups moved off the main thread.
- Public version 1.0.0, package identity and signing certificate are preserved; the Android build number increases for an in-place update.

## V1.0.0 — internal build 10033

- Chat → Pine Studio: Pine code in a reply can be opened in Pine Studio, previewed on a live chart or used to replace a draft. A new local Pine engine runs real indicators and simplified strategies on the chart, with an AI review action.
- Smarter API setup: pasted keys are recognised by provider and switch the endpoint, and an opt-in test lists only the models the key can actually use.
- Smart Combo: requests are classified (code, reasoning, long document, quick) to choose among verified free routes, model capabilities and quality estimates come from the catalog, and the history window no longer shrinks to the smallest route.
- Larger chat area with smaller margins, a searchable Hub launcher for every workspace, a Control Center status line, clearer Neural Galaxy labels and a timeline legend.
- Public version 1.0.0, package identity and signing certificate are preserved; the Android build number increases for an in-place update.

## V1.0.0 — internal build 10032

- Refreshed the public project page with product banners and verified Android screenshots.
- Removed obsolete internal coordination files and legacy personal branding from visible screens.
- Preserved the V1.0.0 application ID and signing identity; increased the Android build number for an in-place update.

## V1.0.0 — internal build 10031

- Updated Brain input handling and graph labels, Chat search, Pine completed-candle previews and Smart Combo capability filters.
- Added preserved-source Chat→Pine handoff, account-bound model catalog and dated per-model tests, compact Chat and reachable Hub/Control.
- Public version 1.0.0, package identity and signing certificate are preserved. Signed build 10030→10031 in-place upgrade and emulator checks passed before the release assets were replaced.
- Limited chart previews remain distinct from full Pine compilation and backtesting.

## v1.0.0 (build 10027)

- Rebuilt the mobile Chat/Work/Hub navigation with compact chat controls, an inline composer and searchable conversation, project, model and persona sheets.
- Added capability filters and explicit model selection, syntax-colored fenced code with independent copy/download, small reaction icons and a native animated navy/gold companion with an off switch.
- Added staged local file review with a bounded change preview and a check against intervening file edits before saving.
- Work now shows actual source previews, compact service entry points and a focused composer; memory and continuity controls remain in Advanced.
- Added a segmented context meter and explicit unknown provider credit; no sample quotas or connected states are fabricated.
- Camera thumbnails, photos, documents and plugin navigation share the attachment menu. Generic web search and image generation remain unavailable and are labelled accordingly.
- Public version and permanent signing identity remain unchanged. Physical Linux setup/build acceptance is still outstanding.

## v1.0.0 (build 10026)

- Fixed installation of the pinned ARM64 Ubuntu runtime: valid multiarch metadata such as `libc6:arm64.list` is accepted, while absolute archive members, traversal, NUL, backslash and Windows drive paths remain rejected. Existing verified downloads can be reused with Resume.
- Moved the unchanged two-pass extractor into a host-testable production component and tested complete checksum-verified Linux and Android SDK/Gradle bundles, not only synthetic paths.
- Added a required CI regression using both pinned upstream archives, including expected Bash, Gradle, AAPT2 and local Maven paths.
- Runtime staging and previous-install cleanup no longer follow guest symlinks into host directories. Cleanup failure cannot obscure the original setup error or leave progress busy.
- Physical ARM64 tool execution, APK build/export/install, Persian speech and live account/provider acceptance remain separate device/account checks; host extraction does not certify them.

## v1.0.0 (build 10025)

- Added the AMOLED workspace palette: pure-black canvas, balanced navy cards, white text, gold confirmations, orange activity and red warnings.
- Redesigned Chat, Agents and Studio presentation with compact panels, consistent branding, focused project shortcuts and retained Android approvals.
- Added an accessible animated context gauge, provider selection grid, observed token transitions and low-budget indicators. Context capacity, local budgets and dated provider rate snapshots remain separate.
- Added message arrival, staggered generation, reaction spring/haptic, copy confirmation and Studio press feedback.
- Extended real Android visual evidence to Brain, Chat, Agents, Studio and Smart Combo; required fresh captures after screen transitions.
- Preserved public version 1.0.0 and the permanent signing identity.

## v1.0.0 (build 10012)

- Added Hindsight Shared Project Memory (opt-in) to Project Studio, with a separate HTTPS endpoint and independently encrypted user-owned API key. Each project uses a stable bank across inference models.
- Implemented authenticated Hindsight Retain/Recall for verified user-saved project checkpoints and optional model-progress excerpts; no shared gateway credentials or project source files in the APK.
- Prefetch memory in the background when selecting projects or models, attach only bounded explicitly untrusted context at inference time, and preserve local workspace-hashed checkpoints plus short AES-GCM encrypted progress notes as the offline fallback.
- New controls: save connection, enable/disable remote memory, separate consent for automatic progress excerpts, recall and sync checkpoint. Disconnecting removes the remote key while preserving encrypted local model progress; cloud memories are not falsely described as deleted.
- Cross-device shared bank permissions, Hindsight service deployment, automatic code execution and agent-driven tool approvals are NOT provided; Hindsight requires a separately configured server/cloud account and may consume LLM credits.
- Android versionName stays 1.0.0; versionCode increases to 10012 for in-place updates.

## v1.0.0 (build 10011)

- Added ElevenLabs Voice Studio in Control Hub: enter a user-owned API key encrypted with Android Keystore, verify subscription, inspect reported allowance, load and select account voices, and select multilingual or low-latency speech models.
- Assistant-message Speak actions can explicitly use ElevenLabs text-to-speech with temporary MP3 playback and cleanup. Auto-spoken responses remain local Android TTS to prevent unapproved remote voice charges.
- Before each speech request, recheck ElevenLabs account limits and prevent requests that exceed reported remaining allowance. No shared provider credential is stored in the APK; synthesis needs explicit user interaction.
- No ElevenLabs voice cloning, arbitrary-model billing, paid auto fallback or streaming conversation claim. Character/credit usage is provider reported, separate from model tokens. API permissions and model-specific billing may differ.
- Added tests for safe IDs/model allow lists, credential encryption/revocation, API input limits and quota states. Existing features remain unchanged.
- Public versionName remains 1.0.0. Internal versionCode increases to 10011 for signed in-place updates.

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
