# Bounded project agent team

Project Studio can prepare a metadata-only Planner → Reviewer run. The approval panel shows the exact captured project metadata, project revision, and permitted provider destinations. No source file contents or credentials are stored in its journal. Only previously verified-free Smart Combo offers with a current credential are admitted; advertised free eligibility is not proof of remaining account quota.

Approval authorizes at most two text-only provider requests, each with a protocol output limit of 1,024 tokens. The reviewer receives the saved planner proposal as untrusted text. Responses have no tool authority: no file changes, shell commands, APK builds, deployment, paid fallback, automatic continuation or retries occur. The user can inspect the outputs and separately perform other actions.

A process-wide broker serializes chat, API validation and team dispatch across Activity ViewModels. Chat and teams share the same Smart Combo quota ledger. Project metadata/revision and encrypted credential identity are rechecked off the main thread and inside the actual HTTP worker before delivery. Revoking consent, modifying the project or re-entering a key invalidates approval. Revocation after delivery cannot retract a provider request.

Each dispatch is atomically checkpointed before sending. Missing responses after process death, timeout, cancellation or ambiguous transport errors produce UNCERTAIN, never automatic replay. Undispatched expired or recovered runs are blocked. A new user approval is required for a fresh run. Corrupt journal recovery explicitly quarantines the unreadable journal; it never reconstructs or replays requests. The journal is app-private and bounded, and is not an encrypted secret store.

The UI reports the actual dispatched provider/model/role and distinguishes estimated from provider-reported usage. Account quota can remain unknown. This workflow runs while its application scope is alive; it does not claim unattended background autonomy, verified code execution or physical-device acceptance.

Validation includes JVM durable lifecycle/ownership/input immutability/failure/revocation tests, actual serialized provider output-limit tests, queued-worker revocation, and an emulator interaction with the real approval panel and durable executor using a recording adapter. The emulator class is included in the existing Android credential/UI suite. No live provider inference is performed by those tests.
