package com.nightshade.gui.settings;

import com.nightshade.gui.GuiDraw;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.Animation;
import com.nightshade.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

public final class BooleanComponent extends SettingComponent<BooleanSetting> {
    private final Animation animation;

    public BooleanComponent(BooleanSetting setting) {
        super(setting);
        animation = new Animation(16f, setting.get() ? 1f : 0f);
    }

    @Override
    public int height() {
        return 20;
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY) {
        drawLabel(ctx);
        animation.setTarget(setting.get() ? 1f : 0f);
        int sx = x + width - GuiDraw.SWITCH_W - 6;
        int sy = y + (height() - GuiDraw.SWITCH_H) / 2;
        GuiDraw.toggle(ctx, sx, sy, animation.update(),
                RenderUtil.isHovered(mouseX, mouseY, sx, sy, sx + GuiDraw.SWITCH_W, sy + GuiDraw.SWITCH_H));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            setting.toggle();
            return true;
        }
        return false;
    }
}
