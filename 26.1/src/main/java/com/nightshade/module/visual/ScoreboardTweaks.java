package com.nightshade.module.visual;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;

/** Sidebar scoreboard: hide the red score numbers, scale it, change the background, or hide it (see GuiScoreboardMixin). */
public final class ScoreboardTweaks extends Module {
    private static volatile ScoreboardTweaks active;
    private final BooleanSetting hide = add(new BooleanSetting("Hide Scoreboard", "Do not draw the sidebar at all", false));
    private final BooleanSetting hideNumbers = add(new BooleanSetting("Hide Numbers", "Remove the red score numbers", true));
    private final NumberSetting scale = add(new NumberSetting("Scale", "Size of the sidebar", 1.0, 0.5, 1.5, 0.05, "x"));
    private final NumberSetting background = add(new NumberSetting("Background", "Background opacity", 30, 0, 100, 5, "%"));
    private final BooleanSetting lowerRight = add(new BooleanSetting("Move Lower", "Place the sidebar lower on the screen, away from the HUD", false));

    public ScoreboardTweaks() {
        super("Scoreboard", "Hide score numbers, scale or hide the sidebar scoreboard", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static boolean hideSidebar() {
        ScoreboardTweaks m = active;
        if (m != null && m.hide.get()) {
            EffectProbe.hit("Scoreboard");
            return true;
        }
        return false;
    }

    public static boolean hideNumbers() {
        ScoreboardTweaks m = active;
        if (m != null && m.hideNumbers.get()) {
            EffectProbe.hit("Scoreboard");
            return true;
        }
        return false;
    }

    /** Sidebar scale, or 1 when inactive. */
    public static float scale() {
        ScoreboardTweaks m = active;
        return m == null ? 1f : m.scale.floatValue();
    }

    /** Vertical shift as a fraction of the screen height (0 = vanilla). */
    public static float lower() {
        ScoreboardTweaks m = active;
        return m != null && m.lowerRight.get() ? 0.18f : 0f;
    }

    /** Replaces the vanilla background colour's alpha, or returns it unchanged when inactive. */
    public static int background(int vanilla) {
        ScoreboardTweaks m = active;
        if (m == null) {
            return vanilla;
        }
        EffectProbe.hit("Scoreboard");
        int alpha = Math.round(m.background.floatValue() / 100f * 255f);
        return (alpha << 24) | (vanilla & 0xFFFFFF);
    }

    public static boolean active() {
        return active != null;
    }
}
