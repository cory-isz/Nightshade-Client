package com.nightshade.mods;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.net.Http;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Downloads PNG icons from URLs asynchronously and uploads them as textures; released when the screen closes. */
public final class RemoteIcons {
    private final HttpClient http = Http.client(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, ModIcons.Icon> loaded = new HashMap<>();
    private final Map<String, Boolean> requested = new HashMap<>();
    private int counter;
    private volatile boolean closed;

    @Nullable
    public ModIcons.Icon get(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        ModIcons.Icon icon = loaded.get(url);
        if (icon != null || requested.putIfAbsent(url, Boolean.TRUE) != null) {
            return icon;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
                byte[] bytes = http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
                // NativeImage only decodes PNG; other formats (webp/gif) fall back to a letter icon.
                return bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' ? bytes : null;
            } catch (Exception e) {
                return null;
            }
        }, Util.nonCriticalIoPool()).thenAccept(bytes -> {
            if (bytes == null) {
                return;
            }
            Minecraft.getInstance().execute(() -> {
                if (closed) {
                    return; // screen already closed: do not create textures nobody will free
                }
                try {
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                    Identifier id = Identifier.fromNamespaceAndPath("nightshade", "remote_icon/" + (counter++));
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "nightshade remote icon", image));
                    loaded.put(url, new ModIcons.Icon(id, image.getWidth(), image.getHeight()));
                } catch (Exception ignored) {
                    // undecodable icon: keep the fallback
                }
            });
        });
        return null;
    }

    public void release() {
        closed = true;
        for (ModIcons.Icon icon : loaded.values()) {
            Minecraft.getInstance().getTextureManager().release(icon.id());
        }
        loaded.clear();
        requested.clear();
    }
}
