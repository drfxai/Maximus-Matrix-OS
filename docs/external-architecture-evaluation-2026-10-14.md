# External architecture and licensing assessment

Review date: 2026-10-14. Target: MAXIMUS MATRIX OS, Android. Public version: V1.0.0.
Policy: selective adoption, independently authored code; no wholesale redistribution.

## Scope and evidence
The portfolios were inventoried: https://github.com/techjarves (25 repositories) and https://github.com/titechprabhasolutions (5 repositories). Four uploaded ZIP archives were enumerated and scanned without executing anything. Their README files, license texts, manifests, dependencies and selected critical modules were examined. This is static screening, not a full line-by-line or runtime security certification.

| ZIP snapshot | Files | SHA-256 |
|---|---:|---|
| Mobile-Harness-main (2).zip | 188 | d88e0d380091b1984f1f376660ca4fe3738f898173e40c1f8ccdf5ef79f9c333 |
| Brahma-Echo-main (1).zip | 194 | 792b2562628a7ff9a1c23c381daccf51c7e65840e1ba463f8a39bc9c4f610878 |
| Brahma-Ai-Evo-main.zip | 283 | 69b5b0180f7cca80f68c8ae8d58b1d773a69e63f011db0526f55be9799d98a0e |
| Brahma-AI---Modular-Chrome-OS-main.zip | 25 | bd33c1b25c7bf8f460fc757a4a10c87ec60def9fab57b24c37593cf8a6015c4e |

Brahma Echo and Evo contain 192 matching relative paths, of which 152 files are byte-identical and 40 differ. Evo adds 91 files, including new skill infrastructure, local brain modules, sensorium, hardware tools and Spotify integration. They should be evaluated as related versions, not as wholly independent implementations.

## 1. techjarves: Mobile Harness and companion portfolio

### Mobile Harness
Repository: https://github.com/techjarves/Mobile-Harness
Source license: MIT for the Android application, with independently licensed components.

Architecture: Kotlin/Jetpack Compose mobile host, Android Keystore vault, foreground runtime service, native C++ process interface, private PRoot ARM64 Ubuntu filesystem, project checkpoints, runtime installer, local/network model endpoints, terminal and Android build toolchain. The archive's manifest lists individually checksummed runtime bundles and an Android toolchain compressed to hundreds of megabytes. Its README warns that PRoot is not a hardened container or virtualization boundary.

Recommended ideas:
- Local-first mobile code editing, project files, Git integration and checkpoint continuity.
- Clear per-operation execution status and artifact export.
- Explicit user-owned provider credentials, bounded downloads and runtime dependency checks.
- A capability-based local/remote execution choice and truthful build diagnostics.
- A separate model gateway and a future local model download catalog.

Do NOT copy PRoot distributions or prepacked toolchains into the production app without threat modeling and separate GPL/LGPL/upstream licensing review. Do NOT run arbitrary imported project scripts with app privileges. On-device local builds may be suitable for projects explicitly trusted by the owner, whereas hostile or shared source needs stronger remote isolation.

### Other potentially useful repositories
| Upstream | Application to Matrix OS | Status |
|---|---|---|
| https://github.com/techjarves/Portable-Local-Studio | Desktop/local model hub: GGUF via llama.cpp, image generation via stable-diffusion.cpp, Whisper speech-to-text, Kokoro text-to-speech | High priority protocol/licensing review |
| https://github.com/techjarves/Uncensored-Local-AI-Multiplatform | Cross-platform local-model user experience | Research |
| https://github.com/techjarves/mobile-server | Mobile-to-host runtime networking approaches | Security audit required |
| https://github.com/techjarves/Linux-on-Samsung | Linux runtime on mobile and ARM64 limits | Research, trusted code only |
| https://github.com/techjarves/Openclaw-Termux-NoRoot | Local terminal/bootstrap workflows | Research, never auto-run installers |
| https://github.com/techjarves/Local-Browser-AI | Local/browser inference concepts | Permission audit required |
| https://github.com/techjarves/DroidDesk-Enhanced | PC remote-control patterns | Separate safety audit |

These are repository discoveries, not approved dependencies or imported code.

## 2. titechprabhasolutions: desktop platform and browser workspace

### Brahma Echo
https://github.com/titechprabhasolutions/Brahma-Echo

Stack: Python, Windows system automation, planner/executor/task queue, desktop diagnostics, voice input/output, webcam/screen processing, documents, browser automation, connected services and Android companion. Interesting general patterns: typed action registry, plan/approve/execute/report state machine, tool consent and observability. Multiple action modules invoke OS processes and manage local files; the trust and access model needs independent verification before deployment.

LICENSE: Brahma Source-Available v1. It is NOT a standard open-source grant: rebranding or distributing a modified public product under another identity requires permission. No Echo source or assets are incorporated.

### Brahma AI Evo
https://github.com/titechprabhasolutions/Brahma-Ai-Evo

Related Python desktop code with dynamic skills, a local brain, hardware diagnostics, multi-modal sensorium, automation and additional integrations. Automatic skill creation, runtime evaluation and self-modifying workflows should NOT have broad unsupervised access on Android or a shared remote builder. Isolated evaluation and explicit review would be necessary.

LICENSE: Brahma AI Evo Source-Available Personal Use v2. Explicitly forbids public redistribution, derivatives and commercial incorporation. RESEARCH ONLY: do not copy source, assets or derivative files into a public or commercial Matrix build.

### Brahma Modular Chrome OS
https://github.com/titechprabhasolutions/Brahma-AI---Modular-Chrome-OS

Stack: React, TypeScript, Vite, Three.js, MediaPipe and Chrome Manifest V3. Source license is MIT. Design inspiration: distinct Study/Gaming/Dev workspaces, context-specific workflows, lightweight mobile dashboard alternatives and visible workflow state.

Risk findings: the browser manifest requests content-script and host access on ALL URLs plus proxy, tabs, scripting and network filtering permissions. The front end uses browser storage for state and has HTML injection sinks requiring review. Do not transplant these browser capabilities or direct web-page access privileges to Matrix OS.

Independently implemented adaptation in this review: per-project Develop / Research / Study / Review task modes. These affect planning prompts only, not tools, credentials, files or execution. No browser source, visual assets or risky extension permissions were copied.

Other account repositories identified but not source-audited:
- https://github.com/titechprabhasolutions/Brahma-AI
- https://github.com/titechprabhasolutions/Brahma-AI---Lite

## 3. Capability classification and integration mapping

| Category | Reference | MAXIMUS subsystem | Adoption |
|---|---|---|---|
| Mobile project management | Mobile Harness | Project Studio | Existing workspace + checkpoints maintained |
| Per-project workflow modes | Modular Chrome OS | Project Studio | Independently implemented in this branch |
| Multi-model context | Mobile Harness and desktop memory patterns | Smart Combo + Hindsight | Existing secure model handoff preserved |
| Desktop/remote builds | Mobile Harness and desktop action pipelines | Remote Worker | Separate draft PR #17, gated |
| Offline local models | Portable Local Studio | Local/Remote Model Hub | Next technical milestone |
| Safe tool operations | Desktop planner/action separation | Tool registry | Plan approval, typed permissions, verifiable outputs |
| Audio and vision | Portable Local Studio / desktop modes | Voice and Vision | Future verified provider/local adapters |
| Dashboard and spatial modes | Modular Chrome OS | Compose Matrix UI | Borrow ideas, not browser privileges |
| Continuous monitoring | Desktop queue/status patterns | Policy-controlled background jobs | User approval and observable status required |

## 4. Non-negotiable rules

1. No restricted Brahma Echo/Evo code or artwork imported. Obtain written rights before redistribution or commercial derivatives.
2. Any real code reuse from MIT projects retains original notices. Third-party runtime packages require separate license review.
3. Archives are treated as untrusted input; never execute installers, post-install scripts, downloaded code or prompts merely because they are found in an imported project.
4. PRoot is not a secure sandbox. Rootless Docker is a stepping stone; mutually untrusted code needs disposable VM isolation, bounded resources and no host signing credentials.
5. Never include shared API, provider, remote-worker, Hindsight or administrative secrets in Android binaries or public GitHub.
6. Plan generation never grants permission to edit, build, install, deploy or call destructive tools.
7. Preserve public V1.0.0 and permanent signing identity. New builds require explicit internal versionCode and full upgrade verification.
8. Access to remote model servers requires authenticated TLS, scoped permissions, per-model capability evidence and accurate token accounting.
9. Browser extension permissions such as all-URLs access and proxy changes are intentionally out of scope.
10. CPU/GPU memory limits and battery effects must be displayed before optional on-device model or toolchain download.

## 5. Execution roadmap

P0: Ship task-mode planning preferences only after Android JVM, lint and emulator tests pass; no silent execution or new network access.

P1: Finish Remote Worker PR #17 with real server validation, immutable source bundle hashes, sandbox escape review, short-lived artifacts and signed upgrade tests. Keep unsafe local shell execution disabled.

P1: Prototype Local/Remote Model Hub: GGUF model metadata, download signatures/checksums, RAM/VRAM requirements, runtime capability matrix (chat, vision, audio), offline adapters and user-owned PC/server inference. Use documented local model protocols instead of bundling unrelated third-party executables by default.

P1: Expand consent-gated tool registry and project lifecycle with typed audit events, preview/diff, approvals, rollback and truthful execution evidence. No self-modifying tool installation without independent verification.

P2: Compose-only productivity dashboards, accessible motion and battery-aware ambient animations inspired by upstream UX. Complete localization and offline-first fallback.

## Evidence boundaries

All provided archives were scanned statically; their binaries, installers and code were not executed. The complete account portfolios were inventoried, but repositories not supplied in ZIP form received relevance screening rather than a full security audit. No restricted-source code has been copied. Release state and test outcomes must be rechecked from CI independently of this document.
