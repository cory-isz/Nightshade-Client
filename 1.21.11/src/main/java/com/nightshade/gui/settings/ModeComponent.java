package com.nightshade.gui.settings;

import com.nightshade.gui.GuiDraw;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** Choice row: left click selects the next mode, right click the previous one. */
public final class ModeComponent extends SettingComponent<ModeSetting> {
    public ModeComponent(ModeSetting setting) {
        super(setting);
    }

    @Override
    public int height() {
        return 20;
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY) {
        drawLabel(ctx);
        String label = "< " + setting.get() + " >";
        int w = GuiDraw.pillWidth(label);
        int px = x + width - w - 5;
        GuiDraw.pill(ctx, px, y + 3, label, 14, RenderUtil.isHovered(mouseX, mouseY, px, y + 3, px + w, y + 17),
                Theme.TEXT, Theme.SELECTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            return false;
        }
        if (button == 0) {
            setting.cycle(1);
            return true;
        }
        if (button == 1) {
            setting.cycle(-1);
            return true;
        }
        return false;
    }
}
