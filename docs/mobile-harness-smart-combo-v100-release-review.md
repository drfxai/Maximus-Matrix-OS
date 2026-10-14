# Mobile Harness phone-host and Smart Combo V1.0.0 build 10014

Review date 2026-10-14. Public versionName remains 1.0.0; versionCode increases from 10013 to 10014 for a signed in-place upgrade from published 10012.

## What actually works in this change

1. Project Studio phone-host panel is redesigned with a responsive no-animation-heavy hero, live RUNNING/STOPPED state, server start/stop, per-project approval/revocation, ephemeral token copy, capabilities and Android hardware/runtime-readiness check.
2. The real Android foreground HTTP server from the already-merged phone-host PR is preserved, localhost-only (127.0.0.1:18767), authenticated, GET-only, and bounded. It can enumerate app-created workspaces and export exactly one explicitly approved project ZIP. There are no unauthenticated network ports or command endpoints.
3. Device inspector reads actual Android API/ABI, RAM class and free storage, and checks whether any local app-owned PRoot executable and rootfs filesystem markers exist. It NEVER claims that mere files equal a verified working Linux runtime.
4. On-device Android instrumentation checks actual TCP endpoint authentication, per-project authorization, capability honesty, ZIP export/revoke and hostile Origin denial. Existing JVM route-policy tests remain.
5. Smart Combo adds process-local per-route health/circuit breaking. A failed dispatched request remains uncertain in the quota ledger and is NOT automatically retried. Later user-initiated requests can choose another authenticated verified-free route while the failed route cools down. Successful results clear failure state; explicit cancellation is not counted as a provider failure.
6. Smart Combo UI lists up to eight provider-authenticated zero-price catalog routes, with clear disclosure that catalog price is not a live remaining provider quota or credit balance.
7. No shared API keys, provider secrets, Hindsight credentials, PRoot binaries or release signing materials are introduced to the APK or git history.

## Known incomplete components (do not describe as completed)

- A genuine ARM64 PRoot/Linux kernel-compatibility userspace, interactive PTY terminal, verified rootfs/toolchain installer, locally executed Gradle builds and model inference engine are NOT installed in this release. Project Studio explicitly labels these as unavailable.
- Phone-host protocol is not a drop-in API of upstream techjarves/Mobile-Harness. Integration of the actual runtime remains a separately gated security/dependency project.
- 127.0.0.1 is only reachable on the same Android phone. An external device needs explicit secured TLS/private tunnel, authentication and user consent; not part of this release.
- Smart Combo does not hold a transactional cloud-wide quota ledger. Provider limit headers are only authoritative when an adapter supplies them; missing remaining quota must remain UNKNOWN.
- No automated paid-model fallback or retry of potentially dispatched requests. Actual local GGUF/LiteRT engine must be connected and tested before becoming a Smart Combo route.
- Hindsight production bank and live provider integrations remain subject to user-owned credentials and external endpoint tests.

## Security and QA criteria

- Treat imported project ZIPs as untrusted data; the on-device HTTP server has no shell command route and exports nothing without a distinct project approval.
- No global cleartext/HTTP listening on LAN, no boot autostart, and no secrets from Android app storage allowed in the upcoming PRoot environment.
- Monitor Android foreground-service restrictions and Play special-use policy before broad distribution.
- Run JVM tests, lint, native Android debug compilation, and emulator instrumentation (including PhoneHarnessIntegrationTest) before new release.
- Update stable V1.0.0 using existing permanent signing key only; verify same application ID, signature, internal monotonic versionCode, user-data-preserving in-place install, APK SHA-256 and release link provenance.
- Do NOT assert an update has been published until its release job and remote metadata are verified.

## Next higher-complexity milestone

The user wants the phone itself to run the Mobile Harness execution stack. To achieve it, independently review and package ARM64 PRoot and the Ubuntu runtime as optional integrity-pinned components, under each third-party license; implement Android native spawn/PTY transport, lifecycle logs and cancel, explicit trusted-project warnings and memory/storage gating. Prove actual on-device Node/Gradle/Android build execution on ARM64 API28+ physical hardware. PRoot is not a hostile-code security boundary. Only after these live gates pass should the UI expose "Terminal ready" or "Build APK on this phone". A local model Smart Combo route similarly requires real GGUF/LiteRT inference readiness and session-capability proof.
