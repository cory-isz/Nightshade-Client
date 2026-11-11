package com.nightshade.gui.settings;

import com.nightshade.module.setting.ActionSetting;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A setting row with a button on the right that runs the setting's action. */
public final class ActionComponent extends SettingComponent<ActionSetting> {
    private final Animation hover = new Animation(18f, 0f);
    private int bx1, by1, bx2, by2;

    public ActionComponent(ActionSetting setting) {
        super(setting);
    }

    @Override
    public int height() {
        return 20;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Fonts.draw(ctx, setting.getName(), x + 6, y + 6, Theme.TEXT, false);
        String label = setting.buttonLabel();
        int bw = Fonts.width(label) + 14;
        bx2 = x + width - 5;
        bx1 = bx2 - bw;
        by1 = y + 3;
        by2 = y + height() - 3;
        float p = hover.setTarget(RenderUtil.isHovered(mouseX, mouseY, bx1, by1, bx2, by2) ? 1f : 0f).update();
        RenderUtil.roundedRect(ctx, bx1, by1, bx2, by2, 3, ColorUtil.lerp(Theme.accent(), Theme.accentLight(), p * 0.4f));
        Fonts.drawCentered(ctx, label, (bx1 + bx2) / 2, by1 + (by2 - by1) / 2 - 4, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, bx1, by1, bx2, by2)) {
            setting.run();
            return true;
        }
        return false;
    }
}
