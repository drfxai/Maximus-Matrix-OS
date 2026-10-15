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
| Voice & Localization Engineer | Persian two-way voice, five-language UI, RTL and accessibility | #36, #35, PR #51 |
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

## Initial priority backlog

- #51 localization PR: rerun interrupted keyboard emulator check; inspect
  actual failure and never bypass the gate.
- #36: five-language app-wide UI, RTL, Persian microphone transcription,
  genuine bidirectional Persian voice on real hardware.
- #24: install pinned PRoot and complete real Android 15 ARM64 APK build.
- #22 and #19: run GGUF/LiteRT and authenticated local PC models; tie to
  verified Smart Combo and Hindsight without leaking provider keys.
- #38 and #40: durable agent teams on explicit user-controlled workers; GitHub
  controlled PR workflow, Cloudflare staged changes with approval.
- #39: screen analysis/camera capture only after per-session Android consent.

Reports must always distinguish PLANNED, IMPLEMENTED, CI-VERIFIED,
PHYSICAL-DEVICE-VERIFIED and RELEASED. The manifest and checks are
authorization boundaries, not an autonomous coding model or root permission.
