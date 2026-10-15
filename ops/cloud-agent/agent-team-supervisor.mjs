// Cloudflare Worker + D1: authenticated, bounded cloud-side agent-team monitoring.
// This is a scheduled evidence pipeline, not an arbitrary code/LLM execution service.
const PERIODS = new Set([15, 180, 1440]);
const REPO = /^[A-Za-z0-9_.-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/;
const JSON_HEADERS = { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' };

export function validateTeam(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return null;
  if (Object.keys(input).sort().join(',') !== 'job,periodMinutes,repository') return null;
  if (input.job !== 'GITHUB_RELEASES' || !PERIODS.has(input.periodMinutes)) return null;
  if (typeof input.repository !== 'string' || !REPO.test(input.repository)) return null;
  const [owner, name] = input.repository.split('/');
  // Accept only real GitHub account-owner syntax; an IPv4 literal is not a user.
  if (!/^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$/.test(owner) ||
      name === '.' || name === '..') return null;
  return { job: input.job, periodMinutes: input.periodMinutes, repository: input.repository };
}

export function releaseSource(repository) {
  const v = validateTeam({ job: 'GITHUB_RELEASES', periodMinutes: 15, repository });
  if (!v) throw new Error('invalid_repository');
  const [owner, name] = repository.split('/');
  return 'https://api.github.com/repos/' + encodeURIComponent(owner) + '/' + encodeURIComponent(name) + '/releases/latest';
}

const json = (status, data) => new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
const error = (status, code) => json(status, { error: { code } });

async function authorized(request, token) {
  const expected = typeof token === 'string' && token.length >= 32 ? 'Bearer ' + token : '';
  const received = request.headers.get('authorization') || '';
  if (!expected || received.length !== expected.length) return false;
  const [a, b] = await Promise.all([crypto.subtle.digest('SHA-256', new TextEncoder().encode(received)), crypto.subtle.digest('SHA-256', new TextEncoder().encode(expected))]);
  const bytesA = new Uint8Array(a), bytesB = new Uint8Array(b);
  let difference = 0;
  for (let i = 0; i < bytesA.length; i++) difference |= bytesA[i] ^ bytesB[i];
  return difference === 0;
}

async function readBody(request) {
  const declared = request.headers.get('content-length');
  if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > 2048)) throw new Error('body_too_large');
  if (!request.body) throw new Error('invalid_body');
  const reader = request.body.getReader();
  let total = 0;
  const parts = [];
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > 2048) { await reader.cancel(); throw new Error('body_too_large'); }
      parts.push(value);
    }
  } finally { reader.releaseLock(); }
  const merged = new Uint8Array(total);
  let cursor = 0;
  for (const p of parts) { merged.set(p, cursor); cursor += p.byteLength; }
  return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(merged));
}

async function readSourceResponse(response) {
  if (!response.ok || response.status !== 200) return null;
  if (Number(response.headers.get('content-length') || 0) > 65536) return null;
  if (!response.body) return null;
  const reader = response.body.getReader();
  let bytes = 0, chunks = [];
  try {
    while (true) {
      const item = await reader.read();
      if (item.done) break;
      bytes += item.value.byteLength;
      if (bytes > 65536) { await reader.cancel(); return null; }
      chunks.push(item.value);
    }
  } finally { reader.releaseLock(); }
  const data = new Uint8Array(bytes);
  let at = 0;
  for (const p of chunks) { data.set(p, at); at += p.byteLength; }
  return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(data));
}

/** Collector -> validator: only HTTPS GitHub public release evidence is accepted. */
export async function collectRelease(team, fetcher = fetch) {
  try {
    const source = releaseSource(team.repository);
    const response = await fetcher(source, {
      headers: { accept: 'application/vnd.github+json', 'user-agent': 'maximus-evidence-monitor' },
      redirect: 'manual',
      signal: AbortSignal.timeout(7000)
    });
    const release = await readSourceResponse(response);
    if (!release || typeof release.tag_name !== 'string' || release.tag_name.length > 100) return { status: 'UNAVAILABLE', summary: 'Source unavailable', evidence: 0 };
    const expectedPrefix = 'https://github.com/' + team.repository + '/releases/tag/';
    if (typeof release.html_url !== 'string' || !release.html_url.startsWith(expectedPrefix)) return { status: 'UNAVAILABLE', summary: 'Unverified release URL', evidence: 0 };
    if (release.html_url.length > 512) return { status: 'UNAVAILABLE', summary: 'Invalid release URL', evidence: 0 };
    return { status: 'VERIFIED', summary: ('GitHub release: ' + release.tag_name).slice(0, 200), evidence: 1, url: release.html_url };
  } catch (_) { return { status: 'UNAVAILABLE', summary: 'Source unavailable', evidence: 0 }; }
}

export async function executeScheduled(team, scheduledTime, db, collector = collectRelease) {
  const period = Number(team.period_minutes);
  if (!PERIODS.has(period) || !validateTeam({ job: team.job, periodMinutes: period, repository: team.repository })) return false;
  const slot = Math.floor(scheduledTime / (period * 60000));
  if (!Number.isSafeInteger(slot) || slot < 0) return false;
  // Durable slot uniqueness prevents duplicate Cron deliveries or overlapping workers
  // from executing an already claimed team slot. Never retry external effects blindly.
  const claim = await db.prepare('INSERT OR IGNORE INTO agent_reports (team_id, slot, status, summary, evidence, observed_at) VALUES (?, ?, ?, ?, ?, ?)')
    .bind(team.id, slot, 'RUNNING', 'Awaiting source verification', 0, scheduledTime).run();
  if (!claim.meta || claim.meta.changes !== 1) return false;
  const result = await collector(team);
  const verified = result && result.status === 'VERIFIED' && result.evidence === 1;
  await db.prepare('UPDATE agent_reports SET status = ?, summary = ?, evidence = ?, url = ?, observed_at = ? WHERE team_id = ? AND slot = ?')
    .bind(verified ? 'VERIFIED' : 'UNAVAILABLE', String(result?.summary || 'Source unavailable').slice(0, 200), verified ? 1 : 0, verified ? String(result.url || '').slice(0, 512) : '', scheduledTime, team.id, slot).run();
  return true;
}

export default {
  async fetch(request, env) {
    if (!env?.AGENT_DB || typeof env.MAXIMUS_AGENT_TOKEN !== 'string' || env.MAXIMUS_AGENT_TOKEN.length < 32) return error(503, 'setup_required');
    if (!await authorized(request, env.MAXIMUS_AGENT_TOKEN)) return error(401, 'unauthorized');
    if (request.headers.has('origin')) return error(403, 'browser_origin_not_allowed');
    const url = new URL(request.url);
    if (url.search || url.hash) return error(400, 'invalid_query');
    try {
      if (request.method === 'GET' && url.pathname === '/v1/teams') {
        const { results } = await env.AGENT_DB.prepare('SELECT id, job, repository, period_minutes, enabled FROM agent_teams ORDER BY id LIMIT 8').all();
        return json(200, { teams: results || [] });
      }
      if (request.method === 'GET' && url.pathname === '/v1/reports') {
        const { results } = await env.AGENT_DB.prepare('SELECT team_id, slot, status, summary, evidence, url, observed_at FROM agent_reports ORDER BY observed_at DESC LIMIT 30').all();
        return json(200, { reports: results || [] });
      }
      if (request.method === 'POST' && url.pathname === '/v1/teams') {
        if (request.headers.get('content-type') !== 'application/json' || request.headers.has('content-encoding')) return error(415, 'unsupported_media_type');
        let body;
        try { body = await readBody(request); } catch (e) { return error(e?.message === 'body_too_large' ? 413 : 400, e?.message === 'body_too_large' ? 'body_too_large' : 'invalid_json'); }
        const team = validateTeam(body);
        if (!team) return error(400, 'invalid_team');
        const row = await env.AGENT_DB.prepare('SELECT COUNT(*) AS count FROM agent_teams').first();
        if (Number(row?.count || 0) >= 8) return error(409, 'team_limit');
        const id = crypto.randomUUID();
        await env.AGENT_DB.prepare('INSERT INTO agent_teams (id, job, repository, period_minutes, enabled) VALUES (?, ?, ?, ?, 1)')
          .bind(id, team.job, team.repository, team.periodMinutes).run();
        return json(201, { id, ...team, status: 'scheduled_by_cloudflare_cron_when_deployed' });
      }
      // Explicit POST control commands work with Android HttpURLConnection,
      // whose method allowlist excludes PATCH. These commands never accept a body.
      const command = /^\/v1\/teams\/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\/(pause|resume)$/.exec(url.pathname);
      if (command) {
        if (request.method !== 'POST') return error(405, 'method_not_allowed');
        if (request.body || request.headers.has('content-length') && request.headers.get('content-length') !== '0')
          return error(400, 'empty_body_required');
        const id = command[1];
        const existing = await env.AGENT_DB.prepare('SELECT id, enabled FROM agent_teams WHERE id = ?').bind(id).first();
        if (!existing) return error(404, 'unknown_team');
        const enabled = command[2] === 'resume';
        await env.AGENT_DB.prepare('UPDATE agent_teams SET enabled = ? WHERE id = ?')
          .bind(enabled ? 1 : 0, id).run();
        return json(200, { id, enabled, runningTasksMayFinish: true });
      }
      const item = /^\/v1\/teams\/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$/.exec(url.pathname);
      if (item) {
        const id = item[1];
        if (request.method !== 'PATCH' && request.method !== 'DELETE') return error(405, 'method_not_allowed');
        const existing = await env.AGENT_DB.prepare('SELECT id, enabled FROM agent_teams WHERE id = ?').bind(id).first();
        if (!existing) return error(404, 'unknown_team');
        if (request.method === 'PATCH') {
          if (request.headers.get('content-type') !== 'application/json' || request.headers.has('content-encoding'))
            return error(415, 'unsupported_media_type');
          let body;
          try { body = await readBody(request); }
          catch (e) { return error(e?.message === 'body_too_large' ? 413 : 400,
              e?.message === 'body_too_large' ? 'body_too_large' : 'invalid_json'); }
          if (!body || typeof body !== 'object' || Array.isArray(body) ||
              Object.keys(body).length !== 1 || typeof body.enabled !== 'boolean')
            return error(400, 'invalid_update');
          await env.AGENT_DB.prepare('UPDATE agent_teams SET enabled = ? WHERE id = ?')
            .bind(body.enabled ? 1 : 0, id).run();
          return json(200, { id, enabled: body.enabled, runningTasksMayFinish: true });
        }
        // A deletion revokes all future scheduling; an already-claimed task may finish.
        if (request.body || request.headers.has('content-length') && request.headers.get('content-length') !== '0')
          return error(400, 'empty_body_required');
        await env.AGENT_DB.prepare('DELETE FROM agent_teams WHERE id = ?').bind(id).run();
        return json(200, { id, deleted: true, runningTasksMayFinish: true });
      }
      if (['/v1/teams', '/v1/reports'].includes(url.pathname)) return error(405, 'method_not_allowed');
      return error(404, 'not_found');
    } catch (_) { return error(503, 'storage_unavailable'); }
  },

  async scheduled(event, env) {
    if (!env?.AGENT_DB) return;
    const scheduledTime = event.scheduledTime;
    if (!Number.isSafeInteger(scheduledTime)) return;
    const { results } = await env.AGENT_DB.prepare('SELECT id, job, repository, period_minutes FROM agent_teams WHERE enabled = 1 ORDER BY id LIMIT 8').all();
    for (const team of results || []) await executeScheduled(team, scheduledTime, env.AGENT_DB);
    // Stored evidence is bounded in time, preserving no prompts or secrets.
    await env.AGENT_DB.prepare('DELETE FROM agent_reports WHERE observed_at < ?').bind(scheduledTime - 30 * 86400000).run();
  }
};
