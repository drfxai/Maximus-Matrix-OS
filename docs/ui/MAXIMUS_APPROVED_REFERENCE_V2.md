# MAXIMUS Matrix OS — Approved UI v2 Reference Contract

The five user-approved concept screens **Chat, Work, Agents, Smart Combo, Pine Studio** are the visual and functional acceptance reference for this repository. This contract prevents later changes from replacing the intended design with generic Material 3 panels. The screenshots supplied/approved on 2026-10-19 are design targets, not proofs of implemented functionality.

## Consistent shell
- AMOLED black `#000000` background, navy `#061A2D` cards, `#17446D` one-pixel outlines, rich gold `#FFCC62` for selection/CTA, cool pale blue informational text.
- MAXIMUS gold wordmark; matching hamburger/search/settings; Brain/Chat/Work/Agents/Hub bottom navigation with only the selected page gold and underlined. Do not replace Brain's existing node graph or remove News, Signals, Vision, Mobile Harness, or existing utilities.
- Minimal horizontal margins (approximately 10–12dp at 360dp), strong hierarchy and useful scroll regions; keep pinned chat/composer and controls reachable above IME. Dynamic typography, TalkBack labels, RTL and five UI languages must be verified, not presumed.
- Align exact card radii, iconography, vertical rhythm, syntax highlighting, line numbers, shadows and section proportions with approved images via Android emulator screenshots. A screenshot match is not an automated functional test.

## Pine Studio — production acceptance
- Real, chronologically sorted OHLCV data, at most **200 previous candles** per selected product/timeframe **1m, 5m, 15m, 1h**; accurate upper/lower candle wicks, visible volume, axis labels, zoom/pan and UTC candle inspection. Clearly attribute exchange and show network failures honestly. Never interpolate or pass synthetic data off as live.
- Separate exchange market candles from Pine script execution. Editable Pine v6 code with syntax highlighting, parameter validation, version history, copy and UTF-8 `.pine` export, alert definitions, and exchange/timeframe selectors.
- A `Compiled`, `Backtested`, `Alert active` or `Deployed` badge is allowed **only** after a genuine corresponding engine/provider operation has succeeded. TradingView chart appearance must not be mistaken for access to TradingView's proprietary editor or backtester.
- Native Pine compiler/execution and full strategy backtesting are **not part of this initial chart implementation**. They need an integration or deterministic engine and security/error handling before being marked operational.

## AI Chat — production acceptance
- Provider + model identity and precise **local context estimate** during generation; provider **quota/credit** only from verified responses, with explicit source and age. Keep context estimate distinct from billed tokens, RPM/TPM, purchased credits and monthly cap. No invented remaining balances.
- Source-code fence detection/export for HTML, CSS, JavaScript, TypeScript, Kotlin, Python, Rust, Go, Pine, MQL, SQL and more. Text is data, not executable tool instructions.
- Isolated on-device HTML/CSS web preview, default network/file/JS-disabled, no native bridge, external navigation blocked. Inline JavaScript requires explicit local opt-in and cannot use the preview to deploy or read secrets.
- Maintain message feedback/reactions, media/attachments, speech, safe copy/download, context persistence and reversible editing. Actual command or repository execution must use existing approval gates.

## Work — production acceptance
- Select real GitHub repository and branch; show committed/uncommitted changes and detailed diffs; save/stage/apply after user approval. Cloudflare workers creation/deploy gated by scoped provider credentials and explicit deploy confirmation.
- Runtime selection, build, tests, logs, changes review, rollback, conflicts, local/proot mobile harness and cloud/private worker runtime must operate against real workspace state, never fake file lists or fabricated `Connected` labels.
- Operations that access external resources must show actual state and failures; dangerous operations must not execute solely because an LLM suggested them.

## Agents — production acceptance
- Real persisted agent definitions, instructions, tasks and permission tools, explicit on/off controls, Teams tab and shared-memory handoff with audit trails. No `Active`, `24/7` or `running` labels without a real scheduler/worker and health evidence.

## Smart Combo — production acceptance
- Animated route diagram may visualise *actual* request/selection state only. Searchable provider/model catalog with All, Code, Vision, Audio, Reasoning and Local categories; never infer unsupported capabilities or pretend an API model is installed offline.
- Verified free-first routing policy, quota headroom, model context and price evidence, failover/checkpoint safety and explicit approval for paid routing. No invented request counts, latency, success rates or model credits; missing metrics must read `Unknown`.
- Keep model changes tied to real provider catalogs and actual active-session routing.

## Release/quality gates
- Public `versionName=1.0.0`, internal `versionCode=10028`, Android signing identity and package remain unchanged until separately approved.
- Android Gradle compilation, JVM unit tests, lint, both Android emulator CI shards, strict screenshot collection, security tests and signed upgrade checks must pass.
- Before approval of a new published build, collect one real emulator screenshot each of the five pages, plus Pine Studio (top/scrolled) and Work Changes/diff with populated fixtures or real state explicitly labeled. Compare against approved concepts for text, margins, icon sizes, cards, status/trust indicators and behavior. Avoid merging or publishing without acceptance.
