// The cosmetics shop, worked out exactly like the client does (com.nightshade.cosmetic.Shop), so the server and the
// game always agree on what is for sale and for how much. It reproduces java.util.Random and Collections.shuffle
// bit for bit; test/shop.test.mjs checks it against a rotation written by the real Java code.
import CATALOG from "./catalog.json";

export const DAILY_SLOTS = 6;
export const WEEKLY_SLOTS = 2;
export const FEATURED_DISCOUNT = 40;
export const FLASH_DISCOUNT = 50;
export const BUNDLE_DISCOUNT = 30;
export const FLASH_HOURS = 4;
const DAILY_DISCOUNTS = [0, 0, 10, 15, 20, 25];
const WEEKLY_DISCOUNTS = [0, 10, 20];

export const ITEMS = CATALOG.items;
export const BY_ID = new Map(ITEMS.map((i) => [i.id, i]));
export const CATEGORIES = CATALOG.categories;

const free = (i) => i.price === 0;
const byId = (a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);

// ------------------------------------------------------------------ Java arithmetic

const M64 = (1n << 64n) - 1n;
const s64 = (x) => BigInt.asIntN(64, x);
const u64 = (x) => x & M64;

class JavaRandom {
  constructor(seed) {
    this.seed = (u64(BigInt(seed)) ^ 0x5deece66dn) & ((1n << 48n) - 1n);
  }
  next(bits) {
    this.seed = (this.seed * 0x5deece66dn + 0xbn) & ((1n << 48n) - 1n);
    return Number(BigInt.asIntN(32, this.seed >> BigInt(48 - bits)));
  }
  nextInt(bound) {
    let r = this.next(31);
    const m = bound - 1;
    if ((bound & m) === 0) {
      return Number((BigInt(bound) * BigInt(r)) >> 31n);
    }
    for (let u = r; ; u = this.next(31)) {
      r = u % bound;
      if (((u - r + m) | 0) >= 0) return r;
    }
  }
}

function shuffle(list, rnd) {
  for (let i = list.length; i > 1; i--) {
    const j = rnd.nextInt(i);
    const t = list[i - 1];
    list[i - 1] = list[j];
    list[j] = t;
  }
}

function hashCode(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(h, 31) + s.charCodeAt(i)) | 0;
  return h;
}

const floorDiv = (a, b) => Math.floor(a / b);
const floorMod = (a, b) => ((a % b) + b) % b;

function mix(n, id) {
  let h = s64(BigInt(n) * 0x9e3779b97f4a7c15n + BigInt(hashCode(id)));
  h = h ^ (u64(h) >> 29n);
  return Number(u64(s64(h * 0xbf58476d1ce4e5b9n)) >> 33n);
}

const roundTen = (price, off) => Math.round((price * (100 - off)) / 1000) * 10;

// ------------------------------------------------------------------ time

export const today = (now = Date.now()) => Math.floor(now / 86400000);
export const week = (day) => floorDiv(day - 4, 7); // weeks start on Monday
export const flashSlot = (now = Date.now()) => Math.floor(new Date(now).getUTCHours() / FLASH_HOURS);

// ------------------------------------------------------------------ rotation

function pool(rare) {
  return ITEMS.filter((i) => !free(i) && (i.tier === "LEGENDARY" || i.tier === "MYTHIC") === rare).sort(byId);
}

function slice(p, slots, n, salt) {
  if (!p.length) return [];
  const perCycle = Math.max(1, Math.floor((p.length + slots - 1) / slots));
  const cycle = floorDiv(n, perCycle);
  const part = floorMod(n, perCycle);
  const order = p.slice();
  shuffle(order, new JavaRandom(s64(BigInt(cycle) * 0x9e3779b97f4a7c15n) ^ BigInt(salt)));
  const out = [];
  for (let k = 0; k < slots && k < order.length; k++) out.push(order[(part * slots + k) % order.length]);
  return out;
}

export const daily = (day) => slice(pool(false), DAILY_SLOTS, day, 0x5eed);
export const weekly = (day) => slice(pool(true), WEEKLY_SLOTS, week(day), 0xc0ffee);

export function flash(day, slot) {
  const d = daily(day), w = weekly(day);
  const all = ITEMS.filter((i) => !free(i) && i.tier !== "MYTHIC" && !d.includes(i) && !w.includes(i)).sort(byId);
  if (!all.length) return null;
  return all[floorMod(mix(day * (24 / FLASH_HOURS) + slot, "flash"), all.length)];
}

export function bundle(day) {
  const w = week(day);
  const r = new JavaRandom(s64(BigInt(w) * 0x2545f4914f6cdd1dn) ^ 0xb0d1en);
  const cats = CATEGORIES.slice().sort();
  shuffle(cats, r);
  const out = [];
  for (const c of cats) {
    if (out.length === 3) break;
    const pick = ITEMS.filter((i) => i.category === c && !free(i) && i.tier !== "MYTHIC" && !i.classic).sort(byId);
    if (pick.length) out.push(pick[r.nextInt(pick.length)]);
  }
  return out;
}

export function discount(item, day, slot) {
  if (!item || free(item)) return 0;
  const d = daily(day);
  if (d[0] === item) return FEATURED_DISCOUNT;
  if (flash(day, slot) === item) return FLASH_DISCOUNT;
  if (d.includes(item)) return DAILY_DISCOUNTS[floorMod(mix(day, item.id), DAILY_DISCOUNTS.length)];
  if (weekly(day).includes(item)) return WEEKLY_DISCOUNTS[floorMod(mix(week(day), item.id), WEEKLY_DISCOUNTS.length)];
  return 0;
}

export function price(item, day, slot) {
  const off = discount(item, day, slot);
  return off === 0 ? item.price : roundTen(item.price, off);
}

export function available(item, day, slot) {
  return !!item && (free(item) || daily(day).includes(item) || weekly(day).includes(item) || flash(day, slot) === item);
}

export function bundlePrice(day, owns) {
  const sum = bundle(day).filter((i) => !owns(i.id)).reduce((s, i) => s + i.price, 0);
  return roundTen(sum, BUNDLE_DISCOUNT);
}

/** Everything the shop shows right now, for GET /v1/shop. */
export function snapshot(now = Date.now()) {
  const day = today(now), slot = flashSlot(now);
  const card = (i) => ({ id: i.id, price: price(i, day, slot), full: i.price, off: discount(i, day, slot) });
  const period = FLASH_HOURS * 3600000;
  return {
    day,
    daily: daily(day).map(card),
    weekly: weekly(day).map(card),
    flash: (() => { const f = flash(day, slot); return f ? card(f) : null; })(),
    bundle: { items: bundle(day).map((i) => i.id), off: BUNDLE_DISCOUNT },
    resets: {
      daily: (day + 1) * 86400000 - now,
      weekly: ((week(day) + 1) * 7 + 4) * 86400000 - now,
      flash: period - floorMod(now, period),
    },
  };
}
