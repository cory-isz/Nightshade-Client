package com.nightshade.manager;

import com.nightshade.input.Keybind;
import com.nightshade.module.BindMode;
import com.nightshade.module.HudModule;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.ModuleSetting;
import com.nightshade.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON persistence.
 * <pre>
 * config/nightshade/client.json                 global + category settings
 * config/nightshade/modules/&lt;category&gt;/&lt;id&gt;.json per-module state, keybind, settings, HUD layout
 * </pre>
 * Files are written atomically (temp file + move). Changes are debounced and only dirty modules are written, so a
 * valid file is never rewritten with defaults. Corrupt files are preserved as {@code *.corrupt-<time>.json} and the
 * module falls back to defaults.
 */
public final class ConfigManager {
    public static final int CONFIG_VERSION = 1;
    private static final long SAVE_DELAY_MILLIS = 750L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final ClientManager client;
    private final Path root;
    private final Path modulesDir;
    private final Path clientFile;
    private final Set<Module> dirtyModules = new LinkedHashSet<>();
    private boolean clientDirty;
    private boolean loading;
    private long lastChangeMillis;

    /** GUI state persisted in client.json. */
    private ModuleCategory lastCategory = ModuleCategory.HUD;
    /** One-time UI flags (e.g. "tourDone"), persisted in client.json. */
    private final java.util.Set<String> flags = new java.util.HashSet<>();

    public ConfigManager(ClientManager client, Path root) {
        this.client = client;
        this.root = root;
        this.modulesDir = root.resolve("modules");
        this.clientFile = root.resolve("client.json");
    }

    // ------------------------------------------------------------------ dirty tracking

    public void markDirty(Module module) {
        if (loading) {
            return;
        }
        synchronized (dirtyModules) {
            dirtyModules.add(module);
        }
        lastChangeMillis = System.currentTimeMillis();
    }

    public void markClientDirty() {
        if (loading) {
            return;
        }
        clientDirty = true;
        lastChangeMillis = System.currentTimeMillis();
    }

    /** Called every client tick; flushes pending changes once edits have settled. */
    public void tick() {
        if ((clientDirty || !dirtyModules.isEmpty()) && System.currentTimeMillis() - lastChangeMillis >= SAVE_DELAY_MILLIS) {
            saveDirty();
        }
    }

    public boolean hasPendingChanges() {
        return clientDirty || !dirtyModules.isEmpty();
    }

    // ------------------------------------------------------------------ loading

    public void loadAll() {
        loading = true;
        List<Module> toEnable = new ArrayList<>();
        try {
            Files.createDirectories(modulesDir);
            loadClientFile();
            for (Module module : client.modules().getModules()) {
                Boolean enabled = loadModule(module);
                boolean wantEnabled = enabled != null ? enabled : module.isEnabledByDefault();
                if (wantEnabled && module.isToggleable()) {
                    if (client.modules().isCategoryEnabled(module.getCategory())) {
                        toEnable.add(module);
                    } else {
                        client.modules().restoreSuspended(module);
                    }
                }
            }
        } catch (IOException e) {
            Log.error("Could not create config directory " + root, e);
        } finally {
            loading = false;
        }
        // Enable after every module is loaded so requirements/conflicts see final settings.
        loading = true;
        try {
            for (Module module : toEnable) {
                client.modules().setEnabled(module, true);
            }
        } finally {
            loading = false;
        }
        Log.info("Configuration loaded from {}", root);
    }

    private void loadClientFile() {
        if (!Files.exists(clientFile)) {
            clientDirty = true; // create it with defaults on first save
            return;
        }
        JsonObject json = readJson(clientFile);
        if (json == null) {
            clientDirty = true;
            return;
        }
        if (json.has("categories") && json.get("categories").isJsonObject()) {
            JsonObject categories = json.getAsJsonObject("categories");
            for (ModuleCategory category : ModuleCategory.values()) {
                JsonElement element = categories.get(category.id());
                if (element != null && element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    if (object.has("enabled") && object.get("enabled").isJsonPrimitive()) {
                        client.modules().restoreCategoryState(category, object.get("enabled").getAsBoolean());
                    }
                }
            }
        }
        if (json.has("flags") && json.get("flags").isJsonArray()) {
            for (JsonElement flag : json.getAsJsonArray("flags")) {
                flags.add(flag.getAsString());
            }
        }
        if (json.has("gui") && json.get("gui").isJsonObject()) {
            JsonObject gui = json.getAsJsonObject("gui");
            lastCategory = parseEnum(ModuleCategory.class, gui.get("lastCategory"), ModuleCategory.HUD);
        }
    }

    /** Loads one module. Returns the stored enabled flag, or null when no valid file exists. */
    private Boolean loadModule(Module module) {
        Path file = moduleFile(module);
        if (!Files.exists(file)) {
            synchronized (dirtyModules) {
                dirtyModules.add(module); // write a default file on first save
            }
            return null;
        }
        JsonObject json = readJson(file);
        if (json == null) {
            synchronized (dirtyModules) {
                dirtyModules.add(module); // corrupt file was preserved; write a fresh valid one
            }
            return null;
        }
        return readModuleData(module, json);
    }

    /**
     * Applies keybind, bind mode, settings and HUD layout from a module JSON object (the per-module file format,
     * also used inside profiles). Returns the stored enabled flag, or null when absent.
     */
    public Boolean readModuleData(Module module, JsonObject json) {
        if (json.has("keybind")) {
            Keybind bind = Keybind.fromJson(json.get("keybind"));
            if (bind != null) {
                module.setKeybindRaw(bind);
            } else {
                Log.warn("Invalid keybind for module {} ignored", module.getId());
            }
        }
        BindMode mode = parseEnum(BindMode.class, json.get("bindMode"), null);
        if (mode != null) {
            module.setBindMode(mode);
        }
        if (json.has("settings") && json.get("settings").isJsonObject()) {
            JsonObject settings = json.getAsJsonObject("settings");
            for (ModuleSetting<?> setting : module.getSettings()) {
                JsonElement value = settings.get(setting.getName());
                if (value != null && !setting.fromJson(value)) {
                    Log.warn("Invalid value for {}.{} ignored", module.getId(), setting.getName());
                }
            }
        }
        if (module instanceof HudModule hud && json.has("hud") && json.get("hud").isJsonObject()) {
            hud.readHud(json.getAsJsonObject("hud"));
        }
        if (json.has("enabled") && json.get("enabled").isJsonPrimitive()) {
            return json.get("enabled").getAsBoolean();
        }
        return null;
    }

    /** Reads a JSON object; a corrupt file is moved aside and null is returned. */
    public JsonObject readJson(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(content);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
            throw new IOException("Root element is not an object");
        } catch (Exception e) {
            Path backup = file.resolveSibling(file.getFileName().toString().replace(".json", "")
                    + ".corrupt-" + System.currentTimeMillis() + ".json");
            try {
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                Log.warn("Corrupt config {} moved to {} ({})", file, backup.getFileName(), e.getMessage());
            } catch (IOException moveError) {
                Log.error("Could not back up corrupt config " + file, moveError);
            }
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, JsonElement element, E fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, element.getAsString());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ saving

    public void saveDirty() {
        List<Module> pending;
        synchronized (dirtyModules) {
            pending = new ArrayList<>(dirtyModules);
            dirtyModules.clear();
        }
        for (Module module : pending) {
            saveModule(module);
        }
        if (clientDirty) {
            clientDirty = false;
            saveClientFile();
        }
    }

    /** Writes every module and the client file (explicit "Save" button and shutdown). */
    public void saveAll() {
        synchronized (dirtyModules) {
            dirtyModules.clear();
        }
        for (Module module : client.modules().getModules()) {
            saveModule(module);
        }
        clientDirty = false;
        saveClientFile();
    }

    private void saveModule(Module module) {
        write(moduleFile(module), exportModule(module));
    }

    /** Serialises one module's state, keybind, settings and HUD layout. */
    public JsonObject exportModule(Module module) {
        JsonObject json = new JsonObject();
        json.addProperty("version", CONFIG_VERSION);
        json.addProperty("enabled", module.isEnabled() || client.modules().isSuspended(module));
        json.add("keybind", module.getKeybind().toJson());
        json.addProperty("bindMode", module.getBindMode().name());
        JsonObject settings = new JsonObject();
        for (ModuleSetting<?> setting : module.getSettings()) {
            settings.add(setting.getName(), setting.toJson());
        }
        json.add("settings", settings);
        if (module instanceof HudModule hud) {
            json.add("hud", hud.writeHud());
        }
        return json;
    }

    private void saveClientFile() {
        JsonObject json = new JsonObject();
        json.addProperty("version", CONFIG_VERSION);
        JsonObject categories = new JsonObject();
        for (ModuleCategory category : ModuleCategory.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("enabled", client.modules().isCategoryEnabled(category));
            JsonArray suspended = new JsonArray();
            for (Module module : client.modules().getByCategory(category)) {
                if (client.modules().isSuspended(module)) {
                    suspended.add(module.getId());
                }
            }
            object.add("suspended", suspended);
            categories.add(category.id(), object);
        }
        json.add("categories", categories);
        JsonObject gui = new JsonObject();
        gui.addProperty("lastCategory", lastCategory.name());
        json.add("gui", gui);
        JsonArray flagArray = new JsonArray();
        flags.forEach(flagArray::add);
        json.add("flags", flagArray);
        write(clientFile, json);
    }

    /** Atomic JSON write (also used by profiles and other client data files). */
    public void write(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("Failed to write config " + file, e);
        }
    }

    private Path moduleFile(Module module) {
        return modulesDir.resolve(module.getCategory().id()).resolve(module.getId() + ".json");
    }

    // ------------------------------------------------------------------ GUI state

    public ModuleCategory getLastCategory() {
        return lastCategory;
    }

    public void setGuiState(ModuleCategory category) {
        if (category != lastCategory) {
            lastCategory = category;
            markClientDirty();
        }
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void setFlag(String flag) {
        if (flags.add(flag)) {
            markClientDirty();
        }
    }

    public Path getRoot() {
        return root;
    }
}
