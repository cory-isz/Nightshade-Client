package com.nightshade.module.interfaces;

import com.nightshade.gui.menu.ProfilesScreen;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;

/** Opens the profile manager (saved setups and per-server profiles). */
public final class ProfilesModule extends Module {
    private static volatile ProfilesModule instance;
    private final com.nightshade.module.setting.BooleanSetting remember = add(new com.nightshade.module.setting.BooleanSetting(
            "Remember Server Changes", "Changes you make while on a server with a linked profile are saved into that profile when you leave", true));

    public ProfilesModule() {
        super("Profiles", "Save, load and link setups to servers", ModuleCategory.INTERFACE);
        instance = this;
    }

    /** Whether leaving a server saves your changes into its linked profile. */
    public static boolean rememberServerChanges() {
        ProfilesModule m = instance;
        return m == null || m.remember.get();
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        mc.gui.setScreen(new ProfilesScreen(mc.gui.screen()));
    }
}
