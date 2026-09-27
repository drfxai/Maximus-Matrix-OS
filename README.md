# MAXIMUS MATRIX OS

MAXIMUS MATRIX OS is a graph-native AI research and agent operating environment developed by DrFXAi.

The Matrix is the primary operating surface: agents, tools, memory, research, validation, artifacts, strategies, and future Pine Script intelligence are represented as live interconnected entities.

## Current implementation

### Web Matrix

The root `index.html` provides the browser Matrix concept and execution graph.

### Android runtime

`android-app/` contains **MAXIMUS MATRIX AI V1.0.3**, an ARM64 Android device-agent runtime with real Android capability adapters.

Implemented:

- Graph-native agent event model
- Mission planner
- Policy engine
- Android tool registry
- Device and battery telemetry
- Web search and URL launch
- Android settings launcher
- Camera launcher
- Alarm and calendar editor integration
- Dialer and SMS composer integration
- Share sheet and clipboard integration
- Application launch by package name
- Validation gate
- Artifact lifecycle events
- Mobile-first scrollable Compose interface
- Safe system-bar and navigation-bar insets
- Touch-friendly 48-56 dp controls
- Responsive live Compose Canvas Matrix
- Custom MAXIMUS MATRIX AI launcher icon
- ARM64 native marker
- English-only repository guard
- Automated release workflow

## Language policy

Repository source code, comments, UI strings, documentation, tests, logs, prompts, and filenames are English-only.

## Security

High-risk or destructive actions must pass policy and confirmation gates. Communication tools use visible Android system interfaces rather than hidden execution. Production signing credentials belong in GitHub Actions secrets and must never be committed.
