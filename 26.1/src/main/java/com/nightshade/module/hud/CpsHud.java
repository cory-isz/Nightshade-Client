package com.nightshade.module.hud;

import com.nightshade.NightshadeClient;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.Fonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Left/right clicks per second over a rolling one-second window. */
public final class CpsHud extends HudModule {
    private final BooleanSetting showLeft = add(new BooleanSetting("Show Left", "Show left-click CPS", true));
    private final BooleanSetting showRight = add(new BooleanSetting("Show Right", "Show right-click CPS", true));
    private final ModeSetting format = add(new ModeSetting("Format", "Display format", "Labeled", "Labeled", "Compact"));
    private final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of the CPS label", 0xFFB794FF));

    private int left = -1;
    private int right = -1;
    private String label = "CPS ";
    private String value = "";
    private int width = 40;
    private String lastKey = "";

    public CpsHud() {
        super("CPS Counter", "Shows left and right clicks per second", ModuleCategory.HUD, 0.0, 0.145);
        enabledByDefault();
    }

    @Override
    protected void refresh() {
        int newLeft = NightshadeClient.client().cps().leftCps();
        int newRight = NightshadeClient.client().cps().rightCps();
        String key = format.get() + showLeft.get() + showRight.get();
        if (newLeft == left && newRight == right && key.equals(lastKey)) {
            return;
        }
        left = newLeft;
        right = newRight;
        lastKey = key;
        StringBuilder builder = new StringBuilder();
        if (showLeft.get()) {
            builder.append(left);
        }
        if (showLeft.get() && showRight.get()) {
            builder.append(" | ");
        }
        if (showRight.get()) {
            builder.append(right);
        }
        if (format.is("Compact")) {
            label = "";
            builder.append(" CPS");
        } else {
            label = "CPS ";
        }
        value = builder.toString();
        width = Fonts.width(label) + Fonts.width(value) + 8;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        int x = 4;
        if (!label.isEmpty()) {
            x = Fonts.draw(context, label, x, 4, labelColor.argb(), shadow()) - 1;
        }
        Fonts.draw(context, value, x, 4, textColor.argb(), shadow());
    }
}
