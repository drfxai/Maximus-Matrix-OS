import test from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
if (!globalThis.crypto) Object.defineProperty(globalThis, 'crypto', { value: webcrypto });
import worker, { validateTeam, releaseSource, collectRelease, executeScheduled } from '../agent-team-supervisor.mjs';

const secret = 'test-only-private-cloud-monitor-token-0123456789';
const proposal = { job: 'GITHUB_RELEASES', repository: 'drfxai/Maximus-Matrix-OS', periodMinutes: 180 };
const team = { id: '00112233-4455-6677-8899-aabbccddeeff', job: proposal.job,
  repository: proposal.repository, period_minutes: 180 };

class Db {
  constructor() { this.teams = []; this.reports = []; }
  prepare(sql) {
    const db = this;
    return {
      args: [],
      bind(...args) { this.args = args; return this; },
      async first() { if (sql.startsWith('SELECT COUNT')) return { count: db.teams.length }; throw Error('unexpected SQL'); },
      async all() {
        if (sql.includes('FROM agent_teams')) return { results: db.teams.slice(0, 8) };
        if (sql.includes('FROM agent_reports')) return { results: db.reports.slice(0, 30) };
        throw Error('unexpected SQL');
      },
      async run() {
        const a = this.args;
        if (sql.startsWith('INSERT OR IGNORE INTO agent_reports')) {
          if (db.reports.some(r => r.team_id === a[0] && r.slot === a[1])) return { meta: { changes: 0 } };
          db.reports.push({ team_id: a[0], slot: a[1], status: a[2], summary: a[3], evidence: a[4], observed_at: a[5] });
          return { meta: { changes: 1 } };
        }
        if (sql.startsWith('UPDATE agent_reports')) {
          const row = db.reports.find(r => r.team_id === a[5] && r.slot === a[6]);
          Object.assign(row, { status: a[0], summary: a[1], evidence: a[2], url: a[3], observed_at: a[4] });
          return { meta: { changes: 1 } };
        }
        if (sql.startsWith('INSERT INTO agent_teams')) {
          db.teams.push({ id: a[0], job: a[1], repository: a[2], period_minutes: a[3], enabled: 1 });
          return { meta: { changes: 1 } };
        }
        if (sql.startsWith('DELETE FROM agent_reports')) { db.reports = db.reports.filter(r => r.observed_at >= a[0]); return { meta: { changes: 0 } }; }
        throw Error('unexpected SQL');
      }
    };
  }
}
function req(path, method = 'GET', body, token = secret, headers = {}) {
  return new Request('https://agent.example' + path, {
    method,
    headers: { authorization: 'Bearer ' + token, ...(body ? { 'content-type': 'application/json' } : {}), ...headers },
    ...(body ? { body: typeof body === 'string' ? body : JSON.stringify(body) } : {})
  });
}

test('strict allowlist prevents arbitrary jobs and arbitrary network endpoints', () => {
  assert.deepEqual(validateTeam(proposal), proposal);
  for (const v of [{ ...proposal, job: 'SHELL' }, { ...proposal, repository: '127.0.0.1/internal' },
    { ...proposal, repository: '../other' }, { ...proposal, periodMinutes: 1 },
    { ...proposal, apiKey: 'secret' }]) assert.equal(validateTeam(v), null);
  assert.equal(releaseSource(proposal.repository), 'https://api.github.com/repos/drfxai/Maximus-Matrix-OS/releases/latest');
});

test('source collector validates publisher URL and never fabricates unavailable evidence', async () => {
  const verified = await collectRelease(team, async (url, options) => {
    assert.equal(url, releaseSource(team.repository));
    assert.equal(options.redirect, 'manual');
    return Response.json({ tag_name: 'v1.0.0', html_url: 'https://github.com/drfxai/Maximus-Matrix-OS/releases/tag/v1.0.0' });
  });
  assert.equal(verified.status, 'VERIFIED');
  assert.equal(verified.evidence, 1);
  const spoofed = await collectRelease(team, async () => Response.json({ tag_name: 'v1', html_url: 'https://attacker.example/tag/v1' }));
  assert.equal(spoofed.status, 'UNAVAILABLE');
  assert.equal(spoofed.evidence, 0);
  assert.equal((await collectRelease(team, async () => new Response(null, {status:404}))).status, 'UNAVAILABLE');
});

test('D1 per-team time-slot lease makes duplicate scheduled deliveries idempotent', async () => {
  const db = new Db();
  let calls = 0;
  const evidence = async () => { calls++; return { status: 'VERIFIED', summary: 'Publisher: v1.0.0', evidence: 1, url: 'https://github.com/drfxai/Maximus-Matrix-OS/releases/tag/v1.0.0' }; };
  const time = 1_800_000_000_000;
  assert.equal(await executeScheduled(team, time, db, evidence), true);
  assert.equal(await executeScheduled(team, time + 15 * 60_000, db, evidence), false);
  assert.equal(calls, 1);
  assert.deepEqual(db.reports.map(r => r.status), ['VERIFIED']);
  assert.equal(await executeScheduled(team, time + 180 * 60_000, db, evidence), true);
  assert.equal(calls, 2);
});

test('cloud team endpoints require strong bearer token and configured D1', async () => {
  const db = new Db(), env = { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: secret };
  assert.equal((await worker.fetch(req('/v1/teams', 'GET', undefined, 'wrong'), env)).status, 401);
  assert.equal((await worker.fetch(req('/v1/teams'), { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: 'weak' })).status, 503);
  assert.equal((await worker.fetch(req('/v1/teams', 'GET', undefined, secret, { origin: 'https://attacker.example' }), env)).status, 403);
  assert.equal((await worker.fetch(req('/v1/teams', 'POST', { ...proposal, job: 'SHELL' }), env)).status, 400);
  assert.equal((await worker.fetch(req('/v1/teams', 'POST', proposal), env)).status, 201);
  const result = await worker.fetch(req('/v1/teams'), env);
  assert.equal(result.status, 200);
  assert.deepEqual((await result.json()).teams[0].repository, proposal.repository);
  assert.equal((await worker.fetch(req('/v1/reports'), env)).status, 200);
});

test('untrusted oversized request bodies are rejected before team creation', async () => {
  const db = new Db(), env = { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: secret };
  const response = await worker.fetch(req('/v1/teams', 'POST', 'x'.repeat(2100)), env);
  assert.equal(response.status, 413);
  assert.equal(db.teams.length, 0);
});
