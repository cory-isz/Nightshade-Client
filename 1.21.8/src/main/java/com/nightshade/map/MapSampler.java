package com.nightshade.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Turns the loaded world into map colours, like a vanilla map: the top block's map colour, shaded by height (slopes
 * facing north are darker) and water by depth. In dimensions with a roof (the Nether) it looks for the first floor
 * below the player instead of the roof. Main thread only (reads the client world).
 */
public final class MapSampler {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    /** Surface height at a column (the block under the top), or Integer.MIN_VALUE when the chunk is not loaded. */
    public int height(Level level, int x, int z, int caveTop) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return Integer.MIN_VALUE;
        }
        if (caveTop != Integer.MIN_VALUE) {
            // Roofed dimension: from just above the player down to the first block that is not air.
            int bottom = Math.max(level.getMinY(), caveTop - 48);
            boolean air = false;
            for (int y = caveTop; y >= bottom; y--) {
                pos.set(x, y, z);
                boolean empty = level.getBlockState(pos).isAir();
                if (!empty && air) {
                    return y;
                }
                air |= empty;
            }
            return Integer.MIN_VALUE;
        }
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    /**
     * ARGB colour of a column. {@code northHeight} is the height of the column to the north (for shading), or
     * Integer.MIN_VALUE when unknown. Returns 0 (transparent) for unloaded columns.
     */
    public int color(Level level, int x, int z, int y, int northHeight) {
        if (y == Integer.MIN_VALUE) {
            return 0;
        }
        pos.set(x, y, z);
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        MapColor color = state.getMapColor(level, pos);
        if (!fluid.isEmpty() && color == MapColor.WATER) {
            // Water: darker the deeper it is, like vanilla maps.
            int depth = 0;
            for (int d = 1; d <= 12; d++) {
                pos.set(x, y - d, z);
                if (level.getBlockState(pos).getFluidState().isEmpty()) {
                    break;
                }
                depth++;
            }
            MapColor.Brightness b = depth > 8 ? MapColor.Brightness.LOW : depth > 3 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
            return MapColor.WATER.calculateARGBColor(b);
        }
        if (color == MapColor.NONE) {
            // Glass, flowers on air...: look one block lower.
            pos.set(x, y - 1, z);
            BlockState below = level.getBlockState(pos);
            color = below.getMapColor(level, pos);
            if (color == MapColor.NONE) {
                return 0xFF101014;
            }
        }
        MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
        if (northHeight != Integer.MIN_VALUE) {
            brightness = y > northHeight ? MapColor.Brightness.HIGH : y < northHeight ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        return color.calculateARGBColor(brightness);
    }
}
