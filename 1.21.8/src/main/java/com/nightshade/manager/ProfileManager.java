package com.nightshade.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nightshade.module.Module;
import com.nightshade.util.Log;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Named setups ("PvP", "Building", ...): a profile is a snapshot of every module's enabled state, keybind,
 * settings and HUD layout, stored in {@code config/nightshade/profiles/<name>.json}.
 * <p>
 * Servers can be linked to a profile; it loads automatically when you join that server and the setup you had
 * before is restored when you leave.
 */
public final class ProfileManager {
    private final ClientManager client;
    private final Path dir;
    private final Path serversFile;
    private final Map<String, String> serverProfiles = new LinkedHashMap<>();
    @Nullable
    private JsonObject beforeServerSnapshot;
    @Nullable
    private String activeServerProfile;

    public ProfileManager(ClientManager client, Path configRoot) {
        this.client = client;
        this.dir = configRoot.resolve("profiles");
        this.serversFile = dir.resolve("servers.json");
    }

    public void init() {
        loadServerMap();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(this::onJoin));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(this::onLeave));
        // Fallback: on some versions (26.2) the disconnect event does not fire when the player leaves by themselves,
        // so also restore as soon as a server profile is active but the connection is gone.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (beforeServerSnapshot != null && mc.getConnection() == null) {
                onLeave();
            }
        });
    }

    // ------------------------------------------------------------------ profiles

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9 _-]{1,32}") && !name.isBlank();
    }

    private Path file(String name) {
        return dir.resolve(name.trim().replace(' ', '_') + ".json");
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path path : stream) {
                String file = path.getFileName().toString();
                if (!file.equals("servers.json") && !file.contains(".corrupt-")) {
                    names.add(file.substring(0, file.length() - 5).replace('_', ' '));
                }
            }
        } catch (IOException e) {
            Log.warn("Could not list profiles: {}", e.toString());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Snapshot of every module. */
    public JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject modules = new JsonObject();
        for (Module module : client.modules().getModules()) {
            modules.add(module.getId(), client.config().exportModule(module));
        }
        root.add("modules", modules);
        return root;
    }

    /** Applies a snapshot: settings, keybinds, HUD layout and enabled state of every module it contains. */
    public void apply(JsonObject snapshot) {
        if (!snapshot.has("modules") || !snapshot.get("modules").isJsonObject()) {
            return;
        }
        JsonObject modules = snapshot.getAsJsonObject("modules");
        for (Map.Entry<String, JsonElement> entry : modules.entrySet()) {
            Module module = client.modules().getById(entry.getKey());
            if (module == null || !entry.getValue().isJsonObject()) {
                continue; // module from an older/newer version
            }
            Boolean enabled = client.config().readModuleData(module, entry.getValue().getAsJsonObject());
            if (enabled != null && module.isToggleable() && module.isEnabled() != enabled
                    && (!enabled || client.modules().isCategoryEnabled(module.getCategory()))) {
                client.modules().setEnabled(module, enabled);
            }
            module.markDirty();
        }
    }

    public String save(String name) {
        if (!isValidName(name)) {
            return "Use 1-32 letters, numbers, spaces, _ or -";
        }
        client.config().write(file(name), snapshot());
        return null;
    }

    public boolean load(String name) {
        Path path = file(name);
        if (!Files.exists(path)) {
            return false;
        }
        JsonObject json = client.config().readJson(path);
        if (json == null) {
            client.notifications().error("Profiles", "Profile '" + name + "' is corrupt");
            return false;
        }
        apply(json);
        client.config().saveDirty();
        return true;
    }

    public boolean delete(String name) {
        try {
            boolean deleted = Files.deleteIfExists(file(name));
            serverProfiles.values().removeIf(value -> value.equalsIgnoreCase(name));
            saveServerMap();
            return deleted;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ per-server profiles

    @Nullable
    public static String currentServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        ServerData info = mc.getCurrentServer();
        if (info == null || mc.isLocalServer()) {
            return null;
        }
        return info.ip.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public String profileForServer(String address) {
        return serverProfiles.get(address.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> serverProfiles() {
        return serverProfiles;
    }

    public void linkServer(String address, @Nullable String profile) {
        String key = address.trim().toLowerCase(Locale.ROOT);
        if (profile == null) {
            serverProfiles.remove(key);
        } else {
            serverProfiles.put(key, profile);
        }
        saveServerMap();
    }

    private void onJoin() {
        String address = currentServerAddress();
        if (address == null) {
            return;
        }
        String profile = profileForServer(address);
        if (profile == null || !Files.exists(file(profile))) {
            return;
        }
        beforeServerSnapshot = snapshot();
        if (load(profile)) {
            activeServerProfile = profile;
            client.notifications().info("Profiles", "Loaded '" + profile + "' for " + address);
        } else {
            beforeServerSnapshot = null;
        }
    }

    private void onLeave() {
        if (beforeServerSnapshot != null && activeServerProfile != null
                && com.nightshade.module.interfaces.ProfilesModule.rememberServerChanges()) {
            save(activeServerProfile); // keep what you changed on this server for next time
        }
        if (beforeServerSnapshot != null) {
            apply(beforeServerSnapshot);
            client.config().saveDirty();
            client.notifications().info("Profiles", "Restored your setup from before '" + activeServerProfile + "'");
        }
        beforeServerSnapshot = null;
        activeServerProfile = null;
    }

    @Nullable
    public String activeServerProfile() {
        return activeServerProfile;
    }

    private void loadServerMap() {
        serverProfiles.clear();
        if (!Files.exists(serversFile)) {
            return;
        }
        JsonObject json = client.config().readJson(serversFile);
        if (json == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                serverProfiles.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
            }
        }
    }

    private void saveServerMap() {
        JsonObject json = new JsonObject();
        serverProfiles.forEach(json::addProperty);
        client.config().write(serversFile, json);
    }
}
