// Builds dist/: the worker and the shop as two ES modules, with the catalog inlined (no JSON imports needed).
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
mkdirSync(new URL("./dist/", import.meta.url), { recursive: true });
const cat = readFileSync(new URL("./src/catalog.json", import.meta.url), "utf8");
const content = readFileSync(new URL("./src/content.json", import.meta.url), "utf8");
const shop = readFileSync(new URL("./src/shop.js", import.meta.url), "utf8").replace('import CATALOG from "./catalog.json";', "const CATALOG = " + cat + ";")
  .replace('import CONTENT from "./content.json";', "const CONTENT = " + content + ";");
writeFileSync(new URL("./dist/shop.js", import.meta.url), shop);
writeFileSync(new URL("./dist/worker.js", import.meta.url), readFileSync(new URL("./src/worker.js", import.meta.url), "utf8")
  .replace('import CONTENT from "./content.json";', "const CONTENT = " + content + ";"));
console.log("built dist/worker.js + dist/shop.js");
