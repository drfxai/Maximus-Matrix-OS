# MAXIMUS MATRIX OS — engineering team and supervisor contract

Updated 2026-10-15. Public Android version: **V1.0.0**. This document
describes role-based engineering accountability, not evidence that all named
coding workers are continuously running. Do not insert development-assistant
or model-vendor attribution into public repository metadata.

## Engineering workstreams

| Workstream | Scope | Existing issues |
|---|---|---|
| Engineering Supervisor | CI status, independent review, release safeguards, cross-team planning | #40, #38 |
| Principal Android Engineer | Brain Android actions, permissions and lifecycle, PRoot phone builds | #39, #24, #40 |
| Voice & Localization Engineer | Persian two-way voice, five-language UI, RTL and accessibility | #36, #35 |
| Smart Combo & Local Model Engineer | Free-first routing, GGUF/LiteRT engines, Hindsight and quota visibility | #22, #19 |
| Agent Orchestration Engineer | Durable jobs, explicit permission scopes, multi-agent teamwork | #38, #40 |
| Developer Infrastructure Engineer | GitHub branch/PR operations, Cloudflare staged preview and rollback | #38, #19, #20 |
| Independent QA & Security Reviewer | Regressions, credential isolation, signature continuity, device tests | #36, #24, #20 |

The machine-readable source of truth is ops/engineering/team.json.
Roles own issues and bounded paths; every implementation must use a separate
branch and reviewable PR. Keep at most four active feature branches at once.
No contributor or role can approve its own change.

## Technical capabilities and constraints

- Android: Kotlin, Jetpack Compose, Android framework Intents, lifecycle,
  WorkManager and CameraX/MediaProjection only with required permissions.
- AI: user-authenticated providers and evidence-based free pricing; do not
  claim account quota, latency, local model inference or Hindsight use without
  live evidence. Deterministic tool admission and cost ceilings.
- Engineering: Java/Gradle, Python, Go, ARM64 PRoot and Linux; no untrusted
  Gradle source under application UID and no production signing secrets in
  the Linux guest or APK.
- Cloud: fine-grained GitHub credentials and Cloudflare tokens, user-approved
  branch/diff/test/PR operations, preview-before-deploy and rollback.
- Quality: independent review, unit/instrumentation tests, five-language RTL
  screenshots, real ARM64 Android 15 smoke, original signing certificate and
  user-data-preserving version upgrade.

## Supervisor modes

**Repository supervisor**: GitHub Actions workflow
.github/workflows/engineering-supervisor.yml runs best-effort every hour.
It reads GitHub issue/PR/check-run/release metadata and emits JSON plus Markdown
artifacts. Its default token is READ-ONLY. A missing check is UNKNOWN, never
green. It does not approve, merge, deploy or publish anything.

**Local phone supervisor**: The Agent Teams panel classifies real WorkManager
reports as verified, stale, unavailable, waiting, paused or inconsistent-clock.
The current locally executable tasks remain only news checks and device health.
Android background scheduling is not guaranteed continuous 24/7 inference.

**True continuous team execution**: A separately authorized cloud/private
worker needs persistent queues, idempotent task leases, per-role scoped tools,
checkpointed shared project state, token/cost budgets, audit logs and pause/
revoke controls. This is outstanding under issues #38 and #40. Scheduled checks
and the team manifest are not substitute workers.

## Development state machine

1. QUEUED: explicit issue, scope and acceptance criteria.
2. IN_PROGRESS: bounded feature branch and source changes.
3. VERIFYING: JVM tests, Android lint, emulator, security gates. Missing or
   failed evidence blocks progress.
4. INDEPENDENT_REVIEW: exact PR head commit verified; no self-approval.
5. MERGE_ELIGIBLE: owner or permitted reviewer authorizes merge after checks.
6. RELEASE_CANDIDATE: original signer, monotonic internal code, same public
   V1.0.0, installation upgrade, SHA-256 and correct source notices.
7. RELEASED: published tag and binary assets independently verified. Physical
   device claims remain separate.

No auto-merge, silent Cloudflare deploy, security permission escalation,
automatic paid provider fallback, owner key substitution or secret logging.

## Current priorities — 2026-10-15 checkpoint

- [PR #75](https://github.com/drfxai/Maximus-Matrix-OS/pull/75) merged the explicit GitHub draft-PR workflow. [PR #76](https://github.com/drfxai/Maximus-Matrix-OS/pull/76) merged the conversation localization slice and split string-catalog validation. These are source/CI milestones, not a new APK release.
- [PR #77](https://github.com/drfxai/Maximus-Matrix-OS/pull/77) and [PR #78](https://github.com/drfxai/Maximus-Matrix-OS/pull/78) are pending at this checkpoint; use their current checks and independent reviews before any merge.
- #36 and #35: continue remaining graph and legacy-screen resource migration, five-language RTL/accessibility inspection, and physical Persian recognition/TTS conversation acceptance. Earlier interrupted keyboard checks are historical; current heads must pass their own gates.
- #38 and #40: finish staged Cloudflare preview, exact-target deployment approval, replacement/route implications and rollback. A successful script upload does not prove a live endpoint or working inference.
- #38 and #40: build on durable worker state toward user-controlled agent execution with reliable leases, recovery, scoped tools, shared checkpoints and observable pause/revoke controls. Continuous execution remains an acceptance requirement, not an assumed capability.
- #24: complete physical ARM64 Android 15 installation, launch and upgrade acceptance. Build/export evidence does not establish installation, runtime behavior, voice quality or same-signed upgrade success.
- #22 and #19: verify GGUF/LiteRT and authenticated PC-model execution with Smart Combo/Hindsight continuity and accurate quota evidence.
- #39: validate screen analysis/camera capture only after the required per-session Android consent.

Reports must always distinguish PLANNED, IMPLEMENTED, CI-VERIFIED,
PHYSICAL-DEVICE-VERIFIED and RELEASED. The manifest and checks are
authorization boundaries, not an autonomous coding model or root permission.
