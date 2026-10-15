# Cloudflare Worker staged deployment

The Developer Connections screen now separates updating an existing Worker from creating a starter Worker. The existing Worker flow is read-only until the user approves a specific immutable preview. It uses the source generated for the selected agent purpose, not code supplied by a model during deployment.

1. Enter the exact account and existing Worker name, then inspect. The preview pins the current deployment ID, the single version serving all traffic, encrypted credential revision, generated source, SHA-256, main module, compatibility settings, limits, placement and supported binding names.
2. Review and approve an inactive version upload. This calls the Workers API JSON version endpoint with `deploy=false`. Every binding, including secret bindings, is inherited from the exact active version UUID. No secret value is read or copied into the preview or source. An inactive version may still have a Cloudflare version preview URL according to account settings; the app does not open or invoke it.
3. Review activation separately. It sends one deployment with `strategy: percentage` and the staged version at 100%. Existing routes are not edited.
4. Review rollback separately. It restores the previously active version only while the app can verify that its own activated deployment is still current. The request never uses `force`; Cloudflare can reject a rollback, including after secret changes.

Only stateless ES module Workers with one active version are supported. Durable Objects, containers, assets, migrations, named/lifecycle exports, traffic splits, unknown version fields and unsupported bindings are rejected. The safe default Worker export's cache override is preserved. Script-level schedules, routes, domains, settings and secrets are not edited by this flow. Binding inheritance references the deployed UUID rather than the most recently uploaded version, which may be inactive.

Account/token replacement invalidates the preview. The transport captures the encrypted credential and its decrypted token together, checks the same revision immediately before I/O, and only contacts the fixed HTTPS Cloudflare API origin with redirects disabled. A change after dispatch cannot undo an already sent request. The app checks the remote deployment before each write and after receiving a write response; Cloudflare provides no compare-and-swap deployment operation, so another actor can still race between the final read and write. There is no automatic retry or automatic recovery write.

Known staged and deployment IDs are retained in the screen and in a private non-secret last-write journal before subsequent verification. A post-write drift or verification failure shows an unverified receipt and blocks further actions. A response lost to network failure can leave the outcome unknown: inspect Cloudflare before retrying. The journal contains account/Worker IDs, previous/staged versions, source hash, activation/rollback IDs and a timestamp, never credentials, binding values or source. It is not proof that a Worker was invoked or passed a health check.

New starter creation uses the Workers API POST create endpoint, not the legacy script PUT. Code upload uses the returned immutable Worker ID, so a name conflict has no overwrite fallback. A restricted initial version is explicitly created and deployed only after the new-Worker confirmation. Both the workers.dev endpoint and preview URLs are disabled on the created resource; no route or custom domain is added. Known resource and version IDs are saved in a separate private creation journal. If upload or activation verification fails after resource creation, the UI reports that partial result and its IDs instead of claiming success or automatically deleting/retrying it. The creation endpoint's required permission is Workers Scripts Write for the intended account.

## API references

- [Workers API create Worker](https://developers.cloudflare.com/api/resources/workers/subresources/beta/subresources/workers/methods/create/) — new resource creation and immutable resource IDs.
- [Workers API create version](https://developers.cloudflare.com/api/resources/workers/subresources/beta/subresources/workers/subresources/versions/methods/create/) — JSON modules, `deploy=false`, exact-version `inherit` binding support.
- [Workers API get version](https://developers.cloudflare.com/api/resources/workers/subresources/beta/subresources/workers/subresources/versions/methods/get/) — top-level immutable version configuration.
- [List deployments](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/deployments/methods/list/) and [create deployment](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/deployments/methods/create/) — traffic deployment and optional force semantics.

These schemas were retrieved before implementation. The account must expose the Workers API and the scoped token must authorize the exact account/Worker. The app does not request a global API key or mint an API token from an existing restricted token.

## Validation

The JVM regressions cover pinned binding inheritance, configuration preservation, unknown/stateful rejection, exact-source validation, traffic split rejection, stale session/deployment prevention, separate staging/activation/rollback, post-upload drift receipts and rollback conflict prevention. Android compilation, lint, JVM execution and emulator checks run in repository CI. No live credentials, deployments, production traffic, physical ARM64 build, or live Worker invocation were used for this change.
