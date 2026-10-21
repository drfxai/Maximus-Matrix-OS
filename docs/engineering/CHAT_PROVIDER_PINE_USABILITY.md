# Chat, providers, Pine and command navigation

Baseline: main `05a4534549265de4b60f073430089cb6b0d4b188`; public Stable refreshed externally to internal10030 on 2026-10-21. No publication or version change is part of this increment.

## Source audit and implementation matrix

| Capability | Existing evidence / gap | Increment | Verification boundary |
| --- | --- | --- | --- |
| Chat → Pine | WorkspaceMessageContent exported fenced code; no studio callback | Explicit Open button for Pine fences/raw versioned source and proposed `.pine` files; create isolated persisted draft and select ID | Android regression verifies exact source and existing draft preservation; corrupt collections fail closed |
| Pine chart | PineLiveMarketChart fetched actual Coinbase OHLCV; source was not an execution input | Limited deterministic SMA/EMA(close) whole-script preview, supported-default inputs, explicit unsupported status, source editing/paste review | Unit numeric fixtures; actual market access and full Pine execution are separate |
| Provider models | Preset defaultCatalog initially filled active picker; discovery/probe async state needed binding | Authenticated catalog-only discovery, credential/endpoint-bound selectable models, stale-result rejection and separate approved test; retain stored model identity for offline history without authorizing dispatch | No provider key supplied in CI; listing does not guarantee paid entitlement or remaining credit |
| Chat viewport | Duplicate header/title rows and nested horizontal padding | One compact toolbar, wider source/message region, existing IME/composer handling retained | Keyboard/emulator acceptance required |
| Hub / Control | Operations nested in Hub-local state; Control tabs hidden behind Advanced | Explicit Hub controls and separate Operations destination, visible scrollable Control sections | Existing navigation tests plus expanded regression |
| Neural Galaxy | Six native layouts already implemented in GraphSpace/MatrixGraphScreen on main | Preserve canonical graph, default camera and identity; verify existing six-layout suite with new navigation | No new performance/FPS or physical acceptance claim |
| Dashboards | Operations projects existing Agent/Pulse/Memory/Runtime/Security stores | Improve access to current real projections; retain unknown/stale distinctions | No newly provisioned backend or simulated live activity |

Decision: [ADR 0012](../architecture/decisions/0012-chat-provider-pine-handoffs.md).

## Acceptance / remaining work

Check exact-head full Android CI, independent QA/security findings and genuine emulator screenshot artifacts. Test a real user-configured provider (catalog and separately approved selected-model probe), multiple-account rotation and unsupported-model errors. Arbitrary Pine indicators/strategies need a genuine compatible execution backend; this increment intentionally reports unsupported source. Physical ARM64, frame pacing, dense graph labels and full TradingView backtest parity remain unverified.
