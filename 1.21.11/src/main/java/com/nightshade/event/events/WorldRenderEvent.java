package com.nightshade.event.events;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** Fired at the end of the main world pass (Fabric's WorldRenderEvents.END_MAIN). Reused instance. */
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
