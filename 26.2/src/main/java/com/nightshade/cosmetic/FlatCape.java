package com.nightshade.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * The shop capes as flat cloth with a detailed painted picture: a thin 10 x 17 x 1 slab whose outside shows the
 * artwork, with a fabric lining on the inside and the edges. Capes with glowing parts get a second, full-bright
 * layer laid just over the outside.
 *
 * Texture layout (320 x 416): the outside picture is 240 x 408 at the top left, the lining is the 80 px strip on
 * the right.
 */
public final class FlatCape {
    public static final int TEX_W = 320;
    public static final int TEX_H = 416;
    public static final int FRONT_W = 240;
    public static final int FRONT_H = 408;
    private static final Set<String> STYLES = Set.of("moon", "stars", "dusk", "tattered", "royal", "checker", "aurora", "ember", "galaxy", "sakura", "storm");
    private static final Set<String> GLOWING = Set.of("moon", "stars", "aurora", "ember", "galaxy", "storm");

    private FlatCape() {
    }

    /** True when this cape style ("moon" for cape_moon) has a painted picture. */
    public static boolean has(String style) {
        return STYLES.contains(style);
    }

    public static boolean hasGlow(String style) {
        return GLOWING.contains(style);
    }

    public static Identifier texture(String style) {
        return Identifier.fromNamespaceAndPath("nightshade", "textures/cape2d/" + style + ".png");
    }

    public static Identifier glow(String style) {
        return Identifier.fromNamespaceAndPath("nightshade", "textures/cape2d/" + style + "_glow.png");
    }

    /**
     * Emits the cape in model units (x -5..5, y 0..17 downwards, z 0..1 with the outside at z = 1), the same space
     * the old voxel capes used, so it hangs in exactly the same place.
     */
    public static void render(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, boolean glowLayer) {
        float s = 1f / 16f;
        float x0 = -5 * s, x1 = 5 * s, y0 = 0, y1 = 17 * s, z0 = 0, z1 = s;
        float fu = FRONT_W / (float) TEX_W;
        float fv = FRONT_H / (float) TEX_H;
        if (glowLayer) {
            float z = z1 + 0.0015f;
            quad(pose, vc, light, overlay, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, 0, 0, fu, fv, 0, 0, 1);
            return;
        }
        // outside: the picture
        quad(pose, vc, light, overlay, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, fu, fv, 0, 0, 1);
        // inside (against your back): the lining
        quad(pose, vc, light, overlay, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, fu, 0, 1, fv, 0, 0, -1);
        // edges: thin strips of the lining
        float e0 = fu, e1 = fu + 6f / TEX_W;
        quad(pose, vc, light, overlay, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, e0, 0, e1, fv, -1, 0, 0);
        quad(pose, vc, light, overlay, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, e0, 0, e1, fv, 1, 0, 0);
        quad(pose, vc, light, overlay, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, fu, 0, 1, 6f / TEX_H, 0, -1, 0);
        quad(pose, vc, light, overlay, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, fu, 0, 1, 6f / TEX_H, 0, 1, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vertex(pose, vc, ax, ay, az, u0, v0, light, overlay, nx, ny, nz);
        vertex(pose, vc, bx, by, bz, u1, v0, light, overlay, nx, ny, nz);
        vertex(pose, vc, cx, cy, cz, u1, v1, light, overlay, nx, ny, nz);
        vertex(pose, vc, dx, dy, dz, u0, v1, light, overlay, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
