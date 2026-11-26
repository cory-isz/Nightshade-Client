package com.nightshade.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Is a box visible from the camera, or completely hidden behind solid blocks? Tested with rays from the camera to the
 * box's centre, corners and top, walking the blocks in between (Amanatides-Woo) and stopping at the first full opaque
 * block. Shared by Entity Culling and Block Entity Culling. Main thread only.
 */
public final class Occlusion {
    private static long rays;

    private Occlusion() {
    }

    /** Rays cast since launch (for budgeting and stats). */
    public static long rays() {
        return rays;
    }

    public static boolean isVisible(Level level, AABB box, double camX, double camY, double camZ) {
        BlockPos camPos = BlockPos.containing(camX, camY, camZ);
        if (opaque(level, camPos.getX(), camPos.getY(), camPos.getZ())) {
            return true; // camera inside a block (e.g. third person clipping): don't guess
        }
        if (box.contains(camX, camY, camZ)) {
            return true;
        }
        double[] xs = {box.minX, (box.minX + box.maxX) / 2, box.maxX};
        double[] ys = {box.minY, (box.minY + box.maxY) / 2, box.maxY};
        double[] zs = {box.minZ, (box.minZ + box.maxZ) / 2, box.maxZ};
        if (rayClear(level, camX, camY, camZ, xs[1], ys[1], zs[1])) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            if (rayClear(level, camX, camY, camZ, xs[(i & 1) * 2], ys[((i >> 1) & 1) * 2], zs[((i >> 2) & 1) * 2])) {
                return true;
            }
        }
        return rayClear(level, camX, camY, camZ, xs[1], ys[2], zs[1]);
    }

    private static boolean opaque(Level level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).isSolidRender();
    }

    /** Walks the blocks on the line from a to b and returns false at the first opaque one (the end block excluded). */
    public static boolean rayClear(Level level, double ax, double ay, double az, double bx, double by, double bz) {
        rays++;
        int x = Mth.floor(ax), y = Mth.floor(ay), z = Mth.floor(az);
        int ex = Mth.floor(bx), ey = Mth.floor(by), ez = Mth.floor(bz);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? (x + 1 - ax) : (ax - x)) * tdx;
        double ty = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? (y + 1 - ay) : (ay - y)) * tdy;
        double tz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? (z + 1 - az) : (az - z)) * tdz;
        int steps = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z);
        for (int i = 0; i < steps; i++) {
            if (tx < ty && tx < tz) {
                x += sx;
                tx += tdx;
            } else if (ty < tz) {
                y += sy;
                ty += tdy;
            } else {
                z += sz;
                tz += tdz;
            }
            if (x == ex && y == ey && z == ez) {
                return true;
            }
            if (opaque(level, x, y, z)) {
                return false;
            }
        }
        return true;
    }
}
