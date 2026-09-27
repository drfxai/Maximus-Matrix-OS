# Android Agent Integration

## Objective

Integrate the existing Maximus Android assistant as a first-class device agent inside Maximus Matrix OS.

## Mapping

| Existing Kotlin component | Matrix OS role |
| --- | --- |
| AgentOrchestrator | Device agent execution adapter |
| AIRouter | Model routing adapter |
| ToolRegistry | Device tool gateway |
| ActionPolicyEngine | Local policy enforcement |
| MemoryRecallEngine | Local memory adapter |
| GoalPlanningEngine | Planning support |
| AndroidCapabilityManager | Android capability boundary |
| MaximusAgentService | Background device runtime |
| Accessibility service | Explicitly permissioned UI automation |
| Notification listener | Notification event source |

## Security boundary

The Android device remains the authority for device permissions. The Matrix control plane must never bypass Android permission checks or local policy. High-risk actions require confirmation. Secrets must not be emitted in graph telemetry.

## Language policy

Repository code, comments, documentation, test fixtures, prompts, logs, and UI strings are English-only. Runtime localization should be implemented through external localization resources rather than hard-coded non-English strings.
