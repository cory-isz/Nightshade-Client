package com.nightshade.event.events;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

/** Fired after the world has been drawn (Fabric's WorldRenderEvents.LAST). Reused instance. */
public final class WorldRenderEvent {
    private WorldRenderContext context;

    public WorldRenderEvent set(WorldRenderContext context) {
        this.context = context;
        return this;
    }

    public WorldRenderContext context() {
        return context;
    }
}
