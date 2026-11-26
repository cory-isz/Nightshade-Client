package com.nightshade.cosmetic;

import com.nightshade.cosmetic.VoxelModel.Grid;

import java.util.ArrayList;
import java.util.List;

/**
 * A wing made of thin stacked layers (e.g. primaries at the back, coverts on top), split into hinged segments
 * so it can curve. Voxels are half a model pixel wide and thinner still in depth, so the wing is detailed but not
 * bulky.
 */
public final class WingModel {
    public static final float UNIT = 0.4f / 16f;
    public static final float UNIT_Z = 0.3f / 16f;

    public final VoxelModel[] base;
    public final VoxelModel[] glow;
    public final int[] start;
    public final int anchorY;
    public final float size;
    public final float flap;
    public final float bend;
    /** A second, smaller pair drawn below (six-winged designs), or null. */
    public final WingModel lower;
    /** Every voxel as x, y, z, colour across all segments (for thumbnails). */
    public final int[] cells;

    public static final class Layer {
        final Grid grid;
        final Grid glow;
        final int z;
        final int depth;

        public Layer(Grid grid, Grid glow, int z, int depth) {
            this.grid = grid;
            this.glow = glow;
            this.z = z;
            this.depth = depth;
        }
    }

    public WingModel(List<Layer> layers, int[] hinges, int anchorY, float size, float flap, float bend, WingModel lower) {
        int w = layers.get(0).grid.w;
        int n = hinges.length + 1;
        this.start = new int[n];
        this.base = new VoxelModel[n];
        this.glow = new VoxelModel[n];
        List<Integer> all = new ArrayList<>();
        for (int s = 0; s < n; s++) {
            int x0 = s == 0 ? 0 : hinges[s - 1];
            int x1 = s == n - 1 ? w : hinges[s];
            start[s] = x0;
            VoxelModel.Builder bb = VoxelModel.builder();
            VoxelModel.Builder gb = VoxelModel.builder();
            for (Layer l : layers) {
                for (int y = 0; y < l.grid.h; y++) {
                    for (int x = x0; x < x1; x++) {
                        int c = l.grid.get(x, y);
                        if (c == 0) {
                            continue;
                        }
                        int g = l.glow == null ? 0 : l.glow.get(x, y);
                        int d = l.grid.depth(x, y) > 0 ? l.grid.depth(x, y) : l.depth;
                        for (int z = 0; z < d; z++) {
                            if (g != 0) {
                                gb.set(x - x0, y, l.z + z, g);
                            } else {
                                bb.set(x - x0, y, l.z + z, c);
                            }
                            all.add(x);
                            all.add(y);
                            all.add(l.z + z);
                            all.add(g != 0 ? g : c);
                        }
                    }
                }
            }
            base[s] = bb.build();
            glow[s] = gb.build();
        }
        this.cells = all.stream().mapToInt(Integer::intValue).toArray();
        this.anchorY = anchorY;
        this.size = size;
        this.flap = flap;
        this.bend = bend;
        this.lower = lower;
    }
}
