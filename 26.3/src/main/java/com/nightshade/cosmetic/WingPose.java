package com.nightshade.cosmetic;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The wing pose (shared by the renderer and the head-clearance check) and the head clearance itself: each wing
 * works out once how far down the back it has to sit so that, in every pose, no part of it touches the head or
 * covers it from behind. Pure maths, all lengths in model pixels (1/16 block) relative to the top of the body.
 */
public final class WingPose {
    /** Wing roots sit on the shoulder blades, this far either side of the spine. */
    public static final float ROOT_X = 3.5f;
    /** Height of the wing root below the neck before any head clearance. */
    public static final float ROOT_Y = 1.0f;
    /** Head box (x and z half-size, height) plus a small gap so wings never touch it. */
    private static final double HEAD_HALF = 4.0 + 0.6, HEAD_H = 8.0 + 0.6;
    private static final Map<WingModel, Float> CLEARANCE = new IdentityHashMap<>();

    /** Spread pose: only a little swept back, so the wings open out wide to the sides, and nearly level. */
    public static float SPREAD_SWEEP = 12f;
    public static float SPREAD_DROP = -4f;

    public float sweep;
    public float drop;
    public float bend;

    /** {@code wave} and {@code bendWave} are the flap sine values in [-1, 1]. */
    public static WingPose of(WingModel w, float open, float speed, float wave, float bendWave, boolean fallFlying) {
        WingPose p = new WingPose();
        // eased, so the wings start and finish the move gently instead of snapping through it
        float e = open * open * (3f - 2f * open);
        // the flap only builds up once the wings are nearly open
        float amp = (3f + 12f * e * e + 5f * speed * e * e) * w.flap;
        p.sweep = lerp(8f, SPREAD_SWEEP, e) + wave * amp * lerp(0.12f, 1f, e);
        p.drop = lerp(70f, SPREAD_DROP, e) + (fallFlying ? -6f : 0f);
        p.bend = lerp(8f, 8f + bendWave * amp * 0.6f, e) * w.bend;
        return p;
    }

    public static float zOffset(float open) {
        return 2.4f + 0.6f * (1f - open * open * (3f - 2f * open));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Extra pixels the wing (and its lower pair) is moved down so it stays clear of the head. */
    public static synchronized float clearance(WingModel w) {
        Float c = CLEARANCE.get(w);
        if (c == null) {
            double need = Math.max(need(w, 0f, 1f, 0f), w.lower == null ? 0 : need(w.lower, 5.5f, 0.78f, 0f));
            c = need <= 0 ? 0f : (float) need + 0.3f;
            CLEARANCE.put(w, c);
        }
        return c;
    }

    /** Number of voxel corners that would touch or cover the head with the given extra drop (0 = clear). */
    public static int headHits(WingModel w, float extra) {
        int[] hits = new int[1];
        scan(w, 0f, 1f, extra, hits);
        if (w.lower != null) {
            scan(w.lower, 5.5f, 0.78f, extra, hits);
        }
        return hits[0];
    }

    private static double need(WingModel w, float dropPx, float scale, float extra) {
        return scan(w, dropPx, scale, extra, null);
    }

    /**
     * Walks every voxel corner through the poses the wing can take (folded to spread, still to running, the
     * whole flap cycle). Returns how far the highest corner above the neck within the head's width reaches.
     */
    private static double scan(WingModel w, float dropPx, float scale, float extra, int[] hits) {
        double need = 0;
        double unit = WingModel.UNIT * 16 * w.size * scale, unitZ = WingModel.UNIT_Z * 16 * scale;
        int[] c = w.cells;
        int n = w.start.length;
        for (int oi = 0; oi <= 8; oi++) {
            float open = oi / 8f;
            for (int sp = 0; sp <= 1; sp++) {
                for (int wi = -4; wi <= 4; wi++) {
                    for (int bi = -2; bi <= 2; bi++) {
                        WingPose p = of(w, open, sp, wi / 4f, bi / 2f, false);
                        double[][] seg = new double[n][];
                        seg[0] = mul(tr(ROOT_X, ROOT_Y + dropPx + extra, zOffset(open)),
                                mul(ry(-p.sweep), mul(rz(p.drop), tr(0, -w.anchorY * unit, 0))));
                        for (int s = 1; s < n; s++) {
                            seg[s] = mul(seg[s - 1], mul(tr((w.start[s] - w.start[s - 1]) * unit, 0, 0), ry(-p.bend * (s == 1 ? 0.45f : 0.6f))));
                        }
                        for (int i = 0; i < c.length; i += 4) {
                            int x = c[i], y = c[i + 1], z = c[i + 2];
                            int s = 0;
                            while (s + 1 < n && x >= w.start[s + 1]) {
                                s++;
                            }
                            double[] m = seg[s];
                            for (int k = 0; k < 8; k++) {
                                double lx = (x - w.start[s] + (k & 1)) * unit, ly = (y + ((k >> 1) & 1)) * unit, lz = (z + ((k >> 2) & 1)) * unitZ;
                                double px = m[0] * lx + m[1] * ly + m[2] * lz + m[3];
                                double py = m[4] * lx + m[5] * ly + m[6] * lz + m[7];
                                double pz = m[8] * lx + m[9] * ly + m[10] * lz + m[11];
                                if (Math.abs(px) < HEAD_HALF && py < 0.3) {
                                    // above the neck within the head's width: touches it or covers it from behind
                                    need = Math.max(need, 0.3 - py);
                                    if (hits != null && (py > -HEAD_H || Math.abs(pz) < HEAD_HALF)) {
                                        hits[0]++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return need;
    }

    private static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[i * 4 + k] * b[k * 4 + j];
                }
                r[i * 4 + j] = s;
            }
        }
        return r;
    }

    private static double[] id() {
        double[] r = new double[16];
        r[0] = r[5] = r[10] = r[15] = 1;
        return r;
    }

    private static double[] tr(double x, double y, double z) {
        double[] r = id();
        r[3] = x;
        r[7] = y;
        r[11] = z;
        return r;
    }

    private static double[] ry(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        double[] r = id();
        r[0] = c;
        r[2] = s;
        r[8] = -s;
        r[10] = c;
        return r;
    }

    private static double[] rz(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        double[] r = id();
        r[0] = c;
        r[1] = -s;
        r[4] = s;
        r[5] = c;
        return r;
    }
}
