package com.nightshade.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outline shapes for objects that are made of two blocks: double chests, beds, doors, tall flowers and grass. The
 * shape of the looked-at block is joined with its other half, so the outline goes around the whole object.
 */
public final class MultiBlockShapes {
    private MultiBlockShapes() {
    }

    /** The other half of a two-block object at {@code pos}, or null if the block stands alone. */
    public static BlockPos otherHalf(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART) && state.hasProperty(BedBlock.FACING)) {
            Direction facing = state.getValue(BedBlock.FACING);
            return pos.relative(state.getValue(BedBlock.PART) == BedPart.FOOT ? facing : facing.getOpposite());
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        return null;
    }

    /**
     * Joins {@code shape} (the looked-at block's outline, relative to {@code pos}) with the other half's outline.
     * {@code fullBlock} uses whole cubes for both halves. Returns {@code shape} unchanged when there is no other half.
     */
    public static VoxelShape whole(BlockGetter level, BlockPos pos, VoxelShape shape, boolean fullBlock) {
        if (level == null || pos == null || shape == null) {
            return shape;
        }
        BlockState state = level.getBlockState(pos);
        BlockPos other = otherHalf(state, pos);
        if (other == null) {
            return shape;
        }
        BlockState otherState = level.getBlockState(other);
        if (otherState.getBlock() != state.getBlock()) {
            return shape; // half missing (e.g. mid-break)
        }
        var camera = Minecraft.getInstance().getCameraEntity();
        CollisionContext context = camera == null ? CollisionContext.empty() : CollisionContext.of(camera);
        VoxelShape otherShape = fullBlock ? Shapes.block() : otherState.getShape(level, other, context);
        if (otherShape.isEmpty()) {
            return shape;
        }
        return Shapes.or(shape, otherShape.move(other.getX() - pos.getX(), other.getY() - pos.getY(), other.getZ() - pos.getZ()));
    }
}
