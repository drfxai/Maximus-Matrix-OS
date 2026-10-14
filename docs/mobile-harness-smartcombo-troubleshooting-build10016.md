# Toolchain Setup + Smart Combo Recovery (V1.0.0 / internal build 10016)

## Reported on an Android device
- Project Studio > Mobile Harness displayed `Cancelled.` and `Linux / PTY Not installed`, `APK Builds Needs Setup` after toolchain installation failed to complete.
- Chat with an NVIDIA NIM model selected showed `No verified free route is ready` while the composer displayed `Used: 0` and an estimated context balance of 8192 tokens.

## Toolchain installation
The original setup service called cancellation in `onDestroy()`, regardless of whether the user pressed Cancel. Android may terminate a foreground service for independent lifecycle and resource-management reasons. The new implementation distinguishes **user-cancelled** and **Android-interrupted** downloads and persists a safe, limited diagnostic reason. Verified archives and partial download segments are retained; **Resume Android build tool setup** resumes with a Content-Range check and verifies the full exact archived SHA-256 before extraction. Cancelling still interrupts and leaves the current environment unactivated. The user must explicitly restart an interrupted install; the app does not auto-download hundreds of megabytes on startup.

What the screenshot does **not** establish: whether the user pressed Cancel, Android killed the service, the network dropped, the download server failed, or the device was out of space. Only a new device attempt and Android logs can identify that cause. Do not claim PRoot/Gradle worked until the device's **Verify tools** succeeds.

Practical steps: update to the newer signed V1.0.0 build, open Project Studio > Mobile Harness, connect to stable Wi-Fi, keep power available and more than 3 GiB internal storage, tap Resume, watch the foreground notification. If it reports Failed rather than Cancelled, send the exact text shown in the app. Network retries do not bypass SHA-256 checks or add arbitrary download hosts.

## Smart Combo
`No verified free route` is **not** proof that the user's NVIDIA NIM API key is invalid or that they have no provider credits. Smart Combo purposely admits only authenticated catalog models that publish an explicit zero text-input price, zero text-output price and trustworthy context limit. Some NVIDIA / Gemini catalogs do not publish verifiable zero-cost pricing or context metadata via `/models`. A saved API key alone is not sufficient evidence, nor is a free-tier promotional allowance. The `8192` token figure in the chat describes *estimated context capacity*, not confirmed remaining free requests or API credits.

Fix: the model catalog now displays non-secret per-provider reasons in Control Hub > API Engine (catalog not reachable/auth issue vs no published zero-cost evidence vs missing context). Chat offers **Refresh** and **Use selected provider** when no free route exists. The second action opens an explicit warning that the chosen NVIDIA/other provider may charge or consume credit, then disables free-only Smart Combo **without sending or replaying a message**. Only a separate later Send actually invokes the chosen model.

Smart Combo does not declare a model free based on a recognizable name, active key or zero used token count. No silent paid fallback or provider credit forecasting is implemented.

## Release discipline
Public version `V1.0.0` is unchanged. Internal build `10016` is used for Android in-place upgrade. Release publishing requires JVM tests/lint/Android emulator and a signed-upgrade gate with the existing permanent keystore. Do not replace `v1.0.0` release assets unless those tests pass. Physical-device toolchain download and actual Gradle build remain necessary for production-readiness proof.
