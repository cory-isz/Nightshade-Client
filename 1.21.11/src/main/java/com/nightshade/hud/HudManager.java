package com.nightshade.hud;

import com.nightshade.manager.ClientManager;
import com.nightshade.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Renders enabled HUD components each frame, isolating failures per component. */
public final class HudManager {
    private final ClientManager client;
    private final Minecraft mc = Minecraft.getInstance();

    public HudManager(ClientManager client) {
        this.client = client;
    }

    public void render(GuiGraphics context, float tickDelta) {
        // The HUD editor draws components itself (with selection outlines); avoid drawing them twice.
        if (mc.screen instanceof HudEditorScreen) {
            return;
        }
        java.util.List<HudModule> visible = new java.util.ArrayList<>();
        for (HudModule module : client.modules().getHudModules()) {
            if (module.isEnabled() && module.shouldRender()) {
                visible.add(module);
            }
        }
        HudLayout.apply(visible, context.guiWidth(), context.guiHeight());
        for (HudModule module : visible) {
            renderSafely(module, context, tickDelta, false);
        }
    }

    public void renderSafely(HudModule module, GuiGraphics context, float tickDelta, boolean preview) {
        try {
            module.render(context, tickDelta, preview);
        } catch (Throwable t) {
            client.modules().handleModuleFailure(module, t);
        }
    }
}
