# MAXIMUS MATRIX OS — ChatGPT Project / Work resume checkpoint

**Purpose:** Carry project state across chats, AI model switches, Work usage interruptions and authorized coding sessions. This is a **handoff contract**; it is not a running AI process, does not automatically detect a ChatGPT quota reset, and cannot independently open a Work session.

## Last observed repository facts (historical — refresh on EVERY resume)

Observation window: **2026-10-20 UTC** (GitHub read during 2026-10-21 setup). These values become stale as new changes land.

| Evidence | Last observed value |
| --- | --- |
| Repo / default branch | `drfxai/Maximus-Matrix-OS` / `main` |
| `main` head | `4842681383375bdeb9540a95c46d2c929bd73296` (merged PR #144) |
| Open PRs | 0, at observation time |
| Latest checked Android Actions run | [39490108055](https://github.com/drfxai/Maximus-Matrix-OS/actions/runs/39490108055) / SUCCESS / main SHA above |
| Gradle version on observed `main` | `versionName=1.0.0`, `versionCode=10029` |
| Release | [v1.0.0](https://github.com/drfxai/Maximus-Matrix-OS/releases/tag/v1.0.0); binary signing/install compatibility not verified by this checkpoint |
| Engineering supervisor | [read-only scheduled audit](../../.github/workflows/engineering-supervisor.yml), NOT a coding worker |
| Open work issues (observed) | #89, #40, #39, #38, #36, #35, #24, #22, #20, #19 |
| Work API quota / reset time | UNKNOWN — no authenticated account quota check or quota-reset event is available to this agent |

Green CI is **not** physical device acceptance or evidence that every feature works end-to-end. A PR is complete only with head-specific checks and any required user review.

## Next prioritized tasks

1. **Smart Combo**: audit durable quota accounting, actual dispatch display, pre-fallback decisions, provider-confirmed evidence versus estimates, Hindsight continuity; test and implement the highest-impact missing slice.
2. **Neural Galaxy + operational dashboard**: compare six current graph layouts/Brain/chat and the real UI acceptance screenshots; fix the highest-risk missing behavior or performance defect.
3. **Agent Factory/Teams**: real queue and task-state visibility, leases/idempotency, recovery, independent approval, role and budget controls.
4. **GitHub/Cloudflare development tools**: verify real repo/ref/file edit, diff, PR, CI, Cloudflare preview/rollback integration. Require explicit approval for live deployment.
5. **Hardware and extended feature validation**: physical ARM64 PRoot APK build, local-model inference, Brain voice and permissions, Pine Studio in Work, terminal, News/Signals, Vision.

For detailed acceptance conditions, read [AGENTS.md](../../AGENTS.md), [implementation priorities](IMPLEMENTATION_PRIORITIES.md), and [work orders](../../ops/engineering/work_orders.json). Latest explicit user direction: English-first for NEW UI, preserve all previous languages and features; public app stays V1.0.0 and signing identity remains intact.

## Mandatory Work session protocol

At the beginning of the next authorized Work/Codex session:

1. Connect the GitHub repository in the supported Work interface. Load the three files `AGENTS.md`, `.github/agents/maximus-continuity.agent.md`, `docs/engineering/WORK_RESUME.md`.
2. Fetch the current `main` commit, open PRs/issues, exact-head checks and release artifacts; explicitly label this document's historical values as superseded when different.
3. Choose the highest-priority testable task not already implemented or under review; open a bounded feature branch; change and test code.
4. Add the actual test output, a branch/PR URL, confirmed result/limitations and next command in a secret-free checkpoint before hitting usage limits. Do not assume CI runs on a branch unless it did.
5. Return a user-readable status report and ask only for a concrete approval when needed.

### Copy into ChatGPT Project instructions

```text
You are MAXIMUS Continuity, my dedicated engineering agent for https://github.com/drfxai/Maximus-Matrix-OS. In every Work session read AGENTS.md, .github/agents/maximus-continuity.agent.md and docs/engineering/WORK_RESUME.md from the latest repository, verify main/PRs/issues/CI/release, reconcile the previous checkpoint, then implement the highest-value remaining authorized task. Priorities: Smart Combo; six Neural Galaxy graphs and Brain/chat UI; agent dashboards/Hindsight; GitHub/Cloudflare Work integrations; physical ARM64/mobile harness and remaining modules. Use English for new UI work but preserve existing five-language infrastructure. Keep public V1.0.0 and signing identity. Create reviewable changes and tests, don't merge/deploy/publish or pay for models without approval. Before stopping, always emit an exact commit/branch/PR/test/next-step checkpoint. Never claim to work in the background or resume solely because a usage limit resets.
```

### Work session start message

```text
Resume MAXIMUS MATRIX OS implementation from the current GitHub state. Read the repository's AGENTS.md, .github/agents/maximus-continuity.agent.md and docs/engineering/WORK_RESUME.md. Refresh the latest main SHA, PRs, CI and issues; pick and implement the next safe, highest-priority unfinished unit of work. Verify it and preserve a durable checkpoint before stopping. Do not increment version or merge/deploy/publish without my approval.
```

## Post-session checkpoint to update

The values below describe the **setup of the continuity agent**, not implementation progress on Smart Combo or Galaxy. Do not fill them with imagined results.

```yaml
checkpoint_schema: 1
project: drfxai/Maximus-Matrix-OS
purpose: work-context-resumption
observed_main_sha: 4842681383375bdeb9540a95c46d2c929bd73296
checkpoint_state: HANDOFF_PREPARED
engineering_task: NONE_STARTED_BY_THIS_CHECKPOINT
last_known_open_pr_count: 0
android_ci:
  run: 39490108055
  sha: 4842681383375bdeb9540a95c46d2c929bd73296
  result: SUCCESS
agent_quota_reset_event: NOT_AVAILABLE
requires:
  - merge this agent-definition PR after review
  - launch or schedule an authorized Work session
  - refresh all live GitHub state before code changes
next_action: Start Work from the instructions above, verify live repo state, then implement the next Smart Combo task
```

**Boundary:** A ChatGPT Scheduled Task may run at a user-chosen time or supported event, subject to availability and quota. A GitHub custom agent becomes discoverable through supported GitHub Copilot agent surfaces after its definition is merged. Neither mechanism guarantees automatic resumption at the moment a ChatGPT Work usage cap resets.

## Active authorized increment — 2026-10-21

The setup checkpoint above is historical. Main was refreshed to `b0cff9209906788358587c248b64817e531acd15`; PR145 adds only continuity instructions. Current implementation branch `feat/operational-stages-1-4` contains Smart Combo durability/recovery, six-view graph fixes and Room timeline, Hub Operations projections, and credential-bound developer writes/reconciliation. See [implementation report](STAGES_1_4_IMPLEMENTATION.md) and [machine checkpoint](stages-1-4-progress.json).

State: VERIFYING.37 Python script tests +7 identity +5 publication checks passed locally; exact-head full Android CI, emulator images and final review remain required. No merge/deploy/release. Next operation: find this branch's PR, inspect its current SHA/checks, fix any compile/runtime regressions, then inspect actual screenshots. Do not duplicate this work or infer approval from this checkpoint.

## App upgrade preparation — 2026-10-21

The earlier VERIFYING entry is historical. PR146 is owner-merged as `d0024708b70d01cb0a69400cafee8e12f8e9ac0f`; PR CI628 and main CI629 passed436 JVM and51 Android tests, lint/build and required-ci. PR146 records exact evidence and known visual/live-account/device limits. The owner requested an app update. Internal10030 is proposed over published10029 while public1.0.0/signing/Room remain unchanged. Follow [release preparation checkpoint](RELEASE_10030_CHECKPOINT.md) and branch `release/prepare-10030`; find the existing PR/verification run before duplicating work. Signed APK/upgrade acceptance is pending at this source checkpoint. No Stable replacement is authorized by reading this document.
