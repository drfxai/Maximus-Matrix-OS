# MAXIMUS AI Android Runtime Integration

MAXIMUS AI is the Android device-runtime node for MAXIMUS MATRIX OS.

## Architecture

The implemented mission path is:

`Mission -> Memory -> Planner -> Policy -> Tool -> Validation -> Artifact -> Matrix Event Stream`

Every meaningful transition emits a structured `MatrixEvent`. The live Matrix consumes
those events and highlights the active path. The graph remains idle when no mission is
executing; it does not display random runtime activity.

## Current boundaries

- The memory recall event is a lifecycle marker; persistent storage is not connected.
- Knowledge lookup is local-only and does not claim document or vector retrieval.
- Strategy validation registers a static validation request and does not claim market or
  TradingView execution.
- AI provider routing and streaming chat belong to the next runtime phase.
- Notification and accessibility services are not registered in this release.
- High-risk actions require confirmation and critical actions are denied by default.
- Secrets are excluded from graph telemetry and application source.

## ABI

The release compiles a native marker with the Android NDK and includes only `arm64-v8a`.
This verifies the artifact is an ARM64 APK rather than an ABI-neutral package with a renamed
filename.
