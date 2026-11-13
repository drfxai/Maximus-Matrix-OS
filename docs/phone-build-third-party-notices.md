# Phone build runtime: upstream and redistribution notes

This file supplies the missing target referenced in `.github/scripts/prepare-phone-native.py`.

## Verified source references in the code

- `techjarves/Mobile-Harness` release `v1.0.7` APK: `mobile-harness-online-v1.0.7.apk`
- Expected complete APK SHA-256: `c41795826ae1c9a9afec8b393d15ebd3c934d52b857d66eb62123a1079a98588`
- Extracted ARM64 ELF names: `libproot.so`, `libprootloader.so`, `libtalloc.so`, `libandroid-shmem.so`
- Runtime bundle source: `techjarves/Mobile-Harness` release `runtime-2026.09.4`; exact archive names, sizes and SHA-256 values live in `PhoneRuntimeBundlePins.kt`.

`prepare-phone-native.py` records PRoot as GPL-2.0, talloc as LGPL, and libandroid-shmem as BSD. These are labels present in the repository script, not independent legal verification of the actual extracted binaries. The upstream APK must still be inspected for notices, specific license versions and corresponding sources. Bundling a GPL component may impose additional distribution conditions; repository-level MIT licensing does not override them.

## Release gate

1. Verify immutable upstream artifact checksums and architecture checks without bypassing failures.
2. Document the exact license, copyright holder and corresponding source location for each extracted binary.
3. Verify whether the LGPL/GPL requirements can be met for the chosen packaging and distribution arrangement.
4. Include the required notices and source/offer with shipped artifacts, not only in GitHub.
5. Keep signing keys and device/user credentials out of source and CI logs.

See [third-party notices](../THIRD_PARTY_NOTICES.md) for the broader review.
