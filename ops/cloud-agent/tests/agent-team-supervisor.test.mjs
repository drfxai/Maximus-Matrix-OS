import test from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
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
      async first() {
        if (sql.startsWith('SELECT COUNT')) return { count: db.teams.length };
        if (sql.startsWith('SELECT id, enabled FROM agent_teams WHERE id = ?'))
          return db.teams.find(t => t.id === this.args[0]) || null;
        throw Error('unexpected SQL');
      },
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
          if (sql.endsWith('WHERE (SELECT COUNT(*) FROM agent_teams) < 8') && db.teams.length >= 8)
            return { meta: { changes: 0 } };
          // Mirror the shipped schema trigger for unconditional inserts.
          if (db.teams.length >= 8) throw Error('team_limit');
          db.teams.push({ id: a[0], job: a[1], repository: a[2], period_minutes: a[3], enabled: 1 });
          return { meta: { changes: 1 } };
        }
        if (sql.startsWith('UPDATE agent_teams SET enabled')) {
          const row = db.teams.find(t => t.id === a[1]);
          if (row) row.enabled = a[0];
          return { meta: { changes: Number(!!row) } };
        }
        if (sql.startsWith('DELETE FROM agent_teams WHERE id')) {
          db.teams = db.teams.filter(t => t.id !== a[0]);
          db.reports = db.reports.filter(r => r.team_id !== a[0]);
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

test('collector exception leaves a durable unavailable report and cannot replay the slot', async () => {
  const db = new Db();
  const now = 1_800_100_000_000;
  let attempts = 0;
  assert.equal(await executeScheduled(team, now, db, async () => {
    attempts++;
    throw new Error('publisher_offline');
  }), true);
  assert.equal(db.reports.length, 1);
  assert.equal(db.reports[0].status, 'UNAVAILABLE');
  assert.equal(db.reports[0].evidence, 0);
  assert.equal(await executeScheduled(team, now + 15 * 60_000, db, async () => {
    attempts++;
    return { status: 'VERIFIED', evidence: 1, summary: 'must not run' };
  }), false);
  assert.equal(attempts, 1);
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

test('cloud team pause, resume and deletion require token and strict payloads', async () => {
  const db = new Db(), env = { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: secret };
  const created = await worker.fetch(req('/v1/teams', 'POST', proposal), env);
  assert.equal(created.status, 201);
  const { id } = await created.json();
  const url = '/v1/teams/' + id;

  assert.equal((await worker.fetch(req(url, 'PATCH', { enabled: false }, 'wrong'), env)).status, 401);
  assert.equal((await worker.fetch(req(url, 'PATCH', { enabled: 'false' }), env)).status, 400);
  assert.equal((await worker.fetch(req(url, 'PATCH', { enabled: false, job: 'SHELL' }), env)).status, 400);
  const paused = await worker.fetch(req(url, 'PATCH', { enabled: false }), env);
  assert.equal(paused.status, 200);
  assert.equal((await paused.json()).enabled, false);
  assert.equal(db.teams[0].enabled, 0);
  assert.equal((await worker.fetch(req(url, 'PATCH', { enabled: true }), env)).status, 200);
  assert.equal(db.teams[0].enabled, 1);
  assert.equal((await worker.fetch(req(url + '/pause', 'POST', undefined, 'wrong'), env)).status, 401);
  assert.equal((await worker.fetch(req(url + '/pause', 'POST', { enabled: true }), env)).status, 400);
  assert.equal((await worker.fetch(req(url + '/pause', 'POST'), env)).status, 200);
  assert.equal(db.teams[0].enabled, 0);
  assert.equal((await worker.fetch(req(url + '/resume', 'POST'), env)).status, 200);
  assert.equal(db.teams[0].enabled, 1);
  assert.equal((await worker.fetch(req(url, 'DELETE', undefined, 'wrong'), env)).status, 401);
  const deleted = await worker.fetch(req(url, 'DELETE'), env);
  assert.equal(deleted.status, 200);
  assert.equal((await deleted.json()).deleted, true);
  assert.equal(db.teams.length, 0);
  assert.equal((await worker.fetch(req(url, 'PATCH', { enabled: true }), env)).status, 404);
});

test('disabled cloud teams are excluded from Cron fetch and cannot be recreated by retries', async () => {
  const db = new Db(), env = { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: secret };
  const result = await worker.fetch(req('/v1/teams', 'POST', proposal), env);
  const { id } = await result.json();
  await worker.fetch(req('/v1/teams/' + id, 'PATCH', { enabled: false }), env);
  const enabled = db.teams.filter(x => x.enabled === 1);
  assert.equal(enabled.length, 0);
});

// Hold both requests at their D1 boundary so an old COUNT-then-INSERT flow
// observes the same seven rows before either request can insert.
function rendezvous(parties) {
  let arrived = 0, release;
  const ready = new Promise(resolve => { release = resolve; });
  return async () => { if (++arrived === parties) release(); await ready; };
}

class OverlappingDb extends Db {
  constructor() { super(); this.readGate = rendezvous(2); this.writeGate = rendezvous(2); this.inserts = []; }
  prepare(sql) {
    const statement = super.prepare(sql), db = this;
    if (sql.startsWith('SELECT COUNT')) {
      const first = statement.first;
      statement.first = async function () { const row = await first.call(this); await db.readGate(); return row; };
    }
    if (sql.startsWith('INSERT INTO agent_teams')) {
      const run = statement.run;
      statement.run = async function () {
        db.inserts.push({ sql, args: this.args });
        await db.writeGate();
        return run.call(this);
      };
    }
    return statement;
  }
}

test('overlapping valid registrations compete atomically for the eighth team slot', async () => {
  const db = new OverlappingDb(), env = { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: secret };
  db.teams = Array.from({ length: 7 }, (_, i) => ({ ...team, id: 'existing-' + i, enabled: i % 2 }));
  const responses = await Promise.all([
    worker.fetch(req('/v1/teams', 'POST', proposal), env),
    worker.fetch(req('/v1/teams', 'POST', proposal), env)
  ]);
  assert.deepEqual(responses.map(r => r.status).sort(), [201, 409]);
  assert.deepEqual(await responses.find(r => r.status === 409).json(), { error: { code: 'team_limit' } });
  assert.equal(db.teams.length, 8);
  const created = await responses.find(r => r.status === 201).json();
  assert.equal(db.teams.filter(t => t.id === created.id).length, 1);

  // Execute the actual prepared SQL on SQLite using the shipped schema and
  // separate concurrent connections. This validates the predicate, not only
  // our D1 fake; Python's standard-library SQLite needs no additional package.
  const sqlite = spawnSync('python3', ['-c', `
import concurrent.futures, json, pathlib, sqlite3, sys, tempfile, threading
payload = json.load(sys.stdin)
with tempfile.TemporaryDirectory() as directory:
    path = str(pathlib.Path(directory) / 'teams.sqlite')
    with sqlite3.connect(path) as db:
        db.executescript(payload['schema'])
        for i in range(7):
            db.execute('INSERT INTO agent_teams VALUES (?, ?, ?, ?, ?)',
                       ('existing-' + str(i), 'GITHUB_RELEASES', 'drfxai/Maximus-Matrix-OS', 180, i % 2))
    gate = threading.Barrier(12)
    def insert(i):
        with sqlite3.connect(path, timeout=10, isolation_level=None) as db:
            gate.wait(timeout=10)
            args = payload['args'].copy()
            args[0] = 'new-' + str(i)
            return db.execute(payload['sql'], args).rowcount
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        changes = list(pool.map(insert, range(12)))
    with sqlite3.connect(path) as db:
        count = db.execute('SELECT COUNT(*) FROM agent_teams').fetchone()[0]
    print(json.dumps({'changes': sorted(changes), 'count': count}))
`], {
    input: JSON.stringify({ ...db.inserts[0], schema: readFileSync(new URL('../schema.sql', import.meta.url), 'utf8') }),
    encoding: 'utf8', timeout: 20000
  });
  assert.equal(sqlite.status, 0, sqlite.error?.message || sqlite.stderr);
  assert.deepEqual(JSON.parse(sqlite.stdout), { changes: [...Array(11).fill(0), 1], count: 8 });
});

test('team creation requires confirmed D1 write metadata', async () => {
  for (const result of [{}, { meta: {} }, { meta: { changes: '1' } }, { meta: { changes: 2 } }]) {
    const db = { prepare: () => ({ bind() { return this; }, async run() { return result; } }) };
    const response = await worker.fetch(req('/v1/teams', 'POST', proposal), { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: secret });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: { code: 'storage_unavailable' } });
  }
});

test('authenticated health probe confirms D1 availability without claiming continuous execution', async () => {
  const db = new Db(), env = { AGENT_DB: db, MAXIMUS_AGENT_TOKEN: secret };
  assert.equal((await worker.fetch(req('/v1/health', 'GET', undefined, 'invalid'), env)).status, 401);
  const response = await worker.fetch(req('/v1/health'), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.status, 'ready');
  assert.equal(data.scheduler, 'cron');
  assert.equal(data.externalExecution, false);
  assert.equal(data.configuredTeams, 0);
  assert.equal((await worker.fetch(req('/v1/health', 'POST'), env)).status, 405);
  assert.equal((await worker.fetch(req('/v1/health'), { MAXIMUS_AGENT_TOKEN: secret })).status, 503);
});
