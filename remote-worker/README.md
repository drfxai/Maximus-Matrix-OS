# Maximus Remote Runtime

Python 3.11+ gateway for authenticated worker discovery and opt-in, isolated Android debug builds. Discovery is the default. It does not execute conversational agents, run arbitrary host commands, sign production APKs, or provide continuous Agent Factory execution.

## Start discovery on PC/server

1. Generate a worker-specific token: `python -c "import secrets; print(secrets.token_urlsafe(48))"`.
2. Store it privately outside the repository and set `MAXIMUS_WORKER_TOKEN` through your host secret facility.
3. Run `python remote-worker/worker.py --host 127.0.0.1 --port 8767`.
4. Terminate TLS at a trusted HTTPS reverse proxy/private tunnel with authentication and rate limits. Forward `/v1/capabilities` to loopback. Do not log authorization headers or disable certificate verification.
5. In Android Project Studio → Remote Runtime, enter the HTTPS origin and worker token, then Test & Save. The app stores the token using Android Keystore and rejects endpoint changes without new credentials.

## Opt-in isolated debug builds

On a separately controlled host with **rootless Docker**, set `MAXIMUS_BUILDER_IMAGE` to an operator-reviewed builder image pinned by `@sha256:…`. The image needs an Android SDK, Gradle, and a populated read-only offline dependency cache at `/opt/gradle-cache`. No image is downloaded automatically. Set `MAXIMUS_WORKER_STATE_DIR` to a dedicated private local directory; the default is `~/.local/state/maximus-worker`. On POSIX it must be owned by the worker user with mode `0700`. Use one stable state directory per worker installation; changing or deleting it resets historical request identities.

This enables only `jobs.submit`, `jobs.status`, and `jobs.artifact` in addition to `capabilities.read`. Configure the trusted proxy to forward those paths only when explicitly enabling builds. Every submission still requires explicit user approval and a source ZIP SHA-256. No provider keys or production signing keys are forwarded. Containers are offline, non-root, read-only, resource/time bounded, and labeled with their journal's private ownership identity. Returned APKs are debug artifacts; `signed=false` means no production signing identity was applied.

Use an OS service manager that terminates the gateway's whole child process group before restarting it (for example, systemd `KillMode=control-group`). This prevents a surviving Docker CLI from starting a late container after recovery has checked for it. Directly killing only the gateway process is not a complete process-group shutdown. Real Docker/host integration still requires operator verification; unit tests use isolated fake builders.

## Durable requests and restart behavior

The owner-private SQLite journal commits the request ID, project ID, source digest, job ID, and container name before dispatch. An exclusive OS lock permits one coordinator per state directory. Keep the directory on local storage supporting OS locks and SQLite synchronization; shared network filesystems are unsupported.

- Reusing a request ID with the same project/digest returns its original job, including after restart. Conflicting reuse is rejected.
- Completed source/output directories expire after one hour. Request identity records remain; expiry never permits replay. Missing or invalid retained APKs report `artifact_ready=false`, including after restart. Artifact paths are restricted to that job's retained directory and hashes are rechecked before serving.
- Queued/running jobs found at startup are **not replayed**. Recovery checks only the recorded container name, verifies its ownership label, and removes its immutable container ID. Confirmed recovery marks the job failed with an interrupted message. A retry needs a new explicitly approved request ID.
- If the Docker daemon, ownership check, journal, or cleanup cannot be verified, new execution is blocked. Unconfirmed cleanup reports `recovery_required`; recovery can be retried after operator repair and service restart. No broad container deletion is used. A coordinator refuses to release its lock while a build is active or cleanup remains unconfirmed.
- At most one build executes at a time, at most 16 retained workspaces are admitted, and at most 100,000 historical request identities are retained. At journal capacity, admission stops for operator intervention; identities are never silently evicted. Do not delete the journal to retry an old request.

The journal contains job metadata and local workspace paths, not bearer tokens, source archives, build logs, provider keys, or signing keys. Back up private state according to host policy. A corrupt journal blocks startup rather than reverting to an empty state.

## Protocol v1

All requests use `Authorization: Bearer <worker-specific-secret>` over trusted HTTPS.

| Request | Availability |
|---|---|
| `GET /v1/capabilities` | Default authenticated discovery |
| `POST /v1/jobs` | Opt-in builder, approved bounded ZIP and digest |
| `GET /v1/jobs/{id}` | Opt-in builder, durable status |
| `GET /v1/jobs/{id}/artifact` | Opt-in builder, retained validated debug APK |

Discovery-only mutation attempts return HTTP 405. Unauthenticated discovery/build requests are rejected. Non-loopback binds are refused.

## Tests

Run `python -m unittest discover -s remote-worker/tests -t remote-worker -v`.

Tests cover approval/authentication, archive validation, sandbox arguments, exact owned-container cleanup, durable restart identity, concurrent duplicates, artifact expiry/missing or tampered paths, interrupted recovery, unavailable Docker, process ownership and corrupt state. These tests do not certify a physical Android device build or production Docker deployment.

## Remaining execution milestones

Continuous agent teams still require scoped tool permits, checkpointed Hindsight integration, task leases, cancellation, model/token budgets, and audit logs. Build-job metadata durability is one prerequisite, not full agent execution. User projects must never run in the application's privileged release-signing workflow.
