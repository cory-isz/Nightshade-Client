package com.nightshade.event.events;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/** Fired at the end of the main world pass (Fabric's LevelRenderEvents.END_MAIN). Reused instance. */
public final class WorldRenderEvent {
    private LevelRenderContext context;

    public WorldRenderEvent set(LevelRenderContext context) {
        this.context = context;
        return this;
    }

    public LevelRenderContext context() {
        return context;
    }
}
