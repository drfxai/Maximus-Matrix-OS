# Third-party components and license review

MAXIMUS MATRIX OS uses independently licensed dependencies, tools and runtime assets. The proposed MIT license in this repository applies **only to material for which DrFXAi has the necessary licensing rights**. It does not relicense upstream code, libraries, binaries, data, fonts, artwork or external services. Before merging a repository-wide license, the owner must inspect the full dependency graph and distribution artifacts, resolve every attribution and source-offer obligation, and verify ownership of bundled media.

## Android dependencies declared in `app/build.gradle.kts`

- AndroidX Activity, Compose, Material, Lifecycle, WorkManager, Room and Android test libraries.
- Kotlin coroutines and Kotlin Compose/KSP tooling.
- Apache Commons Compress, junrar and zstd-jni, used in the optional phone runtime installer.
- JUnit, JSON, AndroidX Test and UI Automator development/test dependencies.

Versions are pinned in the Gradle build. This list identifies dependencies but **does not certify** the individual artifacts' legal notices. A complete license inventory/SBOM and attribution check against resolved versions remains required before redistributed releases.

## Native and external phone runtime

- `.github/scripts/prepare-phone-native.py` downloads a pinned, SHA-256-checked upstream APK from `techjarves/Mobile-Harness` release `v1.0.7`, and extracts `libproot.so`, `libprootloader.so`, `libtalloc.so`, and `libandroid-shmem.so` for ARM64.
- The upstream preparation script labels PRoot GPL-2.0, talloc LGPL, and libandroid-shmem BSD. Those are **upstream labels**, not a completed component-by-component legal audit. Keep the upstream license texts, copyright notices and any required corresponding-source offer with any redistributed binaries; resolve GPL/LGPL obligations with legal review.
- `PhoneRuntimeBundlePins.kt` references checksum-verified `pocketdev-core-arm64-2026.09.5.tar.zst` and `pocketdev-android-arm64-2026.09.1.tar.zst` from `techjarves/Mobile-Harness` release `runtime-2026.09.4`. Inspect license terms and source/redistribution requirements for **every** payload before distributing it.
- Android SDK/Gradle components and the system image have independent upstream terms; their inclusion does not make them MIT licensed.

## Service and content boundaries

Any model provider, market/news API, payment provider or other integration has independent contractual terms. No use of this repository grants provider credentials or permission to republish licensed feeds. App screenshots, logos, and media need independent ownership/license verification before a broad license grant.

## Audit checklist before merge / distribution

- [ ] Confirm DrFXAi has the right to license all original code and media (including contributions).
- [ ] Generate a resolved Android dependency license inventory; capture specific versions, notices and license texts.
- [ ] Review the exact upstream release artifacts and GPL/LGPL/BSD obligations for PRoot and runtime bundles.
- [ ] Include attribution notices and corresponding source or offers where required.
- [ ] Check downloaded fixtures and externally hosted content for nonredistributable material.
- [ ] Have the owner approve the legal impact of allowing commercial use and redistribution under MIT.

Related native details: [phone build notices](docs/phone-build-third-party-notices.md).
