package com.nightshade.module.performance;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * Lowers the frame rate while Minecraft is in the background or minimised, saving GPU/CPU power and heat (and
 * battery on laptops). Works on top of vanilla's frame limiter, so it can only ever lower the cap.
 */
public final class DynamicFps extends Module {
    private static DynamicFps instance;

    private final NumberSetting unfocused = add(new NumberSetting("Unfocused FPS", "Frame cap while another window is focused", 15, 1, 60, 1, " fps"));
    private final NumberSetting minimized = add(new NumberSetting("Minimized FPS", "Frame cap while minimised", 1, 1, 30, 1, " fps"));
    private final BooleanSetting keepInGame = add(new BooleanSetting("Only Out Of Focus", "Never limit while the window has focus", true));

    public DynamicFps() {
        super("Dynamic FPS", "Lowers FPS when the game is in the background", ModuleCategory.PERFORMANCE);
        enabledByDefault();
        instance = this;
    }

    /** Applies the module's cap to vanilla's computed limit. */
    public static int limit(int vanillaLimit) {
        DynamicFps fps = instance;
        if (fps == null || !fps.isEnabled()) {
            return vanillaLimit;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return vanillaLimit;
        }
        if (mc.getWindow().isIconified()) {
            EffectProbe.hit("Dynamic FPS");
            return Math.min(vanillaLimit, fps.minimized.get().intValue());
        }
        if (!mc.isWindowActive()) {
            EffectProbe.hit("Dynamic FPS");
            return Math.min(vanillaLimit, fps.unfocused.get().intValue());
        }
        return vanillaLimit;
    }
}
