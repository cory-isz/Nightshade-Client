package com.nightshade.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nightshade.manager.ConfigManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

/** Stores waypoints per world (server address or singleplayer world name) in config/nightshade/waypoints.json. */
public final class WaypointManager {
    private static final int[] PALETTE = {0xFFB794FF, 0xFF5BE49B, 0xFFFFC857, 0xFF3BA4F0, 0xFFFF5C7A, 0xFFFF9F43};
    private final ConfigManager config;
    private final Path file;
    private final Map<String, List<Waypoint>> byWorld = new LinkedHashMap<>();
    private boolean loaded;

    public WaypointManager(ConfigManager config, Path configRoot) {
        this.config = config;
        this.file = configRoot.resolve("waypoints.json");
    }

    /** Key of the current world, or null when not in a world. */
    @Nullable
    public static String currentWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            return "sp:" + server.getWorldData().getLevelName();
        }
        ServerData info = mc.getCurrentServer();
        return info != null ? "mp:" + info.ip.toLowerCase(java.util.Locale.ROOT) : "mp:unknown";
    }

    @Nullable
    public static String currentDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.dimension().identifier().toString();
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(file)) {
            return;
        }
        JsonObject root = config.readJson(file);
        if (root == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> world : root.entrySet()) {
            if (!world.getValue().isJsonArray()) {
                continue;
            }
            List<Waypoint> list = new ArrayList<>();
            for (JsonElement element : world.getValue().getAsJsonArray()) {
                try {
                    JsonObject o = element.getAsJsonObject();
                    Waypoint waypoint = new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                            o.get("z").getAsInt(), o.get("dimension").getAsString(), (int) Long.parseLong(o.get("color").getAsString(), 16));
                    waypoint.setVisible(!o.has("visible") || o.get("visible").getAsBoolean());
                    list.add(waypoint);
                } catch (RuntimeException ignored) {
                    // skip malformed entries
                }
            }
            byWorld.put(world.getKey(), list);
        }
    }

    /** Waypoints of the current world (all dimensions). */
    public List<Waypoint> current() {
        ensureLoaded();
        String key = currentWorldKey();
        return key == null ? List.of() : byWorld.computeIfAbsent(key, k -> new ArrayList<>());
    }

    /** Waypoints of the current world in the current dimension. */
    public List<Waypoint> inDimension() {
        String dimension = currentDimension();
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : current()) {
            if (waypoint.dimension().equals(dimension)) {
                result.add(waypoint);
            }
        }
        return result;
    }

    /** Adds a waypoint at the player's position. Returns it, or null when not in a world. */
    @Nullable
    public Waypoint addHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || currentWorldKey() == null) {
            return null;
        }
        List<Waypoint> list = current();
        String finalName = name == null || name.isBlank() ? "Waypoint " + (list.size() + 1) : name.trim();
        Waypoint waypoint = new Waypoint(finalName, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(),
                currentDimension(), PALETTE[list.size() % PALETTE.length]);
        list.add(waypoint);
        save();
        return waypoint;
    }

    public void remove(Waypoint waypoint) {
        current().remove(waypoint);
        save();
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> world : byWorld.entrySet()) {
            JsonArray array = new JsonArray();
            for (Waypoint w : world.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name());
                o.addProperty("x", w.x());
                o.addProperty("y", w.y());
                o.addProperty("z", w.z());
                o.addProperty("dimension", w.dimension());
                o.addProperty("color", String.format("%08X", w.color()));
                o.addProperty("visible", w.visible());
                array.add(o);
            }
            if (!array.isEmpty()) {
                root.add(world.getKey(), array);
            }
        }
        config.write(file, root);
    }
}
