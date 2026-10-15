# MAXIMUS — Specialist Engineering Team and Independent Supervisor

Updated 2026-10-15. The public Android version remains V1.0.0 and the signing identity is unchanged.

## What this team is — and what it is not

Seven bounded engineering workstreams have an independent quality reviewer. The permissions manifest is ops/engineering/team.json. The priority/dependency/acceptance backlog is ops/engineering/work_orders.json. The deterministic coordinator ops/engineering/assignment_board.py converts observed GitHub issues and PR CI evidence into assignment proposals.

These role files are NOT autonomous coding models. The coordinator labels roles NOT_RUNNING_UNTIL_VERIFIED_WORKER_CONNECTED; declaring roles does not provide 24-hour AI execution, provider credit or GitHub privileges. A coding worker becomes active only after an independently authenticated connection supplies user-approved task, model and test evidence.

## Specialist capability requirements

| Role | Advanced engineering specialty | Primary backlog |
|---|---|---|
| Engineering Supervisor | CI/CD, release signature, architecture, issue/PR risk triage | all |
| Principal Android Engineer | Kotlin, Compose, Android permissions, lifecycle, ARM64 PRoot | #40, #24, #39 |
| Voice and Localization Engineer | Persian ASR/TTS, five languages, RTL, accessibility | #36, #35 |
| Smart Combo / Local AI Engineer | verified-free API routing, token accounting, GGUF/LiteRT, Hindsight | #22, #19 |
| Agent Runtime Engineer | durable queues, leases, idempotency, Hindsight checkpoints, budgets | #38, #40 |
| GitHub / Cloudflare Engineer | scoped GitHub PRs, Cloudflare previews, rollback, private workers | #38, #19, #20 |
| Independent Security QA | JVM/instrumentation, physical ARM64, signer identity, secret audit | review every other's branch |

High-quality coding requires capable reasoning models, sufficient non-secret code context, reliable tool execution and real tests. There is no model-independent unlimited-compute switch. The present planner does not call models or execute user code.

## Operational artifacts

The existing hourly, read-only GitHub supervisor action now publishes:
- engineering-audit/status.json and summary.md: actual release, exact-head PR checks and main CI.
- engineering-audit/assignment-board.json and assignment-board.md: priority, lead, independent reviewer, issue dependency, linked PR, stalled review and capacity.
- PLANNING_ONLY label, avoiding claims that any coding model is currently running.

The statuses are PROPOSED_FOR_NEXT_BRANCH, QUEUED, BLOCKED_DEPENDENCY, VERIFYING, BLOCKED_CI, AWAITING_INDEPENDENT_REVIEW and ISSUE_NOT_OBSERVED. Missing issues in a bounded API response are UNKNOWN, not proved resolved. At most four parallel branches and one newly proposed branch per specialist are allowed.

## Hindsight-style non-secret checkpoint

Every work item should preserve its repository and issue number, role, independent reviewer, exact base and head SHA, path-scoped diff summary/hash, commands and test evidence, model/runtime capability flags marked VERIFIED or UNKNOWN, approval record, outputs, blockers and next step.

Only user-authorized, scoped Hindsight storage may share this data between models. Never place credentials, provider tokens, private conversations, signing keys or sensitive device data into a shared checkpoint. The current Android Hindsight integration does not itself authorize a server-side memory credential.

## Security and release gates

- A role may propose reviewable PRs but cannot approve its own changes. Missing or failed checks block merge.
- The supervisor never approves, merges, deploys Cloudflare, publishes production APKs or changes user settings by itself.
- Preserve public V1.0.0, original Android signing identity and user-data-preserving upgrade; increase internal versionCode only through verified release work.
- Phone WorkManager teams currently execute bounded news and device-health checks, not 24-hour source coding. Real persistent coding requires an opt-in cloud/private worker, authenticated scoped tooling, lease heartbeats, job budgets and audit logs.
- The existing Railway Hermes Agent Telegram bot is a separate deployment; do not reuse its credentials or modify it without explicit scoped approval.
- Unknown account quota, model capability, CI, physical-device status or memory integrity must remain UNKNOWN, never green.

## Initial work sequence

1. Voice and Localization: issue #36, complete five-language Compose resources and physical Persian STT/TTS proof.
2. Android Platform: issue #40 Brain conversations and supervised actions, followed by #24 Android ARM64 physical APK build.
3. AI Routing: issue #22 real GGUF/LiteRT inference and safe downloads.
4. Agent Runtime: issue #38 durable cloud task leases, shared checkpoints and consented worker actions.
5. Independent QA inspects all output and blocks unverified merges, paid routes, untrusted Android builds and release signing changes.

A green CI run is not evidence that an Android device supports an installed Persian STT engine, that PRoot built an APK on physical ARM64, or that any agent worker is continuously online.
