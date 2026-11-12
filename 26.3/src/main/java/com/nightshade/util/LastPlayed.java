package com.nightshade.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Remembers the last world or server joined, so the main menu can offer a one-click "Continue". Stored in
 * config/nightshade/last_played.json; nothing leaves the computer.
 */
public final class LastPlayed {
    public enum Kind { WORLD, SERVER }

    /** id = world folder name for worlds, address for servers. */
    public record Entry(Kind kind, String id, String name, long time) {
    }

    private static final Gson GSON = new Gson();
    private static Entry cached;
    private static boolean loaded;

    private LastPlayed() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("last_played.json");
    }

    public static synchronized Entry get() {
        if (!loaded) {
            loaded = true;
            try {
                Path f = file();
                if (Files.isRegularFile(f)) {
                    JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
                    cached = new Entry(Kind.valueOf(o.get("kind").getAsString()), o.get("id").getAsString(),
                            o.get("name").getAsString(), o.get("time").getAsLong());
                }
            } catch (Exception e) {
                cached = null; // missing or damaged: simply no Continue button
            }
        }
        if (cached != null && cached.kind() == Kind.WORLD
                && !Files.isDirectory(Minecraft.getInstance().gameDirectory.toPath().resolve("saves").resolve(cached.id()))) {
            return null; // the world was deleted or renamed
        }
        return cached;
    }

    /** Called when a world or server has been joined. */
    public static synchronized void recordJoin(Minecraft mc) {
        try {
            Entry entry = null;
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                String folder = root.getFileName().toString();
                entry = new Entry(Kind.WORLD, folder, server.getWorldData().getLevelName(), System.currentTimeMillis());
            } else {
                ServerData data = mc.getCurrentServer();
                if (data != null && data.ip != null && !data.ip.isBlank()) {
                    ServerMeta.recordJoin(data.ip);
                    entry = new Entry(Kind.SERVER, data.ip, data.name == null || data.name.isBlank() ? data.ip : data.name, System.currentTimeMillis());
                }
            }
            if (entry == null) {
                return;
            }
            cached = entry;
            loaded = true;
            JsonObject o = new JsonObject();
            o.addProperty("kind", entry.kind().name());
            o.addProperty("id", entry.id());
            o.addProperty("name", entry.name());
            o.addProperty("time", entry.time());
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.warn("Could not remember the last played world: " + e.getMessage());
        }
    }
}
