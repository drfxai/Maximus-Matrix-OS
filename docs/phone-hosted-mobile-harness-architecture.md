# Phone-hosted Mobile Harness: Android server and execution roadmap

Review date: 2026-10-14. MAXIMUS MATRIX OS public version: V1.0.0, unchanged.
Reference: https://github.com/techjarves/Mobile-Harness (MIT app source; PRoot/toolchain packages have separate licenses).

## Requirement

The user's own Android phone is the server and development workspace host. Neither a purchased VPS nor desktop PC is required for project storage, authenticated local access, and eventually trusted on-device Linux execution. A desktop or server is an optional accelerator.

## Implemented: local Android phone host

- Foreground Android service, started and stopped by the user from Project Studio. Persistent notification with stop action; no boot autostart; service not exported.
- A real TCP server bound to loopback 127.0.0.1:18767. NOT Wi-Fi or 0.0.0.0; it cannot be reached directly from another device.
- Fresh CSPRNG 256-bit in-memory bearer token on each start. Not part of the APK build, repository or persistent Android preferences.
- Authenticated HTTP/1.1 GET-only access, bounded 8 KiB header, 3-second read timeout, two client workers with a bounded queue, no CORS.
- GET /v1/capabilities gives truthful Android server readiness; execution_enabled is false because the native Linux/PTY/Gradle engine is NOT installed.
- GET /v1/projects exposes the names, IDs and file counts of app-owned Project Studio workspaces, only after bearer authentication.
- GET /v1/projects/{UUID}/manifest and /export reveal only a single project explicitly approved for source sharing through the UI.
- Exported source ZIP is capped at 8 MiB. Stop and revoke controls remain user accessible. Source is never uploaded by starting the server.
- No shell endpoints, uploads, app signing keys, provider secrets, model inference or APK builds through this server.

This is a functional local server and local project sharing API. It is not yet the original Mobile Harness full Linux engine. Upstream Mobile Harness APK does not automatically use this proprietary local API without an adapter.

## User workflow

Project Studio -> select a project -> Mobile Harness - Phone Server -> Start phone server.
Same-phone clients may query http://127.0.0.1:18767 with HTTP Authorization header Bearer <temporary token>. User chooses whether to allow the selected project ZIP to be read. The temporary token must not be pasted into chats or public logs. Revoking stops source access, stopping the service drops the token.

The Android app's existing no-cleartext network policy remains intact. This internal raw-socket listener is only for local developer clients; there is intentionally no unsafe LAN mode. Clients on a different computer/phone need a future separate authenticated HTTPS/private VPN transport and explicit approval.

## What is needed to complete real Mobile Harness execution

The reviewed source archive contains a Kotlin/Compose Android app, a foreground runtime service, ARM64 PRoot Ubuntu userspace, native C++/JNI process interface, and optional CLI/Android build toolchains. Upstream requires Android 9+ with ARM64; Matrix's baseline is Android 8+ with additional ABIs. PRoot does NOT provide hostile-code isolation.

Phased engineering:
1. Detect Android version, ARM64 ABI, available storage/RAM and battery thermal constraints; disable unavailable native runtime features on API26-27 and ARMv7.
2. Secure opt-in rootfs and native binary installer with checksums/signatures, license/provenance records, download limits, archive validation and rollback; never run installers from user-imported ZIP files.
3. Native process/PTTY bridge with constrained trusted-project workspace, typed lifecycle and user approval gates. Never grant access to Matrix API keys, Hindsight tokens or permanent APK release signing.
4. Real terminal operations, streaming stdout/stderr, cancellation, background notification, operation logs, user-facing resource limits and checkpoint recovery.
5. Offline Android SDK/Gradle provisioner and debug APK builder after actual on-device compilation tests; do not claim execution from a fake progress UI.
6. Optional trusted local GGUF/LiteRT model runtime and Smart Combo verified-live route; this is a separate feature.
7. Optional secure external access via scoped mutual authentication and TLS/private tunnel, not plaintext open Wi-Fi.

## Verification and release requirements

Tests must cover unauthenticated request denial, wrong Host/Origin, malformed request and traversal, loopback-only binding, explicit project-share consent, ZIP size cap, simultaneous clients, stop-token rotation, foreground service notification and process kill. Emulator build/test and physical ARM64 tests are required before release; native PRoot and SDK tests are separate gates.

Android specialUse foreground-service subtype and store policy eligibility require additional validation. No internet server or automatic third-party process execution is enabled in this branch. Preserve public V1.0.0 and existing signing identity.
