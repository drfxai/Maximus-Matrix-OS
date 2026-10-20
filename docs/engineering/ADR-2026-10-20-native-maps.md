# ADR: Incremental native MAPS integration

Date: 2026-10-20
Status: Proposed for owner review; this increment implements stabilization only.
Source baseline: d13a199ed95cb5ad232d1782acae91445ebc4652

## Context
Maximus already has Room graph/history, StateFlow presentation, Hindsight project banks,
app-private workspace checkpoints, free-only inference admission, bounded two-role runs,
WorkManager reports and opt-in cloud evidence monitoring. Replacing these would duplicate
truth and risk upgrade/user-data compatibility. MAPS is a layering reference, not an Android
runtime or a licensed copy of the paid dashboard implementation.

## Decision
- Memory: retain ProjectWorkspaceStore/ProjectContinuityStore and Hindsight access scopes.
  Validate existing checkpoint identity before displaying it or handing it to a new model.
  New signposts will reference stable project IDs and repository-relative paths; no second
  memory database or host-absolute-path contract.
- Agent: retain the bounded run journal and shared SmartComboSessionBroker. Cancellation
  intent must survive callback-registration ordering inside the process. This is not a
  durable distributed lease or proof of cloud execution.
- Pulse: adapt existing Android/cloud schedulers through typed commands; no scheduler is
  provisioned by this ADR. Future consequential commands require scoped durable authorization,
  idempotency status and explicit backend ownership.
- Screen: preserve Brain default, quaternion math, approved premium page proportions and
  five-language architecture. Future six layouts consume one read-only canonical projection,
  not six copies of graph state. Source-specific provenance and freshness remain explicit.
- No Room schema, application identity, preferences, signing, versionName or versionCode changes.
- No merge, deployment, release replacement or remote infrastructure mutation.

## First implementation
Persist pending cancellation in the process broker, deliver at most once outside the monitor,
and discard it when ownership changes. Reject a checkpoint whose saved revision differs from
the existing schema-1 manifest hash. Preserve invalid files for explicit recovery.
No automatic rewrite, migration, replay, retry or remote memory sharing.

## Compatibility and limits
Schema-1 serialization is preserved; save and validate use the same existing JSONArray
serialization. The digest detects inconsistent/corrupt manifests, not malicious rewriting
of both the manifest and digest by an actor already controlling private app storage.
Objective/nextTask remain untrusted text; a digest grants no tool authorization.
Process-local cancellation and reservations do not survive process death.
New telemetry, graph schemas, queues and migrations need separate reviewed contracts.

## Verification
JVM regression tests exercise cancel-before/after-register, repeated cancellation, stale
owners, chat isolation and callback reentrancy. Existing Android continuity instrumentation
adds altered-revision, altered-length and altered-hash tests; invalid files remain recoverable.
Required CI includes compilation, lint, JVM tests and both emulator suites.
No UI changes: no new screenshot/performance claims. Physical ARM64 acceptance remains open.
