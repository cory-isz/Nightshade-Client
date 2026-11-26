package com.nightshade.cosmetic;

/**
 * Solid 3D shapes painted into a voxel builder (used for hats). Coordinates are in voxels; a paint function picks
 * each voxel's colour from its position, which is how shading, stripes and gradients are done.
 */
public final class Shapes {
    public interface Paint {
        int at(double x, double y, double z);
    }

    private Shapes() {
    }

    public static Paint solid(int c) {
        return (x, y, z) -> c;
    }

    /** Darkens towards the bottom and the back, lightens on top: cheap fake ambient light. */
    public static Paint shaded(int c, double yTop, double yBottom) {
        return (x, y, z) -> {
            double t = (y - yTop) / Math.max(1e-3, yBottom - yTop);
            float f = (float) (1.12 - 0.32 * Math.max(0, Math.min(1, t)));
            return scale(c, f);
        };
    }

    static int scale(int c, float f) {
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((c >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((c & 0xFF) * f));
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static void put(VoxelModel.Builder b, int x, int y, int z, Paint p) {
        b.set(x, y, z, p.at(x + 0.5, y + 0.5, z + 0.5));
    }

    public static void sphere(VoxelModel.Builder b, double cx, double cy, double cz, double r, Paint p) {
        ellipsoid(b, cx, cy, cz, r, r, r, p);
    }

    public static void ellipsoid(VoxelModel.Builder b, double cx, double cy, double cz, double rx, double ry, double rz, Paint p) {
        for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
            for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
                for (int z = (int) Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry, dz = (z + 0.5 - cz) / rz;
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
                        put(b, x, y, z, p);
                    }
                }
            }
        }
    }

    /** Upright cylinder (y is down) from y0 to y1; {@code wall} > 0 makes it hollow. */
    public static void cylinder(VoxelModel.Builder b, double cx, double cz, int y0, int y1, double r, double wall, Paint p) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            ring(b, cx, y, cz, r, wall, p);
        }
    }

    /** A horizontal disc or ring at height y. */
    public static void ring(VoxelModel.Builder b, double cx, int y, double cz, double r, double wall, Paint p) {
        for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
            for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d <= r && (wall <= 0 || d >= r - wall)) {
                    put(b, x, y, z, p);
                }
            }
        }
    }

    /**
     * Cone rising from a base at {@code yBase} up {@code height} voxels; the tip can lean by {@code leanX/leanZ}
     * (applied quadratically, so the tip bends like a soft hat).
     */
    public static void cone(VoxelModel.Builder b, double cx, double cz, int yBase, int height, double rBase, double rTop, double leanX, double leanZ, Paint p) {
        for (int i = 0; i <= height; i++) {
            double t = i / (double) Math.max(1, height);
            double r = rBase + (rTop - rBase) * t;
            double ox = cx + leanX * t * t, oz = cz + leanZ * t * t;
            ring(b, ox, yBase - i, oz, Math.max(0.5, r), 0, p);
        }
    }

    /** Doughnut lying flat (a halo) with ring radius R and tube radius r. */
    public static void torus(VoxelModel.Builder b, double cx, double cy, double cz, double R, double r, Paint p) {
        for (int x = (int) Math.floor(cx - R - r); x <= Math.ceil(cx + R + r); x++) {
            for (int y = (int) Math.floor(cy - r); y <= Math.ceil(cy + r); y++) {
                for (int z = (int) Math.floor(cz - R - r); z <= Math.ceil(cz + R + r); z++) {
                    double q = Math.hypot(x + 0.5 - cx, z + 0.5 - cz) - R;
                    double dy = y + 0.5 - cy;
                    if (q * q + dy * dy <= r * r) {
                        put(b, x, y, z, p);
                    }
                }
            }
        }
    }

    /** A rounded rod between two points, its radius going from r0 to r1. */
    public static void capsule(VoxelModel.Builder b, double x0, double y0, double z0, double x1, double y1, double z1, double r0, double r1, Paint p) {
        double len = Math.max(1e-3, Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0)));
        int steps = (int) Math.ceil(len * 2);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            sphere(b, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t, r0 + (r1 - r0) * t, p);
        }
    }

    /** A curve through control points (Catmull-Rom), as a tapering rod. */
    public static void curve(VoxelModel.Builder b, double[][] pts, double r0, double r1, Paint p) {
        int segs = pts.length - 1;
        double[] prev = null;
        int total = segs * 8;
        for (int i = 0; i <= total; i++) {
            double u = i / (double) total * segs;
            int k = Math.min(segs - 1, (int) Math.floor(u));
            double t = u - k;
            double[] p0 = pts[Math.max(0, k - 1)], p1 = pts[k], p2 = pts[k + 1], p3 = pts[Math.min(segs, k + 2)];
            double[] q = new double[3];
            for (int a = 0; a < 3; a++) {
                q[a] = 0.5 * ((2 * p1[a]) + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t * t + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t * t * t);
            }
            double r = r0 + (r1 - r0) * (i / (double) total);
            if (prev != null) {
                capsule(b, prev[0], prev[1], prev[2], q[0], q[1], q[2], r, r, p);
            }
            prev = q;
        }
    }

    public static void box(VoxelModel.Builder b, int x0, int y0, int z0, int x1, int y1, int z1, Paint p) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    put(b, x, y, z, p);
                }
            }
        }
    }
}
