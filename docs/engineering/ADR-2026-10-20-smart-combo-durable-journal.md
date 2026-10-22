# ADR: Device-local durable Smart Combo dispatch and checkpoint evidence

Date: 2026-10-20
Status: implementation increment for review, based on main 4842681.

## Source audit
SmartComboSessionBroker currently shares one process-local executor. Quota reservations,
uncertainty and confirmed provider usage disappear on process death. The separate bounded
ProjectTeamRunStore already prevents replay and validates ProjectContinuityStore manifests,
but its role handoff is not linked to the shared inference quota ledger. ApiUsageStore and
user-defined budget preferences already persist aggregate local estimates; they remain their
existing authoritative accounting and settings stores.

## Decision
Add a bounded app-private journal under noBackupFilesDir. It contains only request IDs,
SHA-256 scope/route identities, provider enums, numeric reservations/usage, dated quota
snapshots and optional validated checkpoint IDs/revision. Never persist keys, endpoints,
prompts, response content, hidden reasoning or action approvals. It is metadata, not a secret
vault or instruction store. Hash verification detects corruption, not an attacker able to
rewrite the complete app-private journal.

Every dispatch must durably record its barrier before network send is allowed. Atomic
replacement, synced bytes, file locking and a revision comparison reject stale writers.
On restart an undispatched reservation becomes a released tombstone; a dispatched request
becomes UNCERTAIN. Request IDs remain occupied in all states. No automatic replay, retry,
credential restoration or provider fallback is introduced. Corruption, journal capacity and
write failures fail closed for automatic routing and leave existing evidence untouched.
There is no automatic cleanup or deletion. A separate explicit confirmation may move settled
records older than five minutes into immutable archive segments. Uncertain/in-flight records
are excluded. Archives preserve request-ID deduplication, checkpoint provenance and confirmed
provider totals. Archive-first/current-journal-second ordering makes an interrupted operation
leave duplicate unreferenced evidence rather than lost history. Active entries are bounded to
1024 and archive segments to64; full/error states remain visible and never silently delete data. This is a one-device ledger, not cloud quota
coordination. The shared broker still serializes application callers.

Confirmed usage is persisted separately from estimated completion; uncertain reservations
remain conservative. Existing aggregate usage/budget preferences are not migrated or reset.
Checkpoint references from existing ProjectContinuityStore are captured and revalidated at
handoff/dispatch; they grant no permissions and never cause an operation to resume itself.
Journal I/O runs on IO dispatchers in chat/team paths. The dashboard reads a cached summary.

## Verification
JVM tests cover restart states, deduplication, confirmed/estimated usage, stale quota evidence,
corrupt data, stale writers, write failure and safe checkpoint identity. Existing unit,
Android UI, security and lint suites remain required. No live provider request or physical
phone verification is implied by these tests. Version, identity, signing and Room are unchanged.
