package com.nightshade.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightshade.util.Log;
import com.nightshade.util.NotificationManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lists installed mods and edits the instance's mods folder. Successful changes are shown by the mod manager's
 * state badges and restart banner; only failures raise notifications.
 * <p>
 * Enabling/disabling renames {@code x.jar} <-> {@code x.jar.disabled}; changes apply on the next launch because a
 * running game cannot unload mods. On Windows the running game keeps loaded jars locked, so changes that cannot be
 * made immediately are queued and applied by a small PowerShell helper that waits for the game process to exit.
 */
public final class ModFileManager {
    private static final Set<String> BUILTIN = Set.of("minecraft", "java", "fabricloader", "mixinextras");
    private static final String DISABLED_SUFFIX = ".disabled";

    private final NotificationManager notifications;
    private final Path modsDir;
    private final Path pendingFile;
    /** Session changes keyed by the mod's original jar path. */
    private final Map<Path, ModEntry.State> sessionStates = new HashMap<>();
    /** File operations that must wait until the game exits (Windows file locks). */
    private final List<String[]> deferred = new ArrayList<>();

    public ModFileManager(Path configRoot, NotificationManager notifications) {
        this.notifications = notifications;
        this.modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        this.pendingFile = configRoot.resolve("pending-mod-changes.txt");
        try {
            Files.deleteIfExists(pendingFile); // the previous session's queue has been applied by now
        } catch (IOException ignored) {
            // informational file only
        }
    }

    public Path modsDir() {
        return modsDir;
    }

    public int pendingChanges() {
        return sessionStates.size();
    }

    // ------------------------------------------------------------------ scanning

    public List<ModEntry> scan(boolean includeLibraries) {
        List<ModEntry> result = new ArrayList<>();
        Map<String, List<String>> dependents = dependents();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            boolean nested = container.getContainingMod().isPresent();
            if (nested && !includeLibraries) {
                continue;
            }
            result.add(fromContainer(container, nested, dependents));
        }
        try (DirectoryStream<Path> stream = Files.isDirectory(modsDir) ? Files.newDirectoryStream(modsDir, "*.jar" + DISABLED_SUFFIX) : null) {
            if (stream != null) {
                for (Path file : stream) {
                    ModEntry entry = fromDisabledJar(file);
                    if (entry != null) {
                        result.add(entry);
                    }
                }
            }
        } catch (IOException e) {
            Log.warn("Could not list mods folder: {}", e.toString());
        }
        result.sort(Comparator.comparing((ModEntry e) -> e.builtin()).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return result;
    }

    private Map<String, List<String>> dependents() {
        Map<String, List<String>> map = new HashMap<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            for (ModDependency dependency : container.getMetadata().getDependencies()) {
                if (dependency.getKind() == ModDependency.Kind.DEPENDS) {
                    map.computeIfAbsent(dependency.getModId(), k -> new ArrayList<>()).add(container.getMetadata().getName());
                }
            }
        }
        return map;
    }

    private ModEntry fromContainer(ModContainer container, boolean nested, Map<String, List<String>> dependents) {
        ModMetadata meta = container.getMetadata();
        Path jar = topLevelJar(container);
        ModEntry.State state = jar != null ? sessionStates.getOrDefault(jar, ModEntry.State.ENABLED) : ModEntry.State.ENABLED;
        Map<String, String> contact = new LinkedHashMap<>();
        ContactInformation info = meta.getContact();
        info.get("homepage").ifPresent(v -> contact.put("Website", v));
        info.get("sources").ifPresent(v -> contact.put("Source", v));
        info.get("issues").ifPresent(v -> contact.put("Issues", v));
        List<String> authors = new ArrayList<>();
        for (Person person : meta.getAuthors()) {
            authors.add(person.getName());
        }
        return new ModEntry(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), meta.getDescription(),
                authors, new ArrayList<>(meta.getLicense()), contact, meta.getEnvironment().name().toLowerCase(Locale.ROOT),
                meta.getIconPath(64).orElse(null), jar, true, BUILTIN.contains(meta.getId()) || isBuiltinOrigin(container),
                nested, state, dependents.getOrDefault(meta.getId(), List.of()));
    }

    private static boolean isBuiltinOrigin(ModContainer container) {
        return container.getOrigin().getKind() != ModOrigin.Kind.PATH;
    }

    /** The jar in the mods folder this mod was loaded from, or null (dev classpath, nested jar, other folders). */
    @Nullable
    private Path topLevelJar(ModContainer container) {
        if (container.getContainingMod().isPresent() || container.getOrigin().getKind() != ModOrigin.Kind.PATH) {
            return null;
        }
        List<Path> paths = container.getOrigin().getPaths();
        if (paths.size() != 1) {
            return null;
        }
        Path path = paths.get(0).toAbsolutePath().normalize();
        return path.getFileName().toString().endsWith(".jar") && modsDir.equals(path.getParent()) ? path : null;
    }

    @Nullable
    private ModEntry fromDisabledJar(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return null;
            }
            JsonObject json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            String id = string(json, "id", file.getFileName().toString());
            ModEntry.State state = sessionStates.getOrDefault(file, ModEntry.State.DISABLED);
            Map<String, String> contact = new LinkedHashMap<>();
            if (json.has("contact") && json.get("contact").isJsonObject()) {
                JsonObject c = json.getAsJsonObject("contact");
                if (c.has("homepage")) {
                    contact.put("Website", c.get("homepage").getAsString());
                }
                if (c.has("sources")) {
                    contact.put("Source", c.get("sources").getAsString());
                }
                if (c.has("issues")) {
                    contact.put("Issues", c.get("issues").getAsString());
                }
            }
            String icon = json.has("icon") && json.get("icon").isJsonPrimitive() ? json.get("icon").getAsString() : null;
            return new ModEntry(id, string(json, "name", id), string(json, "version", "?"), string(json, "description", ""),
                    stringList(json, "authors"), stringList(json, "license"), contact, string(json, "environment", "*"),
                    icon, file, false, false, false, state, List.of());
        } catch (Exception e) {
            Log.warn("Could not read disabled mod {}: {}", file.getFileName(), e.toString());
            return null;
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static List<String> stringList(JsonObject json, String key) {
        List<String> list = new ArrayList<>();
        JsonElement element = json.get(key);
        if (element == null) {
            return list;
        }
        if (element.isJsonPrimitive()) {
            list.add(element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement item : (JsonArray) element) {
                if (item.isJsonPrimitive()) {
                    list.add(item.getAsString());
                } else if (item.isJsonObject() && item.getAsJsonObject().has("name")) {
                    list.add(item.getAsJsonObject().get("name").getAsString());
                }
            }
        }
        return list;
    }

    /** Reads raw bytes of a file inside a disabled jar (used for icons). */
    @Nullable
    public byte[] readFromJar(Path jar, String path) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(path.startsWith("/") ? path.substring(1) : path);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ editing

    /** Disables an enabled mod (takes effect after restart). */
    public boolean disable(ModEntry entry) {
        if (!entry.editable() || entry.state() != ModEntry.State.ENABLED) {
            return false;
        }
        Path target = entry.jar().resolveSibling(entry.jar().getFileName() + DISABLED_SUFFIX);
        move(entry.jar(), target);
        sessionStates.put(entry.jar(), ModEntry.State.PENDING_DISABLE);
        return true;
    }

    /** Re-enables a mod: undoes a pending disable, or renames a disabled jar back to .jar. */
    public boolean enable(ModEntry entry) {
        if (entry.jar() == null) {
            return false;
        }
        if (entry.state() == ModEntry.State.PENDING_DISABLE) {
            Path disabled = entry.jar().resolveSibling(entry.jar().getFileName() + DISABLED_SUFFIX);
            cancelDeferredFor(entry.jar());
            if (Files.exists(disabled)) {
                move(disabled, entry.jar());
            }
            sessionStates.remove(entry.jar());
            return true;
        }
        if (entry.state() == ModEntry.State.DISABLED) {
            String fileName = entry.jar().getFileName().toString();
            Path target = entry.jar().resolveSibling(fileName.substring(0, fileName.length() - DISABLED_SUFFIX.length()));
            if (Files.exists(target)) {
                notifications.error(entry.name(), target.getFileName() + " already exists");
                return false;
            }
            move(entry.jar(), target);
            sessionStates.put(entry.jar(), ModEntry.State.PENDING_ENABLE);
            return true;
        }
        if (entry.state() == ModEntry.State.PENDING_ENABLE) {
            String fileName = entry.jar().getFileName().toString();
            Path renamed = entry.jar().resolveSibling(fileName.substring(0, fileName.length() - DISABLED_SUFFIX.length()));
            move(renamed, entry.jar());
            sessionStates.remove(entry.jar());
            return true;
        }
        return false;
    }

    /** Deletes the mod's jar (immediately, or when the game exits if Windows keeps it locked). */
    public boolean delete(ModEntry entry) {
        if (!entry.editable() && !(entry.jar() != null && !entry.loaded())) {
            return false;
        }
        Path current = currentLocation(entry);
        try {
            Files.deleteIfExists(current);
        } catch (IOException e) {
            deferred.add(new String[]{"delete", current.toString()});
        }
        sessionStates.put(entry.jar(), ModEntry.State.PENDING_DELETE);
        return true;
    }

    private Path currentLocation(ModEntry entry) {
        ModEntry.State state = entry.state();
        if (state == ModEntry.State.PENDING_DISABLE) {
            return entry.jar().resolveSibling(entry.jar().getFileName() + DISABLED_SUFFIX);
        }
        if (state == ModEntry.State.PENDING_ENABLE) {
            String fileName = entry.jar().getFileName().toString();
            return entry.jar().resolveSibling(fileName.substring(0, fileName.length() - DISABLED_SUFFIX.length()));
        }
        return entry.jar();
    }

    private void move(Path from, Path to) {
        try {
            Files.move(from, to);
        } catch (IOException e) {
            Log.info("Deferring move of {} until the game exits ({})", from.getFileName(), e.getClass().getSimpleName());
            deferred.add(new String[]{"move", from.toString(), to.toString()});
        }
    }

    private void cancelDeferredFor(Path jar) {
        deferred.removeIf(op -> op[1].equals(jar.toString()));
    }

    public void openModsFolder() {
        try {
            Files.createDirectories(modsDir);
        } catch (IOException ignored) {
            // opening will report the problem
        }
        com.mojang.blaze3d.Blaze3D.openPath(modsDir);
    }

    /** Applies deferred operations after the game exits (Windows only; elsewhere moves never fail on open files). */
    public void onShutdown() {
        if (deferred.isEmpty()) {
            return;
        }
        StringBuilder script = new StringBuilder();
        script.append("Wait-Process -Id ").append(ProcessHandle.current().pid()).append(" -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 700; ");
        StringBuilder summary = new StringBuilder();
        for (String[] op : deferred) {
            if (op[0].equals("move")) {
                script.append("Move-Item -LiteralPath '").append(ps(op[1])).append("' -Destination '").append(ps(op[2])).append("' -Force; ");
                summary.append("move ").append(op[1]).append(" -> ").append(op[2]).append('\n');
            } else {
                script.append("Remove-Item -LiteralPath '").append(ps(op[1])).append("' -Force; ");
                summary.append("delete ").append(op[1]).append('\n');
            }
        }
        try {
            Files.writeString(pendingFile, summary.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // informational only
        }
        if (Util.getPlatform() != Util.OS.WINDOWS) {
            Log.warn("Some mod file changes could not be applied:\n{}", summary);
            return;
        }
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script.toString())
                    .redirectErrorStream(true).start();
            Log.info("Scheduled {} mod file change(s) to apply after exit", deferred.size());
        } catch (IOException e) {
            Log.error("Could not start helper to apply mod changes; apply them manually:\n" + summary, e);
        }
    }

    private static String ps(String path) {
        return path.replace("'", "''");
    }
}
