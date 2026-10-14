# Hindsight Shared Project Memory — Maximus Matrix OS V1.0.0 (10012)

## Goal and status
A single **project memory bank** stores user-approved project facts and model experience so changing inference models does not require re-explaining the task. Hindsight is optional and disabled by default. The implementation uses documented REST endpoints:

- `POST /v1/default/banks/{bank_id}/memories` — Retain.
- `POST /v1/default/banks/{bank_id}/memories/recall` — Recall.

The open-source Hindsight service can be self-hosted behind authenticated HTTPS, or the user may connect their own Hindsight Cloud account. Hindsight requires its own LLM for fact extraction and may incur compute costs. It is NOT bundled inside the Android APK.

## Architecture
- One isolated `maximus-project-{UUID}` bank per local Project Studio project. Inference model names are not part of bank identity. All models working on that project see the same memories.
- Keep app-private local continuity checkpoints with SHA-256 workspace-file hashes, plus short AES-GCM-encrypted on-device progress notes recorded after successful chats when shared memory is enabled. These remain accessible without the remote service. Never treat memory text as evidence of successful source edits or CI jobs.
- Project Studio has an explicit opt-in Hindsight panel: authenticated endpoint, separately encrypted API token, active project, manual checkpoint sync, recall, and optional automatic progress sharing.
- Retain uses a stable `document_id` for checkpoint upsert and content-hash-derived IDs for duplicate-safe short model progress records.
- Android Keystore encrypts the user-owned token; the app never embeds a shared/admin/cloud token.
- On project selection and model switching, recall warms an in-process cache on a background coroutine. Sending a chat request never waits on Hindsight HTTP; a bounded, explicitly untrusted evidence block is provided to the next model when a fresh snapshot exists.
- If the service is offline, validated local checkpoints are still retained for a fast handoff; Hindsight is not needed to read local workspace state.

## Safety and privacy
- Memory excerpts are DATA, not tool instructions or permissions. External text is explicitly marked untrusted. The app makes no destructive edits from memories.
- Cloud data upload occurs only after enabling remote memory; automatic short task/response retention requires a **second opt-in**. Users should not enable automatic sharing for proprietary or sensitive conversations.
- Only bounded text (not attachments, API keys, code archives, binary files) can be uploaded. A basic secret scanner blocks common credential patterns; it is NOT a DLP guarantee and cannot catch all secrets.
- HTTPS origin-only configuration, no URL user-info, path, fragments or redirect following. Configurable self-hosting requires a valid HTTPS DNS name. An edge service must enforce per-user/project access rights; a bank name alone is not an authorization boundary.
- Memory source is user-specific; do not embed a global Hindsight credential in APK or source.
- The current Android build cannot guarantee multi-device same-project synchronization from independently generated workspace UUIDs. The identical bank ID, authorization and workspace content must be shared deliberately before devices can collaborate. Do not claim cross-device project mirroring.
- Retain/Recall is available; automatic Reflect is excluded from the low-latency request path to avoid additional costs and non-deterministic decisions.
- Disconnecting removes the cloud credential and disables remote sync but intentionally preserves encrypted local project progress. Explicit full local clearing is separate. Deleting previously stored remote facts requires the appropriate authenticated remote memory action.

## Tests / release gates
- JVM: HTTPS endpoint hardening, project bank determinism/isolation, unsafe text filters.
- Android instrumentation: Keystore encryption, state preservation, off-by-default auto-share, revocation.
- Full Android CI: JVM, Lint, debug build, emulator tests, signed V1.0.0 release and upgrade identity.
- No live Hindsight Cloud request may be claimed successful without a user-provided key and verified service.

## Service deployment
Recommended deployment is Hindsight on a dedicated service with persistent storage, a structured-output-capable LLM, TLS reverse proxy, authenticated client access and durable backups. Protect the admin/control UI from public internet exposure. No live service is provisioned or configured by this PR.
