# Maximus Matrix OS — Cloud Project Studio delivery plan

## Scope and delivery truth

The Android app Release is built on GitHub Actions using a pinned Android build toolchain and existing signing secrets. **User-imported projects are never run in this privileged CI workflow.** A separate cloud build worker, isolation, per-user auth and bounded billing are not provisioned by this release.

## Phase A — Implemented in Android V1.0.0 build 10006

- Mobile-first Project Studio destination with touch-friendly controls; preserve Chat and News in the existing More workspaces.
- Private persistent per-project workspaces; generate an Android Kotlin/Jetpack Compose starter structure and editable text files.
- Import ZIP from Android Storage Access Framework with traversal prevention, entry-count, per-file and aggregate size restrictions. Import *public* GitHub repositories using a fixed HTTPS GitHub API and codeload endpoint (no arbitrary URL or custom redirects).
- Export project ZIP explicitly via Storage Access Framework. Save a separate project brief and edit existing text source files.
- One-tap AI planning with the currently connected provider (including 9Router) sends only the project title, brief, and file paths after a deliberate tap; it does **not** grant file-system or build execution to the LLM.
- Clearly label the work pipeline as planning-only; no simulated build status, tests or signed APK claims.
- Android instrumentation tests for starter persistence, edits, ZIP round trip, zip-slip and decompression limits, in addition to the pre-existing emulator test suites.

## Phase B — Separate trusted cloud worker (not built)

- Independently deployed Linux build workers with Docker/VM isolation; do not place user code in the Release signing workflow.
- Authenticated session and scoped project capability tokens. Private, isolated storage, CPU, memory, timeout, rate and network restrictions, dependency cache policies.
- Genuine Android Gradle and Emulator jobs with source hash, step logs, signed/unsigned output provenance, artifact checksums and reproducible failure reports.
- Dry-run-by-default and explicit approval for pushing, deploying, signing, deleting, or publishing.
- OAuth-scoped private GitHub repository integration, resumable project actions and secure remote artifact transfer.

## Phase C — Intelligent agent and multimodal workflow (not built)

- Durable task graph, planner/reviewer loop, capability-verified 9Router Smart Combo routing and transparent provider-reported vs estimated tokens.
- File-aware diff review, checkpoints, automatic test diagnostics, bounded retries and user approval gate.
- Capability-dependent realtime voice, video, audio, PDFs and image generation; local and remote models with user-controlled cloud fallback.

## Release gates

Keep public version `1.0.0` and increment only internal version code; signed ARM64 and universal APKs must pass unit tests, lint, emulator smoke, key identity, in-place upgrade and release checks. No hidden account token, agent attribution or fake model telemetry in release notes.
