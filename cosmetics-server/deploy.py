"""Deploys the cosmetics API to Cloudflare (Workers + D1). Safe to re-run.

Needs CF_API_TOKEN (a Cloudflare API token with Workers Scripts and D1 edit rights) and OWNER_HASH (the SHA-256 of
your owner key, never the key itself) in the environment. Run `node build.mjs` first."""
import json, os, sys, uuid, urllib.request, urllib.error, pathlib
API = "https://api.cloudflare.com/client/v4"
HERE = pathlib.Path(__file__).parent
SCRIPT, DBNAME, SUB = "nightshade-cosmetics", "nightshade-cosmetics", sys.argv[1] if len(sys.argv) > 1 else "nightshadeclient"
OWNER_HASH = os.environ["OWNER_HASH"]  # SHA-256 of the owner key (not the key itself)

def cf(method, path, data=None, raw=None, ctype="application/json"):
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    req = urllib.request.Request(API + path, data=body, method=method, headers={"Authorization": "Bearer " + os.environ["CF_API_TOKEN"], "Content-Type": ctype})
    try:
        return json.load(urllib.request.urlopen(req))
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b"{}")

acct = cf("GET", "/accounts")["result"][0]["id"]
# 1. workers.dev subdomain
s = cf("GET", f"/accounts/{acct}/workers/subdomain")
if not s.get("success"):
    s = cf("PUT", f"/accounts/{acct}/workers/subdomain", {"subdomain": SUB})
    print("create subdomain:", s.get("success"), s.get("errors"))
sub = (s.get("result") or {}).get("subdomain")
print("subdomain:", sub)
# 2. database
dbs = cf("GET", f"/accounts/{acct}/d1/database?name={DBNAME}")["result"]
db = next((d for d in dbs if d["name"] == DBNAME), None) or cf("POST", f"/accounts/{acct}/d1/database", {"name": DBNAME})["result"]
dbid = db["uuid"]
print("database:", DBNAME, "ok" if dbid else "FAILED")
q = cf("POST", f"/accounts/{acct}/d1/database/{dbid}/query", {"sql": (HERE / "schema.sql").read_text()})
print("schema:", q.get("success"), q.get("errors"))
# 3. worker (two ES modules) with the database and the owner-key hash bound
meta = {"main_module": "worker.js", "compatibility_date": "2026-11-01",
        "bindings": [{"type": "d1", "name": "DB", "id": dbid}, {"type": "plain_text", "name": "OWNER_HASH", "text": OWNER_HASH}]}
b = uuid.uuid4().hex
parts = [("metadata", "blob", "application/json", json.dumps(meta).encode())]
for f in ("worker.js", "shop.js"):
    parts.append((f, f, "application/javascript+module", (HERE / "dist" / f).read_bytes()))
body = b""
for name, filename, ct, data in parts:
    body += f'--{b}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\nContent-Type: {ct}\r\n\r\n'.encode() + data + b"\r\n"
body += f"--{b}--\r\n".encode()
up = cf("PUT", f"/accounts/{acct}/workers/scripts/{SCRIPT}", raw=body, ctype=f"multipart/form-data; boundary={b}")
print("upload:", up.get("success"), up.get("errors"))
en = cf("POST", f"/accounts/{acct}/workers/scripts/{SCRIPT}/subdomain", {"enabled": True, "previews_enabled": False})
print("enable workers.dev:", en.get("success"), en.get("errors"))
if sub:
    print("URL: https://%s.%s.workers.dev" % (SCRIPT, sub))
