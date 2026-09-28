# Neural workspace — Android 1.0.1

The home screen follows the supplied workspace references: near-black canvas,
small colored spheres, fine links, a compact toolbar, inspector and filters,
purple engine ring, and a bottom mission command dock. On narrow screens the
inspector and filters open from the toolbar; landscape/tablet layouts show both
side panels. The video presenter overlay is not part of the application.

## Interaction

- Drag one finger: orbit around camera X/Y axes.
- Twist two fingers: roll around camera Z.
- Pinch: zoom.
- Tap a node: highlight its neighbors and pause automatic orbit.
- Tap empty space or clear focus: release focus.
- Fit or double-tap: reset orientation, zoom, and focus.
- Pause/play: control the default horizontal orbit (one turn in about 140 seconds).
- Search and inspector list: select any module without needing to hit a sphere.
- Filters: toggle module groups; Show all restores them.
- Cluster spread and link visibility adjust presentation, not engine behavior.

Touch suspends automatic movement, which resumes after 2.2 seconds of inactivity.
A focused node or open panel holds the view stationary. Animation follows Android
lifecycle STARTED and uses elapsed frame time. Quaternions avoid gimbal lock and
keep X/Y/Z controls consistent after mixed gestures. Rendering, selection, and
sphere sizes use the same perspective camera; modules are drawn back to front.

The 33 application modules and their declared relationships remain the source of
node counts. Ambient mesh particles are decorative, can be disabled in Display,
and are excluded from module/connection counts. No backend records are invented
to match the reference's larger dataset. The light theme uses a pale canvas,
darker category colors, dark labels, and white panels.

The existing mission runner, voice-command confirmation, policy checks, chat,
voice messages, attachments, and speech replies remain available.

## Verification

GraphSpaceTest covers independent X/Y/Z quarter turns, 10,000 mixed rotations,
normalization and distance preservation, portrait/landscape fit across orientations,
and perspective depth. The Android CI workflow runs the full JVM test suite,
builds the ARM64 APK, verifies the signature, and publishes its checksum.

Device acceptance checks: portrait/landscape layout; one-finger orbit; two-finger
roll/pinch; hit testing after rotation; focus pause/resume; keyboard in mission
composer; TalkBack inspector selection; light/dark theme; background/resume.
These require a device or emulator and are not implied by a successful CI build.
