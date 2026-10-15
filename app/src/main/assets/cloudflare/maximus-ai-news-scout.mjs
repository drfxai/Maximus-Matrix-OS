/**
 * MAXIMUS Matrix OS — scheduled public AI news evidence scout.
 *
 * Cron wakes this Worker for EACH scheduled invocation; it is not a continuously
 * running Linux process or a general-purpose AI model. No API credentials,
 * personalized prompts, GitHub tokens or private user data enter this script.
 * Collector -> validator -> reporter are bounded, auditable steps.
 *
 * NOTE: Reports are written to Cloudflare Worker logs, not Hindsight. Durable
 * memory and model-team orchestration require separate approved bindings.
 */
const FEEDS = Object.freeze([
  { name: "Hugging Face Blog", url: "https://huggingface.co/blog/feed.xml" },
  { name: "arXiv cs.AI", url: "https://export.arxiv.org/rss/cs.AI" }
]);
const MAX_FEED_BYTES = 96 * 1024;
const MAX_HEADLINES = 4;
const MAX_WAIT_MS = 12000;

function cleanedTitle(input) {
  return input.replace(/<!\[CDATA\[|\]\]>/g, "")
    .replace(/<[^>]*>/g, " ")
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/[\u0000-\u001f]/g, " ").replace(/\s+/g, " ").trim().slice(0, 150);
}

function extractHeadlines(xml) {
  // Titles are untrusted publisher text, not tool commands or instructions.
  // Only use public evidence; never execute extracted content.
  const titles = [...xml.matchAll(/<title(?:\s[^>]*)?>([\s\S]*?)<\/title>/gi)]
    .map(match => cleanedTitle(match[1]))
    .filter(item => item.length >= 12)
    .slice(1, MAX_HEADLINES + 1);
  return [...new Set(titles)];
}

async function collector(feed) {
  const ac = new AbortController();
  const timeout = setTimeout(() => ac.abort(), MAX_WAIT_MS);
  try {
    const response = await fetch(feed.url, {
      signal: ac.signal,
      redirect: "error",
      headers: { "Accept": "application/rss+xml, application/atom+xml, text/xml" }
    });
    if (!response.ok || !(response.headers.get("content-type") || "").match(/xml|rss|atom/i)) {
      return { source: feed.name, ok: false, titles: [] };
    }
    const body = await response.text();
    if (body.length > MAX_FEED_BYTES || !/</.test(body)) {
      return { source: feed.name, ok: false, titles: [] };
    }
    return { source: feed.name, ok: true, titles: extractHeadlines(body) };
  } catch {
    return { source: feed.name, ok: false, titles: [] };
  } finally {
    clearTimeout(timeout);
  }
}

export default {
  async fetch() {
    // Public HTTP requests cannot trigger or configure private agent jobs.
    return new Response("Not available", {
      status: 404, headers: { "cache-control": "no-store" }
    });
  },
  async scheduled(controller, env, ctx) {
    const at = new Date().toISOString();
    const sources = await Promise.all(FEEDS.map(collector));
    const verified = sources.filter(x => x.ok && x.titles.length > 0);
    const evidence = verified.flatMap(x => x.titles.map(title => ({
      source: x.source, headline: title
    }))).slice(0, MAX_HEADLINES);
    const result = {
      app: "maximus-matrix-os",
      job: "public-ai-news-scout-v1",
      trigger: controller.cron || "scheduled",
      at,
      roles: ["collector", "validator", "reporter"],
      status: evidence.length > 0 ? "VERIFIED" : "UNAVAILABLE",
      confirmedSourceCount: verified.length,
      evidence
    };
    // Every run is observable in Cloudflare's Worker logs.
    // Durable memory/history are intentionally NOT fabricated here.
    console.info(JSON.stringify(result));
  }
};
