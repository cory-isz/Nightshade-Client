package com.nightshade.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD panels drawn once into a texture and then reused as one quad. A panel is a drop shadow, a body, a sheen, an
 * outline and an accent bar - about 30 separate pieces when drawn with fills. Minecraft checks every new GUI piece
 * against the earlier ones for overlap, so with many HUD components on, that check (not the drawing) was most of the
 * HUD's cost. As a texture, a panel is a single piece, and its corners are anti-aliased at the real screen resolution.
 */
public final class PanelCache {
    private static final int MAX_ENTRIES = 96;
    private static final int MAX_PIXELS = 1 << 20;
    private static int nextId;

    private record Key(int w, int h, int k, String style, int color, int accent) {
    }

    private record Entry(Identifier id, DynamicTexture texture, int pixelW, int pixelH) {
    }

    private static final LinkedHashMap<Key, Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true);

    private PanelCache() {
    }

    /** Draws a HUD panel; returns false when it is too big to cache (the caller then draws it the slow way). */
    public static boolean draw(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color, String style) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        int k = (int) Math.max(1, Math.min(8, Math.round(mc.getWindow().getGuiScale() * Math.abs(ctx.pose().m00()))));
        int shadow = "Classic".equals(style) ? 0 : 2;
        int pw = w * k;
        int ph = (h + shadow) * k;
        if ((long) pw * ph > MAX_PIXELS) {
            return false;
        }
        Key key = new Key(w, h, k, style, color, "Accent".equals(style) ? Theme.accent() : 0);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            entry = create(key, pw, ph, shadow);
            CACHE.put(key, entry);
            evict(mc);
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, entry.id(), x1, y1, 0f, 0f, w, h + shadow, entry.pixelW(), entry.pixelH(), entry.pixelW(), entry.pixelH());
        return true;
    }

    private record ShapeKey(int w, int h, int r, int k, boolean outline) {
    }

    private static final LinkedHashMap<ShapeKey, Entry> SHAPES = new LinkedHashMap<>(64, 0.75f, true);

    /**
     * A white anti-aliased rounded rectangle (or 1px outline) drawn as one quad tinted with {@code color}: the cheap
     * way for HUD parts that repeat (module list lines, keys). Falls back to {@link RenderUtil} when too large.
     */
    public static void shape(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int color, boolean outline) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0 || ColorUtil.alpha(color) == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int k = (int) Math.max(1, Math.min(8, Math.round(mc.getWindow().getGuiScale() * Math.abs(ctx.pose().m00()))));
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0 && !outline) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        if ((long) w * h * k * k > MAX_PIXELS) {
            if (outline) {
                RenderUtil.roundedOutline(ctx, x1, y1, x2, y2, r, color);
            } else {
                RenderUtil.roundedRect(ctx, x1, y1, x2, y2, r, color);
            }
            return;
        }
        ShapeKey key = new ShapeKey(w, h, r, k, outline);
        Entry entry = SHAPES.get(key);
        if (entry == null) {
            int pw = w * k;
            int ph = h * k;
            NativeImage image = new NativeImage(pw, ph, true);
            for (int py = 0; py < ph; py++) {
                float gy = (py + 0.5f) / k;
                for (int px = 0; px < pw; px++) {
                    float gx = (px + 0.5f) / k;
                    float c = coverage(gx, gy, w, h, r, k);
                    if (outline) {
                        c -= coverage(gx - 1f, gy - 1f, w - 2f, h - 2f, Math.max(0f, r - 1f), k);
                    }
                    image.setPixel(px, py, Math.round(Math.max(0f, c) * 255f) << 24 | 0xFFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("nightshade", "hud_shape/" + (nextId++));
            DynamicTexture texture = new DynamicTexture(() -> "nightshade hud shape", image);
            mc.getTextureManager().register(id, texture);
            entry = new Entry(id, texture, pw, ph);
            SHAPES.put(key, entry);
            Iterator<Map.Entry<ShapeKey, Entry>> it = SHAPES.entrySet().iterator();
            while (SHAPES.size() > 256 && it.hasNext()) {
                mc.getTextureManager().release(it.next().getValue().id());
                it.remove();
            }
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, entry.id(), x1, y1, 0f, 0f, w, h, entry.pixelW(), entry.pixelH(), entry.pixelW(), entry.pixelH(), color);
    }

    /** Drops every cached panel (e.g. after the window or GUI scale changes a lot). */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : CACHE.values()) {
            mc.getTextureManager().release(e.id());
        }
        CACHE.clear();
        for (Entry e : SHAPES.values()) {
            mc.getTextureManager().release(e.id());
        }
        SHAPES.clear();
    }

    private static void evict(Minecraft mc) {
        Iterator<Map.Entry<Key, Entry>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_ENTRIES && it.hasNext()) {
            Entry old = it.next().getValue();
            mc.getTextureManager().release(old.id());
            it.remove();
        }
    }

    private static Entry create(Key key, int pw, int ph, int shadow) {
        int k = key.k();
        float w = key.w();
        float h = key.h();
        boolean classic = "Classic".equals(key.style());
        float r = classic ? Math.min(4, Math.min(w, h) / 2f) : Math.min(5, Math.min(w, h) / 2f);
        int body = classic ? key.color() : ColorUtil.withAlpha(key.color(), Math.min(255, ColorUtil.alpha(key.color()) + 0x18));
        int outline = classic ? 0x18FFFFFF : 0x24FFFFFF;
        NativeImage image = new NativeImage(pw, ph, true);
        for (int py = 0; py < ph; py++) {
            float gy = (py + 0.5f) / k;
            for (int px = 0; px < pw; px++) {
                float gx = (px + 0.5f) / k;
                int out = 0;
                if (shadow > 0) {
                    out = over(out, 0x38000000, coverage(gx, gy - 1f, w, h + 1f, r, k));
                }
                float inside = coverage(gx, gy, w, h, r, k);
                if (inside > 0f) {
                    out = over(out, body, inside);
                    if (!classic && h > 6 && w > 6 && gx >= 2 && gx < w - 2 && gy >= 1 && gy < 1 + (h / 2f - 1)) {
                        float t = (gy - 1f) / Math.max(1f, h / 2f - 1f);
                        out = over(out, ColorUtil.withAlpha(0xFFFFFF, Math.round(0x12 * (1f - t))), inside);
                    }
                    float ring = inside - coverage(gx - 1f, gy - 1f, w - 2f, h - 2f, Math.max(0f, r - 1f), k);
                    if (ring > 0f) {
                        out = over(out, outline, ring);
                    }
                    if (key.accent() != 0 && h > 6) {
                        out = over(out, key.accent(), coverage(gx, gy - 3f, 2f, h - 6f, 1f, k));
                    }
                }
                image.setPixel(px, py, out);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("nightshade", "hud_panel/" + (nextId++));
        DynamicTexture texture = new DynamicTexture(() -> "nightshade hud panel", image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Entry(id, texture, pw, ph);
    }

    /** Anti-aliased coverage (0-1) of the point (x, y) by a rounded rectangle at (0, 0) of size w x h and radius r. */
    private static float coverage(float x, float y, float w, float h, float r, int k) {
        if (w <= 0f || h <= 0f) {
            return 0f;
        }
        float cx = Math.abs(x - w / 2f) - (w / 2f - r);
        float cy = Math.abs(y - h / 2f) - (h / 2f - r);
        float ox = Math.max(cx, 0f);
        float oy = Math.max(cy, 0f);
        float d = (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(cx, cy), 0f) - r;
        return Math.max(0f, Math.min(1f, 0.5f - d * k));
    }

    /** Source-over blend of an ARGB colour (scaled by coverage) onto an ARGB destination. */
    private static int over(int dst, int src, float cov) {
        float sa = ((src >>> 24) / 255f) * cov;
        if (sa <= 0f) {
            return dst;
        }
        float da = (dst >>> 24) / 255f;
        float oa = sa + da * (1f - sa);
        if (oa <= 0f) {
            return 0;
        }
        int r = Math.round((((src >> 16) & 0xFF) * sa + ((dst >> 16) & 0xFF) * da * (1f - sa)) / oa);
        int g = Math.round((((src >> 8) & 0xFF) * sa + ((dst >> 8) & 0xFF) * da * (1f - sa)) / oa);
        int b = Math.round(((src & 0xFF) * sa + (dst & 0xFF) * da * (1f - sa)) / oa);
        return Math.round(oa * 255f) << 24 | r << 16 | g << 8 | b;
    }
}
