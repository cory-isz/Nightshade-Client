package com.nightshade.mods;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads mod icons into GPU textures on demand and releases them when the mod screen closes. */
public final class ModIcons {
    public record Icon(Identifier id, int width, int height) {
    }

    private final ModFileManager files;
    private final Map<String, Optional<Icon>> cache = new HashMap<>();
    private int counter;

    public ModIcons(ModFileManager files) {
        this.files = files;
    }

    @Nullable
    public Icon get(ModEntry entry) {
        String key = entry.id() + (entry.loaded() ? "" : ":disabled");
        return cache.computeIfAbsent(key, k -> load(entry)).orElse(null);
    }

    private Optional<Icon> load(ModEntry entry) {
        if (entry.iconPath() == null) {
            return Optional.empty();
        }
        try (InputStream in = open(entry)) {
            if (in == null) {
                return Optional.empty();
            }
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("nightshade", "modicon/" + sanitize(entry.id()) + "_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "nightshade mod icon " + entry.id(), image));
            return Optional.of(new Icon(id, image.getWidth(), image.getHeight()));
        } catch (Exception e) {
            Log.warn("Could not load icon for {}: {}", entry.id(), e.toString());
            return Optional.empty();
        }
    }

    @Nullable
    private InputStream open(ModEntry entry) throws Exception {
        if (entry.loaded()) {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(entry.id());
            if (container.isEmpty()) {
                return null;
            }
            Optional<Path> path = container.get().findPath(entry.iconPath());
            return path.isPresent() ? Files.newInputStream(path.get()) : null;
        }
        byte[] bytes = files.readFromJar(entry.jar(), entry.iconPath());
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    private static String sanitize(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    /** Frees every loaded icon texture. */
    public void release() {
        for (Optional<Icon> icon : cache.values()) {
            icon.ifPresent(value -> Minecraft.getInstance().getTextureManager().release(value.id()));
        }
        cache.clear();
    }
}
