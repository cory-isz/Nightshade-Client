// Checks the server's shop against the rotation the real Java client code produced (test/shop_ref.json).
import { readFileSync } from "node:fs";
import * as shop from "../dist/shop.js";
const ref = JSON.parse(readFileSync(new URL("./shop_ref.json", import.meta.url)));
let bad = 0, checks = 0;
const ids = (l) => l.map((i) => i.id).join(",");
for (const r of ref) {
  const cmp = (what, a, b) => { checks++; if (a !== b) { if (bad++ < 8) console.log("MISMATCH", r.day, what, a, "vs", b); } };
  cmp("daily", ids(shop.daily(r.day)), r.daily.join(","));
  cmp("weekly", ids(shop.weekly(r.day)), r.weekly.join(","));
  cmp("bundle", ids(shop.bundle(r.day)), r.bundle.join(","));
  for (let s = 0; s < 6; s++) cmp("flash" + s, shop.flash(r.day, s).id, r.flash[s]);
  for (const [id, p] of Object.entries(r.prices)) cmp("price " + id, shop.price(shop.BY_ID.get(id), r.day, 0), p);
}
console.log(bad ? `FAILED ${bad}/${checks}` : `shop matches the Java client: ${checks} checks over ${ref.length} days`);
process.exit(bad ? 1 : 0);
