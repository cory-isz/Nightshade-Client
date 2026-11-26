// Runs the real worker against an in-memory SQLite database (standing in for D1) and a fake Mojang check.
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import worker from "../dist/worker.js";
import * as shop from "../dist/shop.js";
import { createHash } from "node:crypto";

const db = new DatabaseSync(":memory:");
db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
const stmt = (sql) => ({ sql, args: [], bind(...a) { return { ...this, args: a }; },
  async first() { return db.prepare(this.sql).get(...this.args) ?? null; },
  async all() { return { results: db.prepare(this.sql).all(...this.args) }; },
  async run() { db.prepare(this.sql).run(...this.args); return { success: true }; } });
const OWNER = "test-owner-secret";
const env = { DB: { prepare: stmt, async batch(list) { for (const s of list) await s.run(); return []; } },
  OWNER_HASH: createHash("sha256").update(OWNER).digest("hex") };
const joined = new Map(); // serverId -> {name, id}: what "Mojang" knows
globalThis.fetch = async (url) => {
  const u = new URL(url); const j = joined.get(u.searchParams.get("serverId"));
  return j && j.name === u.searchParams.get("username") ? new Response(JSON.stringify(j), { status: 200 }) : new Response(null, { status: 204 });
};
let fails = 0, n = 0;
const check = (ok, what) => { n++; if (!ok) { fails++; console.log("FAIL", what); } };
const call = async (method, path, data, token) => {
  const r = await worker.fetch(new Request("https://x" + path, { method, headers: { "content-type": "application/json", ...(token ? { authorization: "Bearer " + token } : {}) }, body: data ? JSON.stringify(data) : undefined }), env);
  return { status: r.status, body: await r.json() };
};
const UUID = "0123456789abcdef0123456789abcdef", NAME = "Tester";
async function signIn(uuid, name, cheat) {
  const s = await call("POST", "/v1/auth/start", { uuid, name });
  if (!cheat) joined.set(s.body.serverId, { id: uuid, name });
  return call("POST", "/v1/auth/finish", { serverId: s.body.serverId });
}
check((await call("GET", "/v1/health")).body.ok, "health");
check((await signIn(UUID, NAME, true)).status === 401, "sign-in without Mojang join is refused");
const login = await signIn(UUID, NAME);
check(login.status === 200 && /^[0-9a-f]{64}$/.test(login.body.token), "sign-in with Mojang join gives a token");
const T = login.body.token;
check((await call("GET", "/v1/me")).status === 401, "/me needs a token");
check((await call("GET", "/v1/me", null, "f".repeat(64))).status === 401, "a made-up token is refused");
let me = (await call("GET", "/v1/me", null, T)).body;
check(me.points === 0 && me.owned.length === 0, "new player starts empty");
let hb = (await call("POST", "/v1/me/heartbeat", null, T)).body;
check(hb.points === 26 && hb.streak === 1 && hb.notes.length === 1, "first heartbeat: 1 point + daily bonus 25 (" + hb.points + ")");
hb = (await call("POST", "/v1/me/heartbeat", null, T)).body;
check(hb.points === 26, "spamming heartbeats gives nothing extra");
// a not-for-sale item can't be bought; today's shop item can
const day = shop.today(), slot = shop.flashSlot();
const notForSale = shop.ITEMS.find((i) => i.price > 0 && !shop.available(i, day, slot));
db.prepare("UPDATE players SET points = 100000 WHERE uuid = ?").run(UUID);
check((await call("POST", "/v1/me/unlock", { item: notForSale.id }, T)).status === 409, "can't buy what isn't in the shop");
const deal = shop.daily(day)[0];
const before = (await call("GET", "/v1/me", null, T)).body.points;
const u = await call("POST", "/v1/me/unlock", { item: deal.id }, T);
check(u.status === 200 && u.body.owned.includes(deal.id) && before - u.body.points === shop.price(deal, day, slot), "buying the featured deal charges the shop price (" + shop.price(deal, day, slot) + ")");
const bp = shop.bundlePrice(day, (id) => u.body.owned.includes(id));
const b1 = await call("POST", "/v1/me/bundle", null, T);
check(b1.status === 200 && shop.bundle(day).every((i) => b1.body.owned.includes(i.id)) && u.body.points - b1.body.points === bp, "bundle unlocks its items for the bundle price");
check((await call("POST", "/v1/me/bundle", null, T)).status === 409, "can't buy the bundle twice");
// outfits: only owned (or free) items are stored, and others can look them up
const o = await call("PUT", "/v1/me/outfit", { outfit: { [deal.category]: deal.id, CAPES: notForSale.category === "CAPES" ? notForSale.id : "cape_galaxy", WINGS: "seraph" }, show: true }, T);
check(o.body.outfit[deal.category] === deal.id && !Object.values(o.body.outfit).includes("seraph"), "outfit keeps owned items and drops unowned ones");
const look = await call("GET", "/v1/outfits?uuids=" + UUID + ",ffffffffffffffffffffffffffffffff,bad");
check(look.body[UUID] && Object.keys(look.body).length === 1, "public lookup returns this player's outfit only");
await call("PUT", "/v1/me/outfit", { outfit: {}, show: false }, T);
check(!(await call("GET", "/v1/outfits?uuids=" + UUID)).body[UUID], "hidden cosmetics are not shown to others");
// import: once, capped
const T2 = (await signIn("fedcba9876543210fedcba9876543210", "Second")).body.token;
const imp = await call("POST", "/v1/me/import", { points: 999999, owned: shop.ITEMS.map((i) => i.id) }, T2);
const worth = imp.body.points + imp.body.owned.reduce((s, id) => s + shop.BY_ID.get(id).price, 0);
check(imp.status === 200 && worth <= 15000 && imp.body.owned.length > 0, "import is capped at 15,000 points of value (" + worth + ")");
check((await call("POST", "/v1/me/import", { points: 5 }, T2)).status === 409, "import works only once");
// owner tools
check((await call("POST", "/v1/admin", { key: "wrong", action: "points_add", uuid: UUID, amount: 5 })).status === 403, "admin refuses a wrong key");
const ad = await call("POST", "/v1/admin", { key: OWNER, action: "points_set", uuid: UUID, amount: 1234 });
check(ad.status === 200 && ad.body.points === 1234, "admin with the owner key can set points");
const ua = await call("POST", "/v1/admin", { key: OWNER, action: "unlock_all", uuid: UUID });
check(ua.body.owned.length === shop.ITEMS.filter((i) => i.price > 0).length, "admin can unlock everything");
check((await call("POST", "/v1/auth/logout", null, T)).status === 200 && (await call("GET", "/v1/me", null, T)).status === 401, "logout ends the session");
const snap = (await call("GET", "/v1/shop")).body;
check(snap.daily.length === 6 && snap.weekly.length === 2 && snap.daily[0].off === 40 && snap.flash.off === 50, "shop endpoint lists today's shop");
console.log(fails ? `FAILED ${fails}/${n}` : `api: all ${n} checks passed`);
process.exit(fails ? 1 : 0);
