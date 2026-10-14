# Phone-native Android Debug APK builder: provenance and limitations

Target: MAXIMUS MATRIX OS public version V1.0.0. Builds created from user projects are DEBUG APKs; the application's permanent release keystore is never provided to the phone runtime.

## Pinned native PRoot dependencies

The Android CI build imports exactly four ARM64 ELF files from the unchanged upstream Mobile Harness v1.0.7 online APK after validating the WHOLE APK SHA-256:

- Release: https://github.com/techjarves/Mobile-Harness/releases/tag/v1.0.7
- APK SHA-256: c41795826ae1c9a9afec8b393d15ebd3c934d52b857d66eb62123a1079a98588
- Matching source revision: https://github.com/techjarves/Mobile-Harness/tree/96b1da08d03af4f2d63970a3794c7fda068948c4
- Exact files: libproot.so, libprootloader.so, libtalloc.so, libandroid-shmem.so

PRoot and loader are GPL-2.0. talloc is LGPL-3.0-or-later. libandroid-shmem is BSD-3-Clause. The upstream source repository has Git submodules for PRoot and libandroid-shmem; clone WITH --recursive at the pinned source revision to obtain the full corresponding source. Licenses and notice obligations apply to any redistributed APK; the source archive must be provided with the published binary or a compliant written offer.

The build never copies unrelated application UI, account data, provider secrets, binaries or bundled tools from the upstream application. GitHub Actions verifies a pinned APK checksum, reads only exact native library members, checks ELF ARM64 architecture and includes them with legacy native-executable packaging.

## Explicitly downloadable toolchain on the user's phone

Upstream runtime release: https://github.com/techjarves/Mobile-Harness/releases/tag/runtime-2026.09.4

- Linux core file: pocketdev-core-arm64-2026.09.5.tar.zst
  - 72,185,773 bytes
  - SHA-256 df0cf7251c74f82d424231e3804114a4ca66b16130eea9abab11e220dc7ac012
- Android Gradle/SDK file: pocketdev-android-arm64-2026.09.1.tar.zst
  - 569,652,007 bytes
  - SHA-256 01bea058ebcb17416d1eb08c0211b3782da3228eb3719f2d61dd3ec6

The two pinned releases contain Ubuntu ARM64, Temurin OpenJDK17, Android SDK36, ARM64 AAPT2 35.0.0, Gradle 8.14.3 and an offline Maven repository. Each component and downloaded model/project may have separate licensing and security terms.

The app fetches these releases using HTTPS and restricted redirect hostnames; interrupted downloads are resumed only when Content-Range matches the exact pinned length, and a full SHA-256 is always required. Two-pass bounded tar.zst extraction occurs in private temporary storage. Symlink parents are resolved in guest rootfs space; path traversal, unknown archive types, size overruns and excessive entries are rejected. Staging is activated only after expected files exist.

## User workflow

1. Open Project Studio and select an owner-trusted Gradle Kotlin Android project.
2. In the Mobile Harness card choose Install Android build tools. The user must have an ARM64 Android 9+ device and at least 3 GiB internal free space. Download is about 612 MiB and requires a user-started foreground installation service with progress and cancel.
3. Choose Verify tools. A real PRoot process must pass Java/Gradle/Android SDK/AAPT2 command probes before Build Debug APK becomes enabled.
4. Authorize building this trusted project's source code. Build executes a real offline Gradle assembleDebug inside the private PRoot filesystem with live logs and an explicit stop control.
5. After Gradle success and Android package parse verification, use Save verified APK to export the artifact; the builder re-checks SHA-256 at export.

A successful file download does NOT prove that the PRoot process runs on the user's actual Android build. Foreground operation and a green CI build do NOT certify physical ARM64 execution. Android kernel security, target API policy and RAM/thermal/storage constraints can prevent PRoot on particular devices. Completion must be proven by a physical-device end-to-end install, Gradle build, APK export and install smoke test.

## Important security boundary

PRoot is compatibility software, NOT a security sandbox. Running Gradle scripts can execute arbitrary user code with the application's Android UID and read its private files; do NOT build untrusted internet repositories on-phone. Use an isolated remote VM to compile unknown source. No unrestricted command execution is exposed on the phone's local HTTP gateway, which remains authenticated, read-only and loopback-only.

The app does not place provider, Hindsight, gateway or production APK signing keys inside PRoot. The on-device build output is a project Debug APK, never an update of the signed Maximus application itself.

## Release quality gates

Android unit/lint and emulator tests, supply-chain import tests, stable source code and signature identity, in-place signed upgrade and release SHA checks must pass. The real Android 15 ARM64 execution proof and applicable GPL/LGPL source delivery must be completed before describing the runtime as tested for production use.
