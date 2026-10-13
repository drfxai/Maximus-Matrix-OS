# Maximus Smart Combo — P0 core architecture

**Status:** design contract + pure Kotlin selection-policy foundation. Not connected to inference,
provider billing, persistent job execution, or a running cloud worker. Android public version remains `1.0.0`.

## Objective and non-negotiable invariants

Smart Combo is the central execution control plane for conversational and multi-step project work:
free-first routing that preserves capability, quality, privacy, cost ceilings and continuity.

1. Do not conflate **context window**, **per-minute/day rate limits**, **credit balance**,
   **monthly user budget** and **locally counted lifetime usage**.
2. Never display an exact provider quota that an authenticated provider API did not establish.
   `Unknown` is not zero; `Estimated` is not `Verified`.
3. Never enter a paid route without prior explicit account-level permission and a bounded
   per-workflow or per-request spend ceiling; never interpret missing pricing as free.
4. Do not commit a tool operation twice after a timeout, reconnect or provider change.
5. Do not save provider/shared keys inside the APK, traces, project snapshot or repository.
6. Never claim another model can resume the private internal state of a first model:
   transfer a structured, validated, durable **external project checkpoint** instead.
7. No automatic model switch may silently downgrade required capabilities, data privacy
   restrictions, output schema, tool permissions or artifact integrity.

## Existing repository findings (at design time)

- `LlmChatClient` sends to the user-selected provider/model and parses provider usage when
  returned; OpenAI-compatible SSE output is streamed. It does not implement multi-route scheduling.
- `ApiUsageStore` accumulates local usage in SharedPreferences. Its counters are not
  provider account balance, rate limits, or a true rolling monthly quota.
- `ModelLimitsResolver` uses conservative static context estimates, including a generic
  8,192-token fallback for 9Router. This is not provider-authenticated model capacity.
- `ApiControlScreen` already labels capacity estimates and acknowledges provider quota is
  unavailable. Preserve that honesty during migration.
- `ProjectWorkspaceStore` persists project files and a brief; Project Studio's AI planning
  sends project name/brief/path list, not a resumable execution snapshot. Cloud sandbox,
  build worker and durable task engine are separate future components.

## Target components

- **ProviderRegistry / CapabilityCatalog:** authenticated model enumeration, model identity,
  exact capability flags, window limits, current price and terms of use, verified timestamp.
- **QuotaAdapters:** per-provider adapters that consume only documented usage/billing
  endpoints, rate-limit headers and their reset semantics. Persist source + timestamp.
- **TokenLedger:** append-only per-request events with provider request ID, model, input,
  output, cache, tool/reasoning (if reported), USD/micros, source and confidence.
  Upsert/reconcile idempotently; do not double count provider retries.
- **ReservationService:** a transactional per-credential, per-provider-account reservation
  across concurrent workflows. Reserve expected input + pessimistic output before request;
  release or reconcile on final usage. For multi-device/cloud operation use server-side
  atomic transactions (e.g. Redis script or database row lock), not Android preferences.
- **SmartComboPlanner:** synchronous, deterministic route filter/rank (initial Kotlin
  implementation in `llm/SmartComboPlanner.kt`). No network or secret access.
- **HealthCircuitBreaker:** track 429/reset, 402, 5xx, network timeouts, TTFT/latency
  percentiles and error rate; honor Retry-After and cooldown; do not treat all failures alike.
- **WorkflowOrchestrator:** durable task DAG, idempotency keys, checkpoint barriers, tool
  approval, safe resumption from last committed step.
- **CheckpointRepository:** encrypted, versioned per-project external state; checksum/commit
  identity; redact credentials and minimize conversation payload.
- **TelemetryHub:** server-authoritative snapshots and event-driven in-flight estimates,
  displayed through a throttled Android StateFlow/Compose UI.

## Quota and token accuracy policy

A displayed metric MUST have a category and confidence label:

| Metric | Source | Update cadence | Precision label |
| --- | --- | --- | --- |
| Context limit | verified model catalog, when available | catalog refresh | verified/estimated |
| Current context tokens | model tokenizer or heuristic | locally while editing | tokenizer/estimate |
| Request input/output/total | provider completion usage | after completion | provider verified |
| Output tokens during streaming | local tokenizer/heuristic | coalesced every ~250ms | live estimate |
| Remaining RPM/TPM/RPD | provider headers/limits API where supported | response + reset | provider verified or unknown |
| Remaining account credit | authenticated supported billing API | periodic + on demand | provider verified or unknown |
| App/workflow budget | transactionally reconciled internal ledger | every event | locally enforced, not provider balance |

Providers can charge hidden reasoning/cache costs or expose usage only at response end.
Do not infer billed usage from character count, and never re-label it exact. Interrupted
streams may omit the final usage event; mark as `unreconciled` and query provider billing
where supported. Balance refreshes must not block the request fast path.

Example provider capability differences:
- OpenAI-compatible streaming may send usage in a final chunk only when the endpoint
  supports `stream_options.include_usage`; verify support per route.
- Groq exposes different request/day and token/minute headers. The unit and reset
  horizon must be preserved exactly.
- Gemini applies model/project-level RPM, input TPM and RPD; account-key counts are not
  necessarily independent. Do not multiply project quota by the number of API keys.
- OpenRouter exposes separate key limits and account credit information; some credit
  endpoints require a management credential and must remain backend-only.

Provider data freshness is not permanent: expired, missing or unverifiable snapshots
become `Unknown` and cannot be used as a promise of available capacity.

## Preemptive scheduling and graceful handoff

1. Classify work: chat, coding, structured tool use, multimodal or long-context;
   identify hard required capabilities and privacy constraints.
2. Measure/predict input tokens and reserve a safe upper bound for output/tool tokens.
3. Exclude unreachable, unverified, unapproved, incompatible or insufficient routes.
4. For verified quota windows, apply a safety buffer: at least the predicted task demand
   **plus** a configurable headroom (initial policy 15% of published window limit).
   Consider per-minute, per-day, balance and cost windows independently.
5. Select the best eligible free route, keeping a *different* ready route as standby.
   Refine scoring with task-level quality requirements, measured p95 TTFT and error history.
6. Atomically reserve the chosen route, then dispatch. Reservation failure triggers
   immediate deterministic reselection rather than an unbounded retry.
7. Start user-visible SSE deltas as soon as received. Publish approximate live usage no
   more often than ~4Hz, then reconcile final provider-reported usage.
8. On exhausted quota, impending exhaustion, route cooldown or model failure, switch
   **between safe checkpoints**. If a request streams partial text, it is not automatically
   replayed as a completed task. Tool mutations require confirmed idempotency status.
9. If no approved eligible route exists, pause and show next reset, known blockers and
   opt-in alternatives. Never silently bill premium usage.

A standby model is prevalidated and endpoint-warmed if permitted, but no speculative
paid generation is sent solely to hide latency.

## Durable project handoff contract

Each checkpoint is an immutable manifest referencing content-addressed artifacts:

```json
{
  "schemaVersion": 1,
  "projectId": "project-id",
  "workflowId": "workflow-id",
  "stepId": "task-node-id",
  "checkpointId": "unique-checkpoint-id",
  "workspaceRevision": "commit-or-content-hash",
  "inputManifest": [{"path": "app/src/main/...", "sha256": "file-hash"}],
  "objective": "user-authorized goal",
  "constraints": ["must preserve existing Android public version"],
  "completedSteps": ["step-1"],
  "pendingSteps": ["step-2"],
  "pendingToolActions": [{"idempotencyKey": "action-id", "state": "NOT_STARTED"}],
  "testResults": [{"name": "unit-tests", "status": "NOT_RUN"}],
  "artifacts": [],
  "decisionSummary": "concise, sanitized technical decisions",
  "previousRoute": "provider/model",
  "lastUsageStatus": "RECONCILED",
  "approvalState": "READ_ONLY"
}
```

Every handoff executes **QUIESCE -> CHECKPOINT -> VALIDATE -> ROUTE -> RESTORE -> RESUME**.
The next model receives the current objective, relevant constraints, workspace snapshot,
diffs, test evidence and unresolved tasks. It must inspect the actual files and hashes,
not trust a generated textual summary. A checkpoint cannot grant new tool privileges.
Never persist secrets, personal private attachments or unredacted provider responses.

## Live mobile dashboard

Mobile-first English interface with translations via existing locale infrastructure.
A single top summary shows current route, task stage, Free/Paid label and fallback state.
Separately show **Context left**, **RPM/TPM remaining and reset**, **Confirmed billed tokens**,
**Streaming token estimate**, **Account credit**, **Local project budget** and **Provider
quota source / last refreshed**. Missing values render as `Unknown`, not zero.
Show seamless, non-blocking route-change indicators, plus a reviewable change history
and an explicit approval gate for paid routes.

Never render a progress bar whose denominator is a guessed provider allowance.
StateFlow animation must not cause a recompose per incoming streamed character.

## Implementation sequence (P0)

1. **Foundation (this change):** deterministic selection types, verification gates,
   quota headroom and unit tests; contract design. Not connected to the app runtime.
2. **Quota ingestion + ledger:** authenticated adapters, response-header parsing, rate-limit
   reset handling, billing reconciliation, atomic reservation service and data migrations.
3. **Live telemetry:** stream usage event support, tokens at 4Hz with estimate labels,
   exact final reconciliation when available; Compose UI states.
4. **Execution integration:** registry of multiple credentials/endpoints, route selection,
   bounded retries, health, circuit breakers and paid approval. Keep existing send flow
   as an opt-in legacy fallback until parity tests pass.
5. **Project continuity:** durable workflow DAG/checkpoint transactions, resumable handoff,
   idempotent external tools, file-aware summaries and provenance.
6. **Release gates:** unit/property tests, multi-request concurrency, quota reset clock tests,
   API-simulated 429/402/5xx, SSE interruption tests, race/cancel tests, Android emulator,
   privacy/security review and real-provider integration with user-authorized credentials.

### Measurable performance targets (targets, not achieved claims)

- Route selection from warm catalog: p95 < 20ms on representative Android hardware.
- Quota/background refresh: no blocking call in the UI render or inference selection path.
- UI token telemetry refresh: up to 4 updates/second during stream, exact final count when available.
- Checkpoint handoff: no duplicate authorized mutation, no lost committed artifact.
- P95 time-to-first-token must be benchmarked per provider and network; no universal
  zero-delay guarantee is technically credible.

This design does not change app version, app release, signing, providers or production
secret configuration.
