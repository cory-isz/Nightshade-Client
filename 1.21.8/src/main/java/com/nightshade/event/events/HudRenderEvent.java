package com.nightshade.event.events;

import net.minecraft.client.gui.GuiGraphics;

/** Fired while the in-game HUD renders. A single instance is reused to avoid per-frame allocation. */
public final class HudRenderEvent {
    private GuiGraphics context;
    private float tickDelta;

    public HudRenderEvent set(GuiGraphics context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
        return this;
    }

    public GuiGraphics context() {
        return context;
    }

    public float tickDelta() {
        return tickDelta;
    }
}
