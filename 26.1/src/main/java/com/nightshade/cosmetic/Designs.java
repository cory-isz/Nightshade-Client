package com.nightshade.cosmetic;

import com.nightshade.cosmetic.VoxelModel.Grid;

/** Every cosmetic shape, generated in code. All designs are original Nightshade art. */
public final class Designs {
    private Designs() {
    }

    /** A wing split at a hinge: the inner part attaches to the back, the outer part folds at {@code joint}. */
    public static final class Wing {
        public final VoxelModel inner;
        public final VoxelModel outer;
        public final VoxelModel innerGlow;
        public final VoxelModel outerGlow;
        public final VoxelModel layerInner;
        public final VoxelModel layerOuter;
        public final int joint;
        public final int anchorY;
        public final float scale;
        final Grid grid;
        final Grid glowGrid;

        Wing(Grid g, Grid glow, int joint, int anchorY, int depth, float scale) {
            this(g, glow, null, joint, anchorY, depth, scale);
        }

        Wing(Grid g, Grid glow, Grid front, int joint, int anchorY, int depth, float scale) {
            this.grid = g;
            this.glowGrid = glow;
            this.layerInner = front == null ? null : VoxelModel.builder().extrude(front.columns(0, joint), 0, 0, -1, 1).build();
            this.layerOuter = front == null ? null : VoxelModel.builder().extrude(front.columns(joint, front.w), 0, 0, -1, 1).build();
            this.joint = joint;
            this.anchorY = anchorY;
            this.scale = scale;
            this.inner = VoxelModel.builder().extrude(g.columns(0, joint), 0, 0, 0, depth).build();
            this.outer = VoxelModel.builder().extrude(g.columns(joint, g.w), 0, 0, 0, depth).build();
            // glowing details sit on both faces of the wing so they show from the front and from behind
            this.innerGlow = glow == null ? null : VoxelModel.builder().extrude(glow.columns(0, joint), 0, 0, -1, 1).extrude(glow.columns(0, joint), 0, 0, Math.max(depth, maxDepth(g)), 1).build();
            this.outerGlow = glow == null ? null : VoxelModel.builder().extrude(glow.columns(joint, glow.w), 0, 0, -1, 1).extrude(glow.columns(joint, glow.w), 0, 0, Math.max(depth, maxDepth(g)), 1).build();
        }

        private static int maxDepth(Grid g) {
            int d = 1;
            for (int y = 0; y < g.h; y++) {
                for (int x = 0; x < g.w; x++) {
                    d = Math.max(d, g.depth(x, y));
                }
            }
            return d;
        }

        /** Front view for thumbnails. */
        public int[][] preview() {
            int[][] out = new int[grid.w][grid.h];
            for (int x = 0; x < grid.w; x++) {
                for (int y = 0; y < grid.h; y++) {
                    out[x][y] = grid.get(x, y);
                }
            }
            return out;
        }
    }

    static int[][] merge(int[][] a, int[][] b) {
        int h = Math.max(a.length == 0 ? 0 : a[0].length, b.length == 0 ? 0 : b[0].length);
        int[][] out = new int[a.length + b.length][h];
        for (int x = 0; x < a.length; x++) {
            System.arraycopy(a[x], 0, out[x], 0, a[x].length);
        }
        for (int x = 0; x < b.length; x++) {
            System.arraycopy(b[x], 0, out[a.length + x], 0, b[x].length);
        }
        return out;
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 73856093 ^ y * 19349663 ^ seed * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((c >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((c & 0xFF) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ wings

    /** Classic feathered wing: rounded covert row along the top, long separated primaries below. */
    public static Wing feather(int outline, int main, int light, int tip, int sparkle, int seed, int w, int h) {
        Grid g = new Grid(w, h);
        Grid glow = sparkle == 0 ? null : new Grid(w, h);
        int peak = Math.round(w * 0.3f);
        for (int x = 0; x < w; x++) {
            int top = x < peak ? 6 - Math.round(5f * x / peak) : 1 + Math.round(4f * (x - peak) / (w - peak));
            double t = Math.min(1.0, x / (w * 0.72));
            int len = (int) Math.round(6 + (h - 9) * Math.sin(Math.PI * 0.5 * t));
            if (x > w * 0.86) {
                len -= (int) Math.round((x - w * 0.86) * 2.2);
            }
            int k = x % 3;
            int bottom = top + len - (k == 0 ? 2 : k == 2 ? 1 : 0);
            for (int y = top; y <= Math.min(h - 1, bottom); y++) {
                int rr = y - top;
                int c;
                if (rr == 0) {
                    c = outline;
                } else if (rr < 3) {
                    c = light;
                } else if (bottom - y < 3) {
                    c = mix(main, tip, (3 - (bottom - y)) / 3f);
                } else if (k == 0 && rr > 4) {
                    c = shade(main, 0.72f);
                } else {
                    c = mix(light, main, Math.min(1f, (rr - 3) / 4f));
                }
                g.set(x, y, c);
                if (rr < 3) {
                    g.thick(x, y, 2);
                }
                if (glow != null && rr > 3 && bottom - y > 1 && hash(x, y, seed) % 23 == 0) {
                    glow.set(x, y, sparkle);
                }
            }
        }
        g.outline(outline);
        return new Wing(g, glow, Math.round(w * 0.45f), 4, 1, 0.85f);
    }

    /** Two-layer angel wing: long primaries behind, shorter covert feathers layered in front. */
    public static Wing seraph() {
        int gold = 0xFFE8C468, white = 0xFFF8F4E8, cream = 0xFFE9DFC6, edge = 0xFF9C7A3A;
        Wing back = feather(edge, white, 0xFFFFFFFF, gold, 0xFFFFF2B0, 7, 28, 22);
        Grid front = new Grid(28, 22);
        for (int x = 0; x < 22; x++) {
            int top = x < 8 ? 6 - Math.round(5f * x / 8) : 1 + Math.round(3f * (x - 8) / 20);
            int len = 5 + (int) Math.round(3 * Math.sin(x * 0.35)) + (x % 3 == 1 ? 1 : 0);
            for (int y = top + 1; y <= top + len; y++) {
                front.set(x, y, y >= top + len - 1 ? gold : cream);
            }
        }
        front.outline(edge);
        return new Wing(back.grid, back.glowGrid, front, back.joint, back.anchorY, 1, 0.92f);
    }

    /** Bat / dragon membrane wing with raised bones and scalloped edges. */
    public static Wing membrane(int bone, int membrane, int membraneLight, int glowEdge) {
        int w = 26, h = 20;
        Grid g = new Grid(w, h);
        g.polygon(membrane, 0, 6, 9, 2, 25, 1, 24, 9, 17, 16, 8, 17, 0, 12);
        double[][] tips = {{25, 1}, {24, 9}, {17, 16}, {8, 17}, {0, 12}};
        for (int i = 0; i < tips.length - 1; i++) {
            double mx = (tips[i][0] + tips[i + 1][0]) / 2, my = (tips[i][1] + tips[i + 1][1]) / 2;
            double d = Math.hypot(tips[i][0] - tips[i + 1][0], tips[i][1] - tips[i + 1][1]);
            double cx = mx + (mx - 10) * 0.18, cy = my + (my - 6) * 0.18;
            Grid carve = new Grid(w, h);
            carve.ellipse(cx, cy, d * 0.42, d * 0.42, 1);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (carve.filled(x, y)) {
                        g.set(x, y, 0);
                    }
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y)) {
                    g.set(x, y, mix(membraneLight, membrane, Math.min(1f, (x + y) / 30f)));
                }
            }
        }
        Grid glow = glowEdge == 0 ? null : new Grid(w, h);
        if (glow != null) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (g.filled(x, y) && (!g.filled(x, y + 1) || !g.filled(x + 1, y)) && y > 3) {
                        glow.set(x, y, glowEdge);
                    }
                }
            }
        }
        g.line(0, 6, 9, 2, bone, 2);
        g.line(9, 2, 25, 1, bone, 1);
        g.line(9, 2, 24, 9, bone, 1);
        g.line(9, 2, 17, 16, bone, 1);
        g.line(9, 2, 8, 17, bone, 1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.get(x, y) == bone) {
                    g.thick(x, y, 2);
                }
            }
        }
        g.set(25, 0, bone);
        g.set(26 - 1, 1, bone);
        return new Wing(g, glow, 9, 5, 1, 0.85f);
    }

    /** Butterfly wing: two rounded lobes, veins and bright spots near the edge. */
    public static Wing butterfly(int outline, int inner, int outer, int spot) {
        int w = 24, h = 21;
        Grid g = new Grid(w, h);
        g.ellipse(12.5, 7.5, 11.5, 7.2, outer);
        g.ellipse(8.5, 15, 7.6, 5.6, outer);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y)) {
                    double d = Math.hypot(x - 1, y - 10) / 22.0;
                    g.set(x, y, mix(inner, outer, (float) d * 1.3f));
                }
            }
        }
        for (int a = 0; a < 5; a++) {
            double ang = Math.toRadians(-55 + a * 30);
            g.line(1, 10, 1 + (int) Math.round(Math.cos(ang) * 20), 10 + (int) Math.round(Math.sin(ang) * 12), shade(inner, 0.6f), 1);
        }
        int[][] spots = {{19, 4}, {21, 9}, {16, 12}, {11, 18}, {5, 18}};
        for (int[] s : spots) {
            g.ellipse(s[0] + 0.5, s[1] + 0.5, 1.6, 1.6, spot);
        }
        g.outline(outline);
        Grid glow = new Grid(w, h);
        for (int[] s : spots) {
            glow.set(s[0], s[1], 0xFFFFFFFF);
        }
        return new Wing(g, glow, 10, 8, 1, 0.8f);
    }

    /** Crystal wing: faceted shards fanning out, thicker in the middle. */
    public static Wing shards(int dark, int mid, int light, int glint) {
        int w = 26, h = 22;
        Grid g = new Grid(w, h);
        double[][] shards = {{-38, 23, 3.2}, {-16, 22, 3.6}, {6, 20, 3.4}, {28, 16, 3}, {50, 11, 2.6}};
        int i = 0;
        for (double[] s : shards) {
            double a = Math.toRadians(s[0]), len = s[1], wd = s[2];
            double bx = 1, by = 9;
            double tx = bx + Math.cos(a) * len, ty = by + Math.sin(a) * len * 0.75;
            double mxp = bx + Math.cos(a) * len * 0.45, myp = by + Math.sin(a) * len * 0.45 * 0.75;
            double nx = -Math.sin(a) * wd, ny = Math.cos(a) * wd * 0.75;
            g.polygon(i % 2 == 0 ? mid : light, bx, by, mxp + nx, myp + ny, tx, ty, mxp - nx, myp - ny);
            g.line((int) Math.round(bx), (int) Math.round(by), (int) Math.round(tx), (int) Math.round(ty), 0xFFFFFFFF, 1);
            i++;
        }
        g.outline(dark);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y) && g.get(x, y) != dark && x < 14) {
                    g.thick(x, y, 2);
                }
            }
        }
        Grid glow = new Grid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.get(x, y) == 0xFFFFFFFF && hash(x, y, 3) % 4 == 0) {
                    glow.set(x, y, glint);
                }
            }
        }
        return new Wing(g, glow, 11, 9, 1, 0.82f);
    }

    /** Flame wing: feather layout with flickering tongues; frame picks one of two phases. */
    public static Wing flame(int frame) {
        int w = 24, h = 21;
        Grid g = new Grid(w, h);
        Grid glow = new Grid(w, h);
        int[] ramp = {0xFFFFF0B0, 0xFFFFD27A, 0xFFFF9F43, 0xFFE8592A, 0xFF9A2A18};
        for (int x = 0; x < w; x++) {
            int top = x < 7 ? 6 - Math.round(5f * x / 7) : 1 + Math.round(4f * (x - 7) / (w - 7));
            double t = Math.min(1.0, x / (w * 0.7));
            int len = (int) Math.round(5 + 10 * Math.sin(Math.PI * 0.5 * t) + 2.5 * Math.sin(x * 1.1 + frame * 1.7));
            if (x > w * 0.85) {
                len -= (int) Math.round((x - w * 0.85) * 2.4);
            }
            for (int y = top; y <= Math.min(h - 1, top + len); y++) {
                float f = (y - top) / (float) Math.max(1, len);
                int idx = Math.min(ramp.length - 1, (int) (f * ramp.length));
                g.set(x, y, ramp[idx]);
                if (idx <= 1) {
                    glow.set(x, y, ramp[idx]);
                }
            }
        }
        g.outline(0xFF5A1A10);
        return new Wing(g, glow, 10, 4, 1, 0.85f);
    }

    /** Leaf wing: a fan of pointed leaves with raised midribs. */
    public static Wing leaves(int dark, int mid, int light, int rib) {
        int w = 24, h = 22;
        Grid g = new Grid(w, h);
        double[][] leaves = {{-30, 22, 4.2, 0}, {-5, 20, 4.4, 1}, {20, 16, 4, 0}, {45, 11, 3.4, 1}};
        for (double[] l : leaves) {
            double a = Math.toRadians(l[0]), len = l[1], wd = l[2];
            double bx = 1, by = 8;
            int col = l[3] == 0 ? mid : light;
            int n = 18;
            double[] pts = new double[n * 4];
            for (int k = 0; k < n; k++) {
                double s = k / (double) (n - 1);
                double half = Math.sin(Math.PI * s) * wd;
                double px = bx + Math.cos(a) * len * s, py = by + Math.sin(a) * len * s * 0.8;
                pts[k * 2] = px - Math.sin(a) * half;
                pts[k * 2 + 1] = py + Math.cos(a) * half * 0.8;
                pts[(2 * n - 1 - k) * 2] = px + Math.sin(a) * half;
                pts[(2 * n - 1 - k) * 2 + 1] = py - Math.cos(a) * half * 0.8;
            }
            g.polygon(col, pts);
            int tx = (int) Math.round(bx + Math.cos(a) * len * 0.95), ty = (int) Math.round(by + Math.sin(a) * len * 0.95 * 0.8);
            g.line((int) bx, (int) by, tx, ty, rib, 1);
        }
        g.outline(dark);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.get(x, y) == rib) {
                    g.thick(x, y, 2);
                }
            }
        }
        return new Wing(g, null, 10, 8, 1, 0.82f);
    }

    /** Mechanical wing: stacked angular plates with glowing circuit lines. */
    public static Wing circuit(int metal, int metalLight, int edge, int glowColor) {
        int w = 26, h = 20;
        Grid g = new Grid(w, h);
        Grid glow = new Grid(w, h);
        double[][] plates = {
                {0, 4, 10, 1, 25, 2, 20, 6, 4, 8},
                {0, 8, 10, 7, 23, 8, 18, 12, 3, 12},
                {0, 12, 9, 12, 19, 14, 14, 18, 2, 16}};
        int i = 0;
        for (double[] p : plates) {
            g.polygon(i % 2 == 0 ? metal : metalLight, p);
            i++;
        }
        g.outline(edge);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y) && g.get(x, y) != edge) {
                    g.thick(x, y, 2);
                }
            }
        }
        int[][] traces = {{2, 5, 19, 3}, {2, 9, 18, 9}, {2, 13, 14, 15}, {8, 3, 8, 15}};
        for (int[] t : traces) {
            Grid line = new Grid(w, h);
            line.line(t[0], t[1], t[2], t[3], 1, 1);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (line.filled(x, y) && g.filled(x, y)) {
                        glow.set(x, y, glowColor);
                    }
                }
            }
        }
        return new Wing(g, glow, 10, 6, 2, 0.82f);
    }

    // ------------------------------------------------------------------ capes (10 x 16, like the vanilla cape)

    public static final class Cape {
        public final VoxelModel model;
        public final VoxelModel glow;

        Cape(Grid g, Grid glow) {
            this.model = VoxelModel.builder().extrude(g, -5, 0, 0, 1).build();
            this.glow = glow == null ? null : VoxelModel.builder().extrude(glow, -5, 0, 1, 1).build();
        }
    }

    public static Cape cape(String style) {
        Grid g = new Grid(10, 17);
        Grid glow = null;
        switch (style) {
            case "moon": {
                fill(g, 0xFF2B2150, 0xFF241B44, 16);
                border(g, 0xFF7C6CFF, 16);
                Grid m = new Grid(10, 17);
                m.ellipse(5, 6, 3, 3, 0xFFB794FF);
                m.ellipse(6.2, 5.2, 2.6, 2.6, 0);
                for (int y = 0; y < 17; y++) {
                    for (int x = 0; x < 10; x++) {
                        if (m.filled(x, y)) {
                            g.set(x, y, 0xFFD9CCFF);
                            g.thick(x, y, 2);
                        }
                    }
                }
                break;
            }
            case "stars": {
                fill(g, 0xFF101A33, 0xFF0A1022, 16);
                glow = new Grid(10, 17);
                int[][] st = {{2, 2}, {7, 3}, {4, 6}, {8, 8}, {1, 10}, {5, 12}, {7, 14}, {3, 15}};
                for (int[] s : st) {
                    glow.set(s[0], s[1], 0xFFFFFFFF);
                }
                glow.set(5, 6, 0xFFFFE08A);
                break;
            }
            case "dusk": {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 10; x++) {
                        g.set(x, y, mix(0xFF3A1E6E, 0xFFFF9F43, y / 15f));
                    }
                }
                break;
            }
            case "tattered": {
                for (int y = 0; y < 17; y++) {
                    for (int x = 0; x < 10; x++) {
                        int len = 12 + hash(x, 0, 11) % 5;
                        if (y < len && hash(x, y, 5) % 19 != 0) {
                            g.set(x, y, mix(0xFF3A3A44, 0xFF22222A, y / 16f));
                        }
                    }
                }
                break;
            }
            case "aurora": {
                fill(g, 0xFF06121E, 0xFF0A0E1A, 16);
                glow = new Grid(10, 17);
                for (int x = 0; x < 10; x++) {
                    int y0 = 3 + (int) Math.round(Math.sin(x * 0.8) * 1.6);
                    for (int k = 0; k < 4; k++) {
                        glow.set(x, y0 + k, mix(0xFF4DFFB0, 0xFF7C6CFF, k / 3f));
                    }
                }
                glow.set(2, 12, 0xFFFFFFFF);
                glow.set(7, 10, 0xFFFFFFFF);
                glow.set(5, 14, 0xFFFFFFFF);
                break;
            }
            case "ember": {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 10; x++) {
                        g.set(x, y, mix(0xFF1A0E0A, 0xFF3A140C, y / 15f));
                    }
                }
                glow = new Grid(10, 17);
                for (int x = 0; x < 10; x++) {
                    int h = 3 + hash(x, 1, 7) % 4;
                    for (int k = 0; k < h; k++) {
                        glow.set(x, 15 - k, mix(0xFFFFD27A, 0xFFE8402A, k / (float) h));
                    }
                }
                break;
            }
            case "galaxy": {
                fill(g, 0xFF0B0820, 0xFF070514, 16);
                glow = new Grid(10, 17);
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 10; x++) {
                        double dx = x - 4.5, dy = (y - 7.5) * 0.7;
                        double r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
                        double arm = Math.sin(a * 2 - r * 0.9);
                        if (r < 6 && arm > 0.55) {
                            g.set(x, y, mix(0xFF5B2FB8, 0xFF2A3A9A, (float) (r / 6)));
                        }
                    }
                }
                glow.set(4, 7, 0xFFFFFFFF);
                glow.set(5, 8, 0xFFF0E6FF);
                int[][] st = {{1, 2}, {8, 3}, {2, 13}, {7, 14}, {9, 9}, {0, 8}};
                for (int[] p : st) {
                    glow.set(p[0], p[1], 0xFFFFE08A);
                }
                break;
            }
            case "sakura": {
                fill(g, 0xFFFFE6EF, 0xFFF7CFE0, 16);
                for (int i = 0; i < 9; i++) {
                    int x = hash(i, 3, 10) % 10, y = hash(i, 7, 15) % 15;
                    g.set(x, y, 0xFFFF8FC8);
                    g.thick(x, y, 2);
                    if (x + 1 < 10) {
                        g.set(x + 1, y, 0xFFFFB8DC);
                    }
                }
                for (int y = 10; y < 16; y++) {
                    g.set(0, y, 0xFF6B3A2A);
                }
                border(g, 0xFFFF6FB0, 16);
                break;
            }
            case "storm": {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 10; x++) {
                        g.set(x, y, hash(x, y, 9) % 5 == 0 ? 0xFF3A4152 : mix(0xFF2A2F3D, 0xFF161A24, y / 15f));
                    }
                }
                glow = new Grid(10, 17);
                int[][] bolt = {{6, 1}, {5, 2}, {5, 3}, {4, 4}, {4, 5}, {5, 6}, {6, 6}, {5, 7}, {4, 8}, {4, 9}, {3, 10}, {3, 11}, {2, 12}};
                for (int[] p : bolt) {
                    glow.set(p[0], p[1], 0xFFFFF27A);
                }
                glow.set(3, 12, 0xFFFFFFFF);
                break;
            }
            case "checker": {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 10; x++) {
                        g.set(x, y, ((x / 2 + y / 2) & 1) == 0 ? 0xFF1E1A2E : 0xFFECE8F7);
                    }
                }
                border(g, 0xFF7C6CFF, 16);
                break;
            }
            default: { // royal
                fill(g, 0xFFB01E30, 0xFF7A1222, 16);
                for (int y = 0; y < 16; y++) {
                    g.set(0, y, 0xFFE8C468);
                    g.set(9, y, 0xFFE8C468);
                }
                for (int x = 0; x < 10; x++) {
                    g.set(x, 15, 0xFFE8C468);
                    for (int y = 0; y < 3; y++) {
                        g.set(x, y, (x + y) % 4 == 0 ? 0xFF1A1A1A : 0xFFF4F0E6);
                        g.thick(x, y, 3);
                    }
                }
                break;
            }
        }
        return new Cape(g, glow);
    }

    private static void fill(Grid g, int top, int bottom, int rows) {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < g.w; x++) {
                g.set(x, y, mix(top, bottom, y / (float) rows));
            }
        }
    }

    private static void border(Grid g, int c, int rows) {
        for (int y = 0; y < rows; y++) {
            g.set(0, y, c);
            g.set(g.w - 1, y, c);
        }
        for (int x = 0; x < g.w; x++) {
            g.set(x, rows - 1, c);
        }
    }

    // ------------------------------------------------------------------ hats (head space: head spans x -4..3, y -8..-1, z -4..3)

    public static VoxelModel halo() {
        VoxelModel.Builder b = VoxelModel.builder();
        for (int a = 0; a < 360; a += 6) {
            double r = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(r) * 4.6 - 0.5);
            int z = (int) Math.round(Math.sin(r) * 4.6 - 0.5);
            b.set(x, -12, z, 0xFFFFE08A);
        }
        return b.build();
    }

    public static VoxelModel crown() {
        VoxelModel.Builder b = VoxelModel.builder();
        int gold = 0xFFE8C468, dark = 0xFFB08A38;
        for (int x = -5; x <= 4; x++) {
            for (int z = -5; z <= 4; z++) {
                boolean edge = x == -5 || x == 4 || z == -5 || z == 4;
                if (edge) {
                    b.set(x, -9, z, dark);
                    b.set(x, -10, z, gold);
                    boolean spike = (x == -5 || x == 4 || x == 0 || x == -1) && (z == -5 || z == 4) || (z == 0 || z == -1) && (x == -5 || x == 4);
                    if (spike) {
                        b.set(x, -11, z, gold);
                        if ((x == -5 || x == 4) && (z == -5 || z == 4)) {
                            b.set(x, -12, z, gold);
                        }
                    }
                }
            }
        }
        b.set(-1, -10, -6, 0xFFE8304A);
        b.set(0, -10, -6, 0xFFE8304A);
        b.set(-3, -10, -6, 0xFF3DA5FF);
        b.set(2, -10, -6, 0xFF3DA5FF);
        return b.build();
    }

    /** Thick horns that grow from the sides of the head, sweep outwards and curl back to a pale tip. */
    public static VoxelModel horns() {
        VoxelModel.Builder b = VoxelModel.builder();
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i <= 60; i++) {
                double t = i / 60.0;
                double x = side * (3.0 + 4.2 * t - 1.2 * t * t);
                double y = -8.6 - 6.5 * t + 2.2 * t * t * t;
                double z = -1.0 + 3.6 * t * t;
                double r = 1.35 * (1.0 - t * 0.75);
                int col = mix(0xFF2A2340, 0xFFF2ECFF, (float) Math.pow(t, 1.6));
                int ri = (int) Math.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dy = -ri; dy <= ri; dy++) {
                        for (int dz = -ri; dz <= ri; dz++) {
                            if (dx * dx + dy * dy + dz * dz <= r * r + 0.3) {
                                b.set((int) Math.floor(x + dx), (int) Math.floor(y + dy), (int) Math.floor(z + dz), col);
                            }
                        }
                    }
                }
            }
        }
        return b.build();
    }

    public static VoxelModel catEars() {
        VoxelModel.Builder b = VoxelModel.builder();
        int fur = 0xFF2A2340, pink = 0xFFFF8FC8;
        for (int side = -1; side <= 1; side += 2) {
            int base = side < 0 ? -4 : 1;
            for (int row = 0; row < 3; row++) {
                for (int x = row; x < 3 - row + (row == 0 ? 0 : 0); x++) {
                    int px = base + x + (side < 0 ? 0 : 0);
                    b.set(px, -9 - row, -2, fur);
                    b.set(px, -9 - row, -1, fur);
                }
            }
            b.set(base + 1, -9, -3, pink);
        }
        return b.build();
    }

    public static VoxelModel flowerCrown() {
        VoxelModel.Builder b = VoxelModel.builder();
        int[] petals = {0xFFFF8FC8, 0xFFFFE08A, 0xFFB794FF, 0xFF8EC5FF};
        int leaf = 0xFF4E9E5A;
        int i = 0;
        for (int x = -5; x <= 4; x++) {
            for (int z = -5; z <= 4; z++) {
                if (x == -5 || x == 4 || z == -5 || z == 4) {
                    b.set(x, -8, z, leaf);
                    if ((x + z + 10) % 3 == 0) {
                        b.set(x, -9, z, petals[i++ % petals.length]);
                    }
                }
            }
        }
        return b.build();
    }

    public static VoxelModel moon() {
        Grid g = new Grid(6, 6);
        g.ellipse(3, 3, 3, 3, 0xFFD9CCFF);
        g.ellipse(4.2, 2.2, 2.6, 2.6, 0);
        return VoxelModel.builder().extrude(g, -3, -3, 0, 2).build();
    }

    /** One small cube used for aura particles. */
    public static VoxelModel speck(int color) {
        return VoxelModel.builder().set(0, 0, 0, color).build();
    }
}
