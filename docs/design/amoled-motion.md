# AMOLED motion workspace

The approved direction uses a pure #000000 canvas, visible but limited navy #142C4C cards, white #FAFAFC text, gold #F4C35A confirmation/enabled indicators, orange #FF8D38 activity and red #F05262 warnings/off switches. Light mode remains an explicit user preference.

## Interaction contract

- Provider selection animates surface colors. Selecting a provider is not proof of an authenticated connection. The existing model/key configuration remains available.
- Context occupancy uses a 270-degree gauge with a smooth transition between observed readings, a numeric remaining-context label and an accessible range. The warning color starts at 85% context occupancy. Unknown model capacity remains unknown.
- Metric changes crossfade instead of manufacturing ticking numbers. Account credit, local budget, context capacity and rate snapshots retain separate labels.
- Reactions use persisted message feedback with press/release spring scaling and tactile feedback. Copy confirmation only follows the existing copy action.
- Studio shortcuts have subtle press/release feedback. Destination transitions retain the existing fade; toggles use the native switch animation and checked accessibility state.
- Infinite activity is shown only while work is actually generating. Native Compose animation timing observes system animator duration settings; no custom perpetual rendering loop was added.
- Empty teams, missing runtimes and disconnected services remain empty/missing/disconnected. The illustrative team and completed pipeline in the design board are not seeded as product data.

## Evidence

The keyboard instrumentation suite captures home, chat, Studio overview and Agents teams. These real emulator screenshots must be compared with the approved reference before claiming visual parity. Differences in dynamic content and inactive/empty states are expected; no physical-device or animation-performance acceptance is implied by a successful compile.
