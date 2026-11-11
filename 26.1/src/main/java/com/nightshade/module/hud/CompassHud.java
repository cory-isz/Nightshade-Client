package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Horizontal compass strip with cardinal and intercardinal directions and the exact heading. */
public final class CompassHud extends HudModule {
    private static final String[] MARKS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private final NumberSetting widthSetting = add(new NumberSetting("Width", "Width of the compass strip", 160, 80, 300, 10, "px"));
    private final BooleanSetting showDegrees = add(new BooleanSetting("Degrees", "Show the exact heading", true));

    public CompassHud() {
        super("Compass", "A compass strip showing which way you face", ModuleCategory.HUD, 0.5, 0.0);
    }

    @Override
    public int getWidth() {
        return widthSetting.intValue();
    }

    @Override
    public int getHeight() {
        return showDegrees.get() ? 28 : 18;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        int w = getWidth();
        drawBackground(context, w, getHeight());
        float yaw = mc.player == null ? 0f : Mth.wrapDegrees(mc.player.getViewYRot(tickDelta));
        float pixelsPerDegree = w / 180f;
        context.enableScissor(0, 0, w, 18);
        for (int i = 0; i < 8; i++) {
            float angle = i * 45f;
            float delta = Mth.wrapDegrees(angle - yaw);
            if (Math.abs(delta) > 95f) {
                continue;
            }
            int x = Math.round(w / 2f + delta * pixelsPerDegree);
            boolean cardinal = i % 2 == 0;
            int color = MARKS[i].equals("N") ? Theme.DANGER : cardinal ? textColor.argb() : Theme.TEXT_MUTED;
            float fade = 1f - Math.min(1f, Math.abs(delta) / 95f) * 0.7f;
            Fonts.drawCentered(context, MARKS[i], x, 5, ColorUtil.fade(color, fade), shadow());
        }
        for (int degree = 0; degree < 360; degree += 15) {
            if (degree % 45 == 0) {
                continue;
            }
            float delta = Mth.wrapDegrees(degree - yaw);
            if (Math.abs(delta) <= 90f) {
                int x = Math.round(w / 2f + delta * pixelsPerDegree);
                RenderUtil.rect(context, x, 14, x + 1, 17, Theme.TEXT_DIM);
            }
        }
        context.disableScissor();
        RenderUtil.rect(context, w / 2, 0, w / 2 + 1, 18, Theme.accent());
        if (showDegrees.get()) {
            float heading = (yaw + 180f) % 360f; // 0 = north, 90 = east
            Fonts.drawCentered(context, Math.round(heading) % 360 + "°", w / 2, 18, Theme.accentLight(), shadow());
        }
    }
}
