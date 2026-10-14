# Upstream repository portfolio classification (2026-10-14)

This is an inventory and triage of public repositories. Names/categories are not proofs of production readiness. Only the four supplied ZIPs received static file-tree and selected-code review; other repositories require separate source, license and security audits before integration.

## techjarves (25)

| Repository | Category | Priority for MAXIMUS | Decision |
|---|---|---|---|
| Portable-AI-USB | Portable offline AI stack | P2 | Archived; study offline package behavior |
| USB-Uncensored-LLM | Offline LLM binaries | P2 | Archived; do not bundle binaries |
| ClaudeCode-Portable | Portable desktop developer tools | P2 | Study execution interoperability |
| Portable-Local-Studio | Local multimodal AI and model management | **P1** | Evaluate GGUF, image, STT and TTS server interfaces |
| Uncensored-Local-AI-Multiplatform | Cross-platform local inference | **P1** | Evaluate local model UX and capabilities |
| termux-hacklab | Android terminal environment | P2 | Research only; do not run installers |
| Mobile-Harness | Mobile Android development workspace | **P0** | Audited snapshot; selectively adopt design patterns |
| Hermes-USB-Portable | Portable local assistant | P2 | Future local runtime review |
| Linux-on-Samsung | Android/Linux userspace | P2 | Document phone-specific limitations |
| mobile-server | Mobile/remote networking | P1 | Separate security and authentication review |
| Openclaw-Termux-NoRoot | Android terminal automation | P2 | Experimental trusted-device research |
| Portable-Diffusion | Offline image generation | P2 | Archived; model metadata research |
| OpenClaw-USB-Portable | Portable assistant tools | P2 | Research, do not ingest binaries |
| ubuntu-on-android | ARM64 Android Linux | P2 | Trusted-code runtime comparison |
| Odysseus-Portable | Portable developer environment | P3 | Evaluate upon demand |
| open-claude-code-termux | Android terminal coding client | P2 | Evaluate compatibility separately |
| DroidDesk-Enhanced | Android/desktop integration | P2 | Review transport and access controls |
| Local-Hermes-Portable | Local portable assistant | P2 | Offline model ecosystem |
| Local-Browser-AI | Browser-local AI | P2 | Browser security audit |
| techjarves | Account profile/documentation | Reference | No package dependency |
| Deepseek-Harness-Portable | Portable model/client runtime | P2 | Provider adapter research |
| kali-android-autoinstaller | Android Linux/bootstrap | P3 | Explicitly no automatic execution |
| NavigationDrawer | Android UI component | P3 | Check license and SDK compatibility |
| SlideRecycler | Android UI component | P3 | Check license and SDK compatibility |
| local-llm | Local LLM experiments | P2 | Model runner comparison |

## titechprabhasolutions (5)

| Repository | Category | Priority | License and integration decision |
|---|---|---|---|
| Brahma-Echo | Desktop tool orchestration | P1 architecture | Source-available, no rebranding; do not reuse files |
| Brahma-AI---Lite | Desktop assistant | P3 | License and code not yet audited |
| Brahma-AI | Desktop assistant | P3 | License and code not yet audited |
| Brahma-Ai-Evo | Desktop multimodal/dynamic skills | P1 research | Personal/non-commercial; no distribution; do not reuse files |
| Brahma-AI---Modular-Chrome-OS | Browser workspace UX | P1 design | MIT; use ideas, exclude invasive extension permissions |

## Workflow for future contributed links or ZIPs

1. Record exact URL, upload filename, SHA-256, archive entry count and declared version/tag.
2. Enumerate files without extraction or execution; reject dangerous traversal and symlink entries for actual import.
3. Read LICENSE and third-party notices before source adaptation. If terms prohibit distribution or commercial use, restrict to observations and independent implementations.
4. Triage dependencies and privileges: external network, shell commands, permission escalation, API keys, cloud account use, background jobs, camera/microphone, automatic updates.
5. Assign one of: immediate safe feature; opt-in experimental feature; reference-only design; reject.
6. Write independent Kotlin/Python implementation with typed policies, tests and explicit approval boundaries.
7. Open a scoped PR, require green JVM/lint/emulator CI, and do not publish until signing/upgrade verification.
8. Preserve existing V1.0.0 public version and all release identity constraints.
