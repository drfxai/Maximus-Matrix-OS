# Physical ARM64 build acceptance

The physical acceptance test compiles a newly generated, owner-trusted starter
inside the actual phone PRoot runtime, exports its Debug APK through the app's
export implementation, verifies SHA-256 and asks Android to parse its package.
It fails rather than skips when its prerequisites are absent. An x86 emulator
cannot satisfy this gate. No physical run has been completed by adding the test.

Use a dedicated ARM64 Android 9+ test phone. Install the candidate Debug app and
matching instrumentation APK from the same source checkout. Do not replace or
clear a production installation to make Debug signing work. Install the pinned
toolchain through the app's approved foreground setup flow first. Keep sufficient
free storage and the phone available throughout the build.

From the exact checkout used to build both installed APKs, with ADB authorized:

```bash
export ANDROID_SERIAL=<explicit-test-device-serial>
APPROVE_OWNER_PHONE_BUILD=true bash .github/scripts/verify-physical-phone-build.sh
```

The script saves bounded test output, `result.json` and the exported project APK
under `physical-check/`. The JSON identifies the source commit, Android API,
ARM64 architecture, artifact package and hash. It includes no serial number,
production signing key, provider token or project source. The test removes only
the unique starter workspace it created. It never uploads the evidence itself.

This proves compilation and export only when the test actually passes on hardware.
Separately install and launch the generated APK on the test phone; verify Persian
recognition, spoken replies, five-language layouts, approved Android intents,
Doze behavior and the permanent-key production upgrade. A local tool probe,
successful test compilation, or emulator run is not a substitute for these gates.

Internal candidate build 10021 keeps public V1.0.0. No release should be published
until all required acceptance and signed-upgrade gates have actually passed.

## Signed upgrade verification without publication

The release workflow supports `verify_signed=true` with `publish=false` on main,
plus a dedicated `release/verify-v1` branch trigger. Both run the normal Android
validation suites before building with the permanent signing key, comparing the
published certificate identity and performing the emulator in-place upgrade.
The release publication step is excluded in this mode; the publication script
also requires a separate authorization environment flag. Use the signed APK and
upgrade evidence artifacts for acceptance. Never move `release/validated-v1`
merely to obtain a test build: that branch remains a publication trigger.
