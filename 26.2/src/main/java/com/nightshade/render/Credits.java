package com.nightshade.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The "Made and developed by Itz_cory" credit shown on the Nightshade menus. */
public final class Credits {
    public static final String PREFIX = "Made and developed by ";
    public static final String AUTHOR = "Itz_cory";

    private Credits() {
    }

    public static int width() {
        return Fonts.width(PREFIX) + Fonts.width(AUTHOR);
    }

    /** Draws the credit centred on {@code cx}. */
    public static void draw(GuiGraphicsExtractor ctx, int cx, int y, float alpha) {
        drawAt(ctx, cx - width() / 2, y, alpha);
    }

    /** Draws the credit starting at {@code x}. */
    public static void drawAt(GuiGraphicsExtractor ctx, int x, int y, float alpha) {
        Fonts.draw(ctx, PREFIX, x, y, ColorUtil.fade(Theme.TEXT_DIM, alpha), false);
        Fonts.draw(ctx, AUTHOR, x + Fonts.width(PREFIX), y, ColorUtil.fade(Theme.accentLight(), alpha), false);
    }
}
