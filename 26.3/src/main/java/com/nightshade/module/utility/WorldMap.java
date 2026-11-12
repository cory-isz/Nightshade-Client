package com.nightshade.module.utility;

import com.nightshade.input.Keybind;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.util.EffectProbe;

/** Keybind action (M by default): opens the full-screen world map. */
public final class WorldMap extends Module {
    public WorldMap() {
        super("World Map", "Press to open a full-screen map of the world around you", ModuleCategory.UTILITY);
        defaultKeybind(Keybind.key(com.mojang.blaze3d.platform.InputConstants.KEY_M));
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        if (mc.level != null && mc.gui.screen() == null) {
            mc.gui.setScreen(new com.nightshade.gui.menu.WorldMapScreen());
            EffectProbe.hit("World Map");
        }
    }
}
