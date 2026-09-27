# Phase 1 Audit and Migration Map

Audit baseline: repository `c32a0cd` (V1.0.3)

## Repository inventory

The repository contains a single Android application, a small duplicated Kotlin agent-core
reference module, a standalone browser graph prototype, two unit-test classes, one release
workflow, and documentation. It does not contain backend services, databases, MAXIMUS VPN,
provider integrations, or source trees for the legacy projects named in the product brief.
The supplied ZIP and the primary repository contain the same source layout.

## Reusable implementation

- Compose theme, mobile insets, custom launcher artwork, and ARM64 native marker
- Structured Matrix event model and shared-flow event bus
- Deterministic planner, policy engine, tool registry, validation events, and artifact event
- Android intent adapters for device information and visible system actions
- Unit tests for planner and policy behavior
- English-script guard and signed ARM64 release workflow

## Gaps and risks

- The prior Android product exposed the legacy name MAXIMUS MATRIX AI.
- A single screen mixed the graph, actions, feed, and capability cards.
- The browser prototype displays generated nodes and animated telemetry; it is concept art,
  not an authoritative runtime console.
- Memory notes and mission history are process-local and have no provenance store.
- Knowledge and strategy tools are placeholders with deliberately limited claims.
- No provider abstraction, real chat, backend, authentication, database, vector search,
  ingestion, voice, notification listener, accessibility service, observability export, or
  FinOps implementation exists.
- The `android-agent-core` reference duplicates Android app models and is not wired into the
  Gradle build. Preserve it until Phase 3 establishes a shared runtime module.
- The repository has no Gradle wrapper, reducing reproducibility for local builds.
- CI can create an ephemeral key when production secrets are absent. Such artifacts must be
  labeled preview-only and must not be published as production releases.

## Product migration map

| Legacy project name | Internal MAXIMUS MATRIX OS destination |
|---|---|
| AI Ops Hub | Control Plane |
| AI Ops Hub Mission Control | Mission Control |
| DrFXAi Intelligence Foundry | Intelligence Foundry |
| DrFXAi Trading Intelligence OS | Trading Intelligence |
| DrFXAi Research OS | Research Engine |
| DrFXAi Trading Genome | Trading Genome |
| DrFXAi Quant Lab | Quant Lab |
| DrFXAi Agent Factory | Agent Factory |
| Maximus Matrix AI | MAXIMUS AI product |

Legacy names are retained only in this migration record. The public product hierarchy is
DRFXAI -> MAXIMUS MATRIX OS -> MAXIMUS AI and MAXIMUS VPN.

## Phase 1 and Phase 2 decisions

- Keep package `ai.drfx.maximus.matrixai` for Android upgrade compatibility.
- Change all visible application branding to MAXIMUS AI and set version 1.1.0.
- Preserve current agent and Android tool code while exposing its actual availability.
- Separate the UI into dedicated destinations with a persistent command interface.
- Drive active graph visuals only from live mission state.
- Keep unimplemented features visible only with explicit planned, limited, or unavailable
  labels.
- Defer provider, persistence, services, backend, research, and trading execution to their
  specified later phases rather than adding demo behavior.
