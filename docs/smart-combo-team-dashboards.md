# Smart Combo and scheduled-team dashboards

The API control dashboard places session tokens, estimated context remaining,
provider rate-window snapshot and user-defined local token budget in four distinct
tiles. None of these values is used to infer account credit. Unknown provider
limits remain unknown; snapshots include their observed timestamp and can be
stale. Session totals may mix provider-reported usage with local estimates.

The configured provider/model and selected persona remain visible. A separate
in-use section reads the actual request dispatch telemetry; automatic routing can
choose a different provider. Catalog-verified zero-price routes and configured
provider account counts are shown independently from runtime activity.
Existing free-only admission, refresh, quota and budget controls are preserved.

The team command center distinguishes observed RUNNING WorkManager jobs from
enabled schedules, recent healthy report evidence and schedules needing attention.
WorkManager is best effort under Android power, connectivity and thermal policy;
this display does not promise continuously running inference or cloud workers.
The setup form can collapse while run, pause/resume and deletion controls remain
available for each team. Controls wrap at narrow widths.

All new labels use five Android resource catalogs. Dashboard colors use the
application theme; state color and context pressure animate only when data changes.
The API control page uses an 8dp horizontal inset. No credential is rendered by
these dashboard components.

Validation: run `.github/scripts/check-ui-locales.py` for catalog parity and
resource validation. Android CI must additionally compile, lint and execute its
instrumentation suite. Physical phone visual verification and provider traffic
are separate acceptance checks; local source validation does not establish them.
