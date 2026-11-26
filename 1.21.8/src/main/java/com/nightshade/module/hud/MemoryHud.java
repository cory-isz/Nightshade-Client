package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** Java heap usage - handy for spotting memory pressure that causes lag spikes (GC pauses). */
public final class MemoryHud extends HudModule {
    private final BooleanSetting showBar = add(new BooleanSetting("Bar", "Draw a usage bar", true));
    private final BooleanSetting showPercent = add(new BooleanSetting("Percent", "Show the percentage", true));
    private String text = "Memory";
    private float used;

    public MemoryHud() {
        super("Memory Usage", "Shows how much memory the game is using", ModuleCategory.HUD, 0.01, 0.22);
    }

    @Override
    protected void refresh() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long inUse = rt.totalMemory() - rt.freeMemory();
        used = max > 0 ? inUse / (float) max : 0f;
        text = "Mem " + (inUse >> 20) + "/" + (max >> 20) + " MB" + (showPercent.get() ? " (" + Math.round(used * 100f) + "%)" : "");
    }

    @Override
    public int getWidth() {
        return Fonts.width(text) + 8;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6 + (showBar.get() ? 3 : 0);
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        int w = getWidth();
        drawBackground(context, w, getHeight());
        int color = used > 0.85f ? Theme.DANGER : used > 0.7f ? Theme.WARNING : textColor.argb();
        Fonts.draw(context, text, 4, 4, color, shadow());
        if (showBar.get()) {
            int y = Fonts.height() + 5;
            RenderUtil.rect(context, 4, y, w - 4, y + 2, 0x60000000);
            RenderUtil.rect(context, 4, y, 4 + Math.round((w - 8) * used), y + 2, used > 0.85f ? Theme.DANGER : Theme.accent());
        }
    }
}
