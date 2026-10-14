# Hindsight Shared Project Memory — Maximus Matrix OS V1.0.0

## Purpose

Hindsight is a **shared long-term memory bank** for multiple independent models working
on the same Maximus project. Each model can learn the project goal, decisions, failed
approaches, and what to do next without the user repeating the history. The verified
local workspace, Git commit, tests and SHA-256 checkpoints remain the source of truth.

The initial Android integration targets user-owned **Hindsight Cloud** using its
published REST API: https://docs.hindsight.vectorize.io/cloud/recall/
The open-source self-hosted server is an option for a future trusted backend, not an
unauthenticated endpoint on a mobile device.

## Architecture

- Project `<uuid>` maps to bank `matrix_project_<uuid_without_hyphens>`. The bank ID
  can be changed to an already-existing bank when importing a project across devices.
  Bank ID by itself is not an access token.
- The user enters a **Hindsight Cloud API key** into Project Studio. Android Keystore
  AES-GCM encrypts it in app-private preferences. It is never included in APK or logs.
- Cloud memory sharing is **off by default** for each project, with separate user consent.
- `Retain`: first create a local verified checkpoint, then manually approve a
  bounded synopsis and a lesson/decision, and press Retain. The app sends only
  project ID, source revision hash, checkpoint ID, goal, next task and reviewed note.
  No source file contents, full chat history, API secrets or attachments are submitted.
- `Recall`: REST `POST /v1/default/banks/<bank>/memories/recall`. When enabled,
  Project Studio Plan/Resume automatically retrieves relevant project experience and
  combines it with `ProjectContinuityStore.resumePrompt` before a model gets the task.
- `Reflect`: supported by Hindsight upstream; is **not automatically invoked** by
  Android because it may consume additional provider tokens/credits. Later backend
  analysis can explicitly add this capability with cost controls.
- Only after selecting the same bank and supplying authorized credentials can
  a different device or service recall that bank's retained experience.

## Non-negotiable safety

1. Hindsight memory is untrusted historical context, **not** proof that a tool ran,
   a test passed, a bill was paid or a file exists. Validate source/commit/checkpoints.
2. A memory cannot authorize shell execution, signing, release, deletion or payments.
3. User approval is required for every cloud write. This avoids silently recording
   provider output (which may be hallucinated), secrets or unreconciled actions.
4. Cloud reads use a bounded low-budget recall: max 1200 response tokens, 12 parsed
   entries, and a maximum ~2600 characters appended to a handoff.
5. The cloud adapter only connects over HTTPS to
   `https://api.hindsight.vectorize.io`; no redirect-following or custom untrusted URLs.
6. Hindsight Cloud and self-hosted Hindsight each have compute/storage/network costs.
   Do not claim cloud Retain/Recall are always free, or silently bill a paid LLM route.
7. Missing cloud credentials, quota exhaustion or unavailable banks are reported as
   errors. Do not mislabel local-only context as successfully retrieved Hindsight data.
8. No unattended execution. Remote Runtime remains discovery-only until a hardened
   isolation service with transaction-safe idempotent operations is available.

## User workflow

1. Open Control Hub / Project Studio; create or import a project.
2. Save its brief, set Next Task, and save a validated local Checkpoint.
3. Open Hindsight section, save your user-owned Cloud API key, choose the project
   bank ID and enable shared memory for the project.
4. Write a reviewed lesson or decision and press **Retain approved checkpoint & experience**.
5. On a new model or device, select the same authorized bank.
6. Tap **Continue with model**. The new model gets the verified checkpoint and
   relevant project memories without the user restating previous decisions.
7. After a tested/approved milestone, save a fresh checkpoint and Retain the new
   learnings. Unverified output should be described as a hypothesis, not a fact.

## Milestone boundaries

This is a **project-level continuity integration** in Android. It does NOT imply
that all historical conversations were retroactively synchronized, that external
models can read local source code without permission, or that a distributed memory
server has been deployed for free. Further work includes backend role-based
permissions, event logs and conflict resolution for simultaneous collaborators.
