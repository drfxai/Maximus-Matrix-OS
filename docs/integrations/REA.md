# REA integration — Chat · Pine Studio · Work

Upstream: https://github.com/morluto/rea (MIT, copyright © 2026 morluto).
REA means **Reverse Engineer Anything**. It is a Node.js CLI/MCP toolchain, **not**
an LLM provider, a Pine Script interpreter, or an AI reasoning mode implemented by Android.

## What is implemented in MAXIMUS

- Every Chat, Pine Studio and Work screen exposes the same **Off / On / Always on**
  selection. This is a single app-scoped setting, not a flag stored against the
  currently selected API key or model.
- **Off**: normal assistant behavior and no REA-specific instruction.
- **On**: REA evidence-first investigation guidance for the **current app process**.
  It remains on when the user switches Chat providers/models or Smart Combo routes.
- **Always on**: the same guidance, persisted across app/process restarts. It still
  does not start a persistent background Linux service by itself.
- Actual dispatch attaches a bounded REA guidance block to the **system prompt**
  after selecting the persona, and counts its tokens in context budgeting.
  Chat, Work's Ask AI, and Pine's Ask AI all use MatrixViewModel.sendChat().
- **Check engine** and **Install** launch a real foreground Phone Linux PRoot
  command session, **only after showing the exact command and user approval**.
  Check uses `node`, `npm`, `rea --version`; Install uses
  `npm install --global rea-agents@latest`. The Phone Linux plugin and a
  functioning Linux runtime with supported Node.js/npm are prerequisites.
  Inspect the Linux Console for the command result; a toggled setting is **not**
  proof that installation succeeded.
- No APK includes a bundled Hopper, Ghidra, JADX, licensed reverse-engineering
  engine, or copied REA binary. This avoids falsely advertising Android ARM64
  support for tools that currently require x64 Linux/macOS/Windows.
- **Analyze JS** accepts a user-authorized JavaScript/Electron target exclusively
  under `/root/workspace`, builds a strictly validated path argument and shows the
  exact `rea analyze-javascript-application <path> --json` command before execution.
  Results remain in the bounded Phone Linux console; no file is analyzed on toggle
  or provider change. Pine is NOT an eligible REA JavaScript target.
- No API credential, chat history, Pine draft or repository is sent to REA when
  changing modes or checking its version. No reverse engineering is launched
  automatically. Installing packages and analyzing a user-selected target remain
  explicit, scoped, auditable operations.

## REA host prerequisites

Check upstream installation and its exact supported Node.js versions:
https://github.com/morluto/rea/blob/main/docs/installation.md

According to the inspected upstream README, supported Node.js release bands are
22.23.4+, 24.22.1+, and 26.12.1+ (not every intervening Node major is supported).
Native APK decompilation requires headless JADX and a full JDK on supported x64 hosts;
**Android ARM64 is not a guaranteed host for those tasks**.
The CLI may install on Phone Linux if the package and Node dependencies support
that platform; installation failures must be reported, not hidden.

In **Work → Phone Linux** inspect process output and errors. To operate REA as
an MCP **server/tool provider**, register its documented server on a supported,
trusted execution host and implement a separately reviewed MCP transport/permissions
bridge. The current Android integration provides the real approved **CLI setup**
path and persistent **assistant workflow policy**; it does **not** yet wire
remote MCP tool invocations into arbitrary model responses. Never present
assistant-written REA instructions as tool execution evidence.

## Security and lifecycle

- Never attach REA installation or execution to API/provider/model changes.
- Use the existing PhoneLinuxService and verified PRoot ownership, foreground
  notification, cancellable bounded process, and explicit confirmation.
- Before analyzing third-party binaries, obtain authorization and respect licenses.
- Always distinguish inspection evidence from speculation and from real Pine/TradingView
  compiler or backtest results.
- Changing provider leaves `ReaModeStore` untouched; test this separately from
  the external REA CLI readiness or installed package.
