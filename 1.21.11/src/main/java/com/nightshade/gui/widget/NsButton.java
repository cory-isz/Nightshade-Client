package com.nightshade.gui.widget;

import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** Styled button used by the Nightshade screens. Layout is set every frame; hit-testing uses the last layout. */
public final class NsButton {
    public enum Style {
        PRIMARY, SECONDARY, DANGER, GHOST
    }

    private String label;
    private final Style style;
    private final Runnable action;
    private final Animation hover = new Animation(18f, 0f);
    private boolean enabled = true;
    private int x;
    private int y;
    private int w;
    private int h;

    public NsButton(String label, Style style, Runnable action) {
        this.label = label;
        this.style = style;
        this.action = action;
    }

    public NsButton label(String label) {
        this.label = label;
        return this;
    }

    public String label() {
        return label;
    }

    public NsButton enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public int preferredWidth() {
        return Fonts.width(label) + 16;
    }

    public void render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        boolean hovered = enabled && isHovered(mouseX, mouseY);
        float p = hover.setTarget(hovered ? 1f : 0f).update();
        int fill;
        int border;
        int text;
        switch (style) {
            case PRIMARY -> {
                fill = ColorUtil.lerp(Theme.accent(), Theme.accentLight(), p * 0.35f);
                border = ColorUtil.withAlpha(0xFFFFFFFF, 0x30 + (int) (0x30 * p));
                text = 0xFFFFFFFF;
            }
            case DANGER -> {
                fill = ColorUtil.lerp(0xFF3A1626, 0xFF5A1E34, p);
                border = ColorUtil.withAlpha(Theme.DANGER, 0x80 + (int) (0x50 * p));
                text = 0xFFFF9AAE;
            }
            case GHOST -> {
                fill = ColorUtil.fade(Theme.PANEL_HOVER, p);
                border = ColorUtil.fade(Theme.BORDER, p);
                text = ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, p);
            }
            default -> {
                fill = ColorUtil.lerp(Theme.PANEL, Theme.PANEL_HOVER, p);
                border = ColorUtil.lerp(Theme.BORDER, Theme.BORDER_STRONG, p);
                text = Theme.TEXT;
            }
        }
        if (!enabled) {
            fill = ColorUtil.fade(fill, 0.5f);
            text = Theme.TEXT_DIM;
        }
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, 4, fill);
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, 4, border);
        String shown = Fonts.trim(label, w - 8);
        Fonts.drawCentered(ctx, shown, x + w / 2, y + (h - Fonts.height()) / 2 + 1, text, false);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return RenderUtil.isHovered(mouseX, mouseY, x, y, x + w, y + h);
    }

    /** Centre of the button as last drawn (for tests). */
    public int[] center() {
        return new int[]{x + w / 2, y + h / 2};
    }

    /** Runs the action if the click hits this button. */
    public boolean click(double mouseX, double mouseY, int button) {
        if (button == 0 && enabled && w > 0 && isHovered(mouseX, mouseY)) {
            action.run();
            return true;
        }
        return false;
    }
}
