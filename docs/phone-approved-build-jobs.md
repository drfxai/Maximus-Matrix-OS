# Phone-approved native build jobs

The loopback phone host can now accept bounded build **requests**, with separate visible phone approval before execution. This is an executable Android/PRoot build-job service, not a remote shell or a separate Linux kernel. Existing native build verification and physical-device acceptance requirements remain.

## Protocol

All routes require the current process-local bearer token, loopback Host and no Origin header. No CORS, LAN listener, arbitrary command, task selection, upload or source write is added.

| Method | Route | Effect |
|---|---|---|
| POST | `/v1/build-jobs` | Body exactly `{"project_id":"UUID"}`; captures a ZIP and returns 202 awaiting phone approval |
| GET | `/v1/build-jobs/UUID` | Job state, source digest/bytes/count and artifact digest |
| GET | `/v1/build-jobs/UUID/log` | Last 8 KiB of that approved job's output |
| POST | `/v1/build-jobs/UUID/cancel` | Empty body; cancels that job only |
| GET | `/v1/build-jobs/UUID/artifact` | Verified job-owned Debug APK, streamed after success |

Source sharing for that exact Studio project must already be enabled and actual tool probes must have passed in the current process. A restored APK never enables build readiness. Requests are limited to 512 ASCII JSON bytes, one pending/active job, 8 MiB compressed source, 500 ZIP entries, 4 MiB per expanded file and 32 MiB expanded total. Sixteen terminal records maximum are retained per host session. Pending/approved requests expire after ten monotonic-clock minutes. Gradle uses the existing offline fixed `:app:assembleDebug`, 15-minute timeout and foreground cancellation controls.

## Approval and source identity

Capture finishes before the phone shows its review dialog. File names and SHA-256 come from the captured ZIP, not a later enumeration of mutable Studio source. The dialog identifies the project, files, bytes, fixed command and exact digest. The user must approve this request on the phone; there is deliberately no HTTP approve/start endpoint. The visible approval starts the foreground service, which consumes the approval exactly once and passes the captured ZIP and digest into the real native builder. The builder copies and rehashes that exact ZIP before unpacking or starting a process. Later editor changes do not enter the approved job. Capture itself is not an atomic transaction across the Studio editor: approval refers to the actual captured bytes.

The existing direct **Build Debug APK** button remains a separate owner-trusted local workflow; it is not an API approval and cannot be claimed by a remote request. Request approval cannot start an unrelated local build. Cancellation carries an owned job identity, and the builder checks cancellation under its process-start lock. Revoking sharing or stopping the host invalidates approval immediately, cancels its owned pending/active requests and deletes session snapshots/artifacts on IO; it does not cancel an unrelated direct local build. Process restart loses all server tokens, job handles and approvals. Starting a new host removes abandoned request cache. PRoot process termination on physical-device process death remains subject to Android lifecycle behavior; no autonomous survival/recovery is claimed.

## Artifact and safety boundary

Only a successful state bearing the same job and project identity can publish output. Each job owns a separate APK copy whose digest is checked before streaming; it cannot expose the global previous build or an APK from another job. Logs are available only under the authenticated session and bounded to 8 KiB. Trusted build scripts can print sensitive data and run with the app's Android UID: PRoot is **not** a hardened sandbox. Build only trusted source; no production signer is injected.

Unit tests cover approval/replay, captured-source identity, digest tampering, expiry, revocation, bounded requests and cancellation ownership. The existing emulator-gated PhoneHarnessIntegrationTest exercises actual authenticated HTTP requests with production readiness overridden by internal test-only dependency injection and a recording executor. That proves requests do not execute before explicit approval; it does not prove native ARM64 execution. The dedicated physical build/export/install acceptance remains mandatory before advertising the runtime as device-tested.

## Responsive lifecycle and revocation

Main-thread service callbacks reserve one operation and show the foreground notification before IO coordination. Registry lookup/claim, source hashing, APK copying, cleanup and project-scope changes run on IO. Host lifecycle work is serialized off Main; stop immediately detaches the active host and invalidates its listener/approvals before queued teardown. Project changes invalidate a lock-free authorization generation before waiting for any bulk-work lock. Old asynchronous scope changes cannot republish a newer selection.

Each job pins both the registry generation and the external host/project authorization predicate. The builder checks the predicate under its native process-start lock and while polling the child. Both API and direct service builds carry an operation identity and cancellation predicate, so cancellation before IO ownership is assigned still prevents a delayed start. No registry lock is acquired by the native spawn guard. Responses admitted before revocation can already have transmitted bytes; the protocol does not claim to retract them.

Capabilities retain `execution_enabled: false` for the existing arbitrary-command contract. `arbitrary_execution_enabled: false`, `build_execution_ready` and `build_execution_approval: phone_required` explicitly distinguish the real, consent-bound build service.
