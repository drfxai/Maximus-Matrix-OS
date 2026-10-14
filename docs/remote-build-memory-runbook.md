# Maximus Matrix OS — Verified Memory & Remote Build Operations

Public Android version: **V1.0.0**. The new project-build feature must be released
with a higher **internal** Android versionCode than the latest published APK.
It must never reuse the application's permanent release keystore.

## 1. Hindsight Retain/Recall: connect and verify

Hindsight is opt-in per Project Studio workspace. Retain and Recall are in
the Android client and each workspace uses a separate bank ID. The service is
not embedded in the APK. No shared Hindsight or LLM API token is committed.

Use either:
- a user-owned Hindsight Cloud account at https://api.hindsight.vectorize.io
  (managed, typically usage/credit-based), or
- a self-hosted Hindsight deployment behind TLS and authenticated per-user
  access controls, with persistent PostgreSQL storage and a configured
  provider for the service's own extraction/recall inference.

Self-hosting reference: https://github.com/vectorize-io/hindsight
Hindsight image: ghcr.io/vectorize-io/hindsight (pin a reviewed digest).
Typical API listens on port 8888; do **not** expose its admin/control plane,
normally on port 9999, to the public internet. Configure auth, backups and
provider credentials as private server-side environment variables. Database
persistence, operator backups and restoration must be tested.

In Android: Project Studio → Hindsight → enter YOUR HTTPS endpoint and
user-owned access key → save → select project → enable memory → create a
checkpoint → Sync checkpoint → Recall now. Automatic progress sharing requires
a second opt-in. No secret or source archive should be retained.

An end-to-end smoke test is included but is **not run without credentials**:

    HINDSIGHT_TEST_ENDPOINT=https://api.hindsight.vectorize.io \
    HINDSIGHT_TEST_TOKEN=<user-owned-test-key> \
    python3 remote-worker/hindsight-smoke.py --run-live

It creates uniquely named disposable banks, retains a checkpoint, tests
recall from a second model context and verifies bank isolation. It then deletes
ONLY the two generated test banks. This operation may consume user quota.
The test intentionally cannot use the production project bank or credentials
from Android. Passing local unit tests does not prove live cloud connectivity.

## 2. Isolated remote debug-build service

The existing gateway remains **discovery-only by default**. It only enables
jobs when MAXIMUS_BUILDER_IMAGE is a digest-pinned Docker image, and the
Docker daemon is confirmed ROOTLESS. Never run with a privileged Docker daemon,
the Docker socket mounted into build containers, or a public unauthenticated
HTTP listener.

Requirements on the user's Linux build machine:

- Python 3.11+, rootless Docker with memory / pids limits enabled;
- a reviewed and digest-pinned builder image containing Java, Gradle, Android
  SDK and a PREWARMED read-only Gradle dependency cache at
  /opt/gradle-cache/modules-2, compatible with the Gradle/Android versions;
- a separate strong gateway bearer token stored outside the repository;
- TLS-terminating HTTPS reverse proxy or private authenticated ingress;
- at least 4 GB sandbox memory plus host overhead; no APK release secrets.

The offline builder uses a read-only source bind mount, copies files into
temporary in-container storage, enforces no network access, drops all
capabilities, prohibits privilege escalation, runs under an unprivileged UID,
and limits CPU/memory/PIDs and time. Android debug APKs are not production
release APKs. User Gradle scripts are untrusted executable code within a
container, and rootless Docker is not a substitute for a hardened VM
boundary for unknown hostile source code.

Start locally (replace digest with a real reviewed image):

    export MAXIMUS_WORKER_TOKEN=<operator-generated-strong-secret>
    export MAXIMUS_BUILDER_IMAGE=registry.example.com/android-offline@sha256:<64hex>
    python3 remote-worker/worker.py --host 127.0.0.1 --port 8767

The gateway refuses to start build mode when rootless Docker is unavailable.
It still refuses non-loopback binding. The operator must ensure external HTTPS,
authentication, rate limits and network policy.

In Project Studio, connect to the Worker, select a project and explicitly
approve Build Debug APK. The client uploads a size-limited project ZIP with
SHA-256 and a random request ID. The server validates paths, rejects links,
prevents concurrent dispatch, runs an isolated offline build and provides a
job status. The Android UI only exports a debug APK after independently
checking its SHA-256. No background build runs on project browsing.

Supported API endpoints when enabled:
- GET /v1/capabilities
- POST /v1/jobs (explicit approval + source SHA256)
- GET /v1/jobs/{id}
- GET /v1/jobs/{id}/artifact

The gateway does not support server-side project signing, publishing to app
stores, debugging emulators, multi-tenant IAM, durable queues, cancellation,
or lifecycle-managed artifact retention. To serve multiple mutually untrusted
customers, replace the rootless-Docker lab with hardened disposable VMs and
separately authenticated users and project scopes. Never expose build
operations via the application's production signing workflow.

## 3. Smart Combo routing regression

Credential discovery now happens before local quota reservation; a route
whose account credential is absent is rejected before the request identifier
is consumed. This allows another VERIFIED zero-price option to be selected
without a speculative dispatch. Once a request has been dispatched, it is
NEVER automatically replayed onto an alternate provider after a timeout;
unknown consumption remains reserved conservatively. Global, multi-device
hard quota guarantees still require an authenticated transactional backend.

## 4. Release gate

- Python and Android unit suites, Android Lint, Debug assembly;
- both Android emulator test suites;
- protected repository signing secrets and verified certificate identity;
- real Android upgrade from the currently published V1.0.0 APK;
- ARM64 + universal APK hashes and publication through existing release
  workflow, only after all preceding gates pass.

A successful PR, mock run, or gateway discovery is **not** an assertion that a
real Hindsight account was tested, an offline Android builder was provisioned,
or a user project was successfully compiled on a physical host.
