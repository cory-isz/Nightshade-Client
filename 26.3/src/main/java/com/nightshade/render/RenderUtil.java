package com.nightshade.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 2D drawing primitives built only on {@link GuiGraphicsExtractor#fill}, so they batch with vanilla GUI rendering. */
public final class RenderUtil {
    private static final int MAX_RADIUS = 12;
    /** Pre-computed horizontal insets per corner row, indexed [radius][row]. */
    private static final int[][] CORNER_INSETS = new int[MAX_RADIUS + 1][];

    static {
        for (int r = 0; r <= MAX_RADIUS; r++) {
            CORNER_INSETS[r] = new int[r];
            for (int row = 0; row < r; row++) {
                double dy = r - row - 0.5;
                CORNER_INSETS[r][row] = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            }
        }
    }

    private RenderUtil() {
    }

    public static void rect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 > x1 && y2 > y1 && ColorUtil.alpha(color) > 0) {
            ctx.fill(x1, y1, x2, y2, color);
        }
    }

    public static void roundedRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0 || ColorUtil.alpha(color) == 0) {
            return;
        }
        int r = Math.max(0, Math.min(Math.min(radius, MAX_RADIUS), Math.min(w, h) / 2));
        if (r == 0) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        int[] insets = CORNER_INSETS[r];
        for (int row = 0; row < r; row++) {
            int inset = insets[row];
            ctx.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            ctx.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
    }

    /** One pixel rounded outline. */
    public static void roundedOutline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 1 || h <= 1 || ColorUtil.alpha(color) == 0) {
            return;
        }
        int r = Math.max(0, Math.min(Math.min(radius, MAX_RADIUS), Math.min(w, h) / 2));
        if (r == 0) {
            ctx.outline(x1, y1, w, h, color);
            return;
        }
        int[] insets = CORNER_INSETS[r];
        // Straight edges.
        ctx.fill(x1 + r, y1, x2 - r, y1 + 1, color);
        ctx.fill(x1 + r, y2 - 1, x2 - r, y2, color);
        ctx.fill(x1, y1 + r, x1 + 1, y2 - r, color);
        ctx.fill(x2 - 1, y1 + r, x2, y2 - r, color);
        // Corner arcs: row k covers [inset_k, inset_{k-1}) so consecutive rows stay connected.
        for (int k = 0; k < r; k++) {
            int start = insets[k];
            int end = k == 0 ? r : Math.max(start + 1, insets[k - 1]);
            ctx.fill(x1 + start, y1 + k, x1 + end, y1 + k + 1, color);
            ctx.fill(x2 - end, y1 + k, x2 - start, y1 + k + 1, color);
            ctx.fill(x1 + start, y2 - k - 1, x1 + end, y2 - k, color);
            ctx.fill(x2 - end, y2 - k - 1, x2 - start, y2 - k, color);
        }
    }

    public static void outline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 > 0 && y2 - y1 > 0) {
            ctx.outline(x1, y1, x2 - x1, y2 - y1, color);
        }
    }

    public static void horizontalGradient(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int left, int right) {
        int w = x2 - x1;
        if (w <= 0 || y2 <= y1) {
            return;
        }
        for (int i = 0; i < w; i++) {
            ctx.fill(x1 + i, y1, x1 + i + 1, y2, ColorUtil.lerp(left, right, i / (float) Math.max(1, w - 1)));
        }
    }

    public static boolean isHovered(double mouseX, double mouseY, int x1, int y1, int x2, int y2) {
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }
}
