package com.nightshade.cosmetic;

import com.nightshade.cosmetic.VoxelModel.Grid;

import java.util.ArrayList;
import java.util.List;

/** Every wing design. All original Nightshade art, generated from shapes so each one is clean at any size. */
public final class WingDesigns {
    private WingDesigns() {
    }

    // ------------------------------------------------------------------ helpers

    private static int hash(int x, int y, int seed) {
        int h = x * 73856093 ^ y * 19349663 ^ seed * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    static int mix(int a, int b, double t) {
        return Shapes.mix(a, b, t);
    }

    static int shade(int c, double f) {
        return Shapes.scale(c, (float) f);
    }

    /** Copies non-empty pixels of {@code src} onto {@code dst}. */
    private static void paste(Grid dst, Grid src) {
        for (int y = 0; y < src.h; y++) {
            for (int x = 0; x < src.w; x++) {
                if (src.filled(x, y)) {
                    dst.set(x, y, src.get(x, y));
                    if (src.depth(x, y) > 0) {
                        dst.thick(x, y, src.depth(x, y));
                    }
                }
            }
        }
    }

    /**
     * One feather: a tapered blade from its root along a direction, with an outline, a shaft line down the middle
     * and a colour fade to the tip. Drawn into its own grid then pasted, so neighbouring feathers stay separate.
     */
    static void feather(Grid layer, double rx, double ry, double angDeg, double len, double width, int edge, int fill, int tip, int shaft, boolean pointed) {
        Grid g = new Grid(layer.w, layer.h);
        double a = Math.toRadians(angDeg);
        double dx = Math.cos(a), dy = Math.sin(a);
        double nx = -dy, ny = dx;
        int steps = 16;
        double[] pts = new double[(steps + 1) * 4];
        for (int i = 0; i <= steps; i++) {
            double s = i / (double) steps;
            double prof;
            if (s < 0.15) {
                prof = 0.55 + 0.45 * (s / 0.15);
            } else if (s < 0.72) {
                prof = 1.0;
            } else {
                double u = (s - 0.72) / 0.28;
                prof = pointed ? (1 - u) * (1 - u * 0.35) : Math.sqrt(Math.max(0, 1 - u * u));
            }
            double hw = width / 2 * prof;
            double px = rx + dx * len * s, py = ry + dy * len * s;
            pts[i * 2] = px + nx * hw;
            pts[i * 2 + 1] = py + ny * hw;
            pts[(2 * steps + 1 - i) * 2] = px - nx * hw;
            pts[(2 * steps + 1 - i) * 2 + 1] = py - ny * hw;
        }
        g.polygon(1, pts);
        for (int y = 0; y < g.h; y++) {
            for (int x = 0; x < g.w; x++) {
                if (g.filled(x, y)) {
                    double s = ((x + 0.5 - rx) * dx + (y + 0.5 - ry) * dy) / len;
                    double side = ((x + 0.5 - rx) * nx + (y + 0.5 - ry) * ny) / (width / 2);
                    int c = s > 0.62 ? mix(fill, tip, (s - 0.62) / 0.38) : fill;
                    // one side of the vane is a touch darker: reads as the feather's curve
                    g.set(x, y, side > 0 ? c : shade(c, 0.9));
                }
            }
        }
        g.outline(edge);
        Grid shaftLine = new Grid(layer.w, layer.h);
        shaftLine.line((int) Math.round(rx), (int) Math.round(ry), (int) Math.round(rx + dx * len * 0.86), (int) Math.round(ry + dy * len * 0.86), 1, 1);
        for (int y = 0; y < g.h; y++) {
            for (int x = 0; x < g.w; x++) {
                if (shaftLine.filled(x, y) && g.filled(x, y) && g.get(x, y) != edge) {
                    g.set(x, y, shaft);
                }
            }
        }
        paste(layer, g);
    }

    /** Point along a polyline at fraction u (0..1) of its total length. */
    static double[] along(double[][] path, double u) {
        double total = 0;
        double[] seg = new double[path.length - 1];
        for (int i = 0; i < seg.length; i++) {
            seg[i] = Math.hypot(path[i + 1][0] - path[i][0], path[i + 1][1] - path[i][1]);
            total += seg[i];
        }
        double d = u * total;
        for (int i = 0; i < seg.length; i++) {
            if (d <= seg[i] || i == seg.length - 1) {
                double t = Math.min(1, d / seg[i]);
                return new double[]{path[i][0] + (path[i + 1][0] - path[i][0]) * t, path[i][1] + (path[i + 1][1] - path[i][1]) * t};
            }
            d -= seg[i];
        }
        return path[path.length - 1];
    }

    // ------------------------------------------------------------------ feathered wings

    /** Colours for a feathered wing: each row is {edge, fill, tip}. */
    public static final class Feathers {
        int[] primaries;
        int[] secondaries;
        int[] coverts;
        int shaft;
        int arm;
        int sparkle;
        int sparkleEvery;
        boolean glowTips;
        boolean pointed;
        float length = 1f;
        /** Angel shape: broader, rounder, fuller feathers. */
        boolean angelic;

        Feathers(int[] primaries, int[] secondaries, int[] coverts, int shaft, int arm) {
            this.primaries = primaries;
            this.secondaries = secondaries;
            this.coverts = coverts;
            this.shaft = shaft;
            this.arm = arm;
        }

        Feathers sparkle(int c, int every) {
            sparkle = c;
            sparkleEvery = every;
            return this;
        }

        Feathers glowTips() {
            glowTips = true;
            return this;
        }

        Feathers pointed() {
            pointed = true;
            return this;
        }

        Feathers length(float f) {
            length = f;
            return this;
        }

        /** Scatter little five-petal cherry blossoms over the wing. */
        boolean blossoms;

        Feathers blossoms() {
            blossoms = true;
            return this;
        }

        Feathers angelic() {
            angelic = true;
            return this;
        }
    }

    /**
     * One flight feather with a gently curved shaft. {@code bend} curls the tip sideways (towards the body when
     * negative); {@code notch} narrows the outer third like the emarginated primaries of real birds. The vane on
     * one side is slightly darker, the outline is a soft darker shade of the feather rather than black.
     */
    static void quill(Grid layer, double rx, double ry, double angDeg, double len, double width, int edge, int fill, int tip, int shaft,
                      boolean pointed, double bend, boolean notch) {
        Grid g = new Grid(layer.w, layer.h);
        double a = Math.toRadians(angDeg);
        double dx = Math.cos(a), dy = Math.sin(a);
        double nx = -dy, ny = dx;
        int steps = 24;
        double[] cx = new double[steps + 1], cy = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double s = i / (double) steps;
            cx[i] = rx + dx * len * s + nx * bend * len * s * s;
            cy[i] = ry + dy * len * s + ny * bend * len * s * s;
        }
        double[] pts = new double[(steps + 1) * 4];
        for (int i = 0; i <= steps; i++) {
            double s = i / (double) steps;
            double prof;
            if (s < 0.1) {
                prof = 0.5 + 0.5 * (s / 0.1);
            } else if (s < 0.7) {
                prof = notch && s > 0.5 ? 1.0 - (s - 0.5) * 1.2 : 1.0;
            } else {
                double u = (s - 0.7) / 0.3;
                double base = notch ? 0.76 : 1.0;
                prof = base * (pointed ? Math.pow(Math.max(0, 1 - u), 1.15) : Math.sqrt(Math.max(0, 1 - u * u)));
            }
            int k = Math.min(steps, i + 1), j = Math.max(0, i - 1);
            double tx = cx[k] - cx[j], ty = cy[k] - cy[j];
            double tl = Math.max(1e-6, Math.hypot(tx, ty));
            double px = -ty / tl, py = tx / tl;
            double hw = width / 2 * prof;
            pts[i * 2] = cx[i] + px * hw;
            pts[i * 2 + 1] = cy[i] + py * hw;
            pts[(2 * steps + 1 - i) * 2] = cx[i] - px * hw;
            pts[(2 * steps + 1 - i) * 2 + 1] = cy[i] - py * hw;
        }
        g.polygon(1, pts);
        for (int y = 0; y < g.h; y++) {
            for (int x = 0; x < g.w; x++) {
                if (g.filled(x, y)) {
                    double s = ((x + 0.5 - rx) * dx + (y + 0.5 - ry) * dy) / len;
                    double side = (x + 0.5 - rx) * nx + (y + 0.5 - ry) * ny - bend * len * s * s;
                    int c = s > 0.55 ? mix(fill, tip, (s - 0.55) / 0.45) : mix(shade(fill, 0.86), fill, s / 0.55);
                    g.set(x, y, side > 0 ? c : shade(c, 0.92));
                }
            }
        }
        g.outline(edge);
        for (int i = 1; i <= (int) (steps * 0.82); i++) {
            int sx = (int) Math.floor(cx[i] + 0.5 - 0.5), sy = (int) Math.floor(cy[i]);
            if (g.filled(sx, sy) && g.get(sx, sy) != edge) {
                g.set(sx, sy, shaft);
            }
        }
        paste(layer, g);
    }

    /** Point on a quadratic Bezier through three control points. */
    private static double[] bez(double[] p0, double[] p1, double[] p2, double t) {
        double u = 1 - t;
        return new double[]{u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0], u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]};
    }

    /**
     * An anatomical feathered wing: primaries fanning from the hand, secondaries along the forearm, three rows of
     * coverts layered on top and a band of tiny marginal feathers along the leading edge. Built at a finer scale so
     * the feathers are small and many, while the wing keeps its overall size.
     */
    public static WingModel feathered(Feathers f, WingModel lower) {
        boolean ang = f.angelic;
        int w = 68, h = ang ? 66 : 60;
        double L = f.length;
        // the silhouette, as seen from behind: shoulder -> elbow -> wrist along the leading edge, out to the wing
        // tip, then a trailing edge curving back to the body. Every feather is aimed so its tip lands on it.
        // Angel wings: a high rounded wrist, long primaries cascading down to a low pointed tip (the classic
        // arched angel wing). Bird wings: the wing reaches out level to a high tip and the trailing edge sweeps back.
        double[] sh, el, wr, c1, c2, tip, teCtrl, inner;
        if (ang) {
            sh = new double[]{2, 26}; el = new double[]{10, 9}; wr = new double[]{26, 2};
            // the leading edge leaves the back low and rises outward, so the wing grows beside the head, not behind it
            c1 = new double[]{14, 24}; c2 = new double[]{50, 2};
            tip = new double[]{60, Math.min(h - 3, 61 * L)};
            teCtrl = new double[]{30, Math.min(h - 3, 58 * L)};
            inner = new double[]{6, Math.min(h - 3, 36 * L)};
        } else {
            sh = new double[]{1, 20}; el = new double[]{16, 9}; wr = new double[]{30, 4};
            c1 = new double[]{14, 18}; c2 = new double[]{50, 3};
            tip = new double[]{66, 12 + 6 * (L - 1)};
            teCtrl = new double[]{48, Math.min(h - 2, 42 * L)};
            inner = new double[]{4, Math.min(h - 2, 36 * L)};
        }
        double[][] S5 = {sh, c1, wr, c2, tip};
        Grid prim = new Grid(w, h), sec = new Grid(w, h), grt = new Grid(w, h), med = new Grid(w, h), mar = new Grid(w, h), edge = new Grid(w, h);
        int[] P = soften(f.primaries), S = soften(f.secondaries), C = soften(f.coverts);
        // primaries: roots along the hand (wrist towards the tip), tips on the outer part of the trailing edge
        int np = ang ? 13 : 11;
        for (int k = 0; k < np; k++) {
            double t = k / (double) (np - 1);
            double[] r = lead(S5, 0.55 + t * 0.36, 1.2);
            double[] e = bez(tip, teCtrl, inner, (1 - t) * 0.46);
            aim(prim, r, e, ang ? 3.9 : 3.3, P, f.shaft, f.pointed || ang, ang ? 0.05 : -0.06, !ang && f.pointed && k >= np - 4);
        }
        // secondaries and tertials: roots along the arm, tips along the inner trailing edge
        int ns = ang ? 15 : 14;
        for (int k = 0; k < ns; k++) {
            double t = k / (double) (ns - 1);
            double[] r = lead(S5, t * 0.55, 1.2);
            double[] e = bez(tip, teCtrl, inner, 0.44 + (1 - t) * 0.56);
            aim(sec, r, e, ang ? 4.2 : 3.5, S, f.shaft, f.pointed && !ang, ang ? 0.04 : -0.04, false);
        }
        // greater coverts: about 40% of the way from the leading edge to the trailing edge
        int ng = 26;
        for (int k = 0; k < ng; k++) {
            double t = k / (double) (ng - 1);
            double[] r = lead(S5, t * 0.9, 1.6);
            double[] e = bez(tip, teCtrl, inner, 1 - t * 0.9);
            aim(grt, r, lerp(r, e, ang ? 0.4 : 0.38), ang ? 3.3 : 2.8, new int[]{C[0], mix(C[1], S[1], 0.3), C[2]}, C[1], false, 0.02, false);
        }
        // median coverts
        int nm = 28;
        for (int k = 0; k < nm; k++) {
            double t = k / (double) (nm - 1);
            double[] r = lead(S5, t * 0.86, 1.0);
            double[] e = bez(tip, teCtrl, inner, 1 - t * 0.86);
            aim(med, r, lerp(r, e, ang ? 0.22 : 0.2), ang ? 2.9 : 2.5, C, C[1], false, 0, false);
        }
        // marginal coverts: tiny scales hugging the leading edge
        int nn = 38;
        for (int k = 0; k < nn; k++) {
            double t = k / (double) (nn - 1);
            double[] r = lead(S5, t * 0.8, 0.2);
            double[] e = bez(tip, teCtrl, inner, 1 - t * 0.8);
            aim(mar, r, lerp(r, e, 0.09), 2.3, new int[]{C[0], C[2], C[2]}, C[2], false, 0, false);
        }
        for (int i = 0; i <= 60; i++) {
            double[] p = lead(S5, i / 60.0 * 0.8, -0.4);
            edge.set((int) Math.floor(p[0]), (int) Math.floor(p[1]), f.arm);
            edge.set((int) Math.floor(p[0]), (int) Math.floor(p[1]) + 1, f.arm);
        }
        Grid primGlow = null, secGlow = null, edgeGlow = null;
        if (f.sparkle != 0 || f.glowTips) {
            primGlow = new Grid(w, h);
            secGlow = new Grid(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (f.sparkle != 0 && prim.filled(x, y) && hash(x, y, 9) % f.sparkleEvery == 0) {
                        primGlow.set(x, y, f.sparkle);
                    }
                    if (f.sparkle != 0 && sec.filled(x, y) && hash(x, y, 4) % f.sparkleEvery == 0) {
                        secGlow.set(x, y, f.sparkle);
                    }
                    if (f.glowTips && prim.filled(x, y) && prim.get(x, y) == P[2]) {
                        primGlow.set(x, y, P[2]);
                    }
                }
            }
        }
        if (ang && f.sparkle != 0) {
            edgeGlow = new Grid(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (edge.filled(x, y)) {
                        edgeGlow.set(x, y, f.arm);
                    }
                }
            }
        }
        Grid flowers = null;
        if (f.blossoms) {
            flowers = new Grid(w, h);
            for (int k = 0; k < 16; k++) {
                double t = 0.04 + k / 16.0 * 0.84;
                double[] r = lead(S5, t, 2.5 + (hash(k, 3, 1) % 5) * 1.6);
                if (!sec.filled((int) r[0], (int) r[1]) && !prim.filled((int) r[0], (int) r[1])) {
                    continue;
                }
                blossom(flowers, r[0], r[1], k % 3 == 0 ? 0xFFFFFFFF : 0xFFFFD0E6);
            }
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(prim, primGlow, 0, 1));
        layers.add(new WingModel.Layer(sec, secGlow, 1, 1));
        layers.add(new WingModel.Layer(grt, null, 2, 1));
        layers.add(new WingModel.Layer(med, null, 3, 1));
        layers.add(new WingModel.Layer(mar, null, 4, 1));
        layers.add(new WingModel.Layer(edge, edgeGlow, 4, 2));
        if (flowers != null) {
            Grid centres = new Grid(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (flowers.get(x, y) == 0xFFFFE27A) {
                        centres.set(x, y, 0xFFFFE27A);
                    }
                }
            }
            layers.add(new WingModel.Layer(flowers, centres, 5, 1));
        }
        return new WingModel(layers, new int[]{(int) el[0], (int) wr[0]}, (int) sh[1], ang ? 0.74f : 0.7f, 1f, 1f, lower);
    }

    /** A tiny five-petal blossom with a yellow centre. */
    private static void blossom(Grid g, double cx, double cy, int petal) {
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + i * 72);
            double px = cx + Math.cos(a) * 1.6, py = cy + Math.sin(a) * 1.6;
            g.set((int) Math.floor(px), (int) Math.floor(py), petal);
            g.set((int) Math.floor(cx + Math.cos(a) * 0.9), (int) Math.floor(cy + Math.sin(a) * 0.9), mix(petal, 0xFFFF8FC8, 0.35));
        }
        g.set((int) Math.floor(cx), (int) Math.floor(cy), 0xFFFFE27A);
    }

    /** A point along the leading edge (shoulder -> elbow -> wrist -> toward the tip), {@code down} voxels below it. */
    private static double[] lead(double[][] s, double t, double down) {
        double[] p = t < 0.55 ? bez(s[0], s[1], s[2], t / 0.55) : bez(s[2], s[3], s[4], (t - 0.55) / 0.45);
        return new double[]{p[0], p[1] + down};
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    /** Draws a feather from {@code r} with its tip at {@code e}. */
    private static void aim(Grid g, double[] r, double[] e, double width, int[] col, int shaft, boolean pointed, double bend, boolean notch) {
        double len = Math.hypot(e[0] - r[0], e[1] - r[1]);
        if (len < 1.5) {
            return;
        }
        double ang = Math.toDegrees(Math.atan2(e[1] - r[1], e[0] - r[0]));
        quill(g, r[0], r[1], ang, len, width, col[0], col[1], col[2], shaft, pointed, bend, notch);
    }

    /** Outlines a little darker than the feather instead of a hard dark line, so the feathers blend. */
    private static int[] soften(int[] row) {
        return new int[]{mix(row[1], row[0], 0.55), row[1], row[2]};
    }

    private static final double[][] ARM = {{0, 11}, {13, 4}, {27, 2}, {45, 6}};

    // ------------------------------------------------------------------ membrane wings (bat, dragon, demon)

    public static WingModel membrane(int bone, int memTop, int memBottom, int edgeGlow, boolean dragon) {
        int w = 47, h = 38;
        Grid mem = new Grid(w, h), bones = new Grid(w, h);
        double[] wrist = {24, 2};
        double[][] tips = {{45, 1}, {43, 16}, {33, 28}, {19, 32}, {0, 24}};
        mem.polygon(1, 0, 11, 14, 4, wrist[0], wrist[1], 45, 1, 43, 16, 33, 28, 19, 32, 0, 24);
        for (int i = 0; i < tips.length - 1; i++) {
            double mx = (tips[i][0] + tips[i + 1][0]) / 2, my = (tips[i][1] + tips[i + 1][1]) / 2;
            double d = Math.hypot(tips[i][0] - tips[i + 1][0], tips[i][1] - tips[i + 1][1]);
            double cx = mx + (mx - 18) * 0.22, cy = my + (my - 10) * 0.22;
            Grid carve = new Grid(w, h);
            carve.ellipse(cx, cy, d * 0.4, d * 0.4, 1);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (carve.filled(x, y)) {
                        mem.set(x, y, 0);
                    }
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mem.filled(x, y)) {
                    int c = mix(memTop, memBottom, (y + x * 0.3) / 40.0);
                    if (dragon && (x + y * 2) % 4 == 0) {
                        c = shade(c, 0.82);
                    }
                    mem.set(x, y, c);
                }
            }
        }
        Grid glow = edgeGlow == 0 ? null : new Grid(w, h);
        if (glow != null) {
            for (int y = 6; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (mem.filled(x, y) && (!mem.filled(x, y + 1) || !mem.filled(x + 1, y) || !mem.filled(x - 1, y))) {
                        glow.set(x, y, edgeGlow);
                    }
                }
            }
        }
        bones.line(0, 11, 14, 4, bone, 2);
        bones.line(14, 4, 24, 2, bone, 2);
        for (double[] t : tips) {
            if (t[0] > 0) {
                bones.line(24, 2, (int) t[0], (int) t[1], bone, 1);
            }
        }
        bones.set(24, 1, bone);
        bones.set(25, 0, bone);
        bones.set(23, 0, bone);
        if (dragon) {
            int horn = 0xFFE8D8B0;
            for (int x = 2; x < 23; x += 4) {
                int y = (int) Math.round(11 - (x < 14 ? x * 0.5 : 7 + (x - 14) * 0.2)) - 1;
                bones.set(x, y, horn);
                bones.set(x + 1, y - 1, horn);
            }
            bones.set(26, 0, horn);
            bones.set(27, 1, horn);
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(mem, glow, 0, 1));
        layers.add(new WingModel.Layer(bones, null, 1, 2));
        return new WingModel(layers, new int[]{14, 24}, 11, 1f, 1.1f, 1.1f, null);
    }

    // ------------------------------------------------------------------ butterflies

    public static WingModel butterfly(String kind) {
        boolean luna = kind.equals("luna");
        int w = 44, h = luna ? 52 : 40;
        Grid g = new Grid(w, h);
        Grid glow = new Grid(w, h);
        g.polygon(1, 1, 13, 10, 2, 28, 0, 42, 4, 40, 14, 30, 21, 12, 22, 2, 18);
        g.ellipse(13, 28, 12.5, 10.5, 1);
        if (luna) {
            g.polygon(1, 8, 34, 14, 36, 9, 50, 5, 49);
        }
        int veins, border, inner, outer, spot;
        switch (kind) {
            case "monarch" -> {
                veins = 0xFF120C08;
                border = 0xFF160E0A;
                inner = 0xFFFFA040;
                outer = 0xFFE05A1A;
                spot = 0xFFFFFFFF;
            }
            case "luna" -> {
                veins = 0xFF6A9A5A;
                border = 0xFF4E7A44;
                inner = 0xFFDFFFE2;
                outer = 0xFF8FDCA0;
                spot = 0xFFFFD27A;
            }
            case "nebula" -> {
                veins = 0xFF1A0A2E;
                border = 0xFF0C0618;
                inner = 0xFFFF7AD9;
                outer = 0xFF3A1E8A;
                spot = 0xFFFFFFFF;
            }
            default -> { // morpho
                veins = 0xFF0A0E2A;
                border = 0xFF080A1C;
                inner = 0xFF7FE0FF;
                outer = 0xFF1E5AE8;
                spot = 0xFFFFFFFF;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y)) {
                    double d = Math.hypot(x - 1, y - 18) / 40.0;
                    g.set(x, y, mix(inner, outer, d * 1.35));
                }
            }
        }
        Grid veinLines = new Grid(w, h);
        for (int a = 0; a < 7; a++) {
            double ang = Math.toRadians(-62 + a * 22);
            veinLines.line(1, 18, 1 + (int) Math.round(Math.cos(ang) * 44), 18 + (int) Math.round(Math.sin(ang) * 26), veins, 1);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (veinLines.filled(x, y) && g.filled(x, y)) {
                    g.set(x, y, veins);
                }
            }
        }
        Grid copy = new Grid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy.set(x, y, g.get(x, y));
            }
        }
        // a wide dark border, then dots along it
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (copy.filled(x, y)) {
                    boolean nearEdge = false;
                    for (int dy = -2; dy <= 2 && !nearEdge; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (!copy.filled(x + dx, y + dy)) {
                                nearEdge = true;
                                break;
                            }
                        }
                    }
                    if (nearEdge && !kind.equals("luna")) {
                        g.set(x, y, border);
                        if (hash(x, y, 2) % 9 == 0 && copy.filled(x - 2, y) && copy.filled(x + 2, y) && copy.filled(x, y - 2) && copy.filled(x, y + 2)) {
                            g.set(x, y, spot);
                        }
                    }
                }
            }
        }
        g.outline(border);
        if (kind.equals("luna")) {
            g.ellipse(14, 28, 3.2, 3.2, 0xFF7A1E2A);
            g.ellipse(14, 28, 2, 2, spot);
            g.ellipse(24, 11, 2.4, 2.4, 0xFF7A1E2A);
            g.ellipse(24, 11, 1.3, 1.3, spot);
            glow.set(14, 28, spot);
            glow.set(24, 11, spot);
        } else if (kind.equals("morpho") || kind.equals("nebula")) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (g.filled(x, y) && g.get(x, y) != border && g.get(x, y) != veins && hash(x, y, 7) % 17 == 0) {
                        glow.set(x, y, kind.equals("nebula") ? (hash(x, y, 3) % 3 == 0 ? 0xFFFFE08A : 0xFFFFFFFF) : 0xFFB8F0FF);
                    }
                }
            }
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(g, glow, 0, 1));
        return new WingModel(layers, new int[]{15, 30}, 14, 0.92f, 1.5f, 0.6f, null);
    }

    // ------------------------------------------------------------------ the rest

    public static WingModel crystal() {
        int w = 62, h = 54;
        Grid g = new Grid(w, h), glow = new Grid(w, h);
        double[][] shards = {{-40, 40, 5}, {-22, 38, 5.6}, {-3, 34, 5.4}, {17, 28, 4.8}, {37, 21, 4}, {56, 14, 3.2}};
        int[] light = {0xFFE6F0FF, 0xFFC9DCFF};
        int[] dark = {0xFF8EA8F0, 0xFF6F88D8};
        int i = 0;
        for (double[] s : shards) {
            double a = Math.toRadians(s[0]), len = s[1] * 0.76, wd = s[2] * 1.35;
            double bx = 1, by = 19;
            double tx = bx + Math.cos(a) * len * 1.6, ty = by + Math.sin(a) * len;
            double mxp = bx + Math.cos(a) * len * 0.75, myp = by + Math.sin(a) * len * 0.47;
            double nx = -Math.sin(a) * wd, ny = Math.cos(a) * wd * 0.8;
            Grid left = new Grid(w, h), right = new Grid(w, h);
            left.polygon(dark[i % 2], bx, by, mxp + nx, myp + ny, tx, ty);
            right.polygon(light[i % 2], bx, by, tx, ty, mxp - nx, myp - ny);
            paste(g, left);
            paste(g, right);
            glow.set((int) Math.round(tx), (int) Math.round(ty), 0xFFFFFFFF);
            i++;
        }
        g.outline(0xFF3A4A80);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y) && hash(x, y, 5) % 37 == 0 && g.get(x, y) != 0xFF3A4A80) {
                    glow.set(x, y, 0xFFFFFFFF);
                }
            }
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(g, glow, 0, 2));
        return new WingModel(layers, new int[]{19, 38}, 19, 0.75f, 0.6f, 0.6f, null);
    }

    public static WingModel circuit() {
        int w = 47, h = 36;
        Grid g = new Grid(w, h), glow = new Grid(w, h), frame = new Grid(w, h);
        double[][] plates = {
                {0, 7, 18, 2, 46, 3, 38, 11, 6, 13},
                {0, 14, 17, 12, 41, 14, 32, 22, 4, 21},
                {0, 22, 15, 22, 34, 25, 26, 32, 3, 29}};
        int[] metal = {0xFF3C4250, 0xFF4A5262, 0xFF343A46};
        for (int i = 0; i < plates.length; i++) {
            Grid p = new Grid(w, h);
            p.polygon(metal[i], plates[i]);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (p.filled(x, y)) {
                        p.set(x, y, mix(metal[i], 0xFF1E2128, y / 50.0 + (x % 7 == 0 ? 0.15 : 0)));
                    }
                }
            }
            p.outline(0xFF16181E);
            paste(g, p);
        }
        frame.line(0, 7, 18, 2, 0xFF5A6272, 2);
        frame.line(18, 2, 46, 3, 0xFF5A6272, 2);
        int[][] traces = {{3, 9, 34, 6}, {3, 16, 31, 15}, {3, 24, 24, 26}, {12, 6, 12, 28}, {24, 5, 24, 18}};
        for (int[] t : traces) {
            Grid line = new Grid(w, h);
            line.line(t[0], t[1], t[2], t[3], 1, 1);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (line.filled(x, y) && g.filled(x, y)) {
                        glow.set(x, y, 0xFF3DF5FF);
                    }
                }
            }
        }
        for (int[] r : new int[][]{{5, 10}, {20, 7}, {36, 8}, {6, 18}, {28, 17}, {8, 25}, {20, 27}}) {
            glow.set(r[0], r[1], 0xFFB8FBFF);
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(g, glow, 0, 2));
        layers.add(new WingModel.Layer(frame, null, 2, 1));
        return new WingModel(layers, new int[]{17, 30}, 8, 1f, 0.5f, 0.7f, null);
    }

    public static WingModel leaves() {
        int w = 62, h = 62;
        Grid g = new Grid(w, h), ribs = new Grid(w, h);
        double[][] leaves = {{-30, 56, 10}, {-10, 52, 10.5}, {10, 44, 10}, {30, 34, 8.6}, {50, 24, 7}, {68, 16, 5.4}};
        int[] cols = {0xFF3F8A4E, 0xFF5AAE62, 0xFF4E9E5A, 0xFF6CC27A, 0xFF57A866, 0xFF4A9858};
        int i = 0;
        for (double[] l : leaves) {
            double a = Math.toRadians(l[0]), len = l[1], wd = l[2];
            double bx = 1, by = 26;
            int n = 30;
            double[] pts = new double[n * 4];
            for (int k = 0; k < n; k++) {
                double s = k / (double) (n - 1);
                double half = Math.pow(Math.sin(Math.PI * s), 0.8) * wd / 2 * (1 + 0.12 * Math.sin(s * 40));
                double px = bx + Math.cos(a) * len * s, py = by + Math.sin(a) * len * s * 0.85;
                pts[k * 2] = px - Math.sin(a) * half;
                pts[k * 2 + 1] = py + Math.cos(a) * half * 0.85;
                pts[(2 * n - 1 - k) * 2] = px + Math.sin(a) * half;
                pts[(2 * n - 1 - k) * 2 + 1] = py - Math.cos(a) * half * 0.85;
            }
            Grid leaf = new Grid(w, h);
            leaf.polygon(cols[i], pts);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (leaf.filled(x, y)) {
                        double s = ((x - bx) * Math.cos(a) + (y - by) * Math.sin(a)) / len;
                        leaf.set(x, y, mix(cols[i], 0xFFBFF0C4, Math.max(0, s - 0.55)));
                    }
                }
            }
            leaf.outline(0xFF1E3A24);
            paste(g, leaf);
            int tx = (int) Math.round(bx + Math.cos(a) * len * 0.92), ty = (int) Math.round(by + Math.sin(a) * len * 0.92 * 0.85);
            ribs.line((int) bx, (int) by, tx, ty, 0xFF2E6A38, 1);
            for (int v = 1; v < 5; v++) {
                double s = v / 5.5;
                int vx = (int) Math.round(bx + Math.cos(a) * len * s), vy = (int) Math.round(by + Math.sin(a) * len * s * 0.85);
                ribs.line(vx, vy, vx + (int) Math.round(Math.cos(a + 0.8) * wd * 0.4), vy + (int) Math.round(Math.sin(a + 0.8) * wd * 0.4), 0xFF2E6A38, 1);
                ribs.line(vx, vy, vx + (int) Math.round(Math.cos(a - 0.8) * wd * 0.4), vy + (int) Math.round(Math.sin(a - 0.8) * wd * 0.4), 0xFF2E6A38, 1);
            }
            i++;
        }
        Grid veinsOnLeaf = new Grid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (ribs.filled(x, y) && g.filled(x, y)) {
                    veinsOnLeaf.set(x, y, ribs.get(x, y));
                }
            }
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(g, null, 0, 1));
        layers.add(new WingModel.Layer(veinsOnLeaf, null, 1, 1));
        return new WingModel(layers, new int[]{20, 38}, 26, 0.78f, 1f, 0.8f, null);
    }

    public static WingModel sakura() {
        return feathered(new Feathers(new int[]{0xFFC0507E, 0xFFFFA6CF, 0xFFFFF0F7}, new int[]{0xFFC0507E, 0xFFFFB8DC, 0xFFFFF4FA},
                new int[]{0xFFB04A74, 0xFFFF8FC8, 0xFFFFD6EA}, 0xFFE07AAE, 0xFF5A3A2A).blossoms(), null);
    }

    public static WingModel fairy() {
        int w = 40, h = 44;
        Grid g = new Grid(w, h), glow = new Grid(w, h);
        g.polygon(1, 1, 12, 14, 3, 30, 0, 39, 3, 32, 9, 14, 17, 2, 17);
        g.polygon(1, 1, 19, 12, 22, 18, 32, 14, 42, 9, 40, 3, 30);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y)) {
                    g.set(x, y, mix(0xFFE6FFFB, 0xFF9FE8F0, Math.hypot(x, y - 16) / 40.0));
                }
            }
        }
        int vein = 0xFF6FB8C8;
        g.line(1, 15, 38, 3, vein, 1);
        g.line(1, 15, 28, 8, vein, 1);
        g.line(1, 15, 20, 14, vein, 1);
        g.line(1, 19, 14, 40, vein, 1);
        g.line(1, 19, 16, 30, vein, 1);
        g.outline(0xFF5AA8BC);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.get(x, y) == 0xFF5AA8BC) {
                    glow.set(x, y, 0xFFC8FFFF);
                }
            }
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(g, glow, 0, 1));
        return new WingModel(layers, new int[]{13, 26}, 15, 0.9f, 1.6f, 0.4f, null);
    }

    /** Bee wings: two clear lobes with a bold dark outline and dark veins. */
    public static WingModel bee() {
        int w = 40, h = 40;
        Grid g = new Grid(w, h);
        g.polygon(1, 1, 12, 12, 2, 30, 1, 38, 6, 30, 13, 12, 17, 1, 16);
        g.polygon(1, 1, 18, 11, 20, 20, 25, 22, 33, 15, 35, 5, 28, 1, 22);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.filled(x, y)) {
                    g.set(x, y, mix(0xFFFFFFFF, 0xFFDCE8F2, Math.hypot(x, y - 16) / 36.0));
                }
            }
        }
        int dark = 0xFF1A1820;
        g.line(1, 14, 36, 6, dark, 1);
        g.line(12, 10, 26, 13, dark, 1);
        g.line(1, 20, 18, 31, dark, 1);
        g.line(9, 22, 15, 33, dark, 1);
        g.outline(dark);
        Grid edge = new Grid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g.get(x, y) == dark) {
                    g.thick(x, y, 2);
                }
            }
        }
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(g, null, 0, 1));
        return new WingModel(layers, new int[]{13, 26}, 15, 0.9f, 1.7f, 0.35f, null);
    }

    public static WingModel skeletal() {
        int w = 47, h = 38;
        Grid g = new Grid(w, h);
        int bone = 0xFFE9E0C8, dark = 0xFFB8AC8E;
        for (int i = 0; i < ARM.length - 1; i++) {
            g.line((int) ARM[i][0], (int) ARM[i][1], (int) ARM[i + 1][0], (int) ARM[i + 1][1], bone, 2);
        }
        for (double[] j : ARM) {
            g.ellipse(j[0], j[1] + 0.5, 1.8, 1.8, dark);
        }
        for (int k = 0; k < 9; k++) {
            double u = 0.08 + k * 0.105;
            double[] p = along(ARM, u);
            double ang = Math.toRadians(95 - k * 7.5);
            double len = 12 + 8 * Math.sin(Math.PI * (k + 0.5) / 9);
            int ex = (int) Math.round(p[0] + Math.cos(ang) * len), ey = (int) Math.round(p[1] + Math.sin(ang) * len);
            g.line((int) Math.round(p[0]), (int) Math.round(p[1]) + 1, ex, ey, bone, 1);
            g.ellipse(ex + 0.5, ey + 0.5, 1.1, 1.1, dark);
        }
        Grid glow = new Grid(w, h);
        glow.set(13, 4, 0xFF7CFFB8);
        glow.set(27, 2, 0xFF7CFFB8);
        List<WingModel.Layer> layers = new ArrayList<>();
        layers.add(new WingModel.Layer(g, glow, 0, 2));
        return new WingModel(layers, new int[]{13, 27}, 11, 1f, 0.8f, 1f, null);
    }

    // ------------------------------------------------------------------ the catalogue's wings

    public static WingModel build(String id) {
        return switch (id) {
            case "moonlit" -> feathered(new Feathers(new int[]{0xFF1E1638, 0xFF4B3D8A, 0xFFB6A4FF}, new int[]{0xFF221A40, 0xFF5B4B9A, 0xFF9C8CF0},
                    new int[]{0xFF2A2048, 0xFF7C6CFF, 0xFFD9CCFF}, 0xFF2A2048, 0xFF1A1230).sparkle(0xFFFFFFFF, 30), null);
            case "frost" -> feathered(new Feathers(new int[]{0xFF5A86A8, 0xFF8EC2E6, 0xFFF4FBFF}, new int[]{0xFF5A86A8, 0xFFA6D2EE, 0xFFF0FAFF},
                    new int[]{0xFF6E98B8, 0xFFC6E6FA, 0xFFFFFFFF}, 0xFF6E98B8, 0xFF6E98B8).sparkle(0xFFE6F6FF, 40), null);
            case "raven" -> feathered(new Feathers(new int[]{0xFF06070C, 0xFF151924, 0xFF2E3C66}, new int[]{0xFF06070C, 0xFF191E2C, 0xFF34457A},
                    new int[]{0xFF0A0C14, 0xFF222838, 0xFF4A5C8C}, 0xFF2A3550, 0xFF0A0C14).pointed(), null);
            case "dusk" -> feathered(new Feathers(new int[]{0xFF3A1E4E, 0xFF8A3E8E, 0xFFFF9F43}, new int[]{0xFF3A1E4E, 0xFFA04E8A, 0xFFFFB86B},
                    new int[]{0xFF2A1640, 0xFF5B3A9A, 0xFFFF8FC8}, 0xFF4A2A5E, 0xFF2A1640), null);
            case "phoenix" -> feathered(new Feathers(new int[]{0xFF6A1A10, 0xFFE8592A, 0xFFFFD27A}, new int[]{0xFF7A2A14, 0xFFFF9F43, 0xFFFFF0B0},
                    new int[]{0xFF8A3A18, 0xFFFFC857, 0xFFFFF6D0}, 0xFFFFD27A, 0xFF8A3A18).glowTips().pointed().length(1.1f), null);
            case "angel" -> feathered(new Feathers(new int[]{0xFFCFC8B8, 0xFFF4F1EA, 0xFFFFFFFF}, new int[]{0xFFD2CCBE, 0xFFF8F6F0, 0xFFFFFFFF},
                    new int[]{0xFFD8D2C6, 0xFFFFFFFF, 0xFFFFFFFF}, 0xFFE4DED2, 0xFFF2EEE4).angelic(), null);
            case "owl" -> feathered(new Feathers(new int[]{0xFF3A2614, 0xFF7A5634, 0xFFD8C2A0}, new int[]{0xFF3A2614, 0xFF8A6440, 0xFFE0CCAA},
                    new int[]{0xFF4A3220, 0xFFA07850, 0xFFE8D6B8}, 0xFF5A3E24, 0xFF4A3220), null);
            case "storm" -> membrane(0xFF1A1E26, 0xFF3A4458, 0xFF1C222E, 0xFF5AE8FF, false);
            case "nebula" -> butterfly("nebula");
            case "starlight" -> feathered(new Feathers(new int[]{0xFF0E1640, 0xFF26408A, 0xFFE8F0FF}, new int[]{0xFF101A48, 0xFF2E4C9A, 0xFFDDE8FF},
                            new int[]{0xFF0E1640, 0xFF1E2E6E, 0xFF6C86D8}, 0xFF8EA8F0, 0xFFE8F0FF).angelic().sparkle(0xFFFFFFFF, 11).glowTips().length(1.05f),
                    feathered(new Feathers(new int[]{0xFF0E1640, 0xFF26408A, 0xFFE8F0FF}, new int[]{0xFF101A48, 0xFF2E4C9A, 0xFFDDE8FF},
                            new int[]{0xFF0E1640, 0xFF1E2E6E, 0xFF6C86D8}, 0xFF8EA8F0, 0xFFE8F0FF).angelic().sparkle(0xFFFFFFFF, 11).length(0.82f), null));
            case "seraph" -> feathered(new Feathers(new int[]{0xFFD6CDB6, 0xFFF6F2E8, 0xFFFFFFFF}, new int[]{0xFFD8D0BC, 0xFFFAF7EF, 0xFFFFFFFF},
                            new int[]{0xFFDCD4C2, 0xFFFFFFFF, 0xFFFFFDF6}, 0xFFE8DFC8, 0xFFFFE6A0).angelic().sparkle(0xFFFFF4C8, 70).length(1.1f),
                    feathered(new Feathers(new int[]{0xFFD6CDB6, 0xFFF6F2E8, 0xFFFFFFFF}, new int[]{0xFFD8D0BC, 0xFFFAF7EF, 0xFFFFFFFF},
                            new int[]{0xFFDCD4C2, 0xFFFFFFFF, 0xFFFFFDF6}, 0xFFE8DFC8, 0xFFFFE6A0).angelic().length(0.85f), null));
            case "fallen" -> feathered(new Feathers(new int[]{0xFF050507, 0xFF15151C, 0xFF7A1222}, new int[]{0xFF050507, 0xFF1C1C24, 0xFF4A4A56},
                    new int[]{0xFF0A0A0E, 0xFF2A2A34, 0xFF5A5A66}, 0xFF3A3A46, 0xFF0A0A0E).pointed().sparkle(0xFFFF4D4D, 60), null);
            case "bee" -> bee();
            case "void" -> membrane(0xFF0E0A18, 0xFF2A1E4A, 0xFF120C22, 0xFFB794FF, false);
            case "dragon" -> membrane(0xFF2A1A12, 0xFF6A8A3A, 0xFF2E4A22, 0, true);
            case "demon" -> membrane(0xFF120808, 0xFFB01E30, 0xFF4A0A10, 0xFFFF6A3A, false);
            case "monarch", "morpho", "luna" -> butterfly(id);
            case "crystal" -> crystal();
            case "circuit" -> circuit();
            case "verdant" -> leaves();
            case "sakura" -> sakura();
            case "fairy" -> fairy();
            default -> skeletal();
        };
    }
}
