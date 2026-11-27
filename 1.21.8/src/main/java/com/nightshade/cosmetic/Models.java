package com.nightshade.cosmetic;

import java.util.HashMap;
import java.util.Map;

/** Builds and caches the 3D model behind each catalogue item. */
public final class Models {
    private static final Map<String, Object> CACHE = new HashMap<>();

    private Models() {
    }

    public static WingModel wing(String id) {
        WingModel r = RemoteContent.wing(id);
        if (r != null) {
            return r;
        }
        return (WingModel) CACHE.computeIfAbsent("wing_" + id, k -> WingDesigns.build(id));
    }

    public static Designs.Cape cape(String id) {
        Designs.Cape r = RemoteContent.cape(id);
        if (r != null) {
            return r;
        }
        return (Designs.Cape) CACHE.computeIfAbsent(id, k -> Designs.cape(id.substring("cape_".length()).replace("moon", "moon")));
    }

    public static Hats.Hat hat(String id) {
        Hats.Hat r = RemoteContent.hat(id);
        if (r != null) {
            return r;
        }
        return (Hats.Hat) CACHE.computeIfAbsent("hat_" + id, k -> Hats.build(id));
    }

    public static Companions.Companion companion(String id) {
        Companions.Companion r = RemoteContent.companion(id);
        if (r != null) {
            return r;
        }
        return (Companions.Companion) CACHE.computeIfAbsent("pet_" + id, k -> Companions.build(id));
    }

    public static VoxelModel speck(int color) {
        return (VoxelModel) CACHE.computeIfAbsent("speck" + color, k -> Designs.speck(color));
    }

    /**
     * Every voxel of an item as {x, y, z, colour} for the angled thumbnails, or null for items drawn another way
     * (classic capes, auras). Wings come back as a mirrored pair; their depth is in layer steps.
     */
    public static java.util.List<int[]> voxels(Catalog.Item item) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        switch (item.category) {
            case WINGS -> {
                WingModel w = wing(item.id);
                int[] c = w.cells;
                for (int i = 0; i < c.length; i += 4) {
                    out.add(new int[]{c[i], c[i + 1], c[i + 2], c[i + 3]});
                    out.add(new int[]{-c[i] - 4, c[i + 1], c[i + 2], c[i + 3]});
                }
            }
            case CAPES -> {
                if (item.classicCape != null) {
                    return null;
                }
                add(out, cape(item.id).model);
                add(out, cape(item.id).glow);
            }
            case HATS -> {
                for (Hats.Part p : hat(item.id).parts) {
                    add(out, p.model);
                }
            }
            case COMPANIONS -> {
                for (Companions.Part p : companion(item.id).parts) {
                    add(out, p.model);
                }
            }
            default -> {
                return null;
            }
        }
        return out;
    }

    private static void add(java.util.List<int[]> out, VoxelModel m) {
        if (m == null) {
            return;
        }
        for (int i = 0; i < m.cells.length; i += 4) {
            out.add(new int[]{m.cells[i], m.cells[i + 1], m.cells[i + 2], m.cells[i + 3]});
        }
    }
}
