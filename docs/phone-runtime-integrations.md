# Phone development runtime and native plugin store

## Scope and readiness

Public version remains V1.0.0 and the permanent release signing configuration is unchanged. This change implements phone-runtime and integration functionality in the existing screens. The application UI redesign is explicitly deferred until the owner consultation.

The implementation is not physical-device acceptance. A real ARM64 Android 9+ device must still pass the native PRoot/Gradle build/export/install gate and Linux command tests. An x86 emulator, unit test or successful archive download cannot establish this.

## User workflow

1. Open Project Studio → Phone Harness → Plugin Store. Review and install **On-phone APK builder** and **Linux workspace**. These activate native modules already included in the signed APK, not downloaded executable plugins.
2. Install the pinned runtime (about 612 MiB downloaded; at least 3 GiB free storage required). Downloaded archives retain resume state and must pass their pinned SHA-256 checks.
3. Choose **Verify tools**; this actually executes the phone's Java/Gradle/AAPT2. Create a Studio Android starter, review **Build Debug APK**, and build locally. After success, **Save verified APK** exports the actual parsed APK and rechecks its digest. Production signing keys are never used for project Debug builds.
4. In **Linux workspace**, review a noninteractive command and choose **Review and run**. For example: `pwd; uname -m; cat /etc/os-release`. Inspect real output and exit code. **Stop** or the notification cancels the session. Output is bounded to 32 KiB, there is no stdin/PTY, and each session lasts at most 15 minutes. This is a command workspace, not a 24/7 daemon host.
5. Commands start in `/root/workspace`, a bind of persistent app-private `phone-linux-workspace` outside the replaceable toolchain. Files in this workspace survive runtime replacement. Other guest directories belong to the installed toolchain. No host API credentials are injected into environment variables.
6. Open Agents → Developer connections. In Plugin Store, install **GitHub projects** and **Cloudflare Workers** after permission review. Enter your own restricted service token and verify it. GitHub supports source reads, feature-branch writes and reviewed draft pull requests; Cloudflare supports Worker discovery and reviewed deployments through the existing clients. Installing a native plugin does not connect or authenticate an account.
7. Removing a plugin blocks subsequent requests. Removing GitHub/Cloudflare deletes that service's encrypted token. Removing a local execution plugin cancels its active operation at the next check. Already submitted remote operations cannot be undone by removal.

## Runtime ownership

Installation, tool verification, APK builds and manual Linux commands share a process-wide exclusive lease. A toolchain cannot be replaced while a guest process is active. Stale cleanup cannot release another operation's lease. PRoot `--kill-on-exit` is present in the pinned binary and is enabled for build and shell processes so child workloads do not silently remain after normal session termination.

PRoot is Linux userspace on Android's kernel under the application's UID. It is not Android root, a VM or a security sandbox. Trusted manual commands and Gradle scripts can access the app's private data; only owner-trusted code should execute here. The manual command service is not exported, has a foreground notification and is not exposed through the loopback HTTP gateway or model/agent tools. Android may still interrupt foreground work; no continuous availability is claimed.

## Native catalog boundary

The catalog has four fixed modules: GitHub, Cloudflare, APK builder and Linux. Install records contain the exact reviewed ID, version and permission fingerprint. Unknown IDs, corrupt registry files and mismatched fingerprints grant no permissions. Installation is atomic and shared between screens; manifest changes require new review. SHA-256 identifies the reviewed built-in manifest, not an independent publisher signature. Arbitrary marketplace packages, remote MCP plugins and downloaded code execution are outside this release.

## Validation

Pure Kotlin tests cover exclusive runtime ownership, concurrent acquisition, stale cleanup, command bounds, unbounded-line output protection, plugin consent, persisted revocation, corrupt state and manifest changes. Existing native provenance and localization checks remain required. Android compilation/lint, emulator UI tests and physical ARM64 acceptance are separate gates.
