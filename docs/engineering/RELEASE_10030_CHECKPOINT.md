# V1.0.0 / internal10030 release preparation

Owner requested an app update after owner integration of PR146. Main `d0024708b70d01cb0a69400cafee8e12f8e9ac0f` is the source baseline. Main CI629/run39564373447 and PR CI628/run39562191606 passed validation and both emulator suites. The integrated suite contains436 JVM and51 Android tests; real emulator screenshots were reviewed and delivered.

Existing Stable v1.0.0 is internal10029, validated source d13a199ed95cb5ad232d1782acae91445ebc4652. Proposed internal10030 is required because the existing upgrade gate rejects a reused versionCode. Public versionName stays1.0.0; package, Room schema and permanent signing configuration remain unchanged.

Preparation branch: release/prepare-10030. Existing release/verify-v1 lane builds with repository-managed signing secrets, compares currently published APKs and tests an in-place upgrade. A branch push cannot publish Stable assets. No workflow or signing-policy change is proposed.

Signed APKs, certificate comparison, emulator in-place upgrade/data-preservation evidence and final SHA256 hashes are PENDING at this checkpoint. Inspect the exact verification run before recommending installation/publication. Identity7 and publication5 policy regressions and shell/diff checks passed locally.

Release notes describe the integrated Smart Combo, Galaxy, Operations and developer recovery changes. Physical ARM64, live in-app account acceptance, sparse Timeline/dense graph labels and exact comparison with original approved concept binaries remain outstanding. Never uninstall the existing app or reset user data to bypass an upgrade failure.

Owner approval remains required for the preparation PR merge and Stable asset replacement. Prepare concrete verified signed APKs before requesting that final approval. No release replacement is performed by this source change.

```json
{"schema":1,"task":"V1.0.0 internal10030 upgrade","state":"PREPARING_SIGNED_VERIFICATION","base_sha":"d0024708b70d01cb0a69400cafee8e12f8e9ac0f","branch":"release/prepare-10030","published_code":10029,"candidate_code":10030,"version_name":"1.0.0","next_action":"Inspect preparation PR and release/verify-v1 exact-head CI, certificate and in-place upgrade evidence; deliver verified APKs before owner publication approval","publish":"NOT_PERFORMED"}
```
