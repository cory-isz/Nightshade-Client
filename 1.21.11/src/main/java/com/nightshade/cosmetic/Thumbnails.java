package com.nightshade.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Angled 3D thumbnails, drawn once into a small texture per item and reused, so the Wardrobe grid costs one
 * textured quad per tile instead of thousands of rectangles.
 */
public final class Thumbnails {
    public static final int W = 128;
    public static final int H = 96;
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private Thumbnails() {
    }

    /** The texture for an item, or null when it has no voxel model (classic capes, auras). */
    public static Identifier get(Catalog.Item item) {
        if (CACHE.containsKey(item.id)) {
            return CACHE.get(item.id);
        }
        List<int[]> vox = Models.voxels(item);
        Identifier id = null;
        if (vox != null && !vox.isEmpty()) {
            NativeImage img = render(vox, item.category == Catalog.Category.HATS);
            id = Identifier.fromNamespaceAndPath("nightshade", "thumb/" + item.id);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "nightshade thumbnail " + item.id, img));
        }
        CACHE.put(item.id, id);
        return id;
    }

    private static NativeImage render(List<int[]> vox, boolean fromAbove) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] v : vox) {
            int px = v[0] * 2 + v[2];
            int py = v[1] * 2 + (fromAbove ? v[2] : -v[2]);
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px + 2);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py + 2);
        }
        float scale = Math.min((W - 4) / (float) Math.max(1, maxX - minX), (H - 4) / (float) Math.max(1, maxY - minY));
        float ox = (W - (maxX - minX) * scale) / 2f - minX * scale;
        float oy = (H - (maxY - minY) * scale) / 2f - minY * scale;
        vox.sort((a, b) -> fromAbove ? (a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(b[1], a[1])) : Integer.compare(b[2], a[2]));
        NativeImage img = new NativeImage(W, H, true);
        img.fillRect(0, 0, W, H, 0);
        int s = Math.max(1, Math.round(2 * scale));
        int edge = Math.max(1, s / 3);
        for (int[] v : vox) {
            int ix = Math.round(ox + (v[0] * 2 + v[2]) * scale);
            int iy = Math.round(oy + (v[1] * 2 + (fromAbove ? v[2] : -v[2])) * scale);
            int c = v[3] | 0xFF000000;
            rect(img, ix, iy, s, s, c);
            rect(img, ix, iy, s, edge, Shapes.mix(c, 0xFFFFFFFF, 0.25));
            rect(img, ix + s - edge, iy, edge, s, Shapes.mix(c, 0xFF000000, 0.3));
        }
        return img;
    }

    private static void rect(NativeImage img, int x, int y, int w, int h, int argb) {
        for (int yy = Math.max(0, y); yy < Math.min(H, y + h); yy++) {
            for (int xx = Math.max(0, x); xx < Math.min(W, x + w); xx++) {
                img.setPixel(xx, yy, argb);
            }
        }
    }
}
