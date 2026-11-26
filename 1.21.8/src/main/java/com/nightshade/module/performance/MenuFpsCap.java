package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.Minecraft;

/**
 * Caps FPS while a menu is open (title screen, inventory, ClickGUI, pause...). Menus look the same at 60 FPS, so the
 * GPU stops burning power drawing them at hundreds of frames per second - cooler, quieter, longer battery.
 */
public final class MenuFpsCap extends Module {
    private static volatile MenuFpsCap instance;
    private final NumberSetting cap = add(new NumberSetting("Menu FPS", "Frame cap while a menu is open", 60, 20, 144, 5, " fps"));
    private final BooleanSetting inGameScreens = add(new BooleanSetting("In-Game Menus Too", "Also cap inventories and other screens opened in game", true));

    public MenuFpsCap() {
        super("Menu FPS Cap", "Caps FPS in menus so the GPU stays cool and quiet", ModuleCategory.PERFORMANCE);
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        instance = this;
    }

    @Override
    protected void onDisable() {
        instance = null;
    }

    /** Applies the cap to the frame limit computed so far. */
    public static int limit(int current) {
        MenuFpsCap m = instance;
        Minecraft mc = Minecraft.getInstance();
        if (m == null || mc.screen == null || (mc.level != null && !m.inGameScreens.get())) {
            return current;
        }
        EffectProbe.hit("Menu FPS Cap");
        return Math.min(current, m.cap.intValue());
    }
}
