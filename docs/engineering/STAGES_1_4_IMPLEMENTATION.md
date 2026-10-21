# Stages 1–4 implementation and acceptance checkpoint

Baseline: main `b0cff9209906788358587c248b64817e531acd15` (docs-only continuity PR145 reconciled over tested4842681).
Branch: `feat/operational-stages-1-4`. Public1.0.0/code10029/package/signing and Room unchanged.
New UI remains English by owner instruction; existing translations/preferences remain intact.

| Stage | Existing source | Increment | Acceptance limits |
| --- | --- | --- | --- |
| Smart Combo | Admission, process ledger, broker, free-first planner and project continuity | Atomic no-backup request/quota journal, fail-closed restart recovery, hashed scopes, confirmed usage totals, validated checkpoint reference, cancel-safe preparation, explicit evidence archival retaining dedup IDs | Single-device journal; 1024 active rows/64 archive segments; no paid-model consent or server account-credit integration inferred; actual provider acceptance requires own credentials |
| Six Galaxy layouts | Canonical Room/module map, quaternion Brain and six layout engines | Shared safe draw/hit viewport, collision-aware area labels/isolation, relationship evidence, committed Room event marks/accessible history and time filtering, invalid-date handling | 100 committed events; no fabricated full workspace revision/Hindsight event stream; no physical FPS claim |
| Operations | Existing WorkManager routines, team run checkpoints, projects, Hindsight and phone runtime | Contextual Hub screen with Agents/Pulse/Memory/Runtime/Security, independent freshness/provenance, explicit local commands/audit and checkpoint validation | No generic autonomous execution DAG provisioned; remote health UNKNOWN unless separately inspected; project index currently enumerates local metadata |
| Developer connections | Scoped vault, GitHub optimistic writes/PR preview, Cloudflare staged approval/rollback | Injected pinned first-party transport, credential revision bound across review/write, immutable bounded source diff, durable uncertain-write barrier, GET-only exact-marker reconciliation with owner confirmation | No actual credentialed source write/deploy performed; no timeout retry; absent marker never clears uncertainty; bounded search may require external review |

## Verification recorded at this checkpoint
- Python CI-script regression suite:37 tests PASS locally.
- Release identity policy:7 tests PASS; publication safety:5 tests PASS.
- Locale resource gate, shell syntax and diff whitespace checks PASS locally.
- New Kotlin JVM tests and Android acceptance changes are SOURCE-IMPLEMENTED; complete integrated Gradle/CI execution is REQUIRED, not assumed.
- The interrupted local toolchain cache was ephemeral; no successful local full Android build is claimed. GitHub Actions must supply head-specific build, lint, unit and both emulator suites.
- Independent source reviewers checked journal/transport/operational boundaries; findings about unknown zeroes, chronology, cancellation and archival were corrected. Root completed final developer UI/tests after agent interruption; final-head review is still required.
- No authenticated user-provider, GitHub/Cloudflare end-to-end or physical ARM64 test is claimed. No merge/deploy/signing/publication.

## Android visual acceptance
Existing production Brain, Chat, Work/Pine and Agents shell retained. Tests exercise all six modes, graph gestures/search/stable selection, inspect scrolled Smart Combo telemetry, navigate all five Operations tabs through Hub and check dashboard reads do not mutate authoritative settings. New captures are emitted only from a passing combined emulator suite. Real screenshots must be inspected after CI; none are fabricated here.

## Resume
Read `AGENTS.md`, current PR checks and `WORK_RESUME.md`. Find this branch before creating work. Repair current-head compilation/test failures without weakening gates; capture original emulator images, review actual UI; obtain owner approval before merge/release. Refresh main because external work may have advanced it.
