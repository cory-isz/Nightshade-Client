package com.nightshade.event.events;

import com.nightshade.event.Cancellable;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;

/** Fired before the targeted-block outline is drawn; cancelling suppresses the vanilla outline. */
public final class BlockOutlineEvent extends Cancellable {
    private final LevelRenderContext context;
    private final BlockOutlineRenderState outline;

    public BlockOutlineEvent(LevelRenderContext context, BlockOutlineRenderState outline) {
        this.context = context;
        this.outline = outline;
    }

    public LevelRenderContext context() {
        return context;
    }

    public BlockOutlineRenderState outline() {
        return outline;
    }
}
