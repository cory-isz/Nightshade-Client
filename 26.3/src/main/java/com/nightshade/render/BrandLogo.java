package com.nightshade.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The Nightshade client logo (moon + nightshade flower). Pre-scaled copies are shipped so the GUI always samples a
 * texture close to the on-screen pixel size instead of minifying the 256px original with nearest filtering.
 */
public final class BrandLogo {
    private static final int[] SIZES = {16, 32, 48, 64, 128, 256};
    private static final int[] WINDOW_SIZES = {16, 32, 48, 128, 256};

    private BrandLogo() {
    }

    private static Identifier texture(int size) {
        return Identifier.fromNamespaceAndPath("nightshade", "textures/gui/logo_" + size + ".png");
    }

    /** Draws the logo as a {@code size} x {@code size} square in GUI coordinates. */
    public static void draw(GuiGraphicsExtractor ctx, int x, int y, int size) {
        draw(ctx, x, y, size, 0xFFFFFFFF);
    }

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, int size, int color) {
        Minecraft mc = Minecraft.getInstance();
        double pixels = size * mc.getWindow().getGuiScale();
        // The current matrix may scale further (e.g. the main-menu brand); account for it.
        pixels *= Math.max(Math.abs(ctx.pose().m00()), 1e-3);
        int pick = SIZES[SIZES.length - 1];
        for (int s : SIZES) {
            if (s >= pixels) {
                pick = s;
                break;
            }
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture(pick), x, y, 0f, 0f, size, size, pick, pick, pick, pick, color);
    }

    /** Replaces the vanilla window/taskbar icon (SDL in Minecraft 26.3+). Skipped on macOS, where it would change the Dock icon. */
    public static void applyWindowIcon(Minecraft mc) {
        if (Util.getPlatform() == Util.OS.OSX) {
            return;
        }
        ByteBuffer pixels = null;
        org.lwjgl.sdl.SDL_Surface surface = null;
        try (InputStream in = BrandLogo.class.getResourceAsStream("/assets/nightshade/textures/gui/logo_256.png")) {
            if (in == null) {
                throw new IllegalStateException("missing logo_256.png");
            }
            try (NativeImage image = NativeImage.read(in)) {
                int w = image.getWidth();
                int h = image.getHeight();
                pixels = MemoryUtil.memAlloc(w * h * 4);
                for (int py = 0; py < h; py++) {
                    for (int px = 0; px < w; px++) {
                        int argb = image.getPixel(px, py);
                        pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                    }
                }
                pixels.flip();
                surface = org.lwjgl.sdl.SDLSurface.SDL_CreateSurfaceFrom(w, h, org.lwjgl.sdl.SDLPixels.SDL_PIXELFORMAT_RGBA32, pixels, w * 4);
                if (surface == null || !org.lwjgl.sdl.SDLVideo.SDL_SetWindowIcon(mc.getWindow().handle(), surface)) {
                    Log.LOGGER.warn("SDL refused the Nightshade window icon: {}", org.lwjgl.sdl.SDLError.SDL_GetError());
                }
            }
        } catch (Exception e) {
            Log.LOGGER.warn("Could not set the Nightshade window icon", e);
        } finally {
            if (surface != null) {
                org.lwjgl.sdl.SDLSurface.SDL_DestroySurface(surface);
            }
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }
        }
    }
}
