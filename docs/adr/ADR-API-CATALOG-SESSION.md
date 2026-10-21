# Credential-bound model selection and explicit inference verification

Status: Accepted for this increment.

The picker previously initialized from static model suggestions, accepted arbitrary IDs,
classified most `/models` entries as chat models, and sent an unconfirmed inference request
as part of catalog discovery. A delayed response could update a different provider session.

The authenticated catalog is now a process-local projection bound to provider, normalized
endpoint and the existing encrypted-credential fingerprint. A revision invalidates pending
publication and probe approval on input/provider/model changes. Known non-chat endpoint
families and explicit incompatible task/endpoint metadata are filtered. Remaining entries
are catalog candidates; actual compatibility is verified per selected model with a separate
user-approved, 128-output-token maximum inference test. A failed test does not erase the
catalog or assert that authentication/quota/outage errors prove a model unsupported.

Manual dispatch rechecks the current binding immediately before HTTP. Probe dispatch and
completion check the exact approved revision and saved credential identity. Completed probe
usage is accounted even if its UI evidence has become stale. No automatic retries or paid
fallback are introduced. Existing verified-free Smart Combo catalogs remain independent.

The session expires after five minutes and rejects future timestamps. Static suggestions
are never selectable. Credentials and saved model preferences remain in their existing
secure store; catalogs are not migrated or persisted. Consequently startup/provider switching
requires a fresh catalog GET, including offline restart. Persisting encrypted scoped catalogs
may be considered separately if offline model selection is required. Missing catalog support
is reported rather than silently substituting a hard-coded model.

Validation: ApiCatalogSessionTest covers scope, credential rotation, stale publication,
selection approval revocation, expiration/future timestamps and unverified suggestions.
CatalogChatPolicyTest covers mixed endpoint families, explicit metadata and unknown candidates.
Android compilation and UI execution require CI/emulator validation; local SDK is unavailable.
