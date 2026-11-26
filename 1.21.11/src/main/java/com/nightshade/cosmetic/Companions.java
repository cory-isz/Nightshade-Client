package com.nightshade.cosmetic;

import java.util.ArrayList;
import java.util.List;

import static com.nightshade.cosmetic.Shapes.mix;

/**
 * Companions: small 3D buddies that float beside your shoulder. Built from shapes at half-pixel detail, centred on
 * the companion (x right, y down, z forward is -z). Parts can glow, spin, flap or bob.
 */
public final class Companions {
    public static final float UNIT = 0.5f / 16f;

    /** Animation for a part. */
    public static final int STILL = 0, SPIN = 1, BOB = 3, FLAP = 4;

    public static final class Part {
        public final VoxelModel model;
        public final boolean glow;
        public final int anim;
        /** Pivot for SPIN (x, z) and FLAP (x, y); FLAP direction follows the sign of px. */
        public final float px;
        public final float py;
        public final float pz;

        Part(VoxelModel model, boolean glow, int anim, float px, float py, float pz) {
            this.model = model;
            this.glow = glow;
            this.anim = anim;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }
    }

    public static final class Companion {
        public final List<Part> parts = new ArrayList<>();
        /** How fast wings flap (1 = normal). */
        public float flapSpeed = 1f;

        Companion add(VoxelModel.Builder b) {
            parts.add(new Part(b.build(), false, STILL, 0, 0, 0));
            return this;
        }

        Companion glow(VoxelModel.Builder b) {
            parts.add(new Part(b.build(), true, STILL, 0, 0, 0));
            return this;
        }

        Companion anim(VoxelModel.Builder b, boolean glow, int anim, float px, float py, float pz) {
            parts.add(new Part(b.build(), glow, anim, px, py, pz));
            return this;
        }
    }

    private Companions() {
    }

    private static VoxelModel.Builder nb() {
        return VoxelModel.builder();
    }

    /** A thin wing blade in the x/z plane on one side (side -1 left, +1 right), from the root at x = rootX. */
    private static VoxelModel.Builder wing(int side, double rootX, double y, double length, double width, Shapes.Paint p) {
        VoxelModel.Builder b = nb();
        Shapes.ellipsoid(b, rootX + side * length / 2, y, 0.5, length / 2, 0.6, width / 2, p);
        return b;
    }

    public static Companion build(String id) {
        Companion c = new Companion();
        switch (id) {
            case "pet_wisp" -> {
                VoxelModel.Builder core = nb();
                Shapes.sphere(core, 0, 0, 0, 2.6, (x, y, z) -> mix(0xFFFFFFFF, 0xFF8FD8FF, Math.min(1, Math.sqrt(x * x + y * y + z * z) / 2.6)));
                c.glow(core);
                VoxelModel.Builder ring = nb();
                for (int a = 0; a < 360; a += 30) {
                    double r = Math.toRadians(a);
                    ring.set((int) Math.round(Math.cos(r) * 5), a % 60 == 0 ? -1 : 1, (int) Math.round(Math.sin(r) * 5), a % 60 == 0 ? 0xFFBFE9FF : 0xFF5FB8FF);
                }
                c.anim(ring, true, SPIN, 0, 0, 0);
            }
            case "pet_slime" -> {
                VoxelModel.Builder outer = nb();
                Shapes.box(outer, -4, -4, -4, 3, 3, 3, (x, y, z) -> ((int) Math.floor(x * 3 + y * 5 + z * 7) & 3) == 0 ? 0xFF8AE58A : 0xFF6BD36B);
                Shapes.box(outer, -2, -2, -5, -1, -1, -5, Shapes.solid(0xFF16301A));
                Shapes.box(outer, 1, -2, -5, 2, -1, -5, Shapes.solid(0xFF16301A));
                Shapes.box(outer, 0, 1, -5, 0, 1, -5, Shapes.solid(0xFF16301A));
                c.anim(outer, false, BOB, 0, 0, 0);
            }
            case "pet_ghost" -> {
                VoxelModel.Builder body = nb();
                Shapes.Paint sheet = Shapes.shaded(0xFFF4F0FF, -6, 6);
                Shapes.ellipsoid(body, 0, -1, 0, 4.2, 5, 4.2, sheet);
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    Shapes.capsule(body, Math.cos(a) * 2.8, 2.5, Math.sin(a) * 2.8, Math.cos(a) * 3.2, 6 + (i & 1), Math.sin(a) * 3.2, 1.5, 0.3, sheet);
                }
                Shapes.box(body, -2, -3, -5, -1, -1, -5, Shapes.solid(0xFF201830));
                Shapes.box(body, 1, -3, -5, 2, -1, -5, Shapes.solid(0xFF201830));
                Shapes.box(body, -1, 1, -5, 0, 1, -5, Shapes.solid(0xFF201830));
                Shapes.capsule(body, -4, 0, 0, -6, 1, -1, 1, 0.8, sheet);
                Shapes.capsule(body, 4, 0, 0, 6, 1, -1, 1, 0.8, sheet);
                c.anim(body, false, BOB, 0, 0, 0);
            }
            case "pet_bee" -> {
                VoxelModel.Builder body = nb();
                Shapes.ellipsoid(body, 0, 0, 0.5, 3.4, 3.0, 4.8, (x, y, z) -> ((int) Math.floor(z + 10) / 2 & 1) == 0 ? 0xFFFFC93C : 0xFF2A2018);
                Shapes.capsule(body, 0, 0.5, 5, 0, 1, 7.5, 0.8, 0.2, Shapes.solid(0xFF1A1410));
                Shapes.sphere(body, 0, -0.5, -4.3, 2.4, Shapes.solid(0xFF2A2018));
                Shapes.box(body, -2, -1, -6, -1, 0, -6, Shapes.solid(0xFFFFFFFF));
                Shapes.box(body, 1, -1, -6, 2, 0, -6, Shapes.solid(0xFFFFFFFF));
                Shapes.capsule(body, -1, -2.5, -4.5, -2, -5, -5, 0.4, 0.3, Shapes.solid(0xFF1A1410));
                Shapes.capsule(body, 1, -2.5, -4.5, 2, -5, -5, 0.4, 0.3, Shapes.solid(0xFF1A1410));
                c.add(body);
                Shapes.Paint glass = (x, y, z) -> 0xFFD6F1FF;
                c.anim(wing(-1, -2, -3.2, 6, 4, glass), false, FLAP, -2, -3, 0);
                c.anim(wing(1, 2, -3.2, 6, 4, glass), false, FLAP, 2, -3, 0);
                c.flapSpeed = 2.4f;
            }
            case "pet_bat" -> {
                VoxelModel.Builder body = nb();
                Shapes.sphere(body, 0, 0, 0, 3.0, Shapes.solid(0xFF2E2433));
                Shapes.cone(body, -1.6, -1, -3, 3, 1.0, 0.2, 0, 0, Shapes.solid(0xFF2E2433));
                Shapes.cone(body, 1.6, -1, -3, 3, 1.0, 0.2, 0, 0, Shapes.solid(0xFF2E2433));
                c.add(body);
                VoxelModel.Builder eyes = nb();
                eyes.set(-1, -1, -3, 0xFFFF3B4D).set(1, -1, -3, 0xFFFF3B4D);
                c.glow(eyes);
                Shapes.Paint membrane = (x, y, z) -> ((int) Math.floor(Math.abs(x)) % 3 == 0) ? 0xFF1A141E : 0xFF4A3550;
                c.anim(wing(-1, -2, -0.5, 8, 5, membrane), false, FLAP, -2, -0.5f, 0);
                c.anim(wing(1, 2, -0.5, 8, 5, membrane), false, FLAP, 2, -0.5f, 0);
                c.flapSpeed = 1.6f;
            }
            case "pet_dragon" -> {
                VoxelModel.Builder body = nb();
                Shapes.Paint scales = (x, y, z) -> (((int) Math.floor(x * 2) + (int) Math.floor(z * 2)) & 1) == 0 ? 0xFF6A3FD0 : 0xFF5532B0;
                Shapes.ellipsoid(body, 0, 1, 1, 3.2, 3.0, 4.6, scales);
                Shapes.ellipsoid(body, 0, 2.6, 0.6, 2.0, 1.2, 3.6, Shapes.solid(0xFFC9B6FF));
                Shapes.sphere(body, 0, -2.2, -4.2, 2.8, scales);
                Shapes.ellipsoid(body, 0, -1.4, -7.0, 1.5, 1.2, 1.8, scales);
                Shapes.cone(body, -1.4, -4.4, -4, 3, 0.8, 0.1, 0, 0.8, Shapes.solid(0xFFE8DDB4));
                Shapes.cone(body, 1.4, -4.4, -4, 3, 0.8, 0.1, 0, 0.8, Shapes.solid(0xFFE8DDB4));
                Shapes.curve(body, new double[][]{{0, 2, 5}, {0, 3.5, 8}, {1.5, 3, 10.5}, {2.5, 1.5, 11.5}}, 1.3, 0.4, scales);
                Shapes.capsule(body, 2.5, 1.5, 11.5, 3.6, 0.5, 13.5, 1.0, 0.2, Shapes.solid(0xFFB794FF));
                for (int i = 0; i < 4; i++) {
                    body.set(0, -2 + i, 4 + i * 2 - 7, 0xFFB794FF);
                }
                c.add(body);
                VoxelModel.Builder eyes = nb();
                eyes.set(-2, -3, -6, 0xFFFFE08A).set(1, -3, -6, 0xFFFFE08A);
                c.glow(eyes);
                Shapes.Paint wingSkin = (x, y, z) -> ((int) Math.floor(Math.abs(x) * 0.8) % 3 == 0) ? 0xFF3D2580 : 0xFF9A7CF0;
                c.anim(wing(-1, -2.5, -1.5, 9, 6, wingSkin), false, FLAP, -2.5f, -1.5f, 0);
                c.anim(wing(1, 2.5, -1.5, 9, 6, wingSkin), false, FLAP, 2.5f, -1.5f, 0);
                c.flapSpeed = 0.9f;
            }
            case "pet_book" -> {
                VoxelModel.Builder book = nb();
                for (int side = -1; side <= 1; side += 2) {
                    for (int x = 0; x <= 6; x++) {
                        int y = (int) Math.round(x * 0.35);
                        for (int z = -4; z <= 4; z++) {
                            book.set(side * x - (side < 0 ? 1 : 0), y, z, 0xFF6B2A3A);
                            if (x <= 5 && Math.abs(z) <= 3) {
                                book.set(side * x - (side < 0 ? 1 : 0), y - 1, z, x == 5 || Math.abs(z) == 3 ? 0xFFE6D8B8 : 0xFFF6EEDC);
                            }
                        }
                    }
                }
                c.anim(book, false, BOB, 0, 0, 0);
                VoxelModel.Builder runes = nb();
                for (int a = 0; a < 360; a += 45) {
                    double r = Math.toRadians(a);
                    runes.set((int) Math.round(Math.cos(r) * 7), -4 - (a / 45 % 2) * 2, (int) Math.round(Math.sin(r) * 7), a % 90 == 0 ? 0xFFB794FF : 0xFFFFE08A);
                }
                c.anim(runes, true, SPIN, 0, 0, 0);
            }
            case "pet_crystal" -> {
                VoxelModel.Builder gem = nb();
                Shapes.Paint facets = (x, y, z) -> (Math.floorMod((int) Math.floor((Math.atan2(z, x) + Math.PI) / (Math.PI / 3)), 2) == 0) ? 0xFFB794FF : 0xFF7C3AFF;
                Shapes.cone(gem, 0, 0, 0, 6, 3.2, 0.1, 0, 0, facets);
                for (int y = 0; y <= 4; y++) {
                    double r = 3.2 * (1 - y / 4.5);
                    Shapes.ring(gem, 0, y, 0, Math.max(0.6, r), r, facets);
                }
                c.anim(gem, false, SPIN, 0, 0, 0);
                VoxelModel.Builder inner = nb();
                Shapes.sphere(inner, 0, -0.5, 0, 1.2, Shapes.solid(0xFFF0E6FF));
                c.glow(inner);
                VoxelModel.Builder shards = nb();
                for (int i = 0; i < 3; i++) {
                    double a = i * Math.PI * 2 / 3;
                    int x = (int) Math.round(Math.cos(a) * 7), z = (int) Math.round(Math.sin(a) * 7);
                    shards.set(x, -1, z, 0xFFE6D8FF).set(x, 0, z, 0xFFB794FF).set(x, 1, z, 0xFF7C3AFF);
                }
                c.anim(shards, true, SPIN, 0, 0, 0);
            }
            default -> {
            }
        }
        return c;
    }
}
