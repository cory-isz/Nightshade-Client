package com.nightshade.gui.settings;

import com.nightshade.module.ModuleSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** A row in a module's settings panel bound to one {@link ModuleSetting}. */
public abstract class SettingComponent<S extends ModuleSetting<?>> {
    protected final S setting;
    protected int x;
    protected int y;
    protected int width;

    protected SettingComponent(S setting) {
        this.setting = setting;
    }

    public S setting() {
        return setting;
    }

    public abstract int height();

    public final void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        boolean hovered = isHovered(mouseX, mouseY);
        RenderUtil.roundedRect(ctx, x, y, x + width, y + height(), 3, hovered ? Theme.PANEL_HOVER : Theme.PANEL);
        draw(ctx, mouseX, mouseY);
    }

    protected abstract void draw(GuiGraphics ctx, int mouseX, int mouseY);

    protected void drawLabel(GuiGraphics ctx) {
        int color = setting.isDefault() ? Theme.TEXT : Theme.accentLight();
        Fonts.draw(ctx, setting.getName(), x + 6, y + 6, color, false);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return RenderUtil.isHovered(mouseX, mouseY, x, y, x + width, y + height());
    }

    /** Hover over the label area shows the setting description. */
    public boolean isLabelHovered(double mouseX, double mouseY) {
        return RenderUtil.isHovered(mouseX, mouseY, x, y, x + 6 + Fonts.width(setting.getName()), y + 18);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public void mouseDragged(double mouseX, double mouseY) {
    }

    public void mouseReleased() {
    }

    /** Returns true when the key was consumed (e.g. by a focused text field). */
    public boolean keyPressed(int key) {
        return false;
    }

    public boolean charTyped(char chr) {
        return false;
    }

    public boolean hasFocus() {
        return false;
    }

    public void unfocus() {
    }
}
