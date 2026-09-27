# MAXIMUS MATRIX OS

MAXIMUS MATRIX OS is a graph-native AI research and agent operating environment developed by DrFXAi.

The Matrix is the primary operating surface: agents, tools, memory, research, validation, artifacts, strategies, and future Pine Script intelligence are represented as live interconnected entities.

## Current implementation

### Web Matrix

The root `index.html` provides the live browser concept and execution graph.

### Android runtime

`android-app/` contains **MAXIMUS MATRIX AI V1.0.1**, the ARM64 Android device-agent runtime.

Implemented in the Android foundation:

- Graph-native agent event model
- Mission planner
- Policy engine
- Tool registry
- Validation gate
- Artifact lifecycle event
- Live Compose Canvas graph
- ARM64 native marker
- English-only repository guard
- Automated release workflow

## Language policy

Repository source code, comments, UI strings, documentation, tests, logs, prompts, and filenames are English-only. Runtime localization should be implemented through dedicated localization resources, not hard-coded non-English strings.

## Security

High-risk actions must pass confirmation gates. Critical policy-bypass actions are denied by default. Production signing credentials must be stored in GitHub Actions secrets, never committed to the repository.

See `docs/android-agent-integration.md` for the Android architecture and migration plan.
