package com.nightshade.event.events;

import com.nightshade.event.Cancellable;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;

/** Fired before the targeted-block outline is drawn; cancelling suppresses the vanilla outline. */
public final class BlockOutlineEvent extends Cancellable {
    private final WorldRenderContext context;
    private final BlockOutlineRenderState outline;

    public BlockOutlineEvent(WorldRenderContext context, BlockOutlineRenderState outline) {
        this.context = context;
        this.outline = outline;
    }

    public WorldRenderContext context() {
        return context;
    }

    public BlockOutlineRenderState outline() {
        return outline;
    }
}
