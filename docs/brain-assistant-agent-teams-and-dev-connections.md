# MAXIMUS MATRIX OS — Brain assistant, approved Android actions, local agent teams and developer services

Engineering review: 2026-10-14. Public Android version **V1.0.0**. Candidate internal build **10018**. This branch is not a signed public release until all CI, upgrade and device checks pass.

## Observed defect and fixed routing

The Brain home screen previously sent every user question into `MaximusMatrixAgent.runMission()`. Its deterministic planner supports a narrow set of Android commands, so ordinary conversations invariably ended in `unsupported_objective`. A configured LLM was never asked to answer the question.

The Brain composer now routes **regular user text** into the same real streaming LLM chat/session used by the Chat tab, and displays a bounded preview of real user/model responses in Brain. The user can open the complete Chat thread with one tap. No model response is automatically fed into Android actions, and no model is claimed connected without an authenticated provider and valid capabilities. Free-only Smart Combo quota rules remain unchanged; absent zero-cost evidence still stops inference safely.

Only **explicit user imperatives** such as 'دوربین را باز کن', 'تلگرام رو باز کن', 'وای فای را روشن کن', 'search Kotlin', and 'open Google Chrome' go into the existing allowlisted Android mission policy, which requires a separate confirmation before an OS intent opens. Unsupported commands remain conversation prompts, not shell commands. Opening the camera leaves the shutter to the user. Android Wi-Fi settings/panel is user-controlled: the app never silently toggles protected radio settings. Chrome search uses a Google HTTPS query and falls back to an ordinary browser if Chrome is unavailable. Manifest package visibility is limited to approved Telegram and Chrome launch targets, without `QUERY_ALL_PACKAGES`.

The Brain speech recognizer uses the explicitly selected speech language (e.g. fa-IR), puts recognized words into an EDITABLE composer and never auto-executes dictated commands. Recognition quality and offline support depend on the user's installed Android recognizer. Chat's separate Talk mode supports TTS when an appropriate voice is installed. No always-on microphone or unconsented device capture.

The home composer, primary graph lobe tabs and quick actions have string resources for all five languages (English, Persian, Arabic, Turkish, Russian). Full localization is NOT complete: individual legacy graph node names, event descriptions, some menus and older application screens still contain English literals. This must be tracked and fixed before claiming app-wide coverage. Decorative '48 Hz bio-pulse' branding was removed as there is no measured bio-signal.

## Monitoring teams with real background execution

An opt-in panel in Personas / Agent Teams allows at most eight user-defined background teams. Supported initial jobs are (1) verified publisher news ingestion, and (2) Android device health. The user chooses 15 minutes, 3 hours or daily, can pause, resume, run immediately or delete. `WorkManager` runs each job when Android permits; each run performs an actual collection, a source/success check, and saves a bounded report. There are no synthetic LLM results, paid API calls, unapproved terminal commands or background privilege escalations.

**Not guaranteed 24/7**: Android Doze, OEM battery limits, lack of network and process restrictions can delay work. True always-on collaborative LLM team orchestration must use an authenticated off-device worker under user control, with durable jobs, per-agent scoped credentials, checkpoints, cost ceilings, role separation, audit logs, failure recovery and mandatory high-risk action approvals. This implementation is a real LIMITED monitor, not a continuously thinking autonomous LLM team.

## GitHub and Cloudflare connections

The user must separately enter their own narrowly scoped tokens. Android Keystore AES-256-GCM encrypts the tokens at rest; they are never embedded in the APK/repo, exposed to LLM prompts, provided to scheduled teams or sent to arbitrary hosts. The client only communicates to `https://api.github.com` and `https://api.cloudflare.com`; external redirects are disallowed.

**GitHub**: verify authentication, read a bounded source file, create a user-approved feature branch, edit text locally, and commit a specific file after a distinct confirmation with GitHub blob-SHA optimistic conflict checking. Direct writes to main/master/production/release and workflow configuration paths are disallowed. Requires GitHub repository-specific fine-grained token with Contents permissions; a real token/account is needed for live integration tests.

**Cloudflare**: verify the user's token and read the account's Worker script names. Deployment/configuration changes are intentionally NOT implemented: Workers bindings, secrets, environment and production routes are not to be overwritten by a blind upload. A secure Cloudflare deployment adapter needs staged previews, compatibility metadata, version snapshots and explicit deployment confirmations before activation.

The new app connector screens do **not** automatically inherit ChatGPT's connected GitHub/Cloudflare app permissions. The end user must explicitly connect them independently in Matrix OS.

## Required release gates and following engineering stages

- JVM pure-policy tests: normal chat vs explicit commands, protected branch/file paths, strict Cloudflare name validation, permitted scheduler intervals.
- Android JVM/Compose/lint tests, keyboard regression after Brain composer redesign, and emulator navigation/voice smoke where possible.
- Permanent signing certificate and same-package in-place upgrade without losing saved language, conversations, projects and tokens. Keep public V1.0.0.
- Physical-device tests (Android 15 ARM64): launch camera/Telegram/Chrome, Wi-Fi settings panel behavior, speech fa-IR, offline/Doze schedule drift, battery usage, and correct update/installation.
- Complete i18n extraction of legacy UI literals and dynamic node names; full five-language screenshots and accessibility checks.
- Explicit Screen Capture with MediaProjection consent per session and a visible foreground service only if later requested. AccessibilityService must be optional, declared and restricted to an approved purpose; it cannot be a covert root-equivalent.
- Hermes-class live 24/7 multi-model team engine on separate trusted always-on worker, opt-in GitHub branch/PR automation and controlled Cloudflare Workers version deployments, with proper token scopes, test gates, release safeguards and stored checkpoints.

No live external service, continuous autonomous team execution, device screenshot, arbitrary terminal execution, or production Cloudflare Worker deployment is claimed in this change.
