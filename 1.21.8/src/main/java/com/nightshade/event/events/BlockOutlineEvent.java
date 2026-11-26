package com.nightshade.event.events;

import com.nightshade.event.Cancellable;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Fired before the targeted-block outline is drawn; cancelling suppresses the vanilla outline. */
public final class BlockOutlineEvent extends Cancellable {
    /** The block being outlined and its outline shape. */
    public record Outline(BlockPos pos, VoxelShape shape) {
    }

    private final WorldRenderContext context;
    private final Outline outline;

    public BlockOutlineEvent(WorldRenderContext context, Outline outline) {
        this.context = context;
        this.outline = outline;
    }

    public WorldRenderContext context() {
        return context;
    }

    public Outline outline() {
        return outline;
    }
}
