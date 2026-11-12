package com.nightshade.hud;

import com.nightshade.manager.ClientManager;
import com.nightshade.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Renders enabled HUD components each frame, isolating failures per component. */
public final class HudManager {
    private final ClientManager client;
    private final Minecraft mc = Minecraft.getInstance();

    public HudManager(ClientManager client) {
        this.client = client;
    }

    /** Per-component render time, collected only while the benchmark turns profiling on. */
    public static boolean PROFILE;
    public static final java.util.Map<String, Long> TIMES = new java.util.HashMap<>();

    public void render(GuiGraphicsExtractor context, float tickDelta) {
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
        long t0 = PROFILE ? System.nanoTime() : 0L;
        HudLayout.apply(visible, context.guiWidth(), context.guiHeight());
        if (PROFILE) {
            TIMES.merge("(layout)", System.nanoTime() - t0, Long::sum);
        }
        for (HudModule module : visible) {
            long t = PROFILE ? System.nanoTime() : 0L;
            renderSafely(module, context, tickDelta, false);
            if (PROFILE) {
                TIMES.merge(module.getName(), System.nanoTime() - t, Long::sum);
            }
        }
    }

    public void renderSafely(HudModule module, GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        try {
            module.render(context, tickDelta, preview);
        } catch (Throwable t) {
            client.modules().handleModuleFailure(module, t);
        }
    }
}
