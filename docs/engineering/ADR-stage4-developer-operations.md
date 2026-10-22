# ADR: Stage 4 developer operation boundaries

Baseline: `4842681383375bdeb9540a95c46d2c929bd73296`.
Status: incremental hardening; no storage migration or deployment.

## Source audit

GitHub already supports explicit branch creation, single-file optimistic-SHA commits,
draft PR preview/creation and read-only branch/workflow evidence. Cloudflare already
supports read-only inspection, inactive version staging, separately approved traffic
activation and conflict-checked rollback. Keep those flows and their scoped credentials.

The missing boundaries are whole-operation GitHub credential identity, a concrete
before/after source review, and conservative UI state after an ambiguous Cloudflare write.
The HTTP implementation is currently coupled to Android storage, preventing meaningful
JVM transport tests of dispatch-time revocation and redirect/response bounds.

## Decision

Extract the existing fixed-origin HTTP implementation into one production transport with
injectable credential/connection boundaries for recording contract tests. The Android
constructor continues using the same Keystore vault and plugin registry. Pin each
multi-request GitHub operation to one captured credential revision. A loaded source file
and PR preview retain that non-secret identity, so account replacement invalidates approval.

Show the exact original/proposed source and a bounded line diff before a file write.
The approved source snapshot is immutable, and the optimistic blob SHA remains enforced.
Do not silently allow workflow-file edits or default/protected branch writes.

Before a Cloudflare mutation, remove the prior UI's verified status. An exception after
dispatch must remain uncertain and must not leave an old receipt actionable. Read-only
inspection remains available; there is no automatic traffic write or automatic retry.
Full cross-process ambiguous-write reconciliation needs a durable provider-correlated
operation journal and remains an explicit follow-up, not a claimed capability here.

No credential is included in previews, logs, diff models or reports. No Room, application
identity, signing, version or language settings change. New controls are English only.

## Verification

Recording `HttpURLConnection` tests exercise the actual production transport: service/host
pinning, redirects disabled, bounds, revocation before I/O and no automatic retries.
Client contract tests exercise approved file/branch/PR payloads, credential changes,
protected/default branches and invalid source encoding. Existing Cloudflare staged
deployment tests remain mandatory; live scoped accounts and infrastructure are required
for real provider acceptance. No live writes are performed by these tests.
