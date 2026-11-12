package com.nightshade.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 2D drawing primitives: rectangles, anti-aliased rounded shapes and gradients, in as few quads as possible. */
public final class RenderUtil {
    private static final int MAX_RADIUS = 12;
    private RenderUtil() {
    }

    public static void rect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 > x1 && y2 > y1 && ColorUtil.alpha(color) > 0) {
            ctx.fill(x1, y1, x2, y2, color);
        }
    }

    /**
     * Anti-aliased corners: a filled circle and a 1px ring per radius, drawn at 4 texels per GUI pixel, so a rounded
     * shape is 7-8 quads instead of one fill per pixel row, and its corners are smooth at every GUI scale.
     */
    private static final net.minecraft.resources.Identifier CORNERS =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("nightshade", "textures/gui/corners.png");
    private static final int SS = 4;
    private static final int CELL = 96;
    private static final int TEX_W = CELL * 2;
    private static final int TEX_H = CELL * MAX_RADIUS;
    /** A texel inside the largest filled circle: solid white. */
    private static final int SOLID_U = CELL / 2;
    private static final int SOLID_V = (MAX_RADIUS - 1) * CELL + CELL / 2;

    private static void solid(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 > x1 && y2 > y1) {
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CORNERS, x1, y1, SOLID_U, SOLID_V, x2 - x1, y2 - y1,
                    1, 1, TEX_W, TEX_H, color);
        }
    }

    /** One corner quadrant of radius r: qx/qy 0 = left/top half of the circle, 1 = right/bottom half. */
    private static void corner(GuiGraphicsExtractor ctx, int x, int y, int r, int column, int qx, int qy, int color) {
        int big = r * SS;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CORNERS, x, y, column * CELL + qx * big,
                (r - 1) * CELL + qy * big, r, r, big, big, TEX_W, TEX_H, color);
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
        corner(ctx, x1, y1, r, 0, 0, 0, color);
        corner(ctx, x2 - r, y1, r, 0, 1, 0, color);
        corner(ctx, x1, y2 - r, r, 0, 0, 1, color);
        corner(ctx, x2 - r, y2 - r, r, 0, 1, 1, color);
        solid(ctx, x1 + r, y1, x2 - r, y1 + r, color);
        solid(ctx, x1, y1 + r, x2, y2 - r, color);
        solid(ctx, x1 + r, y2 - r, x2 - r, y2, color);
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
        corner(ctx, x1, y1, r, 1, 0, 0, color);
        corner(ctx, x2 - r, y1, r, 1, 1, 0, color);
        corner(ctx, x1, y2 - r, r, 1, 0, 1, color);
        corner(ctx, x2 - r, y2 - r, r, 1, 1, 1, color);
        solid(ctx, x1 + r, y1, x2 - r, y1 + 1, color);
        solid(ctx, x1 + r, y2 - 1, x2 - r, y2, color);
        solid(ctx, x1, y1 + r, x1 + 1, y2 - r, color);
        solid(ctx, x2 - 1, y1 + r, x2, y2 - r, color);
    }

    public static void outline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 > 0 && y2 - y1 > 0) {
            ctx.outline(x1, y1, x2 - x1, y2 - y1, color);
        }
    }

    public static void horizontalGradient(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int left, int right) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) {
            return;
        }
        // A vertical gradient turned a quarter: one quad instead of one fill per pixel column.
        ctx.pose().pushMatrix();
        ctx.pose().translate(x1, y2);
        ctx.pose().rotate((float) (-Math.PI / 2));
        ctx.fillGradient(0, 0, h, w, left, right);
        ctx.pose().popMatrix();
    }

    public static boolean isHovered(double mouseX, double mouseY, int x1, int y1, int x2, int y2) {
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }
}
