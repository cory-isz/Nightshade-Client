package com.nightshade.mods;

import com.nightshade.gui.ClickGuiScreen;
import com.nightshade.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Finds configuration for mods: in-game config screens through the widely used Mod Menu API ("modmenu"
 * entrypoint, resolved reflectively so Mod Menu is optional) and config files in the config folder.
 */
public final class ModConfigScreens {
    private static final List<String> TEXT_EXTENSIONS = List.of(".json", ".json5", ".jsonc", ".toml", ".properties", ".cfg", ".conf", ".txt", ".yml", ".yaml", ".ini");
    private Map<String, Function<Screen, Screen>> factories;

    /** Returns a factory creating the mod's config screen, or null if the mod provides none. */
    @Nullable
    public Function<Screen, Screen> factory(String modId) {
        if ("nightshade".equals(modId)) {
            return ClickGuiScreen::new;
        }
        if (factories == null) {
            factories = discover();
        }
        return factories.get(modId);
    }

    private static Map<String, Function<Screen, Screen>> discover() {
        Map<String, Function<Screen, Screen>> map = new HashMap<>();
        List<EntrypointContainer<Object>> containers;
        try {
            containers = FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class);
        } catch (Throwable t) {
            return map;
        }
        for (EntrypointContainer<Object> container : containers) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                Object api = container.getEntrypoint();
                Method single = api.getClass().getMethod("getModConfigScreenFactory");
                Object factory = single.invoke(api);
                Function<Screen, Screen> function = wrap(factory);
                if (function != null) {
                    map.put(modId, function);
                }
                Method provided = api.getClass().getMethod("getProvidedConfigScreenFactories");
                Object others = provided.invoke(api);
                if (others instanceof Map<?, ?> otherMap) {
                    for (Map.Entry<?, ?> entry : otherMap.entrySet()) {
                        Function<Screen, Screen> other = wrap(entry.getValue());
                        if (other != null && entry.getKey() instanceof String id) {
                            map.putIfAbsent(id, other);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.warn("Config screen of {} unavailable: {}", modId, t.toString());
            }
        }
        return map;
    }

    @Nullable
    private static Function<Screen, Screen> wrap(@Nullable Object factory) {
        if (factory == null) {
            return null;
        }
        Method create = null;
        for (Method method : factory.getClass().getMethods()) {
            if (method.getName().equals("create") && method.getParameterCount() == 1) {
                create = method;
                break;
            }
        }
        if (create == null) {
            return null;
        }
        Method target = create;
        target.setAccessible(true);
        return parent -> {
            try {
                Object screen = target.invoke(factory, parent);
                return screen instanceof Screen s ? s : null;
            } catch (Throwable t) {
                Log.warn("Config screen failed to open: {}", t.toString());
                return null;
            }
        };
    }

    /** Text config files belonging to a mod: files or folders in config/ whose name starts with the mod id. */
    public List<Path> configFiles(String modId) {
        List<Path> result = new ArrayList<>();
        Path configDir = FabricLoader.getInstance().getConfigDir();
        String id = modId.toLowerCase(Locale.ROOT);
        String alt = id.replace('-', '_');
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(name.startsWith(id) || name.startsWith(alt))) {
                    continue;
                }
                if (Files.isDirectory(path)) {
                    collect(path, result, 2);
                } else if (isText(path)) {
                    result.add(path);
                }
            }
        } catch (IOException ignored) {
            // no config folder yet
        }
        return result;
    }

    private static void collect(Path dir, List<Path> out, int depth) throws IOException {
        if (depth < 0) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    collect(path, out, depth - 1);
                } else if (isText(path)) {
                    out.add(path);
                }
            }
        }
    }

    private static boolean isText(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith) && !name.contains(".corrupt-");
    }
}
