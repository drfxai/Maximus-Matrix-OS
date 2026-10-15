-- Cloudflare D1 migration. Apply explicitly before deploying the opt-in cloud supervisor.
-- No provider keys, prompts, model transcripts or private source files are stored.
CREATE TABLE IF NOT EXISTS agent_teams (
  id TEXT PRIMARY KEY,
  job TEXT NOT NULL CHECK (job = 'GITHUB_RELEASES'),
  repository TEXT NOT NULL,
  period_minutes INTEGER NOT NULL CHECK (period_minutes IN (15, 180, 1440)),
  enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1))
);

CREATE TABLE IF NOT EXISTS agent_reports (
  team_id TEXT NOT NULL REFERENCES agent_teams(id) ON DELETE CASCADE,
  slot INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('RUNNING', 'VERIFIED', 'UNAVAILABLE')),
  summary TEXT NOT NULL,
  evidence INTEGER NOT NULL DEFAULT 0,
  url TEXT NOT NULL DEFAULT '',
  observed_at INTEGER NOT NULL,
  PRIMARY KEY (team_id, slot)
);
CREATE INDEX IF NOT EXISTS idx_agent_reports_latest ON agent_reports(observed_at DESC);
