# Maximus Matrix Android Agent Core

This module is the graph-native evolution path for the uploaded Maximus Kotlin assistant.

## Source architecture reviewed

The Android project already contains agent orchestration, AI provider routing, a tool registry, action policy and confirmation, memory recall, goal planning, Android capability services, accessibility integration, notification integration, voice components, and Room-based local persistence.

The original assistant uses a bounded Act -> Observe -> Reflect loop. Matrix OS extends that model with explicit mission events, policy decisions, validation gates, provenance, and graph telemetry.

## Design rules

- Repository source and documentation are English-only.
- High-risk actions require explicit confirmation.
- Critical or policy-bypass actions are denied.
- Every mission emits structured Matrix events.
- Tool outcomes are validated before the mission advances.
- Failures are explicit and auditable.
- UI animation is driven by runtime events, not decorative random state.
- Android-specific capabilities remain behind tool interfaces.

## Runtime path

Mission -> Memory -> Planner -> Policy -> Confirmation -> Tool -> Validation -> Result

Every transition emits a MatrixEvent that can be streamed to the live graph UI through WebSocket, SSE, or an Android bridge.

## Next integration step

Adapt the existing Android AgentOrchestrator, ToolRegistry, AIRouter, MemoryRecallEngine, and ActionPolicyEngine to these interfaces. Do not duplicate Android capability implementations. The existing application should become a device runtime node connected to the Maximus Matrix OS control plane.
