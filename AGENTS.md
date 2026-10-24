# MAXIMUS MATRIX OS — persistent engineering agent instructions

These instructions apply to development agents working on this repository, including agents launched from ChatGPT Work, Codex, or an IDE. They are project instructions, **not** a background-running process or a way to bypass service usage limits.

## Resume protocol (mandatory at the start of EVERY session)

1. Read `docs/engineering/WORK_RESUME.md` and `.github/agents/maximus-continuity.agent.md`.
2. Refresh live facts rather than trusting a previous conversation: `main` SHA; current open PRs/issues; exact-head GitHub Actions results; release/tag/assets; relevant implementation files; existing project checkpoints.
3. Compare the checkpoint's base/head SHA to current GitHub. If divergent, mark the checkpoint STALE, reconcile changes and avoid duplicate branches or work.
4. Select one feasible, user-authorized unit of work; verify test criteria and dependencies before changing code. If the current tool cannot edit/test, report the block and preserve an actionable handoff rather than claiming completion.
5. At the end or before context/usage interruption, write a **secret-free** handoff with what changed, branch/commit/PR, tests actually run, evidence links, unresolved blockers, and the exact next operation. Update `docs/engineering/WORK_RESUME.md` through a reviewable PR when possible.

## Product direction and priorities

- Repository: `drfxai/Maximus-Matrix-OS`; Android / Kotlin / Jetpack Compose / Room; preserve app identity, data and modules.
- Public `versionName` must stay **1.0.0 / V1.0.0**. Do not change `versionCode`, keystore identity, release artifacts, merge policy or production deployment without explicit approval.
- Focus current new UI work on **English** to accelerate delivery. Preserve existing EN/FA/AR/TR/RU resources/settings; do not delete localization. Do not interpret English-first as authorization to remove languages.
- Priority order for the next engineering work: (1) **Maximus Smart Combo** free-first capability-aware routing, quota/actual-dispatch evidence, durable cross-model continuity; (2) **six operational Neural Galaxy layouts**, graph reliability/performance and compact Brain chat; (3) **agent/team operational dashboards** and Hindsight checkpointing; (4) real **GitHub/Cloudflare Work** editing, preview/PR/approval workflows; (5) phone ARM64 harness, supervised Brain actions, Pine Studio inside Work, terminal and preserved other modules.
- Use current GitHub issues, verified source, existing engineering ADRs and current user instructions as the source of truth. Do not rely on missing historical planning files. Newer explicit user priorities override older backlog ordering, not safety gates.
- Do not remove Brain, Chat, Smart Combo, Work, Pine Studio, Agent Factory/Teams, News/Signals, Vision, API Control, Hindsight, Mobile Harness, Local Model Hub, ZimZilla, GitHub/Cloudflare integrations, Plugin Store or settings.

## Engineering and security gates

- Changes: scoped feature branch -> diff -> meaningful unit/lint/instrumentation/CI validation -> independent review -> human-approved merge. Preserve code review and rollback capability.
- Distinguish SOURCE-IMPLEMENTED, TESTED, CI-VERIFIED, DEVICE-VERIFIED, DEPLOYED, RELEASED and UNKNOWN. A screenshot mockup is not a real emulator/device capture; a CI success is not physical-device acceptance.
- Never silently consume paid models or expose private tokens/keystores. Distinguish verified provider usage/quota from estimates. Do not assume a fallback request is safe to replay after an ambiguous stream failure.
- No autonomous production deployment, release signing, privilege escalation, phone actions, destructive cleanup, or merges without specific approval.
- Do not imply permanent unattended execution from Android WorkManager or GitHub's read-only supervisor. Work scheduled tasks require a supported schedule/trigger and the account's access/quotas.

## Handoff references

- [Work resume and verified baseline](docs/engineering/WORK_RESUME.md)
- [Existing supervisor limits](ops/engineering/TEAM_OPERATIONS.md)
- [Open engineering issues](https://github.com/drfxai/Maximus-Matrix-OS/issues)
- [Smart Combo durability ADR](docs/engineering/ADR-2026-10-20-smart-combo-durable-journal.md)
- [Read-only supervisor workflow](.github/workflows/engineering-supervisor.yml)

If usage or tool access ends: stop cleanly; emit a resumable checkpoint and ask for only the approval or tool access strictly required. Never fabricate work that did not run.
