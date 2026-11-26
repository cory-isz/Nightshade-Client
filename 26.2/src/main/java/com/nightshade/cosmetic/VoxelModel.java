package com.nightshade.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.HashMap;
import java.util.Map;

/**
 * A 3D model made of small coloured cubes ("voxels"). Faces hidden between two neighbouring voxels are dropped when
 * the model is built, so even a large wing is only a few hundred quads. Coordinates follow Minecraft model space:
 * x right, y down, z towards the back, one unit = one model pixel (1/16 of a block, times the model's scale).
 */
public final class VoxelModel {
    /** Per quad: 4 corners x 3 floats, normal x 3, colour (as float bits), 16 floats. */
    private final float[] quads;
    private final int count;
    public final int minX;
    public final int maxX;
    public final int minY;
    public final int maxY;
    /** Front view (looking at -z faces) for flat thumbnails: [x][y] colour or 0. */
    public final int[][] front;
    /** Every voxel as x, y, z, colour (for angled thumbnails). */
    public final int[] cells;

    private VoxelModel(float[] quads, int count, int minX, int maxX, int minY, int maxY, int[][] front, int[] cells) {
        this.cells = cells;
        this.quads = quads;
        this.count = count;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.front = front;
    }

    public int quadCount() {
        return count;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Emits every face. {@code unit} is the size of one voxel in model units (pixels / 16). */
    public void render(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float unit, float alpha) {
        render(pose, vc, light, overlay, unit, unit, alpha);
    }

    /** As above with a separate (usually thinner) depth for each voxel. */
    public void render(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float unit, float unitZ, float alpha) {
        float[] q = quads;
        for (int i = 0; i < count; i++) {
            int o = i * 16;
            int c = Float.floatToRawIntBits(q[o + 15]);
            int a = Math.round(((c >>> 24) & 0xFF) * alpha);
            int argb = (a << 24) | (c & 0xFFFFFF);
            float nx = q[o + 12];
            float ny = q[o + 13];
            float nz = q[o + 14];
            for (int v = 0; v < 4; v++) {
                vc.addVertex(pose, q[o + v * 3] * unit, q[o + v * 3 + 1] * unit, q[o + v * 3 + 2] * unitZ)
                        .setColor(argb)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    public static final class Builder {
        private final Map<Long, Integer> cells = new HashMap<>();

        private static long key(int x, int y, int z) {
            return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
        }

        public Builder set(int x, int y, int z, int argb) {
            if ((argb >>> 24) == 0) {
                cells.remove(key(x, y, z));
            } else {
                cells.put(key(x, y, z), argb);
            }
            return this;
        }

        public boolean has(int x, int y, int z) {
            return cells.containsKey(key(x, y, z));
        }

        public Builder box(int x0, int y0, int z0, int x1, int y1, int z1, int argb) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, argb);
                    }
                }
            }
            return this;
        }

        /** Extrudes a 2D grid (x across, y down) between z0 and z0 + depth - 1. */
        public Builder extrude(Grid g, int ox, int oy, int z0, int depth) {
            for (int y = 0; y < g.h; y++) {
                for (int x = 0; x < g.w; x++) {
                    int c = g.get(x, y);
                    if (c != 0) {
                        int d = g.depth(x, y) > 0 ? g.depth(x, y) : depth;
                        for (int z = 0; z < d; z++) {
                            set(ox + x, oy + y, z0 + z, c);
                        }
                    }
                }
            }
            return this;
        }

        public VoxelModel build() {
            int n = 0;
            float[] buf = new float[Math.max(16, cells.size() * 6 * 16)];
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (Map.Entry<Long, Integer> e : cells.entrySet()) {
                long k = e.getKey();
                int x = (int) ((k >> 40) & 0xFFFFF) - 512;
                int y = (int) ((k >> 20) & 0xFFFFF) - 512;
                int z = (int) (k & 0xFFFFF) - 512;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                int c = e.getValue();
                // -X, +X, -Y (top, model y is down), +Y, -Z (front), +Z (back)
                if (!has(x - 1, y, z)) n = face(buf, n, c, -1, 0, 0, x, y, z, x, y + 1, z, x, y + 1, z + 1, x, y, z + 1);
                if (!has(x + 1, y, z)) n = face(buf, n, c, 1, 0, 0, x + 1, y, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, x + 1, y, z);
                if (!has(x, y - 1, z)) n = face(buf, n, c, 0, -1, 0, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z, x, y, z);
                if (!has(x, y + 1, z)) n = face(buf, n, c, 0, 1, 0, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                if (!has(x, y, z - 1)) n = face(buf, n, c, 0, 0, -1, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z, x, y, z);
                if (!has(x, y, z + 1)) n = face(buf, n, c, 0, 0, 1, x, y, z + 1, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y, z + 1);
            }
            if (cells.isEmpty()) {
                minX = maxX = minY = maxY = 0;
            }
            int[][] front = new int[maxX - minX + 1][maxY - minY + 1];
            int[][] frontZ = new int[maxX - minX + 1][maxY - minY + 1];
            for (Map.Entry<Long, Integer> e : cells.entrySet()) {
                long k = e.getKey();
                int x = (int) ((k >> 40) & 0xFFFFF) - 512 - minX;
                int y = (int) ((k >> 20) & 0xFFFFF) - 512 - minY;
                int z = (int) (k & 0xFFFFF) - 512;
                if (front[x][y] == 0 || z < frontZ[x][y]) {
                    front[x][y] = e.getValue();
                    frontZ[x][y] = z;
                }
            }
            float[] out = new float[n * 16];
            System.arraycopy(buf, 0, out, 0, out.length);
            int[] list = new int[cells.size() * 4];
            int li = 0;
            for (Map.Entry<Long, Integer> e : cells.entrySet()) {
                long k = e.getKey();
                list[li++] = (int) ((k >> 40) & 0xFFFFF) - 512;
                list[li++] = (int) ((k >> 20) & 0xFFFFF) - 512;
                list[li++] = (int) (k & 0xFFFFF) - 512;
                list[li++] = e.getValue();
            }
            return new VoxelModel(out, n, minX, maxX, minY, maxY, front, list);
        }

        private static int face(float[] b, int n, int c, float nx, float ny, float nz, float... p) {
            int o = n * 16;
            System.arraycopy(p, 0, b, o, 12);
            b[o + 12] = nx;
            b[o + 13] = ny;
            b[o + 14] = nz;
            b[o + 15] = Float.intBitsToFloat(c);
            return n + 1;
        }
    }

    /** A 2D pixel canvas used to paint shapes before extruding them. */
    public static final class Grid {
        public final int w;
        public final int h;
        private final int[] px;
        private final int[] dp;

        public Grid(int w, int h) {
            this.w = w;
            this.h = h;
            this.px = new int[w * h];
            this.dp = new int[w * h];
        }

        public boolean in(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h;
        }

        public int get(int x, int y) {
            return in(x, y) ? px[y * w + x] : 0;
        }

        public void set(int x, int y, int c) {
            if (in(x, y)) {
                px[y * w + x] = c;
            }
        }

        public int depth(int x, int y) {
            return in(x, y) ? dp[y * w + x] : 0;
        }

        /** Marks a pixel as thicker than the rest (bones, ridges). */
        public void thick(int x, int y, int d) {
            if (in(x, y) && get(x, y) != 0) {
                dp[y * w + x] = d;
            }
        }

        public boolean filled(int x, int y) {
            return get(x, y) != 0;
        }

        /** Recolours every filled pixel that touches an empty one. */
        public void outline(int c) {
            int[] copy = px.clone();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (copy[y * w + x] != 0 && (edge(copy, x - 1, y) || edge(copy, x + 1, y) || edge(copy, x, y - 1) || edge(copy, x, y + 1))) {
                        px[y * w + x] = c;
                    }
                }
            }
        }

        private boolean edge(int[] copy, int x, int y) {
            return !in(x, y) || copy[y * w + x] == 0;
        }

        /** Draws a thick line with Bresenham's algorithm. */
        public void line(int x0, int y0, int x1, int y1, int c, int thickness) {
            int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
            int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
            int err = dx + dy;
            while (true) {
                for (int t = 0; t < thickness; t++) {
                    set(x0, y0 + t, c);
                }
                if (x0 == x1 && y0 == y1) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 >= dy) {
                    err += dy;
                    x0 += sx;
                }
                if (e2 <= dx) {
                    err += dx;
                    y0 += sy;
                }
            }
        }

        public void ellipse(double cx, double cy, double rx, double ry, int c) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double ex = (x + 0.5 - cx) / rx;
                    double ey = (y + 0.5 - cy) / ry;
                    if (ex * ex + ey * ey <= 1.0) {
                        set(x, y, c);
                    }
                }
            }
        }

        /** Fills a polygon given as x0, y0, x1, y1, ... (even-odd rule at pixel centres). */
        public void polygon(int c, double... pts) {
            int n = pts.length / 2;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double px0 = x + 0.5, py0 = y + 0.5;
                    boolean inside = false;
                    for (int i = 0, j = n - 1; i < n; j = i++) {
                        double xi = pts[i * 2], yi = pts[i * 2 + 1], xj = pts[j * 2], yj = pts[j * 2 + 1];
                        if ((yi > py0) != (yj > py0) && px0 < (xj - xi) * (py0 - yi) / (yj - yi) + xi) {
                            inside = !inside;
                        }
                    }
                    if (inside) {
                        set(x, y, c);
                    }
                }
            }
        }

        /** Copies the columns [from, to) into a new grid (used to split a wing into hinged sections). */
        public Grid columns(int from, int to) {
            Grid g = new Grid(to - from, h);
            for (int y = 0; y < h; y++) {
                for (int x = from; x < to; x++) {
                    g.set(x - from, y, get(x, y));
                    if (depth(x, y) > 0) {
                        g.dp[y * g.w + (x - from)] = depth(x, y);
                    }
                }
            }
            return g;
        }
    }
}
