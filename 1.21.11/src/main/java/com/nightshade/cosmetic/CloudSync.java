package com.nightshade.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightshade.NightshadeClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Online cosmetics: signs you in to the Nightshade cosmetics service (the same check Minecraft servers use, so only
 * the real owner of an account can sign in as it), keeps your points and unlocks there, shares your outfit, and
 * fetches what other Nightshade players near you are wearing so they can be drawn.
 *
 * Everything here runs off the render thread. When you're offline (no Microsoft account, no internet, service down)
 * the Wardrobe simply keeps working locally like before.
 */
public final class CloudSync {
    public static final String BASE = "https://nightshade-cosmetics.nightshadeclient.workers.dev";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nightshade cosmetics sync");
        t.setDaemon(true);
        return t;
    });
    private static final long REMOTE_TTL = 120_000;
    private static final long RETRY_AFTER = 10 * 60_000;

    private static volatile boolean online;
    private static volatile String status = "Not signed in";
    private static volatile String token;
    private static volatile UUID signedInAs;
    private static volatile boolean signingIn;
    private static long lastAttempt;
    private static volatile int points;
    private static volatile int streak;
    private static volatile Set<String> owned = Set.of();
    private static volatile List<String[]> history = List.of();
    private static volatile long outfitDirtyAt;
    private static long lastRemoteTick;

    /** What other players wear: uuid -> outfit (empty map = not a Nightshade player or hiding), and when it was fetched. */
    private static final Map<UUID, Map<Catalog.Category, String>> REMOTE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FETCHED = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    /** Players the service knows as Nightshade players (who share their cosmetics): they get the logo by their name. */
    private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();

    /** Settings (from the Cosmetics module). */
    public static volatile boolean shareMine = true;
    public static volatile boolean seeOthers = true;
    /** Test hook: keep the self-test away from the real service. */
    public static volatile boolean disabledForTest;

    private CloudSync() {
    }

    public static boolean online() {
        return online && !disabledForTest;
    }

    public static String status() {
        return status;
    }

    public static int points() {
        return points;
    }

    public static int streak() {
        return streak;
    }

    public static boolean owns(String id) {
        return owned.contains(id);
    }

    public static List<String[]> history() {
        return history;
    }

    // ------------------------------------------------------------------ every client tick

    public static void tick(Minecraft mc) {
        if (disabledForTest || mc.player == null || mc.level == null) {
            return;
        }
        boolean share = com.nightshade.module.visual.Cosmetics.shareMine();
        if (share != shareMine) {
            shareMine = share;
            outfitChanged();
        }
        seeOthers = com.nightshade.module.visual.Cosmetics.seeOthers();
        long now = System.currentTimeMillis();
        UUID me = mc.getUser().getProfileId();
        if (online && me != null && !me.equals(signedInAs)) {
            online = false; // switched account
            token = null;
        }
        if (!online && !signingIn && now - lastAttempt > RETRY_AFTER) {
            lastAttempt = now;
            signingIn = true;
            WORKER.submit(CloudSync::signIn);
        }
        if (online && outfitDirtyAt > 0 && now - outfitDirtyAt > 1500) {
            outfitDirtyAt = 0;
            WORKER.submit(CloudSync::sendOutfit);
        }
        if (seeOthers && now - lastRemoteTick > 2000) {
            lastRemoteTick = now;
            List<UUID> want = new ArrayList<>();
            for (var p : mc.level.players()) {
                UUID id = p.getUUID();
                if (p == mc.player || IN_FLIGHT.contains(id)) {
                    continue;
                }
                Long at = FETCHED.get(id);
                if (at == null || now - at > REMOTE_TTL) {
                    want.add(id);
                }
                if (want.size() >= 100) {
                    break;
                }
            }
            if (!want.isEmpty()) {
                IN_FLIGHT.addAll(want);
                WORKER.submit(() -> fetchOutfits(want));
            }
        }
    }

    /** Call when what you wear (or whether you hide it) changes: it is sent a moment later. */
    public static void outfitChanged() {
        outfitDirtyAt = System.currentTimeMillis();
    }

    /** A minute of real play: the server adds the point (and daily/streak bonuses). */
    public static void playedMinute() {
        if (online()) {
            WORKER.submit(() -> {
                JsonObject r = call("POST", "/v1/me/heartbeat", null);
                if (r != null && r.has("points")) {
                    points = r.get("points").getAsInt();
                    streak = r.get("streak").getAsInt();
                    if (r.has("notes")) {
                        for (JsonElement n : r.getAsJsonArray("notes")) {
                            JsonObject o = n.getAsJsonObject();
                            notify(o.get("title").getAsString(), o.get("text").getAsString());
                        }
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ other players

    /** What this entity wears, if it's a Nightshade player who shares their cosmetics; null otherwise. */
    public static Map<Catalog.Category, String> outfitOf(Entity e) {
        if (!seeOthers || e == null) {
            return null;
        }
        Map<Catalog.Category, String> o = REMOTE.get(e.getUUID());
        return o == null || o.isEmpty() ? null : o;
    }

    /** True for Nightshade players who share their cosmetics (and for you, while you're online and sharing). */
    public static boolean isUser(UUID id) {
        if (USERS.contains(id)) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        return online() && shareMine && !Wardrobe.hidden() && id.equals(mc.getUser().getProfileId());
    }

    /** Test hook: pretend the service knows this player as a Nightshade player. */
    public static void markUserForTest(UUID id, boolean user) {
        if (user) {
            USERS.add(id);
        } else {
            USERS.remove(id);
        }
    }

    /** Test hook: pretend the service said this entity wears this outfit. */
    public static void putRemoteForTest(UUID id, Map<Catalog.Category, String> outfit) {
        USERS.add(id);
        REMOTE.put(id, outfit);
        FETCHED.put(id, Long.MAX_VALUE / 2);
    }

    private static void fetchOutfits(List<UUID> ids) {
        try {
            StringBuilder q = new StringBuilder();
            for (UUID id : ids) {
                q.append(q.isEmpty() ? "" : ",").append(id.toString().replace("-", ""));
            }
            JsonObject r = call("GET", "/v1/outfits?uuids=" + q, null);
            long now = System.currentTimeMillis();
            for (UUID id : ids) {
                String key = id.toString().replace("-", "");
                REMOTE.put(id, r != null && r.has(key) ? parseOutfit(r.getAsJsonObject(key)) : Map.of());
                if (r != null && r.has(key)) {
                    USERS.add(id);
                } else if (r != null) {
                    USERS.remove(id);
                }
                FETCHED.put(id, r == null ? now - REMOTE_TTL + 30_000 : now); // retry sooner if the service didn't answer
            }
        } finally {
            IN_FLIGHT.removeAll(ids);
        }
    }

    private static Map<Catalog.Category, String> parseOutfit(JsonObject o) {
        Map<Catalog.Category, String> out = new EnumMap<>(Catalog.Category.class);
        for (Catalog.Category c : Catalog.Category.values()) {
            if (o.has(c.name())) {
                Catalog.Item item = Catalog.byId(o.get(c.name()).getAsString());
                if (item != null && item.category == c) {
                    out.put(c, item.id);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ signing in

    private static Path tokenFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("cosmetics-session.json");
    }

    private static void signIn() {
        try {
            Minecraft mc = Minecraft.getInstance();
            var user = mc.getUser();
            UUID id = user.getProfileId();
            if (id == null) {
                status = "Offline account - cosmetics stay on this computer";
                return;
            }
            String uuid = id.toString().replace("-", "");
            // a saved session from last time?
            String saved = readSavedToken(uuid);
            if (saved != null) {
                token = saved;
                if (refreshProfile()) {
                    finishSignIn(id);
                    return;
                }
                token = null;
            }
            JsonObject start = new JsonObject();
            start.addProperty("uuid", uuid);
            start.addProperty("name", user.getName());
            JsonObject s = call("POST", "/v1/auth/start", start);
            if (s == null || !s.has("serverId")) {
                status = "Cosmetics service unreachable - working offline";
                return;
            }
            String serverId = s.get("serverId").getAsString();
            try {
                var sessions = mc.services().sessionService();
                sessions.joinServer(id, user.getAccessToken(), serverId); // proves to Mojang that this is really you
            } catch (Exception e) {
                status = "Only Microsoft accounts can share cosmetics";
                return;
            }
            JsonObject fin = new JsonObject();
            fin.addProperty("serverId", serverId);
            JsonObject f = call("POST", "/v1/auth/finish", fin);
            if (f == null || !f.has("token")) {
                status = "Sign-in was refused - working offline";
                return;
            }
            token = f.get("token").getAsString();
            saveToken(uuid, token);
            applyProfile(f.getAsJsonObject("profile"));
            finishSignIn(id);
        } catch (Exception e) {
            status = "Cosmetics sync failed - working offline";
        } finally {
            signingIn = false;
        }
    }

    private static void finishSignIn(UUID id) {
        signedInAs = id;
        online = true;
        status = "Online - other Nightshade players can see your cosmetics";
        importOnce();
        outfitChanged();
    }

    /** First time online: move this computer's points and unlocks over (the service caps how much). */
    private static void importOnce() {
        JsonObject me = call("GET", "/v1/me", null);
        if (me == null || (me.has("imported") && me.get("imported").getAsBoolean())) {
            return;
        }
        JsonObject b = new JsonObject();
        b.addProperty("points", Wardrobe.localPoints());
        JsonArray arr = new JsonArray();
        for (String id : Wardrobe.localOwned()) {
            arr.add(id);
        }
        b.add("owned", arr);
        JsonObject r = call("POST", "/v1/me/import", b);
        if (r != null && r.has("points")) {
            applyProfile(r);
            notify("Cosmetics are online", "Moved over " + r.get("importedItems").getAsInt() + " items and " + r.get("importedPoints").getAsInt() + " points");
        }
    }

    private static boolean refreshProfile() {
        JsonObject me = call("GET", "/v1/me", null);
        if (me == null || !me.has("points")) {
            return false;
        }
        applyProfile(me);
        return true;
    }

    private static void applyProfile(JsonObject p) {
        if (p == null) {
            return;
        }
        points = p.get("points").getAsInt();
        streak = p.has("streak") ? p.get("streak").getAsInt() : streak;
        Set<String> own = new HashSet<>();
        for (JsonElement e : p.getAsJsonArray("owned")) {
            own.add(e.getAsString());
        }
        owned = own;
        if (p.has("history")) {
            List<String[]> h = new ArrayList<>();
            for (JsonElement e : p.getAsJsonArray("history")) {
                JsonObject o = e.getAsJsonObject();
                int d = o.get("delta").getAsInt();
                String when = java.time.Instant.ofEpochMilli(o.get("ts").getAsLong()).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"));
                h.add(new String[]{o.get("reason").getAsString(), (d >= 0 ? "+" : "") + d, when});
            }
            history = h;
        }
    }

    // ------------------------------------------------------------------ buying and wearing

    /** Buys an item from today's shop. Blocks for a moment (it's a button press in the Wardrobe). */
    public static boolean unlock(Catalog.Item item) {
        JsonObject b = new JsonObject();
        b.addProperty("item", item.id);
        JsonObject r = call("POST", "/v1/me/unlock", b);
        if (r != null && r.has("points")) {
            applyProfile(r);
            return owned.contains(item.id);
        }
        return false;
    }

    public static boolean buyBundle() {
        JsonObject r = call("POST", "/v1/me/bundle", null);
        if (r != null && r.has("points")) {
            applyProfile(r);
            return true;
        }
        return false;
    }

    /** Owner tools (needs the owner key file): "points_add", "points_set" or "unlock_all" for yourself. */
    public static boolean admin(String action, int amount, String ownerKey) {
        UUID me = signedInAs;
        if (me == null) {
            return false;
        }
        JsonObject b = new JsonObject();
        b.addProperty("key", ownerKey);
        b.addProperty("action", action);
        b.addProperty("uuid", me.toString().replace("-", ""));
        b.addProperty("amount", amount);
        JsonObject r = call("POST", "/v1/admin", b);
        if (r != null && r.has("points")) {
            applyProfile(r);
            return true;
        }
        return false;
    }

    private static void sendOutfit() {
        JsonObject b = new JsonObject();
        JsonObject o = new JsonObject();
        boolean hide = Wardrobe.hidden() || !shareMine;
        for (Catalog.Category c : Catalog.Category.values()) {
            String id = Wardrobe.equipped(c);
            if (id != null && !hide) {
                o.addProperty(c.name(), id);
            }
        }
        b.add("outfit", o);
        b.addProperty("show", !hide);
        call("PUT", "/v1/me/outfit", b);
    }

    // ------------------------------------------------------------------ plumbing

    /** One request to the service; null when it failed. */
    private static JsonObject call(String method, String path, JsonObject body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Nightshade/" + NightshadeClient.VERSION).header("Content-Type", "application/json");
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 401 && token != null && !path.startsWith("/v1/auth")) {
                online = false; // session expired: sign in again soon
                token = null;
                lastAttempt = 0;
            }
            if (r.statusCode() / 100 != 2) {
                return null;
            }
            JsonElement e = JsonParser.parseString(r.body());
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readSavedToken(String uuid) {
        try {
            if (Files.isRegularFile(tokenFile())) {
                JsonObject o = JsonParser.parseString(Files.readString(tokenFile(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has(uuid)) {
                    return o.get(uuid).getAsString();
                }
            }
        } catch (Exception ignored) {
            // no saved session
        }
        return null;
    }

    private static void saveToken(String uuid, String value) {
        try {
            JsonObject o = Files.isRegularFile(tokenFile()) ? JsonParser.parseString(Files.readString(tokenFile(), StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            o.addProperty(uuid, value);
            Files.createDirectories(tokenFile().getParent());
            Files.writeString(tokenFile(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // signs in again next launch
        }
    }

    private static void notify(String title, String text) {
        Minecraft.getInstance().execute(() -> {
            try {
                NightshadeClient.client().notifications().success(title, text);
            } catch (RuntimeException ignored) {
                // not ready yet
            }
        });
    }

    /** Test hook: the service's shop for today (or null), to compare with the game's own rotation. */
    public static JsonObject shopForTest() {
        return call("GET", "/v1/shop", null);
    }

    /** Test hook: look these players up on the real service (blocking). */
    public static JsonObject lookupForTest(String uuids) {
        return call("GET", "/v1/outfits?uuids=" + uuids, null);
    }
}
