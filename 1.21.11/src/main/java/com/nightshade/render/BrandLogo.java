package com.nightshade.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
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
    public static void draw(GuiGraphics ctx, int x, int y, int size) {
        draw(ctx, x, y, size, 0xFFFFFFFF);
    }

    public static void draw(GuiGraphics ctx, int x, int y, int size, int color) {
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

    /** Replaces the vanilla window/taskbar icon. macOS has no per-window icons (GLFW rejects the call), so it is skipped. */
    public static void applyWindowIcon(Minecraft mc) {
        if (Util.getPlatform() == Util.OS.OSX) {
            return;
        }
        List<NativeImage> images = new ArrayList<>();
        List<ByteBuffer> buffers = new ArrayList<>();
        try (GLFWImage.Buffer icons = GLFWImage.malloc(WINDOW_SIZES.length)) {
            for (int i = 0; i < WINDOW_SIZES.length; i++) {
                int s = WINDOW_SIZES[i];
                try (InputStream in = BrandLogo.class.getResourceAsStream("/assets/nightshade/textures/gui/logo_" + s + ".png")) {
                    if (in == null) {
                        throw new IllegalStateException("missing logo_" + s + ".png");
                    }
                    NativeImage image = NativeImage.read(in);
                    images.add(image);
                    ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
                    buffers.add(pixels);
                    for (int py = 0; py < image.getHeight(); py++) {
                        for (int px = 0; px < image.getWidth(); px++) {
                            int argb = image.getPixel(px, py);
                            pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                        }
                    }
                    pixels.flip();
                    icons.position(i);
                    icons.width(image.getWidth());
                    icons.height(image.getHeight());
                    icons.pixels(pixels);
                }
            }
            icons.position(0);
            GLFW.glfwSetWindowIcon(mc.getWindow().handle(), icons);
        } catch (Exception e) {
            Log.LOGGER.warn("Could not set the Nightshade window icon", e);
        } finally {
            buffers.forEach(MemoryUtil::memFree);
            images.forEach(NativeImage::close);
        }
    }
}
