# Brain, Chat, Pine and Smart Combo debug checkpoint

Base: `main` at `bce8af722f6008d5e3da44ab7f381448afe537f8`.
Branch: `codex/brain-chat-pine-combo-debug`; PR [#153](https://github.com/drfxai/Maximus-Matrix-OS/pull/153).
Public version and internal version code are unchanged by this branch.

## Corrected behavior

- Brain text input is not tagged as speech merely because spoken replies are enabled. The composer retains text while a device action is running or awaiting confirmation.
- Chat search keeps its own visible-message list and stops scrolling to hidden messages; closing search returns to the latest conversation.
- Pine market requests end at the last completed candle. Response parsing discards the current open interval before limiting history to 200 candles. Exchange responses are bounded while reading.
- Smart Combo search includes the provider name. Model names alone no longer imply code, vision or audio input support; the audio group reports unavailable capability evidence.
- Brain graph label placement avoids visible node and label rectangles and reserves the camera toolbar and footer bands.

## Verification and limits

Focused unit tests cover Pine interval and history boundaries plus Smart Combo category/search behavior. Exact-head Android CI, both emulator suites and screenshot review must be recorded from the final commit before merge. Real provider accounts, full Pine execution/backtesting and physical ARM64 acceptance are outside this code change and remain unverified.

The last published Stable v1.0.0 APK is internal code 10030. Main contains candidate 10031; signed verification and emulator in-place upgrade passed on `release/verify-v1` run 39691028350. This PR does not publish release assets.
