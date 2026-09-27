# MAXIMUS MATRIX OS

MAXIMUS MATRIX OS is the internal intelligence platform for two end-user products:

- **MAXIMUS AI** — the graph-native Android intelligence product in `android-app/`
- **MAXIMUS VPN** — the secure networking product (not yet present in this repository)

Internal capability domains include the Control Plane, Mission Control, Agent Runtime,
Knowledge Core, Intelligence Foundry, Research Engine, Trading Intelligence, Trading
Genome, Quant Lab, and Agent Factory. They are architecture modules, not separate products.

## MAXIMUS AI V1.1.0 foundation

The Android application currently provides:

- A mobile-first Compose shell with Matrix, Chat, Missions, Agents, Research, Trading,
  Knowledge, Memory, Tools, Activity, and Settings destinations
- A persistent command dock across the product
- A deterministic mission planner, policy engine, Android tool registry, validation gate,
  artifact events, and an in-process Matrix event stream
- Runtime-driven graph highlighting with no idle fake telemetry
- Real Android intents for device status, web search, URLs, settings, camera, alarms,
  calendar, dialer, SMS composer, share, clipboard, and application launch
- Honest availability states for capabilities that are not implemented yet
- ARM64 native marker, English-only guard, tests, and release automation

The current release is a foundation, not the completed platform. AI providers, streaming
chat, persistent encrypted memory, backend APIs, ingestion, retrieval indexes, voice,
notifications, accessibility automation, Trading Genome execution, and Quant Lab are not
connected and are identified as unavailable or planned in the UI.

## Security

The runtime does not bypass Android permissions. Communication actions open visible
Android system interfaces. Cleartext traffic is disabled. Production signing credentials
must be supplied through GitHub secrets and must never be committed.

## Documentation

- [Repository audit and migration map](docs/phase-1-audit-and-migration-map.md)
- [Android runtime integration](docs/android-agent-integration.md)
- [Android build notes](android-app/README.md)

## Language policy

Repository source, comments, UI, documentation, prompts, logs, tests, filenames, and
workflow content are English-only. Run `python3 scripts/check_english_only.py` before a
release.


## MAXIMUS AI V1.2.0

The Android product now includes provider-aware LLM chat, capability-gated agent selection, a larger interactive live Matrix graph, API usage/subscription metadata, and a company data-center gateway for Pine sources, documents, projects and research.

The repository does not contain the private 3,000-source Pine library or production company database credentials. Those assets remain external and are exposed through the configurable data-center connector rather than fabricated in the app.


## MAXIMUS AI V1.3.0

- NVIDIA NIM API Catalog support with automatic `nvapi-` key recognition and the NVIDIA hosted base URL.
- Copyable redacted diagnostic log for API discovery, chat failures, data-center operations and Matrix runtime events.
- Light, dark and system appearance modes.
- Larger Matrix graph with two-finger pinch zoom, drag-to-pan, tap inspection, double-tap reset and explicit zoom controls.
- Credentials are never written to the repository or diagnostic log.
