package com.nightshade.cosmetic;

import java.util.ArrayList;
import java.util.List;

import static com.nightshade.cosmetic.Shapes.mix;
import static com.nightshade.cosmetic.Shapes.scale;

/**
 * Every hat, built from 3D shapes at half-pixel detail. Head space in half pixels: the head spans x -8..7,
 * y -16..-1, z -8..7 (front is -z). Parts can glow or move (spin, orbit, bob).
 */
public final class Hats {
    public static final float UNIT = 0.5f / 16f;

    public static final class Part {
        public final VoxelModel model;
        public final boolean glow;
        /** 0 still, 1 spin about the y axis through (px, pz), 2 orbit the head, 3 bob up and down. */
        public final int anim;
        public final float px;
        public final float pz;

        Part(VoxelModel model, boolean glow, int anim, float px, float pz) {
            this.model = model;
            this.glow = glow;
            this.anim = anim;
            this.px = px;
            this.pz = pz;
        }
    }

    public static final class Hat {
        public final List<Part> parts = new ArrayList<>();

        Hat add(VoxelModel.Builder b) {
            parts.add(new Part(b.build(), false, 0, 0, 0));
            return this;
        }

        Hat glow(VoxelModel.Builder b) {
            parts.add(new Part(b.build(), true, 0, 0, 0));
            return this;
        }

        Hat anim(VoxelModel.Builder b, boolean glow, int anim, float px, float pz) {
            parts.add(new Part(b.build(), glow, anim, px, pz));
            return this;
        }
    }

    private Hats() {
    }

    private static VoxelModel.Builder nb() {
        return VoxelModel.builder();
    }

    private static Shapes.Paint noise(int a, int b, int seed) {
        return (x, y, z) -> (((int) Math.floor(x) * 73856093 ^ (int) Math.floor(y) * 19349663 ^ (int) Math.floor(z) * 83492791 ^ seed) & 7) < 2 ? b : a;
    }

    /** Upper half of an ellipsoid (a dome) sitting on y = cy. */
    private static void dome(VoxelModel.Builder b, double cx, double cy, double cz, double rx, double ry, double rz, Shapes.Paint p) {
        for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
            for (int y = (int) Math.floor(cy - ry); y < cy; y++) {
                for (int z = (int) Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry, dz = (z + 0.5 - cz) / rz;
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
                        b.set(x, y, z, p.at(x + 0.5, y + 0.5, z + 0.5));
                    }
                }
            }
        }
    }

    public static Hat build(String id) {
        Hat h = new Hat();
        switch (id) {
            case "halo" -> {
                VoxelModel.Builder ring = nb();
                Shapes.torus(ring, 0, -24, 0, 6.2, 1.0, (x, y, z) -> mix(0xFFFFF2B0, 0xFFFFC857, (Math.atan2(z, x) + Math.PI) / (2 * Math.PI)));
                h.anim(ring, true, 3, 0, 0);
            }
            case "crown" -> {
                VoxelModel.Builder c = nb();
                Shapes.Paint gold = Shapes.shaded(0xFFE8C468, -24, -15);
                Shapes.cylinder(c, 0, 0, -18, -15, 9.4, 1.4, gold);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Shapes.cone(c, Math.cos(a) * 8.6, Math.sin(a) * 8.6, -19, i % 2 == 0 ? 6 : 4, 1.6, 0.4, 0, 0, gold);
                }
                VoxelModel.Builder cap = nb();
                dome(cap, 0, -17, 0, 7.4, 4.5, 7.4, Shapes.shaded(0xFFB01E30, -22, -17));
                h.add(cap).add(c);
                VoxelModel.Builder gems = nb();
                Shapes.sphere(gems, 0, -16.5, -9.6, 1.2, Shapes.solid(0xFFE8304A));
                Shapes.sphere(gems, -6.8, -16.5, -6.8, 1.0, Shapes.solid(0xFF3DA5FF));
                Shapes.sphere(gems, 6.8, -16.5, -6.8, 1.0, Shapes.solid(0xFF3DA5FF));
                Shapes.sphere(gems, 9.6, -16.5, 0, 1.0, Shapes.solid(0xFF2EE59D));
                Shapes.sphere(gems, -9.6, -16.5, 0, 1.0, Shapes.solid(0xFF2EE59D));
                Shapes.sphere(gems, 0, -25.5, 0, 1.3, Shapes.solid(0xFFFFFFFF));
                h.glow(gems);
            }
            case "wizard" -> {
                VoxelModel.Builder b = nb();
                Shapes.cylinder(b, 0, 0, -17, -16, 12.5, 0, Shapes.shaded(0xFF2A2A6E, -17, -16));
                Shapes.cone(b, 0, 0, -18, 20, 8.2, 0.6, 5, 4, Shapes.shaded(0xFF34348A, -38, -18));
                VoxelModel.Builder band = nb();
                Shapes.cylinder(band, 0, 0, -20, -18, 8.6, 1, Shapes.solid(0xFFE8C468));
                VoxelModel.Builder stars = nb();
                double[][] st = {{-5, -24, -5}, {4, -28, -4}, {-2, -32, -2}, {6, -22, 3}, {2, -35, 1}};
                for (double[] s : st) {
                    Shapes.sphere(stars, s[0], s[1], s[2], 0.9, Shapes.solid(0xFFFFE08A));
                }
                Shapes.sphere(stars, 5, -38, 4, 1.3, Shapes.solid(0xFFFFFFFF));
                h.add(b).add(band).glow(stars);
            }
            case "top_hat" -> {
                VoxelModel.Builder b = nb();
                Shapes.cylinder(b, 0, 0, -17, -16, 12, 0, Shapes.shaded(0xFF1E1E24, -17, -16));
                Shapes.cylinder(b, 0, 0, -30, -18, 7.8, 0, Shapes.shaded(0xFF26262E, -30, -18));
                VoxelModel.Builder band = nb();
                Shapes.cylinder(band, 0, 0, -21, -18, 8.2, 1, Shapes.shaded(0xFF8A2A3A, -21, -18));
                Shapes.box(band, -2, -21, -9, 1, -18, -9, Shapes.solid(0xFFE8C468));
                h.add(b).add(band);
            }
            case "witch" -> {
                VoxelModel.Builder b = nb();
                for (int i = 0; i <= 2; i++) {
                    Shapes.ring(b, 0, -16 - i, 0, 13.5 - i * 1.5, 0, Shapes.solid(scale(0xFF2A1E3A, 1f - i * 0.05f)));
                }
                Shapes.cone(b, 0, 0, -18, 18, 7.8, 0.5, -7, 5, Shapes.shaded(0xFF3A2650, -36, -18));
                VoxelModel.Builder band = nb();
                Shapes.cylinder(band, 0, 0, -20, -18, 8.2, 1, Shapes.solid(0xFF7C3AAE));
                Shapes.box(band, -2, -21, -9, 1, -18, -9, Shapes.solid(0xFFE8C468));
                h.add(b).add(band);
            }
            case "cat_ears" -> h.add(ears(0xFF2A2340, 0xFF4A3D6A, 0xFFFF9FCF, 3.6, 7, 0));
            case "fox_ears" -> {
                VoxelModel.Builder b = ears(0xFFE8742A, 0xFFFF9F43, 0xFFFFF4EA, 3.8, 8, 0);
                h.add(b);
            }
            case "bunny_ears" -> {
                VoxelModel.Builder b = nb();
                Shapes.Paint fur = Shapes.shaded(0xFFF4F0F6, -34, -16);
                Shapes.curve(b, new double[][]{{-4, -16, 0}, {-4.5, -24, 0.5}, {-4, -33, 1}}, 2.4, 1.8, fur);
                Shapes.curve(b, new double[][]{{4, -16, 0}, {5, -24, 0.5}, {8, -28, 1}, {12, -24, 1.5}}, 2.4, 1.8, fur);
                VoxelModel.Builder pink = nb();
                Shapes.curve(pink, new double[][]{{-4, -18, -1.8}, {-4.5, -24, -1.4}, {-4, -31, -1}}, 1.1, 0.8, Shapes.solid(0xFFFFB8D2));
                h.add(b).add(pink);
            }
            case "devil_horns" -> {
                VoxelModel.Builder b = nb();
                for (int s = -1; s <= 1; s += 2) {
                    Shapes.curve(b, new double[][]{{s * 4.5, -15, -3}, {s * 6, -20, -3.5}, {s * 7.5, -24, -2}, {s * 6.5, -27, -4.5}}, 2.0, 0.4,
                            (x, y, z) -> mix(0xFF8A0E1A, 0xFFFF4D4D, (-y - 15) / 12.0));
                }
                h.add(b);
            }
            case "ram_horns" -> {
                VoxelModel.Builder b = nb();
                for (int s = -1; s <= 1; s += 2) {
                    double[][] pts = new double[9][];
                    for (int i = 0; i < 9; i++) {
                        double t = i / 8.0;
                        double ang = Math.PI * 0.6 + t * Math.PI * 1.7;
                        double r = 6.5 - t * 3.2;
                        pts[i] = new double[]{s * (8 + Math.cos(ang) * r * 0.45), -12 + Math.sin(ang) * r, -1 + Math.cos(ang) * r};
                    }
                    final int side = s;
                    Shapes.curve(b, pts, 2.3, 0.8, (x, y, z) -> ((int) Math.floor(y + z) & 1) == 0 ? 0xFFE6DCC4 : 0xFFC9BC9C);
                }
                h.add(b);
            }
            case "antlers" -> {
                VoxelModel.Builder b = nb();
                Shapes.Paint wood = Shapes.shaded(0xFF8A6A4A, -36, -16);
                for (int s = -1; s <= 1; s += 2) {
                    Shapes.curve(b, new double[][]{{s * 4.5, -15, 0}, {s * 7, -22, 1}, {s * 11, -29, 2.5}, {s * 13, -35, 4}}, 1.4, 0.6, wood);
                    Shapes.capsule(b, s * 7, -22, 1, s * 6, -28, -1, 0.9, 0.5, wood);
                    Shapes.capsule(b, s * 10.5, -28, 2.3, s * 9.5, -34, 0, 0.8, 0.4, wood);
                    Shapes.capsule(b, s * 9, -25, 1.8, s * 13, -26, -0.5, 0.8, 0.4, wood);
                }
                h.add(b);
            }
            case "flower_crown" -> {
                VoxelModel.Builder vine = nb();
                Shapes.torus(vine, 0, -16, 0, 8.8, 0.8, Shapes.solid(0xFF4E9E5A));
                VoxelModel.Builder flowers = nb();
                int[] petals = {0xFFFF8FC8, 0xFFFFFFFF, 0xFFB794FF, 0xFF8EC5FF, 0xFFFFC08A};
                for (int i = 0; i < 9; i++) {
                    double a = i * Math.PI * 2 / 9 + 0.2;
                    double cx = Math.cos(a) * 8.8, cz = Math.sin(a) * 8.8;
                    for (int k = 0; k < 5; k++) {
                        double pa = k * Math.PI * 2 / 5;
                        Shapes.sphere(flowers, cx + Math.cos(pa) * 1.3, -17 + Math.sin(pa) * 1.3, cz, 1.0, Shapes.solid(petals[i % petals.length]));
                    }
                    Shapes.sphere(flowers, cx, -17, cz - 0.4, 0.8, Shapes.solid(0xFFFFE08A));
                    Shapes.ellipsoid(vine, Math.cos(a + 0.35) * 9.2, -15.5, Math.sin(a + 0.35) * 9.2, 1.2, 0.6, 1.2, Shapes.solid(0xFF6CC27A));
                }
                h.add(vine).add(flowers);
            }
            case "viking" -> {
                VoxelModel.Builder b = nb();
                dome(b, 0, -14, 0, 9.4, 8.5, 9.4, (x, y, z) -> mix(0xFFCFD5DE, 0xFF7A8290, (y + 22) / 10.0 + (x * 0.02)));
                VoxelModel.Builder band = nb();
                Shapes.cylinder(band, 0, 0, -15, -13, 9.6, 1.2, Shapes.shaded(0xFF7A5A3A, -15, -13));
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    band.set((int) Math.round(Math.cos(a) * 9.6), -14, (int) Math.round(Math.sin(a) * 9.6), 0xFFE8D8A8);
                }
                Shapes.box(band, -1, -14, -10, 0, -7, -10, Shapes.solid(0xFF9AA2B0));
                VoxelModel.Builder horns = nb();
                for (int s = -1; s <= 1; s += 2) {
                    Shapes.curve(horns, new double[][]{{s * 8.5, -17, 0}, {s * 12, -20, 0}, {s * 14, -25, -1}, {s * 13, -29, -2}}, 1.8, 0.5,
                            (x, y, z) -> mix(0xFFF2EAD6, 0xFFB8AC8E, (-y - 17) / 12.0));
                }
                h.add(b).add(band).add(horns);
            }
            case "beanie" -> {
                VoxelModel.Builder b = nb();
                dome(b, 0, -14, 0, 9.2, 8, 9.2, (x, y, z) -> ((int) Math.floor(-y) / 2) % 2 == 0 ? 0xFF3A6AE8 : 0xFF2A4AB8);
                Shapes.cylinder(b, 0, 0, -16, -12, 9.6, 1.6, (x, y, z) -> ((int) Math.floor(Math.atan2(z, x) * 8)) % 2 == 0 ? 0xFFECE8F7 : 0xFFD6D0E6);
                VoxelModel.Builder pom = nb();
                Shapes.sphere(pom, 0, -24, 0, 3.4, noise(0xFFFFFFFF, 0xFFDDE4F6, 3));
                h.add(b).add(pom);
            }
            case "headphones" -> {
                VoxelModel.Builder b = nb();
                Shapes.curve(b, new double[][]{{-9.5, -8, 0}, {-8, -15, 0}, {0, -18.5, 0}, {8, -15, 0}, {9.5, -8, 0}}, 1.1, 1.1, Shapes.solid(0xFF2A2A34));
                for (int s = -1; s <= 1; s += 2) {
                    Shapes.ellipsoid(b, s * 9.6, -7, 0, 1.8, 3.8, 3.4, Shapes.shaded(0xFF1E1E26, -11, -3));
                }
                VoxelModel.Builder lights = nb();
                for (int s = -1; s <= 1; s += 2) {
                    Shapes.ellipsoid(lights, s * 11.2, -7, 0, 0.6, 2.4, 2.2, Shapes.solid(0xFF7C6CFF));
                }
                h.add(b).glow(lights);
            }
            case "party_hat" -> {
                VoxelModel.Builder b = nb();
                Shapes.cone(b, 0, 0, -16, 15, 6.5, 0.6, 2, 0, (x, y, z) -> {
                    int band = (int) Math.floor((-y + Math.atan2(z, x) * 2) / 2.5);
                    return new int[]{0xFFFF4D6D, 0xFFFFC857, 0xFF3DA5FF, 0xFF2EE59D}[Math.floorMod(band, 4)];
                });
                VoxelModel.Builder pom = nb();
                Shapes.sphere(pom, 2, -32, 0, 2.0, noise(0xFFFFFFFF, 0xFFFFE08A, 1));
                h.add(b).glow(pom);
            }
            case "propeller" -> {
                VoxelModel.Builder cap = nb();
                dome(cap, 0, -14, 0, 9, 6.5, 9, (x, y, z) -> new int[]{0xFFFF4D6D, 0xFFFFC857, 0xFF3DA5FF, 0xFF2EE59D}[Math.floorMod((int) Math.floor((Math.atan2(z, x) + Math.PI) / (Math.PI / 2)), 4)]);
                Shapes.cylinder(cap, 0, 0, -23, -20, 0.8, 0, Shapes.solid(0xFFBFC6D2));
                VoxelModel.Builder blade = nb();
                Shapes.ellipsoid(blade, 0, -23.5, 0, 9, 0.6, 1.6, Shapes.solid(0xFFECE8F7));
                Shapes.sphere(blade, 0, -24, 0, 1.0, Shapes.solid(0xFFE8304A));
                h.add(cap).anim(blade, false, 1, 0, 0);
            }
            case "planet" -> {
                VoxelModel.Builder b = nb();
                Shapes.sphere(b, 0, 0, 0, 3.4, (x, y, z) -> ((int) Math.floor(y * 1.2) & 1) == 0 ? 0xFFB794FF : 0xFF7C6CFF);
                for (int a = 0; a < 360; a += 4) {
                    double r = Math.toRadians(a);
                    int x = (int) Math.round(Math.cos(r) * 6), z = (int) Math.round(Math.sin(r) * 6);
                    b.set(x, (int) Math.round(x * 0.35), z, 0xFFFFE08A);
                }
                h.anim(b, true, 2, 0, 0);
            }
            case "mushroom" -> {
                VoxelModel.Builder b = nb();
                dome(b, 0, -15, 0, 11.5, 7, 11.5, (x, y, z) -> {
                    int hsh = ((int) Math.floor(x / 3.0) * 7 + (int) Math.floor(z / 3.0) * 13 + (int) Math.floor(y / 3.0) * 5) & 7;
                    return hsh == 0 ? 0xFFFFFFFF : Shapes.scale(0xFFE8304A, (float) (1.1 - (y + 22) * 0.02));
                });
                Shapes.ring(b, 0, -15, 0, 11.5, 0, Shapes.solid(0xFFF2E6CC));
                h.add(b);
            }
            case "cowboy" -> {
                VoxelModel.Builder b = nb();
                for (int x = -13; x <= 13; x++) {
                    for (int z = -11; z <= 11; z++) {
                        double e = (x / 13.0) * (x / 13.0) + (z / 11.0) * (z / 11.0);
                        if (e <= 1.0) {
                            // the brim curls up at the sides
                            int lift = (int) Math.round(Math.pow(Math.abs(x) / 13.0, 3) * 3);
                            b.set(x, -17 - lift, z, Shapes.scale(0xFF8A5A2E, (float) (1.05 - e * 0.15)));
                        }
                    }
                }
                Shapes.cylinder(b, 0, 0, -26, -18, 7.6, 0, Shapes.shaded(0xFF9A6634, -26, -18));
                Shapes.cylinder(b, 0, 0, -27, -26, 5.6, 0, Shapes.solid(0xFF7A4E26));
                VoxelModel.Builder band = nb();
                Shapes.cylinder(band, 0, 0, -20, -18, 7.9, 1, Shapes.solid(0xFF3A2414));
                Shapes.box(band, -1, -20, -9, 1, -18, -9, Shapes.solid(0xFFE8C468));
                h.add(b).add(band);
            }
            case "chef" -> {
                VoxelModel.Builder b = nb();
                Shapes.cylinder(b, 0, 0, -22, -16, 8.4, 0, Shapes.shaded(0xFFF4F4F8, -22, -16));
                Shapes.Paint puff = noise(0xFFFFFFFF, 0xFFE6E8EE, 4);
                Shapes.sphere(b, 0, -28, 0, 7.0, puff);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + Math.PI / 4;
                    Shapes.sphere(b, Math.cos(a) * 4.6, -26.5, Math.sin(a) * 4.6, 4.6, puff);
                }
                h.add(b);
            }
            case "grad_cap" -> {
                VoxelModel.Builder b = nb();
                Shapes.cylinder(b, 0, 0, -20, -16, 8.2, 0, Shapes.shaded(0xFF1E1E26, -20, -16));
                Shapes.box(b, -11, -22, -11, 10, -21, 10, Shapes.solid(0xFF16161C));
                VoxelModel.Builder tassel = nb();
                Shapes.sphere(tassel, 0, -23, 0, 1.2, Shapes.solid(0xFFE8C468));
                Shapes.curve(tassel, new double[][]{{0, -23, 0}, {6, -22.5, -6}, {10.5, -21.5, -10.5}, {11, -15, -11}}, 0.6, 0.6, Shapes.solid(0xFFE8C468));
                Shapes.capsule(tassel, 11, -15, -11, 11, -11, -11, 1.1, 1.4, noise(0xFFFFD26A, 0xFFE8B84A, 3));
                h.add(b).add(tassel);
            }
            case "frog" -> {
                VoxelModel.Builder b = nb();
                dome(b, 0, -15, 0, 9.2, 6.2, 9.2, Shapes.shaded(0xFF5CC25A, -21, -15));
                Shapes.ring(b, 0, -15, 0, 9.2, 0, Shapes.solid(0xFF3E9A42));
                for (int s = -1; s <= 1; s += 2) {
                    Shapes.sphere(b, s * 4.6, -21.5, -3.5, 3.2, Shapes.solid(0xFF5CC25A));
                    Shapes.sphere(b, s * 4.6, -22, -5.6, 2.3, Shapes.solid(0xFFFFFFFF));
                    Shapes.sphere(b, s * 4.6, -22, -7.4, 1.2, Shapes.solid(0xFF14141A));
                    Shapes.sphere(b, s * 6.8, -16.5, -7.6, 1.1, Shapes.solid(0xFFFF8FA8));
                }
                h.add(b);
            }
            case "pirate" -> {
                VoxelModel.Builder b = nb();
                Shapes.Paint felt = Shapes.shaded(0xFF2A2430, -24, -15);
                dome(b, 0, -15, 0, 8.4, 6, 8.4, felt);
                for (int a = 0; a < 360; a += 6) {
                    double r = Math.toRadians(a);
                    double rad = 11 + 2.2 * Math.cos(3 * r);
                    for (double k = 8; k <= rad; k += 0.8) {
                        int x = (int) Math.round(Math.cos(r) * k), z = (int) Math.round(Math.sin(r) * k);
                        int lift = (int) Math.round((k - 8) * 0.55 * (1 + Math.cos(3 * r)) / 2);
                        b.set(x, -16 - lift, z, k > rad - 1 ? 0xFFE8C468 : 0xFF241F2A);
                    }
                }
                h.add(b);
                VoxelModel.Builder skull = nb();
                Shapes.box(skull, -2, -20, -9, 1, -18, -9, Shapes.solid(0xFFF2EEE6));
                skull.set(-1, -19, -10, 0xFF111111).set(0, -19, -10, 0xFF111111);
                Shapes.box(skull, -1, -17, -9, 0, -17, -9, Shapes.solid(0xFFF2EEE6));
                h.add(skull);
            }
            case "bow" -> {
                VoxelModel.Builder b = nb();
                Shapes.Paint pink = Shapes.shaded(0xFFFF6FB0, -24, -12);
                Shapes.ellipsoid(b, 4, -18, -2, 3.2, 2.6, 1.4, pink);
                Shapes.ellipsoid(b, 11, -18, -2, 3.2, 2.6, 1.4, pink);
                Shapes.sphere(b, 7.5, -18, -2, 1.5, Shapes.solid(0xFFE8448F));
                Shapes.capsule(b, 7, -17, -2, 5, -12, -1, 0.9, 0.6, pink);
                Shapes.capsule(b, 8, -17, -2, 10, -12, -1, 0.9, 0.6, pink);
                h.add(b);
            }
            case "headband" -> {
                VoxelModel.Builder b = nb();
                Shapes.Paint red = Shapes.solid(0xFFD92E3A);
                Shapes.cylinder(b, 0, 0, -12, -10, 8.8, 1.2, red);
                Shapes.box(b, -2, -12, -10, 1, -10, -10, Shapes.solid(0xFFBFC6D2));
                Shapes.curve(b, new double[][]{{2, -11, 9}, {4, -9, 12}, {5, -6, 14}, {6, -4, 15}}, 0.9, 0.6, red);
                Shapes.curve(b, new double[][]{{-1, -11, 9}, {-2, -8, 12}, {-2, -5, 14}, {-3, -3, 15}}, 0.9, 0.6, red);
                h.add(b);
            }
            case "star_crown" -> {
                VoxelModel.Builder ring = nb();
                Shapes.torus(ring, 0, -18, 0, 8.8, 0.8, (x, y, z) -> mix(0xFFFFF2B0, 0xFFFFC857, (Math.atan2(z, x) + Math.PI) / (2 * Math.PI)));
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Shapes.cone(ring, Math.cos(a) * 8.8, Math.sin(a) * 8.8, -19, 3, 1.0, 0.2, 0, 0, Shapes.solid(0xFFFFE08A));
                }
                h.glow(ring);
                VoxelModel.Builder stars = nb();
                for (int i = 0; i < 5; i++) {
                    double a = i * 2 * Math.PI / 5;
                    star(stars, Math.cos(a) * 4, (i % 2) * 2, Math.sin(a) * 4, i % 2 == 0 ? 0xFFFFFFFF : 0xFFB8D8FF);
                }
                h.anim(stars, true, 2, 0, 0);
                VoxelModel.Builder top = nb();
                star(top, 0, -31, 0, 0xFFFFE08A);
                Shapes.sphere(top, 0, -31, 0, 1.6, Shapes.solid(0xFFFFFFFF));
                h.anim(top, true, 3, 0, 0);
            }
            default -> { // santa
                VoxelModel.Builder b = nb();
                Shapes.cone(b, 0, 0, -17, 14, 8.2, 1.2, 9, 3, Shapes.shaded(0xFFD8283A, -31, -17));
                VoxelModel.Builder fur = nb();
                Shapes.torus(fur, 0, -16.5, 0, 8.6, 1.8, noise(0xFFFFFFFF, 0xFFE6EAF2, 5));
                Shapes.sphere(fur, 9, -31, 3, 2.4, noise(0xFFFFFFFF, 0xFFE6EAF2, 6));
                h.add(b).add(fur);
            }
        }
        return h;
    }

    /** A small four-pointed 3D star. */
    private static void star(VoxelModel.Builder b, double cx, double cy, double cz, int c) {
        Shapes.sphere(b, cx, cy, cz, 1.1, Shapes.solid(c));
        Shapes.capsule(b, cx - 2.6, cy, cz, cx + 2.6, cy, cz, 0.5, 0.5, Shapes.solid(c));
        Shapes.capsule(b, cx, cy - 2.6, cz, cx, cy + 2.6, cz, 0.5, 0.5, Shapes.solid(c));
    }

    /** Pointed ears with a lighter inside and a pale tip. */
    private static VoxelModel.Builder ears(int outer, int light, int inner, double base, int height, int lean) {
        VoxelModel.Builder b = nb();
        for (int s = -1; s <= 1; s += 2) {
            double cx = s * 5.2;
            for (int i = 0; i <= height; i++) {
                double t = i / (double) height;
                double r = base * (1 - t) + 0.4;
                for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
                    for (int z = -2; z <= 1; z++) {
                        if (Math.abs(x + 0.5 - cx - s * t * 1.2) <= r) {
                            int c = t > 0.8 ? light : outer;
                            if (z == -2 && Math.abs(x + 0.5 - cx - s * t * 1.2) < r - 1 && t < 0.75) {
                                c = inner;
                            }
                            b.set(x, -16 - i, z, c);
                        }
                    }
                }
            }
        }
        return b;
    }
}
