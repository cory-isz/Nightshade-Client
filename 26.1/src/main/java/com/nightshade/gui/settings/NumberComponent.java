package com.nightshade.gui.settings;

import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.Animation;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Slider row: drag anywhere on the track to change the value. */
public final class NumberComponent extends SettingComponent<NumberSetting> {
    private final Animation fill;
    private boolean dragging;

    public NumberComponent(NumberSetting setting) {
        super(setting);
        fill = new Animation(20f, (float) setting.getFraction());
    }

    @Override
    public int height() {
        return 28;
    }

    private int trackX1() {
        return x + 6;
    }

    private int trackX2() {
        return x + width - 6;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        drawLabel(ctx);
        String value = setting.format();
        Fonts.draw(ctx, value, x + width - 6 - Fonts.width(value), y + 6, Theme.TEXT_MUTED, false);
        fill.setTarget((float) setting.getFraction());
        float fraction = dragging ? (float) setting.getFraction() : fill.update();
        int trackY = y + 19;
        int x1 = trackX1();
        int x2 = trackX2();
        RenderUtil.roundedRect(ctx, x1, trackY, x2, trackY + 3, 1, Theme.SWITCH_OFF);
        int filled = x1 + Math.round((x2 - x1) * fraction);
        RenderUtil.roundedRect(ctx, x1, trackY, Math.max(x1 + 2, filled), trackY + 3, 1, Theme.accent());
        RenderUtil.roundedRect(ctx, filled - 3, trackY - 2, filled + 3, trackY + 5, 2, dragging ? Theme.accentLight() : 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY) && mouseY >= y + 14) {
            dragging = true;
            update(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY) {
        if (dragging) {
            update(mouseX);
        }
    }

    @Override
    public void mouseReleased() {
        dragging = false;
    }

    private void update(double mouseX) {
        setting.setFraction((mouseX - trackX1()) / (double) Math.max(1, trackX2() - trackX1()));
    }
}
