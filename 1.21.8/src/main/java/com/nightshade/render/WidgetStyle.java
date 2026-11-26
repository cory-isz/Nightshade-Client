package com.nightshade.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Nightshade look for vanilla widgets (buttons and sliders in Options, world creation, the pause menu...): rounded
 * glass in the client colour instead of the stone sprites. Toggled by the Main Menu module's Styled Buttons.
 */
public final class WidgetStyle {
    private static volatile boolean enabled = true;

    private WidgetStyle() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void button(GuiGraphics ctx, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        int r = Math.min(5, h / 3);
        float a = Math.max(0f, Math.min(1f, alpha));
        int fill;
        int border;
        if (!active) {
            fill = ColorUtil.withAlpha(Theme.BACKGROUND, 0x70);
            border = 0x18FFFFFF;
        } else if (hovered) {
            fill = ColorUtil.withAlpha(ColorUtil.lerp(Theme.SELECTED, Theme.accent(), 0.25f), 0xE8);
            border = ColorUtil.withAlpha(Theme.accentLight(), 0xE0);
        } else {
            fill = ColorUtil.withAlpha(Theme.PANEL, 0xD8);
            border = 0x38FFFFFF;
        }
        if (active && hovered) {
            Fx.glowRect(ctx, x - 10, y - 6, x + w + 10, y + h + 6, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 0x40), a));
        }
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, r, ColorUtil.fade(fill, a));
        if (w > 8) {
            ctx.fill(x + r, y + 1, x + w - r, y + 2, ColorUtil.fade(active ? 0x1CFFFFFF : 0x0AFFFFFF, a));
        }
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, r, ColorUtil.fade(border, a));
    }

    public static void sliderTrack(GuiGraphics ctx, int x, int y, int w, int h, boolean hovered, float alpha) {
        int r = Math.min(5, h / 3);
        float a = Math.max(0f, Math.min(1f, alpha));
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, r, ColorUtil.fade(ColorUtil.withAlpha(Theme.BACKGROUND, 0xD0), a));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, r, ColorUtil.fade(hovered ? ColorUtil.withAlpha(Theme.accentLight(), 0xB0) : 0x30FFFFFF, a));
    }

    public static void sliderHandle(GuiGraphics ctx, int x, int y, int w, int h, int trackX, boolean hovered, float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        int cx = x + w / 2;
        // Filled part of the track up to the knob (soft, so the label stays readable), then a slim pill knob.
        if (cx > trackX + 3) {
            RenderUtil.roundedRect(ctx, trackX + 2, y + 2, cx, y + h - 2, Math.min(4, h / 3), ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 0x48), a));
        }
        int kw = 3;
        RenderUtil.roundedRect(ctx, cx - kw / 2 - 1, y + 3, cx + kw / 2 + 2, y + h - 3, 2,
                ColorUtil.fade(ColorUtil.withAlpha(hovered ? Theme.accentLight() : Theme.accent(), 0xF0), a));
    }
}
