package com.nightshade.module.interfaces;

import com.nightshade.hud.HudEditorScreen;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;

/** Opens the drag-and-drop HUD editor and stores its preferences. */
public final class HudEditorModule extends Module {
    private final BooleanSetting snap = add(new BooleanSetting("Snap To Grid", "Snap components to a grid while dragging", true));
    private final NumberSetting gridSize = add(new NumberSetting("Grid Size", "Grid spacing", 4, 2, 16, 1, "px")).visibleWhen(snap::get);
    private final BooleanSetting guides = add(new BooleanSetting("Center Guides", "Snap to and show screen centre lines", true));
    private final BooleanSetting smartGuides = add(new BooleanSetting("Smart Guides", "Snap to the edges and centres of other components while dragging", true));

    public HudEditorModule() {
        super("HUD Editor", "Drag, scale and toggle HUD components", ModuleCategory.INTERFACE);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));
    }

    public boolean snap() {
        return snap.get();
    }

    public int gridSize() {
        return gridSize.intValue();
    }

    public boolean guides() {
        return guides.get();
    }

    public boolean smartGuides() {
        return smartGuides.get();
    }

    public void toggleSnap() {
        snap.set(!snap.get());
    }
}
