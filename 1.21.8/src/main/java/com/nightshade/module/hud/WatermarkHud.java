package com.nightshade.module.hud;

import com.nightshade.render.BrandLogo;
import com.nightshade.NightshadeClient;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** Nightshade Client brand mark with optional version. */
public final class WatermarkHud extends HudModule {
    private static final String TITLE = "Nightshade";
    private final BooleanSetting showVersion = add(new BooleanSetting("Show Version", "Show the client version", true));
    private final BooleanSetting accentLine = add(new BooleanSetting("Accent Line", "Draw an accent line under the watermark", true));

    public WatermarkHud() {
        super("Watermark", "Shows the Nightshade Client brand mark", ModuleCategory.HUD, 0.0, 0.0);
        enabledByDefault();
    }

    private String version() {
        return "v" + NightshadeClient.VERSION;
    }

    @Override
    public int getWidth() {
        int width = 14 + Fonts.width(TITLE) + 6;
        if (showVersion.get()) {
            width += Fonts.width(version()) + 4;
        }
        return width;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 7;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        int w = getWidth();
        int h = getHeight();
        drawBackground(context, w, h);
        BrandLogo.drawMark(context, 3, (h - 10) / 2, 10, com.nightshade.render.Theme.accentLight());
        int x = Fonts.draw(context, TITLE, 15, 4, Theme.accentLight(), shadow());
        if (showVersion.get()) {
            Fonts.draw(context, version(), x + 3, 4, Theme.TEXT_DIM, shadow());
        }
        if (accentLine.get()) {
            RenderUtil.horizontalGradient(context, 2, h - 1, w - 2, h, Theme.accent(), 0x00000000);
        }
    }
}
