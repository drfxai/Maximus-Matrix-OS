# ADR: evidence-backed operational dashboards

Status: accepted for this feature branch. Baseline main4842681; scope stages1–4.

Existing authorities remain Room events/graph, WorkManager, AgentTeamStore,
ProjectTeamRunStore via ViewModel, ProjectWorkspaceStore/ProjectContinuityStore,
HindsightMemorySettings and phone runtime coordinator. No parallel dashboard database.

The Hub opens a contextual Operations screen with Agents, Pulse, Memory, Runtime and
Security projections. Independently dated local observations expire; missing state is
UNKNOWN/UNAVAILABLE and parsing errors remain ERROR. A lifecycle-bound bounded IO
refresh does not call remote providers, decrypt credentials, repair journals or execute
jobs. Project checkpoints are hash-validated only on explicit request; rendering cannot
invoke the run store's mutating recovery reader.

Pulse commands re-read the existing team configuration and use the existing scheduler;
Run Now, Pause/Resume and Cancel require explicit button presses. WorkManager owns job
state; configuration enabled is never running. Its next schedule time is only eligible
and does not promise Doze-free execution. Durable command audit records contain action,
target and timestamps, never tokens or prompts. Approval for cloud writes stays in Work.

Limits: no cloud health inferred from saved credentials; no complete generic execution
DAG or always-online agent claim; physical performance and live-account acceptance
require their actual infrastructure. Preserve approved shell, identity and Room schema.
