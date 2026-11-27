// Nightshade cosmetics API (Cloudflare Worker + D1).
//
// Players prove they own their Minecraft account the same way joining a server does (Mojang session check), then the
// server keeps their points, unlocks and outfit. Anyone can look up what Nightshade players are wearing, so other
// Nightshade users can draw their cosmetics. The shop rotation is identical to the game's (see shop.js).
import * as shop from "./shop.js";

import CONTENT from "./content.json";

const DAILY = 25;          // first game of the day, on day 1 of a streak...
const DAILY_STEP = 5;      // ...growing this much a day of the streak...
const DAILY_MAX = 60;      // ...up to this
const STREAK = 200;        // every 7-day streak
const WEEKLY_GOAL = 300;   // active minutes in a week (Monday to Sunday, UTC)...
const WEEKLY_BONUS = 250;  // ...for this bonus
const dailyBonus = (streak) => Math.min(DAILY_MAX, DAILY + DAILY_STEP * Math.max(0, streak - 1));
const weekOf = (day) => Math.floor((day - 4) / 7);
const IMPORT_CAP = 15000;  // most a one-time import of old local progress can be worth
const SESSION_DAYS = 30;
const MAX_LOOKUP = 100;

// ------------------------------------------------------------------ helpers

const json = (data, status = 200, extra = {}) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json", ...extra } });
const err = (status, message) => json({ error: message }, status);

const cleanUuid = (u) => (typeof u === "string" ? u.toLowerCase().replace(/-/g, "") : "");
const isUuid = (u) => /^[0-9a-f]{32}$/.test(u);
const isName = (n) => typeof n === "string" && /^[A-Za-z0-9_]{1,16}$/.test(n);

function randomHex(bytes) {
  const b = new Uint8Array(bytes);
  crypto.getRandomValues(b);
  return [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
}

async function sha256(text) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

function sameText(a, b) {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

async function body(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

const utcDay = (now = Date.now()) => Math.floor(now / 86400000);

// ------------------------------------------------------------------ players

async function player(env, uuid) {
  return env.DB.prepare("SELECT * FROM players WHERE uuid = ?").bind(uuid).first();
}

async function owned(env, uuid) {
  const r = await env.DB.prepare("SELECT item FROM owned WHERE uuid = ?").bind(uuid).all();
  return new Set(r.results.map((x) => x.item));
}

async function history(env, uuid, delta, reason) {
  await env.DB.prepare("INSERT INTO history (uuid, ts, delta, reason) VALUES (?, ?, ?, ?)").bind(uuid, Date.now(), delta, reason).run();
}

async function profile(env, uuid) {
  const p = await player(env, uuid);
  const own = await owned(env, uuid);
  const out = await env.DB.prepare("SELECT outfit, show FROM outfits WHERE uuid = ?").bind(uuid).first();
  const h = await env.DB.prepare("SELECT ts, delta, reason FROM history WHERE uuid = ? ORDER BY ts DESC LIMIT 20").bind(uuid).all();
  return {
    uuid,
    name: p.name,
    points: p.points,
    streak: p.streak,
    activeMinutes: p.active_minutes,
    weekMinutes: p.week_id === weekOf(utcDay(Date.now())) ? p.week_minutes : 0,
    weekGoalDone: p.week_id === weekOf(utcDay(Date.now())) && !!p.week_paid,
    imported: !!p.imported,
    owned: [...own],
    outfit: out ? JSON.parse(out.outfit) : {},
    show: out ? !!out.show : true,
    history: h.results,
  };
}

/** The signed-in player for a request, or null. */
async function authed(request, env) {
  const auth = request.headers.get("authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!/^[0-9a-f]{64}$/.test(token)) return null;
  const row = await env.DB.prepare("SELECT uuid, expires FROM sessions WHERE token_hash = ?").bind(await sha256(token)).first();
  if (!row || row.expires < Date.now()) return null;
  return row.uuid;
}

// ------------------------------------------------------------------ routes

async function authStart(request, env) {
  const b = await body(request);
  const uuid = cleanUuid(b.uuid);
  if (!isUuid(uuid) || !isName(b.name)) return err(400, "uuid and name are required");
  const serverId = randomHex(20);
  await env.DB.prepare("DELETE FROM nonces WHERE expires < ?").bind(Date.now()).run();
  await env.DB.prepare("INSERT INTO nonces (server_id, uuid, name, expires) VALUES (?, ?, ?, ?)")
    .bind(serverId, uuid, b.name, Date.now() + 120000).run();
  return json({ serverId });
}

async function authFinish(request, env) {
  const b = await body(request);
  const serverId = typeof b.serverId === "string" ? b.serverId : "";
  const n = await env.DB.prepare("SELECT * FROM nonces WHERE server_id = ?").bind(serverId).first();
  if (!n || n.expires < Date.now()) return err(400, "Sign-in expired, try again");
  await env.DB.prepare("DELETE FROM nonces WHERE server_id = ?").bind(serverId).run();
  // Mojang confirms this account just asked to join "serverId" - only the real owner can do that
  const url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" + encodeURIComponent(n.name) + "&serverId=" + serverId;
  const r = await fetch(url, { headers: { "user-agent": "nightshade-cosmetics/1.0" } });
  if (r.status !== 200) return err(401, "Could not verify your Minecraft account (only Microsoft accounts can sign in)");
  const prof = await r.json();
  if (cleanUuid(prof.id) !== n.uuid) return err(401, "Account mismatch");
  const now = Date.now();
  await env.DB.prepare(
    "INSERT INTO players (uuid, name, points, streak, last_day, last_beat, active_minutes, imported, created) VALUES (?, ?, 0, 0, 0, 0, 0, 0, ?) " +
      "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name"
  ).bind(n.uuid, prof.name, now).run();
  const token = randomHex(32);
  await env.DB.prepare("DELETE FROM sessions WHERE uuid = ? AND expires < ?").bind(n.uuid, now).run();
  await env.DB.prepare("INSERT INTO sessions (token_hash, uuid, expires) VALUES (?, ?, ?)")
    .bind(await sha256(token), n.uuid, now + SESSION_DAYS * 86400000).run();
  return json({ token, profile: await profile(env, n.uuid) });
}

/** Called once a minute while you actually play: 1 point a minute, plus the daily and streak bonuses. */
async function heartbeat(env, uuid) {
  const p = await player(env, uuid);
  const now = Date.now();
  const notes = [];
  let points = p.points, streak = p.streak, lastDay = p.last_day, active = p.active_minutes, lastBeat = p.last_beat;
  const day = utcDay(now), week = weekOf(day);
  let weekId = p.week_id, weekMinutes = p.week_minutes, weekPaid = p.week_paid;
  if (weekId !== week) {
    weekId = week;
    weekMinutes = 0;
    weekPaid = 0;
  }
  if (now - lastBeat >= 55000) { // at most one point a minute, however often this is called
    points += 1;
    active += 1;
    weekMinutes += 1;
    lastBeat = now;
    if (!weekPaid && weekMinutes >= WEEKLY_GOAL) {
      weekPaid = 1;
      points += WEEKLY_BONUS;
      notes.push({ title: "Weekly goal +" + WEEKLY_BONUS, text: "You played 5 hours this week" });
      await history(env, uuid, WEEKLY_BONUS, "Weekly goal: 5 hours played");
    }
  }
  if (day !== lastDay) {
    streak = lastDay === day - 1 ? streak + 1 : 1;
    lastDay = day;
    const bonus = dailyBonus(streak);
    points += bonus;
    notes.push({ title: "Daily bonus +" + bonus, text: "Day " + streak + " streak" });
    await history(env, uuid, bonus, "Daily bonus (day " + streak + ")");
    if (streak % 7 === 0) {
      points += STREAK;
      notes.push({ title: "7-day streak +" + STREAK, text: "Keep it going!" });
      await history(env, uuid, STREAK, "7-day streak");
    }
  }
  await env.DB.prepare("UPDATE players SET points = ?, streak = ?, last_day = ?, last_beat = ?, active_minutes = ?, week_id = ?, week_minutes = ?, week_paid = ? WHERE uuid = ?")
    .bind(points, streak, lastDay, lastBeat, active, weekId, weekMinutes, weekPaid, uuid).run();
  return json({ points, streak, activeMinutes: active, weekMinutes, weekGoalDone: !!weekPaid, notes });
}

async function unlock(request, env, uuid) {
  const b = await body(request);
  const item = shop.BY_ID.get(b.item);
  if (!item) return err(400, "Unknown item");
  const own = await owned(env, uuid);
  if (item.price === 0 || own.has(item.id)) return json(await profile(env, uuid));
  const now = Date.now(), day = shop.today(now), slot = shop.flashSlot(now);
  if (!shop.available(item, day, slot, now)) return err(409, item.remote && item.until ? "This limited item is no longer available" : "Not in the shop right now");
  const cost = shop.price(item, day, slot);
  const p = await player(env, uuid);
  if (p.points < cost) return err(409, "Not enough points");
  await env.DB.batch([
    env.DB.prepare("UPDATE players SET points = points - ? WHERE uuid = ?").bind(cost, uuid),
    env.DB.prepare("INSERT OR IGNORE INTO owned (uuid, item) VALUES (?, ?)").bind(uuid, item.id),
  ]);
  const off = shop.discount(item, day, slot);
  await history(env, uuid, -cost, "Unlocked " + item.name + (off ? " (" + off + "% off)" : ""));
  return json(await profile(env, uuid));
}

async function buyBundle(env, uuid) {
  const own = await owned(env, uuid);
  const day = shop.today();
  const items = shop.bundle(day);
  if (!items.length || items.every((i) => own.has(i.id))) return err(409, "You already own this bundle");
  const cost = shop.bundlePrice(day, (id) => own.has(id));
  const p = await player(env, uuid);
  if (p.points < cost) return err(409, "Not enough points");
  await env.DB.batch([
    env.DB.prepare("UPDATE players SET points = points - ? WHERE uuid = ?").bind(cost, uuid),
    ...items.map((i) => env.DB.prepare("INSERT OR IGNORE INTO owned (uuid, item) VALUES (?, ?)").bind(uuid, i.id)),
  ]);
  await history(env, uuid, -cost, "Unlocked the weekly bundle (" + shop.BUNDLE_DISCOUNT + "% off)");
  return json(await profile(env, uuid));
}

async function setOutfit(request, env, uuid) {
  const b = await body(request);
  const own = await owned(env, uuid);
  const outfit = {};
  for (const [cat, id] of Object.entries(b.outfit || {})) {
    const item = shop.BY_ID.get(id);
    if (!item || item.category !== cat) continue;
    if (item.price !== 0 && !own.has(id)) continue; // you can only wear what you own
    outfit[cat] = id;
  }
  const show = b.show === undefined ? 1 : b.show ? 1 : 0;
  await env.DB.prepare("INSERT INTO outfits (uuid, outfit, show, updated) VALUES (?, ?, ?, ?) " +
      "ON CONFLICT(uuid) DO UPDATE SET outfit = excluded.outfit, show = excluded.show, updated = excluded.updated")
    .bind(uuid, JSON.stringify(outfit), show, Date.now()).run();
  return json({ outfit, show: !!show });
}

/** Moves your old local progress over once, worth at most IMPORT_CAP points in total (cheapest items first). */
async function importLocal(request, env, uuid) {
  const p = await player(env, uuid);
  if (p.imported) return err(409, "Already imported");
  const b = await body(request);
  let budget = IMPORT_CAP;
  const items = [...new Set(Array.isArray(b.owned) ? b.owned : [])]
    .map((id) => shop.BY_ID.get(id)).filter((i) => i && i.price > 0).sort((a, c) => a.price - c.price);
  const take = [];
  for (const i of items) {
    if (i.price > budget) break;
    take.push(i);
    budget -= i.price;
  }
  const pts = Math.max(0, Math.min(budget, Math.floor(Number(b.points) || 0)));
  await env.DB.batch([
    env.DB.prepare("UPDATE players SET points = points + ?, imported = 1 WHERE uuid = ?").bind(pts, uuid),
    ...take.map((i) => env.DB.prepare("INSERT OR IGNORE INTO owned (uuid, item) VALUES (?, ?)").bind(uuid, i.id)),
  ]);
  await history(env, uuid, pts, "Moved over from this computer (" + take.length + " items)");
  return json({ ...(await profile(env, uuid)), importedItems: take.length, importedPoints: pts });
}

/** Public: what these players are wearing (only Nightshade players who show their cosmetics). */
async function lookup(url, env) {
  const uuids = [...new Set((url.searchParams.get("uuids") || "").split(",").map(cleanUuid).filter(isUuid))].slice(0, MAX_LOOKUP);
  if (!uuids.length) return json({});
  const marks = uuids.map(() => "?").join(",");
  const r = await env.DB.prepare(`SELECT uuid, outfit FROM outfits WHERE show = 1 AND uuid IN (${marks})`).bind(...uuids).all();
  const out = {};
  for (const row of r.results) out[row.uuid] = JSON.parse(row.outfit);
  return json(out, 200, { "cache-control": "public, max-age=60" });
}

/** Owner tools: the owner key (checked against its SHA-256, like the game does) instead of a password. */
async function admin(request, env) {
  const b = await body(request);
  if (typeof b.key !== "string" || !env.OWNER_HASH || !sameText(await sha256(b.key.trim()), env.OWNER_HASH)) return err(403, "Not allowed");
  const uuid = cleanUuid(b.uuid);
  if (!isUuid(uuid) || !(await player(env, uuid))) return err(404, "That player hasn't signed in yet");
  const n = Math.floor(Number(b.amount) || 0);
  if (b.action === "points_add") {
    await env.DB.prepare("UPDATE players SET points = MAX(0, points + ?) WHERE uuid = ?").bind(n, uuid).run();
    await history(env, uuid, n, "Owner gift");
  } else if (b.action === "points_set") {
    await env.DB.prepare("UPDATE players SET points = ? WHERE uuid = ?").bind(Math.max(0, n), uuid).run();
    await history(env, uuid, 0, "Owner set points to " + Math.max(0, n));
  } else if (b.action === "unlock_all") {
    await env.DB.batch([...shop.ITEMS, ...shop.REMOTE].filter((i) => i.price > 0).map((i) => env.DB.prepare("INSERT OR IGNORE INTO owned (uuid, item) VALUES (?, ?)").bind(uuid, i.id)));
    await history(env, uuid, 0, "Owner unlocked everything");
  } else {
    return err(400, "Unknown action");
  }
  return json(await profile(env, uuid));
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const route = request.method + " " + url.pathname;
    try {
      switch (route) {
        case "GET /": case "GET /v1/health":
          return json({ ok: true, service: "nightshade-cosmetics", items: shop.ITEMS.length });
        case "GET /v1/shop":
          return json(shop.snapshot(), 200, { "cache-control": "public, max-age=30" });
        case "GET /v1/content":
          return json(CONTENT, 200, { "cache-control": "public, max-age=300" });
        case "GET /v1/outfits":
          return lookup(url, env);
        case "POST /v1/auth/start":
          return authStart(request, env);
        case "POST /v1/auth/finish":
          return authFinish(request, env);
        case "POST /v1/admin":
          return admin(request, env);
      }
      const uuid = await authed(request, env);
      if (!uuid) return err(401, "Sign in first");
      switch (route) {
        case "GET /v1/me": return json(await profile(env, uuid));
        case "POST /v1/me/heartbeat": return heartbeat(env, uuid);
        case "POST /v1/me/unlock": return unlock(request, env, uuid);
        case "POST /v1/me/bundle": return buyBundle(env, uuid);
        case "PUT /v1/me/outfit": return setOutfit(request, env, uuid);
        case "POST /v1/me/import": return importLocal(request, env, uuid);
        case "POST /v1/auth/logout":
          await env.DB.prepare("DELETE FROM sessions WHERE uuid = ?").bind(uuid).run();
          return json({ ok: true });
      }
      return err(404, "Not found");
    } catch (e) {
      return err(500, "Server error");
    }
  },
};
