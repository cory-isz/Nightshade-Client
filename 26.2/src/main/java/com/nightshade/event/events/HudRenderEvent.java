package com.nightshade.event.events;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fired while the in-game HUD renders. A single instance is reused to avoid per-frame allocation. */
public final class HudRenderEvent {
    private GuiGraphicsExtractor context;
    private float tickDelta;

    public HudRenderEvent set(GuiGraphicsExtractor context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
        return this;
    }

    public GuiGraphicsExtractor context() {
        return context;
    }

    public float tickDelta() {
        return tickDelta;
    }
}
