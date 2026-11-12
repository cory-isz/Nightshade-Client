package com.nightshade.module.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TickEvent;
import com.nightshade.map.MapSampler;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.EffectProbe;
import com.nightshade.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A minimap of the loaded world in map colours, with you in the middle, other players, mobs and your waypoints. The
 * world is sampled a quarter at a time each tick (so it never costs a frame), and the picture is turned and clipped
 * on the CPU, then uploaded as one small texture.
 */
public final class MinimapHud extends HudModule {
    private static final int OUT = 128;          // texture size
    private static final int GRID = 184;         // sampled area in pixels (covers the corners when the map turns)
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("nightshade", "minimap");

    private final NumberSetting size = add(new NumberSetting("Size", "Size of the minimap", 96, 60, 180, 4, "px"));
    private final NumberSetting zoom = add(new NumberSetting("Zoom", "Blocks per pixel (higher shows more of the world)", 1, 1, 4, 1, "x"));
    private final ModeSetting shape = add(new ModeSetting("Shape", "Square or round minimap", "Square", "Square", "Circle"));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", "Turn the map with you, so up is where you look (off: north is up)", true));
    private final BooleanSetting players = add(new BooleanSetting("Show Players", "Other players as white dots", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Show Mobs", "Hostile mobs in red, other animals in green", true));
    private final BooleanSetting waypoints = add(new BooleanSetting("Show Waypoints", "Your waypoints, pinned to the edge when far away", true));
    private final BooleanSetting coords = add(new BooleanSetting("Show Coordinates", "Your coordinates under the map", true));

    private final MapSampler sampler = new MapSampler();
    private int[] front = new int[GRID * GRID];
    private int[] back = new int[GRID * GRID];
    private int[] backHeights = new int[GRID * GRID];
    private int[] frontHeights = new int[GRID * GRID];
    private int passesSinceFull;
    private boolean fullPass;
    private static final int FULL_EVERY = 10; // passes (one pass is 4 ticks): a full resample about every 2 seconds
    private int frontCX, frontCZ, frontStep = 1;
    private int backCX, backCZ, backStep = 1, backRow = -1;
    private boolean haveFront;
    private DynamicTexture texture;
    private long lastCompose;

    public MinimapHud() {
        super("Minimap", "A map of the world around you, with players, mobs and waypoints", ModuleCategory.HUD, 1.0, 0.0);
        listen(TickEvent.Post.class, event -> sampleSome());
    }

    @Override
    public int getWidth() {
        return size.intValue();
    }

    @Override
    public int getHeight() {
        return size.intValue() + (coords.get() ? 11 : 0);
    }

    /** Test hook: the map has a full picture of the world around you. */
    public boolean readyForTest() {
        return haveFront;
    }

    @Override
    protected void onDisable() {
        haveFront = false;
        backRow = -1;
    }

    // ------------------------------------------------------------------ sampling (one quarter of the grid per tick)

    private void sampleSome() {
        Level level = mc.level;
        if (level == null || mc.player == null) {
            haveFront = false;
            backRow = -1;
            return;
        }
        int step = zoom.intValue();
        if (backRow < 0) {
            // Centre snapped to the zoom step, so the new grid lines up with the previous one and can reuse it.
            backCX = Math.floorDiv(mc.player.getBlockX(), step) * step;
            backCZ = Math.floorDiv(mc.player.getBlockZ(), step) * step;
            backStep = step;
            backRow = 0;
            fullPass = !haveFront || frontStep != step || ++passesSinceFull >= FULL_EVERY;
            if (fullPass) {
                passesSinceFull = 0;
            }
        }
        int caveTop = level.dimensionType().hasCeiling() ? mc.player.getBlockY() + 1 : Integer.MIN_VALUE;
        int originX = backCX - GRID / 2 * backStep;
        int originZ = backCZ - GRID / 2 * backStep;
        int frontOriginX = frontCX - GRID / 2 * frontStep;
        int frontOriginZ = frontCZ - GRID / 2 * frontStep;
        boolean reuse = !fullPass;
        int rows = GRID / 4 + 1;
        for (int r = 0; r < rows && backRow < GRID; r++, backRow++) {
            int z = originZ + backRow * backStep;
            int fz = (z - frontOriginZ) / backStep;
            boolean rowInFront = reuse && fz >= 0 && fz < GRID;
            for (int gx = 0; gx < GRID; gx++) {
                int x = originX + gx * backStep;
                int i = backRow * GRID + gx;
                if (rowInFront) {
                    int fx = (x - frontOriginX) / backStep;
                    if (fx >= 0 && fx < GRID) {
                        // Already sampled in the previous picture: copy it instead of reading the world again.
                        back[i] = front[fz * GRID + fx];
                        backHeights[i] = frontHeights[fz * GRID + fx];
                        continue;
                    }
                }
                int h = sampler.height(level, x, z, caveTop);
                int north = backRow > 0 ? backHeights[(backRow - 1) * GRID + gx] : sampler.height(level, x, z - backStep, caveTop);
                backHeights[i] = h;
                back[i] = sampler.color(level, x, z, h, north);
            }
        }
        if (backRow >= GRID) {
            int[] t = front;
            front = back;
            back = t;
            int[] th = frontHeights;
            frontHeights = backHeights;
            backHeights = th;
            frontCX = backCX;
            frontCZ = backCZ;
            frontStep = backStep;
            haveFront = true;
            backRow = -1;
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Screen offset (in texture pixels from the centre) of a world position, following the map rotation. */
    private float[] toMap(double wx, double wz, double px, double pz, float yaw, int step) {
        double dx = (wx - px) / step;
        double dz = (wz - pz) / step;
        if (!rotate.get()) {
            return new float[]{(float) dx, (float) dz};
        }
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);   // forward
        double rx = -Math.cos(rad), rz = -Math.sin(rad);  // right
        return new float[]{(float) (dx * rx + dz * rz), (float) -(dx * fx + dz * fz)};
    }

    private void compose(double px, double pz, float yaw) {
        if (texture == null) {
            texture = new DynamicTexture(() -> "nightshade minimap", new NativeImage(OUT, OUT, true));
            mc.getTextureManager().register(TEXTURE, texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        boolean circle = shape.is("Circle");
        int step = frontStep;
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        boolean turn = rotate.get();
        double originX = frontCX - GRID / 2.0 * step;
        double originZ = frontCZ - GRID / 2.0 * step;
        double half = OUT / 2.0;
        for (int oy = 0; oy < OUT; oy++) {
            double dy = oy + 0.5 - half;
            for (int ox = 0; ox < OUT; ox++) {
                double dx = ox + 0.5 - half;
                if (circle && dx * dx + dy * dy > half * half) {
                    image.setPixel(ox, oy, 0);
                    continue;
                }
                double wx, wz;
                if (turn) {
                    wx = rx * dx - fx * dy;
                    wz = rz * dx - fz * dy;
                } else {
                    wx = dx;
                    wz = dy;
                }
                int gx = (int) Math.floor((px + wx * step - originX) / step);
                int gz = (int) Math.floor((pz + wz * step - originZ) / step);
                int argb = gx >= 0 && gz >= 0 && gx < GRID && gz < GRID ? front[gz * GRID + gx] : 0;
                image.setPixel(ox, oy, argb == 0 ? 0xFF15121E : argb);
            }
        }
        texture.upload();
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor ctx, float tickDelta, boolean preview) {
        int s = size.intValue();
        boolean circle = shape.is("Circle");
        if (mc.player == null || !haveFront) {
            RenderUtil.roundedRect(ctx, 0, 0, s, s, circle ? s / 2 : 4, ColorUtil.withAlpha(Theme.BACKGROUND, 0xC0));
            Fonts.drawCentered(ctx, preview ? "Minimap" : "Loading map...", s / 2, s / 2 - 4, Theme.TEXT_MUTED, false);
            return;
        }
        double px = Mth.lerp(tickDelta, mc.player.xo, mc.player.getX());
        double pz = Mth.lerp(tickDelta, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getViewYRot(tickDelta);
        long now = System.currentTimeMillis();
        if (now - lastCompose >= 33) { // ~30 updates a second is plenty for a map
            compose(px, pz, yaw);
            lastCompose = now;
        }
        // frame
        if (circle) {
            ring(ctx, s / 2f, s / 2f, s / 2f + 1.5f, 0xC0000000);
        } else {
            RenderUtil.rect(ctx, -1, -1, s + 1, s + 1, 0xC0000000);
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, s, s, OUT, OUT, OUT, OUT);
        float scale = s / (float) OUT;
        float c = s / 2f;
        int step = frontStep;
        double radius = OUT / 2.0 - 2;

        if (waypoints.get()) {
            for (Waypoint w : NightshadeClient.client().waypoints().inDimension()) {
                if (!w.visible()) {
                    continue;
                }
                float[] m = toMap(w.x() + 0.5, w.z() + 0.5, px, pz, yaw, step);
                // pinned to the edge when outside the map
                double reach = circle ? Math.sqrt(m[0] * m[0] + m[1] * m[1]) : Math.max(Math.abs(m[0]), Math.abs(m[1]));
                if (reach > radius) {
                    m[0] *= (float) (radius / reach);
                    m[1] *= (float) (radius / reach);
                }
                int x = Math.round(c + m[0] * scale);
                int y = Math.round(c + m[1] * scale);
                RenderUtil.rect(ctx, x - 2, y - 2, x + 3, y + 3, 0xFF000000);
                RenderUtil.rect(ctx, x - 1, y - 1, x + 2, y + 2, 0xFF000000 | w.color());
            }
        }
        if (players.get() || mobs.get()) {
            double range = OUT / 2.0 * step;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !(e instanceof LivingEntity) || Math.abs(e.getX() - px) > range || Math.abs(e.getZ() - pz) > range) {
                    continue;
                }
                int color;
                if (e instanceof Player) {
                    if (!players.get()) {
                        continue;
                    }
                    color = 0xFFFFFFFF;
                } else {
                    if (!mobs.get()) {
                        continue;
                    }
                    color = e instanceof Enemy ? 0xFFFF4D5E : 0xFF6EE07A;
                }
                float[] m = toMap(Mth.lerp(tickDelta, e.xo, e.getX()), Mth.lerp(tickDelta, e.zo, e.getZ()), px, pz, yaw, step);
                if (circle ? m[0] * m[0] + m[1] * m[1] > radius * radius : Math.abs(m[0]) > radius || Math.abs(m[1]) > radius) {
                    continue;
                }
                int x = Math.round(c + m[0] * scale);
                int y = Math.round(c + m[1] * scale);
                int r = e instanceof Player ? 2 : 1;
                RenderUtil.rect(ctx, x - r - 1, y - r - 1, x + r + 1, y + r + 1, 0xA0000000);
                RenderUtil.rect(ctx, x - r, y - r, x + r, y + r, color);
            }
        }
        // north marker
        float[] north = toMap(px, pz - 1000, px, pz, yaw, step);
        double nl = Math.sqrt(north[0] * north[0] + north[1] * north[1]);
        double edge = circle ? radius : radius / Math.max(Math.abs(north[0]), Math.abs(north[1])) * nl;
        int nx = Math.round(c + (float) (north[0] / nl * edge) * scale);
        int ny = Math.round(c + (float) (north[1] / nl * edge) * scale);
        RenderUtil.roundedRect(ctx, nx - 4, ny - 5, nx + 5, ny + 5, 3, 0xC0000000);
        Fonts.drawCentered(ctx, "N", nx + 1, ny - 4, Theme.DANGER, false);
        // you: an arrow in the middle
        ctx.pose().pushMatrix();
        ctx.pose().translate(c, c);
        if (!rotate.get()) {
            ctx.pose().rotate((float) Math.toRadians(yaw + 180f));
        }
        for (int row = 0; row < 5; row++) {
            int half = row / 2 + 1;
            RenderUtil.rect(ctx, -half - 1, row - 3 - 1, half + 1, row - 3 + 1, 0xFF000000);
        }
        for (int row = 0; row < 5; row++) {
            int half = row / 2;
            RenderUtil.rect(ctx, -half, row - 3, half + 1, row - 2, Theme.accentLight());
        }
        ctx.pose().popMatrix();
        if (coords.get()) {
            String text = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
            Fonts.drawCentered(ctx, text, s / 2, s + 2, textColor.argb(), true);
        }
        EffectProbe.hit("Minimap");
    }

    /** A round frame made of small squares. */
    private static void ring(GuiGraphicsExtractor ctx, float cx, float cy, float r, int color) {
        int n = Math.max(32, (int) (r * 4));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = Math.round(cx + (float) Math.cos(a) * r);
            int y = Math.round(cy + (float) Math.sin(a) * r);
            RenderUtil.rect(ctx, x - 1, y - 1, x + 1, y + 1, color);
        }
    }
}
