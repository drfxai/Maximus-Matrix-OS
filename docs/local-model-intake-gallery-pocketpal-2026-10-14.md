# Gallery + PocketPal: local-model architecture and secure integration

Review: 2026-10-14. Target: Maximus Matrix OS, public version V1.0.0.

## Source evidence

| Project | Uploaded snapshot | Files | SHA-256 | License |
|---|---|---:|---|---|
| https://github.com/google-ai-edge/gallery | gallery-main.zip | 635 | 9240eefa31f027ffff7195c8ef971c2f965cdfa8e56c8ab43f55ef9256f08e71 | Apache 2.0 |
| https://github.com/a-ghorbani/pocketpal-ai | pocketpal-ai-main.zip | 1,754 | 8ab06a21cc6b31dcf75836dabf9cb3f5883fa56a88efb3ef2c0525b69e032158 | MIT |

The uploaded archives were enumerated and selected implementation files read, without executing any package, code or installer. The reviewed Gradle, downloader and model-capability files matched the corresponding current GitHub main Git blob hashes. This does not guarantee every snapshot file matches current main. Latest main commits at the time: Gallery 8f5f2a0bdb7c81693384dbdecbabec9b1e8d7f4e; PocketPal 7b809ef8358e3c160318bd94101ded1cfa7aa69c. Latest releases: Gallery 1.0.20 and PocketPal v1.19.0. The PocketPal source snapshot's application version is 1.19.1.

## A: Google AI Edge Gallery

Primary stack: native Android Kotlin/Jetpack Compose and LiteRT-LM 0.18.0 with optional MediaPipe tasks, CameraX, media support and Kotlin MCP tools.

High-value architecture:
- Separate model download strategy, installed-state management, runtime lifecycle, streaming response, cancellation and token-count interfaces.
- Model Manager, Hugging Face browser/import, benchmarks and resource reporting.
- Native chat, image understanding, audio transcription, local embedding-powered instant media search and video timestamp finding.
- MCP tools with permission prompts and prompt/session management.

Integration limits:
- Android app minSdk31 (Android 12), targetSdk37. Maximus minSdk26; do NOT add an unconditional LiteRT dependency or change the whole-app minimum API.
- Imported LiteRT files must pass actual runtime and ABI checks; their extension alone is not proof of a compatible model.
- Gallery's manifest requests camera, audio, calendar, media, account/network and service permissions; importing the whole manifest would increase Maximus privileges unnecessarily.
- The reviewed HuggingFaceApiClient supports optional bearer tokens on URLs with standard HTTP connections. Any future Matrix downloader must strictly scope Hugging Face tokens and redirect behavior, not blindly reuse arbitrary URL/token handling.
- Model license and weight redistribution terms are distinct from the Apache 2.0 source license.

Verdict: P1 optional Android-12+ LiteRT-LM backend, P2 embeddings/video/audio. Independently implement rather than bundling Gallery app components.

## B: PocketPal AI

Primary stack: React Native 0.82.1, native llama.rn 0.13.0-rc.7 (llama.cpp, GGUF), ONNX runtime and multiple local speech engines. Source is MIT.

High-value architecture:
- Hugging Face GGUF search, gated model imports, foreground/background download progress, cancel/resume patterns and size estimates.
- Local chat using GGUF and optional hardware-specific GPU/NPU backends.
- Model capability resolver separates DECLARED metadata from LIVE loaded-session capability. A model listing or name is not proof of runtime vision or context size.
- Local and remote OpenAI-compatible model-server support, model lifecycle and native performance benchmarks.
- Offline TTS engines including Kokoro, plus optional connected Pals and online features.

Integration limits:
- PocketPal Android Gradle minSdk24; ARM64 and x86_64 native ABI filters. Maximus supports Android API26+, ARMv7, ARM64 and x86_64; unsupported native architectures need graceful fallback.
- PocketPal release Gradle config falls back to debug signing when permanent credentials are missing. This MUST NOT be imported: Matrix must fail closed and preserve release signing identity.
- Local chat can be offline, but HF model discovery, PalsHub, optional sync and external tools involve network.
- Reusing the React Native application stack inside the existing Kotlin app is unnecessary and could inflate size. Prefer an optional native GGUF bridge and independent Kotlin controls.
- Model weights have separate licenses; do not bundle or redistribute them without review.

Verdict: P1 GGUF native backend, local model inventory/downloads and measured resource management. P2 local speech.

## Capability map

| Subsystem | Gallery | PocketPal | Matrix target |
|---|---|---|---|
| Runtime engine | LiteRT-LM | llama.cpp / GGUF | Optional separate modules, not yet installed |
| Models | LiteRT package manager | GGUF/HF picker | User-owned local model inventory, future verified downloader |
| Execution | Streaming session | Native context/token output | Cancel, lifecycle, effective model capability verification |
| Vision | Ask Image | Model-dependent multimodal | Live-loaded vision probe; UNKNOWN until verified |
| Audio | Audio scribe | Offline TTS | Optional STT/TTS with model/source permission |
| Semantic search | EmbeddingGemma 2 + video | Not assumed | P2 local media search |
| Token measurement | Runtime counters | Completion/context metrics | Estimated and confirmed usage distinguished |
| Cloud | Not needed for model inference | Optional online features | Smart Combo API/local/remote choice, never silent upload |

## Direct changes from this review

Local Models tab in Control Hub implements a strictly offline-oriented, user-approved file INVENTORY, not a runtime:
1. Android OpenDocument picker obtains an explicit read-only SAF URI grant.
2. The first 8 bytes and bounded known file size are checked without unpacking/executing input.
3. GGUF v2/v3 recognized by signature and header; wrong magic/version rejected.
4. LiteRT-LM file extension only is indexed as UNVERIFIED until a runtime can validate it.
5. Limited filename/size/format/URI metadata stored privately; original file is never copied, uploaded or deleted.
6. User can forget a reference and release the persisted read permission.
7. The UI never claims a loaded or runnable model, verified token quota, or audio/vision support.

Known limits: A SAF document may be backed by an online storage provider and unavailable offline; the header check does not verify full content SHA256. There is no built-in model downloader, native GGUF/LiteRT execution, or true inference in this phase.

## Safety and licensing gates

- Source application licenses do not grant rights to distribute arbitrary GGUF, LiteRT or embedding weights. License/provenance review per model is required.
- Hugging Face private tokens belong to the user and must never follow cross-origin redirects; downloaded model size, digest, HTTP ranges, filenames and paths must be validated.
- Show storage/thermal/RAM/VRAM requirements and device ABI before installation.
- Do not open unauthenticated on-device inference servers to public interfaces. Keep endpoint identity, scoped auth and verified HTTPS where remote.
- Do not add Gallery's privileged permissions or PocketPal's debug-sign fallback.
- Do not treat a saved file as validated runtime capabilities; confirm with actual load/inference probe.
- Preserve Matrix public V1.0.0, release keystore and data across upgrades.

## Prioritized implementation roadmap

P0: Verify importer UI, unit/Android tests and SAF behavior; merge only on green CI.
P1: Native runtime interface with load, unload, cancel, streamed tokens, effective context and capability probes.
P1: GGUF llama.cpp Android engine with ABI checks, dynamic loading, RAM estimates and device tests.
P1: Secure HF catalog and checksum-verified resumable downloads using approved device storage budgets.
P1: Separate LiteRT-LM engine on Android 12+ without changing baseline Android 8 support.
P1: Smart Combo local-first routing ONLY to verified live model sessions; Hindsight project continuity remains consent-gated.
P2: Audio transcription/TTS, EmbeddingGemma semantic media and video moment finder.

No third-party source files, weights, binaries, UI artwork or privileged runtime components were copied in this change.
