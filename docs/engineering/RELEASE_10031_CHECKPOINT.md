# V1.0.0 / internal10031 upgrade preparation

Owner requested an application update after owner merging PR151. Current main9f3a3c1c9aff54d62215e4031fca46b1933d536e and main run39688785503 passed required validation and both emulator suites. PR151 run39686662368 verified460 JVM and57 Android tests with36 genuine emulator screenshots. Existing Stable v1.0.0 remains internal10030.

Candidate10031 increments only internal versionCode for in-place installation over published10030. Public1.0.0, applicationId, signing configuration, Room schema and user preferences remain unchanged. Source scope: versionCode; factual release notes/changelog; resumable checkpoint. No release workflow or quality gate changes.

Use existing release/verify-v1 with exactly this reviewed tree. It builds using repository-managed permanent signing secrets, verifies both APK variants against current public certificates and checks an emulator in-place upgrade/data preservation. This verification branch cannot publish Stable assets.

Signed artifacts, certificate equality, APK hashes and signed in-place-upgrade results are PENDING at this source checkpoint. Do not describe an artifact as upgrade-verified solely because it was uploaded before the upgrade gate. Deliver APKs only after the complete exact-head run succeeds.

Physical ARM64, live user API acceptance, general Pine engine/backtests, dense graph labels/Timeline and FPS profiling remain unverified. No uninstall or data reset is allowed to bypass an upgrade failure.

Owner review applies to the concrete preparation PR and any public Stable replacement after verification. No release replacement is a side effect of this source change.
