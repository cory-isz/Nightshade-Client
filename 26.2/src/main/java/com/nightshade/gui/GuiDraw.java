package com.nightshade.gui;

import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared widget drawing for the client GUI so every control looks consistent. */
public final class GuiDraw {
    public static final int SWITCH_W = 22;
    public static final int SWITCH_H = 11;

    private GuiDraw() {
    }

    /** Animated toggle switch; {@code progress} is 0 (off) .. 1 (on). */
    public static void toggle(GuiGraphicsExtractor ctx, int x, int y, float progress, boolean hovered) {
        int track = ColorUtil.lerp(Theme.SWITCH_OFF, Theme.accent(), progress);
        if (hovered) {
            track = ColorUtil.lerp(track, 0xFFFFFFFF, 0.08f);
        }
        RenderUtil.roundedRect(ctx, x, y, x + SWITCH_W, y + SWITCH_H, 5, track);
        int knobX = x + 2 + Math.round((SWITCH_W - SWITCH_H) * progress);
        RenderUtil.roundedRect(ctx, knobX, y + 2, knobX + SWITCH_H - 4, y + SWITCH_H - 2, 3, 0xFFFFFFFF);
    }

    /** Rounded pill button with centred label. Returns its width. */
    public static int pill(GuiGraphicsExtractor ctx, int x, int y, String label, int height, boolean hovered, int textColor, int fill) {
        int w = Fonts.width(label) + 10;
        int color = hovered ? ColorUtil.lerp(fill, 0xFFFFFFFF, 0.1f) : fill;
        RenderUtil.roundedRect(ctx, x, y, x + w, y + height, 3, color);
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + height, 3, hovered ? Theme.BORDER_STRONG : Theme.BORDER);
        Fonts.draw(ctx, label, x + 5, y + (height - Fonts.height()) / 2 + 1, textColor, false);
        return w;
    }

    public static int pillWidth(String label) {
        return Fonts.width(label) + 10;
    }

    /** Small floating tooltip clamped to the screen. */
    public static void tooltip(GuiGraphicsExtractor ctx, String text, int mouseX, int mouseY, int screenW, int screenH) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int w = Fonts.width(text) + 8;
        int h = Fonts.height() + 6;
        int x = Math.min(mouseX + 8, screenW - w - 2);
        int y = Math.max(2, Math.min(mouseY - h - 2, screenH - h - 2));
        ctx.pose().pushMatrix();
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, 3, com.nightshade.render.Theme.tint(0xF20E081A));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, 3, Theme.BORDER_STRONG);
        Fonts.draw(ctx, text, x + 4, y + 4, Theme.TEXT, false);
        ctx.pose().popMatrix();
    }
}
