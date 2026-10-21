# Hub and Control navigation increment

## Source audit

At main 05a4534, the bottom Hub button opened ToolsHubScreen. Operations was held as private state inside that screen, so tapping the already-selected Hub destination could leave the user inside Operations. API Control and every other Control section were hidden until Smart Combo's Advanced action; Hub's Control entry was otherwise only a settings icon or a row near the bottom. Existing exact accessibility descriptions (`More`, `Interactive node graph`) are used by acceptance tests and remain compatible.

## Changes

- Operations has an explicit Matrix destination; Hub always returns to the Hub landing page. System Back from Operations or Control returns to Hub, and advanced Control sections first return to Smart Combo.
- Hub exposes Smart Combo / API Control and developer connections as labeled entries near the top, while preserving existing utilities and credential-state wording.
- Control sections remain directly accessible from the first screen in a horizontally scrollable, native section bar. The Hub return action is explicit.
- Operations shows saved configuration counts with UNKNOWN/ERROR when evidence is absent or invalid. Counts do not imply live workers. Evidence colors distinguish errors, stale observations and unavailable data; existing command guards remain unchanged.
- Galaxy layout options expose the selected mode to accessibility services. The default Brain camera, geometry, palette, graph gestures and six existing layouts are preserved.
- Chat → Pine navigation stores the source first and opens the returned durable draft only after success; failures show an explicit message.

## Verification and limits

`git diff --check` passed. OperationalAcceptanceTest.hubReselectionExitsOperationsAndControlSectionsAreImmediatelyReachable covers Hub → Operations → Hub and direct access to API Control → Smart Combo → Hub using actual app routes. It has not been executed locally: no Gradle installation, SDK or local.properties is present in this workspace. Existing OperationalAcceptanceTest and KeyboardLayoutTest remain required regression checks. Source audit confirms shared canonical graph preparation, selection, filters, search, fitting, relationships and committed-event Timeline already exist; this increment does not claim missing runtime or physical-device evidence has been supplied. Genuine emulator screenshots and performance verification must be collected by the parent release workflow.

No provider credential, application identity, database schema, release version, signing configuration, deployment or published artifact changes are made by these navigation edits.

## Handoff

Changed screens: MatrixScreen, ToolsHubScreen, ControlHubScreen, OperationsScreen, MatrixGraphScreen. Extended OperationalAcceptanceTest with the navigation regression. Parent owns ProviderChatScreen, Pine source storage/import, PineStudioScreen and release validation. Run instrumentation including the new regression, capture Hub/Control and five Operations panels, and check expanded-font and IME layouts before acceptance.
