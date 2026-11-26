package com.nightshade.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Per-server data the server browser keeps: when you last joined each server and which folder it is in. */
public final class ServerMeta {
    private static final Gson GSON = new Gson();
    private static JsonObject data;

    private ServerMeta() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("servers.json");
    }

    private static String key(String ip) {
        return ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT);
    }

    private static synchronized JsonObject data() {
        if (data == null) {
            try {
                Path f = file();
                data = Files.isRegularFile(f) ? GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class) : new JsonObject();
            } catch (Exception e) {
                data = new JsonObject();
            }
            if (data == null) {
                data = new JsonObject();
            }
        }
        return data;
    }

    private static JsonObject entry(String ip) {
        JsonObject d = data();
        String k = key(ip);
        if (!d.has(k) || !d.get(k).isJsonObject()) {
            d.add(k, new JsonObject());
        }
        return d.getAsJsonObject(k);
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(data()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.warn("Could not save server data: " + e.getMessage());
        }
    }

    public static synchronized void recordJoin(String ip) {
        entry(ip).addProperty("last", System.currentTimeMillis());
        save();
    }

    /** When you last joined the server (ms), or 0 if never. */
    public static synchronized long lastJoined(String ip) {
        JsonObject d = data();
        String k = key(ip);
        return d.has(k) && d.getAsJsonObject(k).has("last") ? d.getAsJsonObject(k).get("last").getAsLong() : 0L;
    }

    /** The folder the server is in, or "" for none. */
    public static synchronized String folder(String ip) {
        JsonObject d = data();
        String k = key(ip);
        return d.has(k) && d.getAsJsonObject(k).has("folder") ? d.getAsJsonObject(k).get("folder").getAsString() : "";
    }

    public static synchronized void setFolder(String ip, String folder) {
        if (folder == null || folder.isBlank()) {
            entry(ip).remove("folder");
        } else {
            entry(ip).addProperty("folder", folder.trim());
        }
        save();
    }

    /** Every folder name in use, sorted. */
    public static synchronized java.util.List<String> folders() {
        TreeSet<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, com.google.gson.JsonElement> e : data().entrySet()) {
            if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("folder")) {
                out.add(e.getValue().getAsJsonObject().get("folder").getAsString());
            }
        }
        return new java.util.ArrayList<>(out);
    }
}
