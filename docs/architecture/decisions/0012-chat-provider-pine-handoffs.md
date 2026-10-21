# ADR 0012 — Explicit source handoff and account-bound model discovery

Status: proposed in feature PR; source baseline `05a4534549265de4b60f073430089cb6b0d4b188`.

The Chat code card could export Pine but did not open it in the existing studio. Model pickers could offer preset suggestions independently of the entered credential. Hub Operations retained internal navigation state and API Control sections were hidden behind Advanced.

## Decision

- Keep Chat, Pine drafts and provider credentials in their existing stores. An explicit Open in Pine Studio action creates a new private draft, then navigates by its stable ID. Source is inert, never executed as tool instructions. Unreadable existing draft storage blocks import rather than being replaced.
- Bind discovered model evidence to the provider, normalized endpoint and credential revision. Static examples are not actionable account-discovered models. Catalog discovery and an explicitly approved short inference test are separate operations; discovery is not proof of account entitlement, credit or inference success.
- Reject late async results after input changes. Probe authorization identifies the selected target and input revision. No private chat content enters a connection probe, and no automatic paid probe/fallback is introduced.
- Preserve the native Brain graph as home. Make Hub, Control and Operations reachable through existing navigation; do not create a replacement application or independent dashboard fact store.
- A native bounded arithmetic preview may plot explicitly supported whole-script SMA/EMA forms over real exchange candles. Reject unsupported statements instead of drawing unrelated example signals. This is not full Pine compilation, TradingView parity or strategy backtesting. EMA initialization is disclosed.

## Consequences and validation

Existing public version, signing, Room schemas and user preferences remain. New UI is English-first; existing language resources remain. Tests must cover source preservation, corruption, supported/unsupported previews, stale provider results, non-chat models, navigation and keyboard insets. Exact-head CI and real emulator captures are required before merge; authenticated provider and physical-device acceptance remain separate.
