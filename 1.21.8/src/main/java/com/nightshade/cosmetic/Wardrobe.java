package com.nightshade.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightshade.NightshadeClient;
import com.nightshade.module.visual.Cosmetics;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Points, owned and equipped cosmetics. Saved in config/nightshade/wardrobe.json on this computer only; nothing is
 * sent anywhere. Points: 1 per active minute in game, +25 daily, +100 per 7-day streak, +300 welcome gift.
 */
public final class Wardrobe {
    /** Render-state id used by the Wardrobe's 3D preview (shows tentative picks). */
    public static final int PREVIEW_ID = -735_412;
    public static final int WELCOME = 300;
    public static final int DAILY = 25;
    public static final int STREAK = 100;
    public static final int AFK_SECONDS = 300;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded;
    private static int points;
    private static long activeTicks;
    private static int ticksTowardPoint;
    private static int sessionEarned;
    private static String lastDay = "";
    private static int streak;
    private static boolean hidden;
    private static final Set<String> OWNED = new LinkedHashSet<>();
    private static final Map<Catalog.Category, String> EQUIPPED = new EnumMap<>(Catalog.Category.class);
    private static final Map<Catalog.Category, String> PREVIEW = new EnumMap<>(Catalog.Category.class);
    private static final List<String[]> HISTORY = new ArrayList<>();
    /** Three saved outfits (category -> item id); empty map = nothing saved yet. */
    public static final int OUTFITS = 3;
    private static final List<Map<Catalog.Category, String>> OUTFIT = new ArrayList<>();
    private static final Set<String> FAVOURITES = new LinkedHashSet<>();
    /** How the Wardrobe model holds its wings: 0 opens and closes, 1 folded, 2 spread. */
    public static int previewWings;

    static {
        for (int i = 0; i < OUTFITS; i++) {
            OUTFIT.add(new EnumMap<>(Catalog.Category.class));
        }
    }
    private static boolean dirty;
    private static long lastSave;
    // AFK tracking
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static float lastYaw;
    private static float lastPitch;
    private static long lastActivity = System.currentTimeMillis();
    private static boolean afkNotified;
    private static boolean dailyChecked;

    private Wardrobe() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("wardrobe.json");
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        boolean fresh = true;
        try {
            if (Files.isRegularFile(file())) {
                JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
                fresh = false;
                points = o.has("points") ? o.get("points").getAsInt() : 0;
                activeTicks = o.has("activeTicks") ? o.get("activeTicks").getAsLong() : 0;
                ticksTowardPoint = o.has("ticksTowardPoint") ? o.get("ticksTowardPoint").getAsInt() : 0;
                lastDay = o.has("lastDay") ? o.get("lastDay").getAsString() : "";
                streak = o.has("streak") ? o.get("streak").getAsInt() : 0;
                hidden = o.has("hidden") && o.get("hidden").getAsBoolean();
                if (o.has("owned")) {
                    for (JsonElement e : o.getAsJsonArray("owned")) {
                        OWNED.add(e.getAsString());
                    }
                }
                if (o.has("equipped")) {
                    JsonObject eq = o.getAsJsonObject("equipped");
                    for (Catalog.Category c : Catalog.Category.values()) {
                        if (eq.has(c.name()) && Catalog.byId(eq.get(c.name()).getAsString()) != null) {
                            EQUIPPED.put(c, eq.get(c.name()).getAsString());
                        }
                    }
                }
                if (o.has("outfits")) {
                    JsonArray arr = o.getAsJsonArray("outfits");
                    for (int i = 0; i < Math.min(OUTFITS, arr.size()); i++) {
                        JsonObject of = arr.get(i).getAsJsonObject();
                        for (Catalog.Category c : Catalog.Category.values()) {
                            if (of.has(c.name()) && Catalog.byId(of.get(c.name()).getAsString()) != null) {
                                OUTFIT.get(i).put(c, of.get(c.name()).getAsString());
                            }
                        }
                    }
                }
                if (o.has("favourites")) {
                    for (JsonElement e : o.getAsJsonArray("favourites")) {
                        if (Catalog.byId(e.getAsString()) != null) {
                            FAVOURITES.add(e.getAsString());
                        }
                    }
                }
                if (o.has("history")) {
                    for (JsonElement e : o.getAsJsonArray("history")) {
                        JsonArray a = e.getAsJsonArray();
                        HISTORY.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsString()});
                    }
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("Nightshade").warn("Wardrobe could not be read, starting fresh", e);
        }
        if (fresh) {
            earn(WELCOME, "Welcome gift");
            EQUIPPED.put(Catalog.Category.CAPES, "cape_moon");
        }
        for (Catalog.Item i : Catalog.all()) {
            if (i.free()) {
                OWNED.add(i.id);
            }
        }
        dirty = true;
        save();
    }

    public static synchronized void save() {
        CloudSync.outfitChanged(); // sent (a moment later) so other players see what you wear
        if (!loaded || !dirty) {
            return;
        }
        dirty = false;
        lastSave = System.currentTimeMillis();
        try {
            JsonObject o = new JsonObject();
            o.addProperty("points", points);
            o.addProperty("activeTicks", activeTicks);
            o.addProperty("ticksTowardPoint", ticksTowardPoint);
            o.addProperty("lastDay", lastDay);
            o.addProperty("streak", streak);
            o.addProperty("hidden", hidden);
            JsonArray owned = new JsonArray();
            OWNED.forEach(owned::add);
            o.add("owned", owned);
            JsonObject eq = new JsonObject();
            EQUIPPED.forEach((k, v) -> eq.addProperty(k.name(), v));
            o.add("equipped", eq);
            JsonArray outfits = new JsonArray();
            for (Map<Catalog.Category, String> of : OUTFIT) {
                JsonObject j = new JsonObject();
                of.forEach((k, v) -> j.addProperty(k.name(), v));
                outfits.add(j);
            }
            o.add("outfits", outfits);
            JsonArray fav = new JsonArray();
            FAVOURITES.forEach(fav::add);
            o.add("favourites", fav);
            JsonArray hist = new JsonArray();
            for (String[] h : HISTORY) {
                JsonArray a = new JsonArray();
                for (String s : h) {
                    a.add(s);
                }
                hist.add(a);
            }
            o.add("history", hist);
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("wardrobe.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("Nightshade").warn("Wardrobe could not be saved", e);
        }
    }

    private static void earn(int amount, String what) {
        points += amount;
        HISTORY.add(0, new String[]{what, (amount >= 0 ? "+" : "") + amount, LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))});
        while (HISTORY.size() > 40) {
            HISTORY.remove(HISTORY.size() - 1);
        }
        dirty = true;
    }

    // ------------------------------------------------------------------ earning

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        ensureLoaded();
        CloudSync.tick(mc);
        if (dirty && System.currentTimeMillis() - lastSave > 5000) {
            save();
        }
        if (mc.player == null || mc.level == null) {
            dailyChecked = false;
            return;
        }
        boolean moved = Math.abs(mc.player.getX() - lastX) > 0.01 || Math.abs(mc.player.getY() - lastY) > 0.01 || Math.abs(mc.player.getZ() - lastZ) > 0.01
                || Math.abs(mc.player.getYRot() - lastYaw) > 0.1 || Math.abs(mc.player.getXRot() - lastPitch) > 0.1
                || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown() || (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen));
        lastX = mc.player.getX();
        lastY = mc.player.getY();
        lastZ = mc.player.getZ();
        lastYaw = mc.player.getYRot();
        lastPitch = mc.player.getXRot();
        long now = System.currentTimeMillis();
        if (moved) {
            lastActivity = now;
            afkNotified = false;
        }
        if (!dailyChecked) {
            dailyChecked = true;
            checkDaily();
        }
        boolean paused = mc.isPaused() || mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen;
        boolean afk = now - lastActivity > AFK_SECONDS * 1000L;
        if (afk && !afkNotified) {
            afkNotified = true;
            notify("Points paused", "You're AFK - move to keep earning");
        }
        if (paused || afk) {
            return;
        }
        activeTicks++;
        if (++ticksTowardPoint >= 1200) {
            ticksTowardPoint = 0;
            if (CloudSync.online()) { // signed in: the service adds the point (and the daily and streak bonuses)
                CloudSync.playedMinute();
                return;
            }
            points++;
            sessionEarned++;
            dirty = true;
            if (sessionEarned % 60 == 0) {
                HISTORY.add(0, new String[]{"Played 1 hour", "+60", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))});
                notify("+60 points", "1 hour played  -  " + String.format("%,d", points) + " total");
                announceAffordable();
            }
        }
    }

    private static void checkDaily() {
        if (CloudSync.online()) {
            return; // the service gives the daily bonus
        }
        String today = LocalDate.now().toString();
        if (today.equals(lastDay)) {
            return;
        }
        String yesterday = LocalDate.now().minusDays(1).toString();
        streak = yesterday.equals(lastDay) ? streak + 1 : 1;
        lastDay = today;
        earn(DAILY, "Daily bonus");
        String msg = "Day " + streak + " streak";
        if (streak % 7 == 0) {
            earn(STREAK, "7-day streak");
            msg += " - +" + STREAK + " streak bonus!";
        } else {
            msg += ", " + (7 - streak % 7) + " more for +" + STREAK;
        }
        notify("Daily bonus +" + DAILY, msg);
        Catalog.Item deal = Shop.featured();
        if (deal != null && !owns(deal)) {
            notify("New in the shop", deal.name + " is " + Shop.FEATURED_DISCOUNT + "% off today");
        }
        List<Catalog.Item> wished = wishlistInShop();
        if (!wished.isEmpty()) { // starred items are your wishlist
            notify("From your wishlist", wished.get(0).name + (wished.size() > 1 ? " and " + (wished.size() - 1) + " more are" : " is") + " in the shop today");
        }
    }

    private static void announceAffordable() {
        for (Catalog.Item i : Catalog.all()) {
            int price = Shop.price(i);
            if (!owns(i) && Shop.available(i) && price <= points && price > points - 60) {
                notify("You can afford " + i.name, "It's in the shop right now - open the Wardrobe to unlock it");
                return;
            }
        }
    }

    private static void notify(String title, String msg) {
        try {
            NightshadeClient.client().notifications().success(title, msg);
        } catch (RuntimeException ignored) {
            // notifications are not ready yet during start-up
        }
    }

    // ------------------------------------------------------------------ queries and actions

    public static int points() {
        ensureLoaded();
        return CloudSync.online() ? CloudSync.points() : points;
    }

    /** This computer's own points and unlocks (moved over to the service once, the first time you sign in). */
    public static int localPoints() {
        ensureLoaded();
        return points;
    }

    public static java.util.Set<String> localOwned() {
        ensureLoaded();
        return new java.util.HashSet<>(OWNED);
    }

    public static long activeMinutes() {
        return activeTicks / 1200;
    }

    public static int streak() {
        return CloudSync.online() ? CloudSync.streak() : streak;
    }

    public static List<String[]> history() {
        return CloudSync.online() && !CloudSync.history().isEmpty() ? CloudSync.history() : HISTORY;
    }

    public static boolean owns(Catalog.Item i) {
        ensureLoaded();
        return i.free() || (CloudSync.online() ? CloudSync.owns(i.id) : OWNED.contains(i.id));
    }

    public static String equipped(Catalog.Category c) {
        ensureLoaded();
        return EQUIPPED.get(c);
    }

    /** Hours of play still needed to afford an item. */
    public static double hoursNeeded(Catalog.Item i) {
        return Math.max(0, Shop.price(i) - points()) / 60.0;
    }

    public static boolean unlock(Catalog.Item i) {
        ensureLoaded();
        if (owns(i)) {
            return true;
        }
        if (CloudSync.online()) {
            return CloudSync.unlock(i); // the service checks the shop and your points
        }
        int price = Shop.price(i);
        if (!Shop.available(i) || points < price) {
            return false; // only what is in the shop right now can be bought
        }
        OWNED.add(i.id);
        earn(-price, "Unlocked " + i.name + (price < i.price ? " (" + Shop.discount(i) + "% off)" : ""));
        save();
        return true;
    }

    /** Buys this week's bundle: the items you don't own yet, at the bundle price. */
    public static boolean unlockBundle() {
        ensureLoaded();
        if (CloudSync.online()) {
            return CloudSync.buyBundle();
        }
        int price = Shop.bundlePrice(Wardrobe::owns);
        List<Catalog.Item> items = Shop.bundle();
        if (items.isEmpty() || items.stream().allMatch(Wardrobe::owns) || points < price) {
            return false;
        }
        for (Catalog.Item i : items) {
            OWNED.add(i.id);
        }
        earn(-price, "Unlocked the weekly bundle (" + Shop.BUNDLE_DISCOUNT + "% off)");
        save();
        return true;
    }

    /** Starred items that are in the shop today and not owned yet. */
    public static List<Catalog.Item> wishlistInShop() {
        List<Catalog.Item> out = new java.util.ArrayList<>();
        for (Catalog.Item i : Shop.listing()) {
            if (favourite(i) && !owns(i)) {
                out.add(i);
            }
        }
        Catalog.Item f = Shop.flash();
        if (f != null && favourite(f) && !owns(f) && !out.contains(f)) {
            out.add(f);
        }
        return out;
    }

    public static boolean equip(Catalog.Item i) {
        if (!owns(i)) {
            return false;
        }
        EQUIPPED.put(i.category, i.id);
        if (i.category == Catalog.Category.CAPES) {
            Cosmetics m = Cosmetics.instance();
            if (m != null) {
                m.setCape(i.classicCape != null ? i.classicCape : "None");
            } else if (i.classicCape != null) {
                NightshadeClient.client().modules().setEnabled(NightshadeClient.client().modules().get(Cosmetics.class), true);
                if (Cosmetics.instance() != null) {
                    Cosmetics.instance().setCape(i.classicCape);
                }
            }
        }
        dirty = true;
        save();
        return true;
    }

    public static void unequip(Catalog.Category c) {
        EQUIPPED.remove(c);
        if (c == Catalog.Category.CAPES && Cosmetics.instance() != null) {
            Cosmetics.instance().setCape("None");
        }
        dirty = true;
        save();
    }

    // ------------------------------------------------------------------ outfits, favourites, random

    /** Saves what is worn now into an outfit slot. */
    public static void saveOutfit(int slot) {
        Map<Catalog.Category, String> of = OUTFIT.get(slot);
        of.clear();
        of.putAll(EQUIPPED);
        dirty = true;
        save();
    }

    /** Puts on a saved outfit; returns false if the slot is empty. */
    public static boolean wearOutfit(int slot) {
        Map<Catalog.Category, String> of = OUTFIT.get(slot);
        if (of.isEmpty()) {
            return false;
        }
        for (Catalog.Category c : Catalog.Category.values()) {
            Catalog.Item it = Catalog.byId(of.get(c));
            if (it != null && owns(it)) {
                equip(it);
            } else {
                unequip(c);
            }
        }
        return true;
    }

    public static boolean hasOutfit(int slot) {
        return !OUTFIT.get(slot).isEmpty();
    }

    /** A few words describing a saved outfit, e.g. "Seraph Wings, Halo". */
    public static String outfitSummary(int slot) {
        List<String> names = new ArrayList<>();
        for (String id : OUTFIT.get(slot).values()) {
            Catalog.Item it = Catalog.byId(id);
            if (it != null) {
                names.add(it.name);
            }
        }
        return names.isEmpty() ? "Empty" : String.join(", ", names);
    }

    public static boolean favourite(Catalog.Item i) {
        return FAVOURITES.contains(i.id);
    }

    public static void toggleFavourite(Catalog.Item i) {
        if (!FAVOURITES.remove(i.id)) {
            FAVOURITES.add(i.id);
        }
        dirty = true;
        save();
    }

    /** Wears a random owned item in every category (sometimes nothing, for capes and auras). */
    public static void randomOutfit(java.util.Random rnd) {
        for (Catalog.Category c : Catalog.Category.values()) {
            List<Catalog.Item> own = new ArrayList<>();
            for (Catalog.Item i : Catalog.in(c)) {
                if (owns(i)) {
                    own.add(i);
                }
            }
            if (own.isEmpty() || (c != Catalog.Category.WINGS && rnd.nextInt(4) == 0)) {
                unequip(c);
            } else {
                equip(own.get(rnd.nextInt(own.size())));
            }
        }
    }

    public static int ownedCount() {
        int n = 0;
        for (Catalog.Item i : Catalog.all()) {
            if (owns(i)) {
                n++;
            }
        }
        return n;
    }

    public static boolean hidden() {
        ensureLoaded();
        return hidden;
    }

    public static void setHidden(boolean h) {
        hidden = h;
        dirty = true;
        save();
    }

    public static void setPreview(Catalog.Category c, String id) {
        if (id == null) {
            PREVIEW.remove(c);
        } else {
            PREVIEW.put(c, id);
        }
    }

    public static void clearPreview() {
        PREVIEW.clear();
    }

    /** What to draw for a render state: the preview picks for the Wardrobe model, otherwise what is equipped. */
    public static String shown(Catalog.Category c, boolean preview) {
        ensureLoaded();
        if (preview && PREVIEW.containsKey(c)) {
            return PREVIEW.get(c);
        }
        return EQUIPPED.get(c);
    }

    // ------------------------------------------------------------------ owner tools (OwnerKey must be present)

    public static void ownerAdd(int amount) {
        if (OwnerKey.present()) {
            if (CloudSync.online()) {
                CloudSync.admin("points_add", amount, OwnerKey.secret());
                return;
            }
            earn(amount, "Owner: added points");
            save();
        }
    }

    public static void ownerSet(int amount) {
        if (OwnerKey.present()) {
            if (CloudSync.online()) {
                CloudSync.admin("points_set", amount, OwnerKey.secret());
                return;
            }
            earn(amount - points, "Owner: set points");
            save();
        }
    }

    public static int ownerUnlockAll() {
        if (!OwnerKey.present()) {
            return 0;
        }
        if (CloudSync.online()) {
            CloudSync.admin("unlock_all", 0, OwnerKey.secret());
        }
        int n = 0;
        for (Catalog.Item i : Catalog.all()) {
            if (OWNED.add(i.id)) {
                n++;
            }
        }
        dirty = true;
        save();
        return n;
    }

    /** Test hooks. */
    public static void tickForTest(int ticks) {
        for (int i = 0; i < ticks; i++) {
            activeTicks++;
            if (++ticksTowardPoint >= 1200) {
                ticksTowardPoint = 0;
                points++;
                dirty = true;
            }
        }
    }

    public static void setActivityForTest(long millisAgo) {
        lastActivity = System.currentTimeMillis() - millisAgo;
    }
}
