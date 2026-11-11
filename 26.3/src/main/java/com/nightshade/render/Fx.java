package com.nightshade.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Smooth effects built from small bilinear-filtered textures (soft glow, vignette, drop shadow), tinted at draw time.
 * They complement {@link RenderUtil}'s pixel-exact shapes with gradients that would be far too expensive as fills.
 */
public final class Fx {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("nightshade", "textures/gui/glow.png");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("nightshade", "textures/gui/vignette.png");
    private static final Identifier SHADOW = Identifier.fromNamespaceAndPath("nightshade", "textures/gui/shadow.png");
    private static final int GLOW_SIZE = 128;
    private static final int SHADOW_SIZE = 64;
    /** Texels of the shadow texture between its edge and the card it was blurred from. */
    private static final int SHADOW_INSET = 14;
    private static final int SHADOW_SLICE = 24;

    private Fx() {
    }

    /** A soft round glow centred on (cx, cy). {@code color} carries the tint and peak opacity. */
    public static void glow(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int color) {
        if (!com.nightshade.module.performance.MenuOptimizer.allowGlow()) {
            return;
        }
        if (radius <= 0.5f || ColorUtil.alpha(color) == 0) {
            return;
        }
        int size = Math.max(2, Math.round(radius * 2f));
        int x = Math.round(cx - size / 2f);
        int y = Math.round(cy - size / 2f);
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, 0f, 0f, size, size, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
    }

    /** Like {@link #glow} but positioned with sub-pixel precision (smooth motion instead of 1px steps). */
    public static void glowSmooth(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int color) {
        if (!com.nightshade.module.performance.MenuOptimizer.allowGlow()) {
            return;
        }
        if (radius <= 0.5f || ColorUtil.alpha(color) == 0) {
            return;
        }
        int size = Math.max(2, Math.round(radius * 2f));
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, -size / 2, -size / 2, 0f, 0f, size, size, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
        ctx.pose().popMatrix();
    }

    /**
     * Draws a full-screen texture scaled to cover the screen (plus {@code overscan}), offset by (dx, dy) with
     * sub-pixel precision. Used for slowly drifting background layers.
     */
    public static void cover(GuiGraphicsExtractor ctx, Identifier texture, int texW, int texH, int width, int height, float overscan,
                             float dx, float dy, int color) {
        float scale = Math.max(width / (float) texW, height / (float) texH) * (1f + overscan);
        float w = texW * scale;
        float h = texH * scale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((width - w) / 2f + dx, (height - h) / 2f + dy);
        ctx.pose().scale(scale, scale);
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, texW, texH, texW, texH, texW, texH, color);
        ctx.pose().popMatrix();
    }

    /** An elliptical glow stretched over a rectangle (used for aurora blobs and button halos). */
    public static void glowRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        if (!com.nightshade.module.performance.MenuOptimizer.allowGlow()) {
            return;
        }
        if (x2 <= x1 || y2 <= y1 || ColorUtil.alpha(color) == 0) {
            return;
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, x1, y1, 0f, 0f, x2 - x1, y2 - y1, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
    }

    /** Darkens the screen edges. */
    public static void vignette(GuiGraphicsExtractor ctx, int width, int height, int color) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, width, height, 320, 180, 320, 180, color);
    }

    /** Soft drop shadow around the rectangle (x1, y1)-(x2, y2), spreading {@code spread} pixels outwards. */
    public static void shadow(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int spread, int color) {
        if (ColorUtil.alpha(color) == 0 || spread <= 0) {
            return;
        }
        // Destination slice size: the texture's SHADOW_INSET maps onto `spread`.
        int d = Math.round(spread * SHADOW_SLICE / (float) SHADOW_INSET);
        int ox1 = x1 - spread;
        int oy1 = y1 - spread;
        int ox2 = x2 + spread;
        int oy2 = y2 + spread;
        if (ox2 - ox1 < 2 * d || oy2 - oy1 < 2 * d) {
            d = Math.max(1, Math.min(ox2 - ox1, oy2 - oy1) / 2);
        }
        int s = SHADOW_SLICE;
        int mid = SHADOW_SIZE - 2 * s;
        // corners
        slice(ctx, ox1, oy1, d, d, 0, 0, s, s, color);
        slice(ctx, ox2 - d, oy1, d, d, SHADOW_SIZE - s, 0, s, s, color);
        slice(ctx, ox1, oy2 - d, d, d, 0, SHADOW_SIZE - s, s, s, color);
        slice(ctx, ox2 - d, oy2 - d, d, d, SHADOW_SIZE - s, SHADOW_SIZE - s, s, s, color);
        // edges
        slice(ctx, ox1 + d, oy1, ox2 - ox1 - 2 * d, d, s, 0, mid, s, color);
        slice(ctx, ox1 + d, oy2 - d, ox2 - ox1 - 2 * d, d, s, SHADOW_SIZE - s, mid, s, color);
        slice(ctx, ox1, oy1 + d, d, oy2 - oy1 - 2 * d, 0, s, s, mid, color);
        slice(ctx, ox2 - d, oy1 + d, d, oy2 - oy1 - 2 * d, SHADOW_SIZE - s, s, s, mid, color);
        // centre
        slice(ctx, ox1 + d, oy1 + d, ox2 - ox1 - 2 * d, oy2 - oy1 - 2 * d, s, s, mid, mid, color);
    }

    private static void slice(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int u, int v, int uw, int vh, int color) {
        if (w > 0 && h > 0) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, SHADOW, x, y, u, v, w, h, uw, vh, SHADOW_SIZE, SHADOW_SIZE, color);
        }
    }

    /** Vertical gradient from {@code top} to {@code bottom}. */
    public static void verticalGradient(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int top, int bottom) {
        ctx.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    // ------------------------------------------------------------------ easing

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.4f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    public static float easeInOutSine(float t) {
        return (float) (-(Math.cos(Math.PI * clamp01(t)) - 1.0) / 2.0);
    }

    /** Seconds since an arbitrary epoch, for continuous animations (shimmer, particles). */
    public static float time() {
        return (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000f;
    }
}
