# MAXIMUS MATRIX OS (Android)

MAXIMUS MATRIX OS is a graph-native intelligence platform and executive Android assistant app built with Kotlin and Jetpack Compose.

## Key Features & Capabilities

- **Multilingual UI (v1.0.0 build 10007)**: Choose English (default), Russian, Turkish, Arabic or Persian in Control Hub → Language. Main navigation, settings and Project Studio use localized resources, with RTL layouts for Arabic/Persian; legacy hardcoded screen copy may remain English. See [language notes](docs/app-languages.md).
- **Mobile Project Studio (v1.0.0 build 10006)**: Private Kotlin/Jetpack Compose starter projects, public GitHub and safe ZIP import, file editor, project brief, explicit ZIP export and deliberate AI planning through a connected model. User-project cloud builds are **not** connected. See [scope and phased plan](docs/project-studio-cloud-plan.md).
- **Living Matrix Graph**: Interactive canvas graph visualization featuring multi-touch pinch zoom, drag-to-pan, node inspection, and live mission path telemetry.
- **AI Control Hub & Multi-Provider LLM Chat**: Integration architecture for OpenAI, Anthropic, Gemini, NVIDIA NIM API Catalog, and custom endpoints with capability discovery and usage tracking.
- **Deterministic Mission Runtime**: Matrix event stream, policy engine with risk gate validation, mission planner, and execution pipeline.
- **Android Capabilities & Tools**: Real system integration for alarms, camera, calendar, dialer, SMS composer, share, clipboard, and app launching.
- **Data Center & Knowledge Connector**: Configurable gateway for documents, research sources, and enterprise datasets.
- **Redacted Diagnostics & Telemetry**: Full runtime event logging and diagnostic tracing with sensitive data redaction.
- **Adaptive Material 3 Design**: Supports dark, light, and system themes with edge-to-edge support.
- **News Intelligence (v1.0.0)**: Verified publisher reports, an optional authorized HTTPS news and economic calendar feed, manual and 15-minute in-screen refresh, daily and weekly views, and advance reminders for high-impact events. See [feed setup](docs/news-feed-v1.md).

## Architecture

- **Runtime**: Android SDK 36 (Min SDK 26), Kotlin 2.2.10, AGP 9.1.1, Jetpack Compose Material 3.
- **State Management**: Kotlin Coroutines, StateFlow, ViewModel.
