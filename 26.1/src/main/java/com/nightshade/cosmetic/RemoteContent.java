package com.nightshade.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cosmetics downloaded from the cosmetics service, so new items (like limited event sets) arrive without a new
 * version of the mod. Only data is downloaded: item details and models made of coloured cubes, never code. Every
 * download is signed; anything without a valid signature, or too big, is thrown away. The last good download is
 * kept on disk so the items still show when you are offline.
 */
public final class RemoteContent {
    /** Public half of the key the content is signed with (Ed25519). */
    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAmmcD1wXU8ZvsO+a0PZ2mzsI1pcpDvg0VloFzFT4ZIaI=";
    private static final int MAX_BYTES = 2_000_000;
    private static final int MAX_ITEMS = 200;
    private static final int MAX_VOXELS = 40_000;
    private static final int MAX_GRID = 128;
    private static final long REFRESH_MS = 30 * 60_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    /** An aura made of specks: which built-in movement, how many specks and their colours. */
    public record Aura(String motion, int count, int[] colors) {
    }

    private static volatile Map<String, Object> models = Map.of();
    private static volatile int version;
    private static volatile String lastError;
    private static long lastFetch;

    private RemoteContent() {
    }

    // ------------------------------------------------------------------ loading

    /** Loads the saved copy right away, then checks the service in the background (and every 30 minutes). */
    public static void start() {
        try {
            Path cache = cacheFile();
            if (Files.exists(cache)) {
                apply(Files.readString(cache), false);
            }
        } catch (Exception e) {
            lastError = "cache: " + e.getMessage();
        }
        Thread t = new Thread(() -> {
            while (true) {
                refresh();
                try {
                    Thread.sleep(REFRESH_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Nightshade cosmetics download");
        t.setDaemon(true);
        t.start();
    }

    /** Checks the service for new content now (no more than once a minute). */
    public static synchronized void refresh() {
        if (CloudSync.disabledForTest || System.currentTimeMillis() - lastFetch < 60_000L) {
            return;
        }
        lastFetch = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(CloudSync.BASE + "/v1/content")).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Nightshade").GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200 || r.body().length() > MAX_BYTES * 2) {
                lastError = "HTTP " + r.statusCode();
                return;
            }
            if (apply(r.body(), true)) {
                Path cache = cacheFile();
                Files.createDirectories(cache.getParent());
                Files.writeString(cache, r.body());
            }
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static Path cacheFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("downloaded-cosmetics.json");
    }

    /** Checks the signature and, if it is good, swaps the downloaded items in. Returns true when applied. */
    private static boolean apply(String envelope, boolean fresh) throws Exception {
        JsonObject env = JsonParser.parseString(envelope).getAsJsonObject();
        String content = env.get("content").getAsString();
        byte[] sig = Base64.getDecoder().decode(env.get("sig").getAsString());
        if (content.length() > MAX_BYTES || !verify(content.getBytes(StandardCharsets.UTF_8), sig)) {
            lastError = "bad signature";
            return false;
        }
        JsonObject c = JsonParser.parseString(content).getAsJsonObject();
        int v = c.get("version").getAsInt();
        if (fresh && v < version) {
            return false; // never go back to older content
        }
        load(c);
        version = v;
        lastError = null;
        return true;
    }

    private static boolean verify(byte[] data, byte[] sig) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    /** Builds every item and model. Anything malformed or too big is skipped; the rest still load. */
    private static void load(JsonObject c) {
        List<Catalog.Item> items = new ArrayList<>();
        Map<String, Object> built = new HashMap<>();
        JsonArray arr = c.getAsJsonArray("items");
        for (int n = 0; n < arr.size() && items.size() < MAX_ITEMS; n++) {
            try {
                JsonObject o = arr.get(n).getAsJsonObject();
                String id = o.get("id").getAsString();
                if (!id.matches("[a-z0-9_]{1,48}") || Catalog.byIdBuiltIn(id) != null) {
                    continue; // never replace a built-in item
                }
                Catalog.Category cat = Catalog.Category.valueOf(o.get("category").getAsString());
                Catalog.Tier tier = Catalog.Tier.valueOf(o.get("tier").getAsString());
                int price = Math.max(0, o.get("price").getAsInt());
                Catalog.Item it = Catalog.remoteItem(id, str(o, "name", id), cat, tier, price, str(o, "description", ""));
                if (o.has("limited")) {
                    JsonObject l = o.getAsJsonObject("limited");
                    it.limitedFrom = l.get("from").getAsLong();
                    it.limitedUntil = l.get("until").getAsLong();
                    it.limitedTag = str(l, "tag", "Limited");
                }
                it.added = o.has("added") ? o.get("added").getAsLong() : 0;
                Object model = model(cat, id, o.getAsJsonObject("model"));
                if (model == null) {
                    continue;
                }
                built.put(id, model);
                items.add(it);
            } catch (RuntimeException e) {
                lastError = "item " + n + ": " + e.getMessage();
            }
        }
        models = built;
        Catalog.setRemote(items);
        Thumbnails.clearRemote();
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    // ------------------------------------------------------------------ models

    private static Object model(Catalog.Category cat, String id, JsonObject m) {
        int[] palette = palette(m.getAsJsonArray("palette"));
        cellChars = m.has("cellChars") && m.get("cellChars").getAsInt() == 2 ? 2 : 1;
        return switch (cat) {
            case WINGS -> wing(m, palette);
            case CAPES -> cape(m, palette);
            case HATS -> hat(m, palette);
            case COMPANIONS -> companion(m, palette);
            case AURAS -> aura(m, palette);
        };
    }

    private static int[] palette(JsonArray a) {
        if (a == null) {
            return new int[0];
        }
        int[] p = new int[Math.min(a.size(), 62 * 62)];
        for (int i = 0; i < p.length; i++) {
            p[i] = (int) Long.parseLong(a.get(i).getAsString().replace("#", ""), 16);
        }
        return p;
    }

    /** Palette index of a grid character: 0-9, a-z, A-Z; '.' or ' ' is empty. */
    private static int code(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'z') return 10 + ch - 'a';
        if (ch >= 'A' && ch <= 'Z') return 36 + ch - 'A';
        return -1;
    }

    /** Characters per cell in the grids of the model being read: 1 (up to 62 colours) or 2 (up to 3,844). */
    private static int cellChars = 1;

    /** A picture made of rows of palette codes ('.' = empty). */
    private static VoxelModel.Grid grid(JsonArray rows, int[] palette, int[] voxels) {
        if (rows == null) {
            return null;
        }
        int cw = cellChars;
        int h = Math.min(rows.size(), MAX_GRID);
        int w = 0;
        for (int y = 0; y < h; y++) {
            w = Math.max(w, rows.get(y).getAsString().length() / cw);
        }
        w = Math.min(w, MAX_GRID);
        VoxelModel.Grid g = new VoxelModel.Grid(w, h);
        for (int y = 0; y < h; y++) {
            String row = rows.get(y).getAsString();
            for (int x = 0; x < Math.min(w, row.length() / cw); x++) {
                int k = cw == 1 ? code(row.charAt(x)) : code(row.charAt(x * 2)) < 0 || code(row.charAt(x * 2 + 1)) < 0 ? -1
                        : code(row.charAt(x * 2)) * 62 + code(row.charAt(x * 2 + 1));
                if (k >= 0 && k < palette.length) {
                    g.set(x, y, palette[k]);
                    if (++voxels[0] > MAX_VOXELS) {
                        throw new IllegalArgumentException("too many voxels");
                    }
                }
            }
        }
        return g;
    }

    private static WingModel wing(JsonObject m, int[] palette) {
        int[] count = {0};
        List<WingModel.Layer> layers = new ArrayList<>();
        int w = -1;
        for (JsonElement e : m.getAsJsonArray("layers")) {
            JsonObject l = e.getAsJsonObject();
            VoxelModel.Grid g = grid(l.getAsJsonArray("rows"), palette, count);
            VoxelModel.Grid glow = grid(l.getAsJsonArray("glow"), palette, count);
            if (w < 0) {
                w = g.w;
            }
            if (g.w != w || (glow != null && (glow.w != g.w || glow.h != g.h))) {
                throw new IllegalArgumentException("wing layers must be the same size");
            }
            layers.add(new WingModel.Layer(g, glow, clamp(l.get("z").getAsInt(), -8, 8), clamp(l.get("depth").getAsInt(), 1, 4)));
        }
        JsonArray hj = m.getAsJsonArray("hinges");
        int[] hinges = new int[hj == null ? 0 : Math.min(hj.size(), 6)];
        for (int i = 0; i < hinges.length; i++) {
            hinges[i] = clamp(hj.get(i).getAsInt(), 1, w - 1);
        }
        return new WingModel(layers, hinges, m.get("anchorY").getAsInt(), f(m, "size", 1f, 0.3f, 2f), f(m, "flap", 1f, 0f, 2f), f(m, "bend", 1f, 0f, 2f), null);
    }

    private static Designs.Cape cape(JsonObject m, int[] palette) {
        int[] count = {0};
        VoxelModel.Grid g = grid(m.getAsJsonArray("rows"), palette, count);
        VoxelModel.Grid glow = grid(m.getAsJsonArray("glow"), palette, count);
        int res = clamp(m.has("res") ? m.get("res").getAsInt() : 1, 1, 6);
        if (g.w != 10 * res || g.h != 17 * res) {
            throw new IllegalArgumentException("cape must be " + 10 * res + " x " + 17 * res);
        }
        return new Designs.Cape(g, glow, res);
    }

    private static Hats.Hat hat(JsonObject m, int[] palette) {
        Hats.Hat hat = new Hats.Hat();
        int[] count = {0};
        for (JsonElement e : m.getAsJsonArray("parts")) {
            JsonObject p = e.getAsJsonObject();
            hat.parts.add(new Hats.Part(voxels(p.get("voxels").getAsString(), palette, count), p.has("glow") && p.get("glow").getAsBoolean(),
                    clamp(p.has("anim") ? p.get("anim").getAsInt() : 0, 0, 3), f(p, "px", 0, -64, 64), f(p, "pz", 0, -64, 64)));
        }
        return hat;
    }

    private static Companions.Companion companion(JsonObject m, int[] palette) {
        Companions.Companion c = new Companions.Companion();
        c.flapSpeed = f(m, "flapSpeed", 1f, 0.2f, 4f);
        int[] count = {0};
        for (JsonElement e : m.getAsJsonArray("parts")) {
            JsonObject p = e.getAsJsonObject();
            int anim = p.has("anim") ? p.get("anim").getAsInt() : 0;
            if (anim != Companions.STILL && anim != Companions.SPIN && anim != Companions.BOB && anim != Companions.FLAP) {
                anim = Companions.STILL;
            }
            c.parts.add(new Companions.Part(voxels(p.get("voxels").getAsString(), palette, count), p.has("glow") && p.get("glow").getAsBoolean(),
                    anim, f(p, "px", 0, -64, 64), f(p, "py", 0, -64, 64), f(p, "pz", 0, -64, 64)));
        }
        return c;
    }

    private static Aura aura(JsonObject m, int[] palette) {
        String motion = m.get("motion").getAsString();
        if (!CosmeticLayer.AURA_MOTIONS.contains(motion)) {
            throw new IllegalArgumentException("unknown aura movement " + motion);
        }
        int[] colors = palette.length == 0 ? new int[]{0xFFFFFFFF} : palette;
        return new Aura(motion, clamp(m.has("count") ? m.get("count").getAsInt() : 20, 1, 48), colors);
    }

    /** Cubes written as "x,y,z,p;x,y,z,p;..." where p is the palette index. */
    private static VoxelModel voxels(String s, int[] palette, int[] count) {
        VoxelModel.Builder b = VoxelModel.builder();
        for (String v : s.split(";")) {
            if (v.isBlank()) {
                continue;
            }
            String[] f = v.split(",");
            int p = Integer.parseInt(f[3].trim());
            if (p < 0 || p >= palette.length) {
                continue;
            }
            b.set(clamp(Integer.parseInt(f[0].trim()), -64, 64), clamp(Integer.parseInt(f[1].trim()), -64, 64), clamp(Integer.parseInt(f[2].trim()), -64, 64), palette[p]);
            if (++count[0] > MAX_VOXELS) {
                throw new IllegalArgumentException("too many voxels");
            }
        }
        return b.build();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float f(JsonObject o, String k, float def, float lo, float hi) {
        return o.has(k) ? Math.max(lo, Math.min(hi, o.get(k).getAsFloat())) : def;
    }

    // ------------------------------------------------------------------ lookups

    public static WingModel wing(String id) {
        return models.get(id) instanceof WingModel w ? w : null;
    }

    public static Designs.Cape cape(String id) {
        return models.get(id) instanceof Designs.Cape c ? c : null;
    }

    public static Hats.Hat hat(String id) {
        return models.get(id) instanceof Hats.Hat h ? h : null;
    }

    public static Companions.Companion companion(String id) {
        return models.get(id) instanceof Companions.Companion c ? c : null;
    }

    public static Aura aura(String id) {
        return models.get(id) instanceof Aura a ? a : null;
    }

    public static int version() {
        return version;
    }

    public static String lastError() {
        return lastError;
    }

    // ------------------------------------------------------------------ test hooks

    /** Loads content as if it had come from the service (signature checked like a real download). */
    public static boolean applyForTest(String envelope) {
        try {
            return apply(envelope, false);
        } catch (Exception e) {
            lastError = e.getMessage();
            return false;
        }
    }

    /** Throws away everything downloaded (for tests). */
    public static void clearForTest() {
        models = Map.of();
        version = 0;
        Catalog.setRemote(List.of());
    }
}
