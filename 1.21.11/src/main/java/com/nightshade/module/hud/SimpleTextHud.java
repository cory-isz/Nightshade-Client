package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.render.Fonts;
import net.minecraft.client.gui.GuiGraphics;

/** A one-line "Label value" HUD widget; subclasses compute the value (and optionally its colour) in refresh(). */
abstract class SimpleTextHud extends HudModule {
    protected final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of the label", 0xFFB794FF));
    protected String label;
    protected String value = "";
    protected int valueColor;

    SimpleTextHud(String name, String description, String label, double x, double y) {
        super(name, description, ModuleCategory.HUD, x, y);
        this.label = label;
    }

    @Override
    public boolean shouldRender() {
        return mc.player != null && mc.level != null;
    }

    @Override
    public int getWidth() {
        return Fonts.width(label) + Fonts.width(value) + 8;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        if (preview && value.isEmpty()) {
            value = "-";
        }
        drawBackground(context, getWidth(), getHeight());
        int x = Fonts.draw(context, label, 4, 4, labelColor.argb(), shadow()) - 1;
        Fonts.draw(context, value, x, 4, valueColor != 0 ? valueColor : textColor.argb(), shadow());
    }
}
