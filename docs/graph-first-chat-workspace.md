# Graph-first home and focused conversation workspace

Public version remains V1.0.0. No signing or release change.

The home assistant defaults to a compact dock: full-width input, actions and a two-line latest response. History, approval explanation and quick actions expand on demand. The node graph owns the remaining screen; selected node IDs, routing and approval dialogs are unchanged. When the IME opens, home telemetry and lobe filters hide, optional details close visually and the dock has a scrollable height cap. The header and question input remain visible.

The Chat workspace uses 4dp outer margins and wider message surfaces. Provider/agent/usage telemetry stays at the top; session actions share one menu and optional configuration has a bounded scroll viewport. The viewport hides while typing. A missing verified free route remains visible and manual paid-provider use keeps its separate cost confirmation.

Actual dispatch telemetry records the provider, model and agent selected for the outgoing request, including automatic Smart Combo routes. Configured selection is separate. Active telemetry clears on completion, failure and cancellation; last dispatch is public metadata held only in memory. No token, endpoint, prompt or response is recorded there.

Final token accounting now keeps response rate-limit evidence with provider/model identity instead of replacing it with the pre-request snapshot. Latest local budget edits survive completion. Provider usage reports and local output estimates have distinct labels. Context capacity/remaining are estimates, not account quota or billing credit. Dispatch estimates use the actual selected route capacity.

Three JVM regressions cover preservation of provider-rate evidence, current budgets and actual-route context estimates. The real keyboard suite adds collapsed graph dominance, expand/collapse and Chat control-toggle assertions without removing any IME checks. Best-effort home/chat screenshots are collected from two fixed paths for visual review. JVM/Android execution and physical-device/large-font/landscape visual acceptance remain pending until independently observed; source checks alone do not establish those outcomes.
