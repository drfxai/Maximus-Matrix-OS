# Android Agent Integration

MAXIMUS MATRIX AI is the Android device-runtime node for Maximus Matrix OS.

## Architecture

The Android runtime follows this mission path:

`Mission -> Memory -> Planner -> Policy -> Tool -> Validation -> Artifact -> Matrix Event Stream`

Every meaningful transition emits a structured `MatrixEvent`. The live graph consumes these events and highlights the active system path instead of playing unrelated decorative animation.

## Migration from the original Kotlin assistant

The uploaded Kotlin application established the design basis for:

- Agent orchestration
- AI-provider routing
- Tool registration
- Memory recall
- Goal planning
- Action policy
- Confirmation gates
- Android capability boundaries
- Accessibility and notification integration
- Voice-oriented interaction

Version 1.0.1 creates a clean English-only graph-native runtime foundation. Device capabilities from the original app can be migrated behind the `MatrixToolRegistry` without giving the control plane authority to bypass Android permissions.

## Security boundaries

- High-risk actions require explicit confirmation.
- Critical actions are denied by default.
- Secrets are never placed in graph telemetry.
- Cleartext network traffic is disabled.
- The release workflow supports repository-secret signing and falls back to one-time CI signing only when no production key has been configured.
- One-time CI signing is suitable for preview distribution, not long-term upgrade continuity.

## ABI

The release pipeline compiles a small native ABI marker with the Android NDK and enables only `arm64-v8a`. This ensures the release asset is a true ARM64 APK rather than merely renaming an ABI-neutral package.
