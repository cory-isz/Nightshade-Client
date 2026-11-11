package com.nightshade.gui.menu;

import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Glass tiles with an icon and a label, shared by the Nightshade pause and options screens. */
final class MenuTiles {
    record Tile(String id, String label, String sub, String[] icon, Runnable action, boolean enabled, boolean danger) {
    }

    private record Hit(int x1, int y1, int x2, int y2, Tile tile) {
    }

    private final Map<String, Animation> hover = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private String tooltip;

    void beginFrame() {
        hits.clear();
        tooltip = null;
    }

    /** Description of the hovered small tile this frame, or null. */
    String tooltip() {
        return tooltip;
    }

    /** Draws one tile; {@code big} puts the label beside a larger icon (with a subtitle), otherwise icon above label. */
    void draw(GuiGraphicsExtractor ctx, Tile tile, int x, int y, int w, int h, int mouseX, int mouseY, float appear, boolean big) {
        boolean hovered = tile.enabled() && RenderUtil.isHovered(mouseX, mouseY, x, y, x + w, y + h);
        float p = hover.computeIfAbsent(tile.id(), k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
        float a = tile.enabled() ? appear : appear * 0.5f;
        int lift = Math.round((1f - appear) * 10f) - Math.round(p * 2f);
        int y1 = y + lift;
        int accent = tile.danger() ? 0xFFFF6B88 : Theme.accent();
        Fx.shadow(ctx, x, y1 + 2, x + w, y1 + h + 2, 6, ColorUtil.fade(0x90000000, a));
        if (p > 0.01f) {
            Fx.glowRect(ctx, x - 20, y1 - 14, x + w + 20, y1 + h + 14, ColorUtil.fade(ColorUtil.withAlpha(accent, Math.round(0x40 * p)), a));
        }
        RenderUtil.roundedRect(ctx, x, y1, x + w, y1 + h, 6, ColorUtil.fade(ColorUtil.lerp(ColorUtil.withAlpha(Theme.PANEL, 0xB8),
                ColorUtil.withAlpha(Theme.SELECTED, 0xE0), p), a));
        ctx.fill(x + 6, y1 + 1, x + w - 6, y1 + 2, ColorUtil.fade(ColorUtil.withAlpha(0xFFFFFF, 0x14 + Math.round(0x20 * p)), a));
        RenderUtil.roundedOutline(ctx, x, y1, x + w, y1 + h, 6, ColorUtil.fade(ColorUtil.lerp(0x2EFFFFFF, ColorUtil.withAlpha(accent, 0xD0), p), a));
        int iconColor = ColorUtil.fade(ColorUtil.lerp(0xFFD6D0EE, 0xFFFFFFFF, p), a);
        int iconAccent = ColorUtil.fade(tile.danger() ? 0xFFFF9DB0 : Theme.accentLight(), a);
        int textColor = ColorUtil.fade(ColorUtil.lerp(tile.danger() ? 0xFFE7A1B0 : Theme.TEXT_MUTED, Theme.TEXT, p), a);
        if (big) {
            int chip = Math.min(26, h - 10);
            int cx = x + 10;
            int cy = y1 + (h - chip) / 2;
            RenderUtil.roundedRect(ctx, cx, cy, cx + chip, cy + chip, 5, ColorUtil.fade(ColorUtil.withAlpha(accent, 0x48 + Math.round(0x40 * p)), a));
            int scale = chip >= 22 ? 2 : 1;
            PixelIcons.menu(ctx, tile.icon(), cx + (chip - 9 * scale) / 2, cy + (chip - 9 * scale) / 2, scale, iconColor, iconAccent);
            int tx = cx + chip + 10 + Math.round(p * 2f);
            boolean sub = tile.sub() != null && h >= 34;
            int ty = sub ? y1 + h / 2 - Fonts.height() : y1 + (h - Fonts.height()) / 2 + 1;
            Fonts.draw(ctx, Fonts.trim(tile.label(), x + w - tx - 8), tx, ty, ColorUtil.fade(Theme.TEXT, a), false);
            if (sub) {
                Fonts.draw(ctx, Fonts.trim(tile.sub(), x + w - tx - 8), tx, ty + Fonts.height() + 3, ColorUtil.fade(Theme.TEXT_DIM, a), false);
            }
        } else {
            int scale = h >= 40 ? 2 : 1;
            int iconSize = 9 * scale;
            int gap = scale == 2 ? 4 : 3;
            int iy = y1 + (h - iconSize - gap - Fonts.height()) / 2 + 1;
            PixelIcons.menu(ctx, tile.icon(), x + (w - iconSize) / 2, iy, scale, iconColor, iconAccent);
            Fonts.drawCentered(ctx, Fonts.trim(tile.label(), w - 6), x + w / 2, iy + iconSize + gap, textColor, false);
        }
        if (tile.enabled()) {
            hits.add(new Hit(x, y, x + w, y + h, tile));
        }
        if (!big && hovered && tile.sub() != null) {
            tooltip = tile.sub();
        }
    }

    /** Runs the clicked tile's action; returns whether one was hit. */
    boolean click(double mouseX, double mouseY) {
        for (Hit hit : List.copyOf(hits)) {
            if (RenderUtil.isHovered(mouseX, mouseY, hit.x1(), hit.y1(), hit.x2(), hit.y2())) {
                hit.tile().action().run();
                return true;
            }
        }
        return false;
    }
}
