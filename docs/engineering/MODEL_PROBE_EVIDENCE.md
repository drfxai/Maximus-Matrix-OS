# Model test evidence and actual Chat → Pine acceptance

Base main: `2a9d8a0bed50a7ba225204619af1b8f7f33b68d7` (owner-merged PR150). The prior chat-provider-pine checkpoint is historical: PR150 exact-head CI passed 455 JVM and55 Android tests; it is now merged. Public release remains internal10030; this increment does not publish or change identity/version.

## Increment

- Keep dated per-model inference observations across model selection within the same authenticated catalog session. API key/provider/endpoint invalidation and catalog rebinding clear all observations.
- Outcomes are successful, unsuccessful, or inconclusive; account denial/quota/endpoint failures are typed. A catalog entry alone is not inference support, and failed/network tests do not prove permanent model incompatibility.
- Test requests remain separately confirmed,128-output-token bounded and now30-second cancellable. Timeout/network loss is uncertain, never automatically replayed. Expiry during a probe clears VALIDATING without accepting late evidence.
- API controls, in-chat model picker and Smart Combo model cards show dated outcomes. Selected-model status restores only fresh success; historical results do not grant pricing/quota/tool rights. A local expiration wakeup marks visible observations stale without a per-frame chat timer.
- A new Android test seeds a clearly labeled saved chat fixture through the encrypted production repository, uses the actual MainActivity/MatrixScreen navigation and Open in Pine Studio action, checks exact stored source and preservation of prior drafts, and captures the real destination. It invokes no model or account. Existing component fixture remains separate.

## Acceptance and limits

Run ApiCatalogSessionTest, HttpLifecycleTest, full JVM/lint/build and both emulator suites. Genuine captures `pine-chat-route-fixture.png` and `model-probe-fixture.png` are test data and collected with the strict allowlist. No live provider credentials, physical ARM64, FPS profiling, full Pine compiler or strategy backtester is available in this increment. Existing six Galaxy modes, Hub navigation, Chat proportions and operational data stores are preserved. General indicator execution needs an explicitly selected compatible backend; no implementation is fabricated.

No automatic inference testing across the whole catalog, paid fallback, merge, deployment, release signing or publication. Owner review remains required.
