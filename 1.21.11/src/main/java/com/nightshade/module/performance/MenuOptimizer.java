package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.util.EffectProbe;

/**
 * Makes the Nightshade menus (main menu, pause menu, modules menu, mod menu...) much cheaper to draw: the in-game
 * background blur (several full-screen passes every frame) is replaced by a plain dark tint, and the floating lights
 * and soft glows can be turned off too. On by default.
 */
public final class MenuOptimizer extends Module {
    private static volatile MenuOptimizer active;
    private final BooleanSetting noBlur = add(new BooleanSetting("Skip Blur", "Tint the world behind menus instead of blurring it (the most expensive effect)", true));
    private final BooleanSetting noLights = add(new BooleanSetting("Skip Floating Lights", "No drifting light motes on the menu background", true));
    private final BooleanSetting noTransitions = add(new BooleanSetting("Skip Transitions", "Open menus instantly instead of easing them in", false));
    private final BooleanSetting noGlow = add(new BooleanSetting("Skip Glows", "No soft glow behind hovered buttons and cards", false));

    public MenuOptimizer() {
        super("Menu Optimizer", "Lighter menus: skips the background blur, floating lights and (optionally) glows", ModuleCategory.PERFORMANCE);
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Whether Nightshade screens ease in when they open. */
    public static boolean allowTransitions() {
        MenuOptimizer m = active;
        return m == null || !m.noTransitions.get();
    }

    /** Whether menus may blur the world behind them. */
    public static boolean allowBlur() {
        MenuOptimizer m = active;
        if (m != null && m.noBlur.get()) {
            EffectProbe.hit("Menu Optimizer");
            return false;
        }
        return true;
    }

    public static boolean allowLights() {
        MenuOptimizer m = active;
        if (m != null && m.noLights.get()) {
            EffectProbe.hit("Menu Optimizer");
            return false;
        }
        return true;
    }

    public static boolean allowGlow() {
        MenuOptimizer m = active;
        return m == null || !m.noGlow.get();
    }
}
