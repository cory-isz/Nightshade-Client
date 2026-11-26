# Nightshade cosmetics server

The small online service that lets Nightshade players see each other's Wardrobe cosmetics. It runs on Cloudflare
Workers with a D1 database.

- **Sign-in** uses the same check Minecraft servers use (`hasJoined`), so nobody can wear cosmetics as someone else.
- **Points, unlocks and the shop** are worked out on the server, exactly like the game does (`src/shop.js` is a
  bit-for-bit port of the game's shop rotation; `test/shop.test.mjs` checks it).
- **Outfits** are public: the game looks up the outfits of Nightshade players near you.

## Files

| File | What it is |
|---|---|
| `src/worker.js` | The API (`/v1/...`) |
| `src/shop.js` | Shop rotation, discounts and prices |
| `src/catalog.json` | Every cosmetic with its price, tier and category |
| `schema.sql` | Database tables |
| `build.mjs` | Bundles `src/` into `dist/` |
| `deploy.py` | Creates the database and uploads the worker |
| `test/` | Tests (`node test/shop.test.mjs`, `node test/api.test.mjs`) |

## Deploying

```bash
node build.mjs
CF_API_TOKEN=... OWNER_HASH=... python3 deploy.py
```
