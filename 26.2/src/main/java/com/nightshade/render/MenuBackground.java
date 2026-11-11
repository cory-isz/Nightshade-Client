package com.nightshade.render;

import com.nightshade.NightshadeClient;
import com.nightshade.module.interfaces.MainMenuModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * The Nightshade menu background ("Nightshade Sky"): a smooth drifting nebula in a choice of colour palettes, faint
 * stars and soft floating lights. Drawn behind the Nightshade menus and - through ScreenPanoramaMixin - behind every
 * vanilla menu screen opened from them, so the background never jumps back to the Minecraft panorama.
 */
public final class MenuBackground {
    private static final Identifier NEBULA_A = Identifier.fromNamespaceAndPath("nightshade", "textures/gui/menu_nebula_a.png");
    private static final Identifier NEBULA_B = Identifier.fromNamespaceAndPath("nightshade", "textures/gui/menu_nebula_b.png");
    private static final Identifier WISPS = Identifier.fromNamespaceAndPath("nightshade", "textures/gui/menu_wisps.png");
    private static final Identifier STARS = Identifier.fromNamespaceAndPath("nightshade", "textures/gui/menu_stars.png");
    private static final Identifier AURORA = tex("scene_aurora");
    private static final Identifier AURORA2 = tex("scene_aurora2");
    private static final Identifier PEAKS_FAR = tex("scene_peaks_far");
    private static final Identifier PEAKS_NEAR = tex("scene_peaks_near");
    private static final Identifier FOG = tex("scene_fog");
    private static final Identifier FOREST_FAR = tex("scene_forest_far");
    private static final Identifier FOREST_MID = tex("scene_forest_mid");
    private static final Identifier FOREST_NEAR = tex("scene_forest_near");
    private static final Identifier PLANET = tex("scene_planet");

    /** Background choices that are drawn by Nightshade (everything except the two panorama modes). */
    public static final String[] SCENES = {"Aurora Peaks", "Night Forest", "Deep Space", "Nightshade Sky"};

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("nightshade", "textures/gui/" + name + ".png");
    }

    /** Menu colour palettes: sky top, sky middle, sky bottom, main nebula, second nebula tone, wisps, lights. */
    public enum Palette {
        NIGHTSHADE("Nightshade", 0x0C071C, 0x1C0E3A, 0x0A0516, 0x7C6CFF, 0xC86BFF, 0xD2B4FF, 0xB9AEFF),
        AURORA("Aurora", 0x061418, 0x0A2430, 0x050E14, 0x2EE6A8, 0x7C6CFF, 0x9CFFE0, 0xA8FFE4),
        CRIMSON("Crimson", 0x16060A, 0x2E0A14, 0x10040A, 0xFF4D6D, 0xFF8A3D, 0xFFB4C2, 0xFFB0BE),
        OCEAN("Ocean", 0x050C1C, 0x0A1A3A, 0x040914, 0x3DA5FF, 0x36E0FF, 0xA8DCFF, 0xA8D8FF),
        EMERALD("Emerald", 0x05140A, 0x0A2A16, 0x040E08, 0x2ED47A, 0xC6F25B, 0xB4FFD0, 0xB0FFCC),
        SUNSET("Sunset", 0x180A0C, 0x3A1420, 0x12060A, 0xFF8A3D, 0xFF4DA6, 0xFFD2A8, 0xFFC8A0),
        ROSE("Rose", 0x160814, 0x341030, 0x10060E, 0xFF6BC8, 0xB98CFF, 0xFFC4EA, 0xFFC0E6),
        MIDNIGHT("Midnight", 0x05070F, 0x0E1428, 0x04050A, 0x6C7CFF, 0x9AA6C8, 0xC8D0FF, 0xD0D8FF),
        AMBER("Amber", 0x140C04, 0x2E1C08, 0x0E0804, 0xFFB020, 0xFF7A3D, 0xFFE0A8, 0xFFDCA0),
        MONO("Mono", 0x0A0A0C, 0x18181C, 0x060608, 0xB8BCC8, 0x7A7E8A, 0xE0E2E8, 0xE8EAF0);

        public final String label;
        final int top;
        final int mid;
        final int bottom;
        final int nebula;
        final int nebula2;
        final int wisps;
        final int lights;

        Palette(String label, int top, int mid, int bottom, int nebula, int nebula2, int wisps, int lights) {
            this.label = label;
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.nebula = nebula;
            this.nebula2 = nebula2;
            this.wisps = wisps;
            this.lights = lights;
        }

        public static String[] labels() {
            String[] out = new String[values().length];
            for (int i = 0; i < out.length; i++) {
                out[i] = values()[i].label;
            }
            return out;
        }

        public static Palette byLabel(String label) {
            for (Palette p : values()) {
                if (p.label.equals(label)) {
                    return p;
                }
            }
            return NIGHTSHADE;
        }
    }

    private static final int MOTES = 24;
    private static final float[][] MOTE = new float[MOTES][];

    static {
        Random random = new Random(0x4E5348L);
        for (int i = 0; i < MOTES; i++) {
            // x, speed, phase, size, warm, offset
            MOTE[i] = new float[]{random.nextFloat(), 0.009f + random.nextFloat() * 0.02f, random.nextFloat() * 6.283f,
                    1.6f + random.nextFloat() * 2.6f, random.nextFloat() < 0.3f ? 1f : 0f, random.nextFloat()};
        }
    }

    private static float frozenAt = 40f;

    /** Animation clock for the scenes; stands still when Animate Background is off. */
    private static float sceneTime() {
        if (com.nightshade.module.interfaces.MainMenuModule.animateBackground()) {
            frozenAt = Fx.time();
            return frozenAt;
        }
        return frozenAt;
    }

    private static float parallaxX;
    private static float parallaxY;
    private static float targetParallaxX;
    private static float targetParallaxY;
    private static long lastParallaxNanos;

    private MenuBackground() {
    }

    /** Mouse position as -0.5..0.5 of the screen; the sky layers drift slightly against it (eased). */
    public static void parallax(double mouseX, double mouseY, int width, int height) {
        targetParallaxX = (float) (mouseX / Math.max(1, width) - 0.5);
        targetParallaxY = (float) (mouseY / Math.max(1, height) - 0.5);
    }

    private static void stepParallax() {
        long now = System.nanoTime();
        float dt = lastParallaxNanos == 0 ? 0f : Math.min(0.1f, (now - lastParallaxNanos) / 1e9f);
        lastParallaxNanos = now;
        float k = 1f - (float) Math.exp(-4f * dt);
        parallaxX += (targetParallaxX - parallaxX) * k;
        parallaxY += (targetParallaxY - parallaxY) * k;
    }

    private static MainMenuModule module() {
        var client = NightshadeClient.client();
        return client == null || client.modules() == null ? null : client.modules().get(MainMenuModule.class);
    }

    /** The background mode chosen in the Main Menu module ("Nightshade Sky", "Blurred Panorama", "Panorama"). */
    public static String mode() {
        MainMenuModule m = module();
        return m == null ? "Aurora Peaks" : m.background();
    }

    /** Whether a Nightshade scene replaces the vanilla panorama right now. */
    public static boolean skyActive() {
        MainMenuModule m = module();
        return m != null && m.isEnabled() && isScene(m.background());
    }

    public static boolean isScene(String mode) {
        for (String s : SCENES) {
            if (s.equals(mode)) {
                return true;
            }
        }
        return false;
    }

    public static Palette palette() {
        return Theme.palette();
    }

    public static boolean lightsEnabled() {
        MainMenuModule m = module();
        return m == null || m.particles();
    }

    /** Draws the chosen background scene for the current palette. */
    public static void sky(GuiGraphicsExtractor ctx, int width, int height) {
        switch (mode()) {
            case "Aurora Peaks" -> auroraPeaks(ctx, width, height);
            case "Night Forest" -> nightForest(ctx, width, height);
            case "Deep Space" -> deepSpace(ctx, width, height);
            default -> nebulaSky(ctx, width, height);
        }
    }

    // ------------------------------------------------------------------ scenes

    private static int shade(int rgb, float k) {
        int r = Math.round(((rgb >> 16) & 255) * k);
        int g = Math.round(((rgb >> 8) & 255) * k);
        int b = Math.round((rgb & 255) * k);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static void gradient(GuiGraphicsExtractor ctx, int width, int height, int top, int mid, int bottom) {
        int split = Math.round(height * 0.55f);
        ctx.fillGradient(0, 0, width, split, 0xFF000000 | top, 0xFF000000 | mid);
        ctx.fillGradient(0, split, width, height, 0xFF000000 | mid, 0xFF000000 | bottom);
    }

    private static void stars(GuiGraphicsExtractor ctx, int width, int height, float t, float alpha) {
        int a = Math.round((0xA0 + 0x30 * (float) Math.sin(t * 0.6)) * alpha);
        Fx.cover(ctx, STARS, 1280, 720, width, height, 0.04f, -parallaxX * 6f, -parallaxY * 4f, ColorUtil.withAlpha(0xFFFFFF, a));
    }

    /** A 960x540 layer scaled to cover the width, anchored to the bottom edge (silhouettes). */
    private static void ground(GuiGraphicsExtractor ctx, Identifier texture, int width, int height, float depth, int color) {
        float scale = Math.max(width / 960f, height / 540f) * (1f + 0.06f * depth);
        float w = 960f * scale;
        float h = 540f * scale;
        float dx = -parallaxX * 40f * depth;
        float dy = -parallaxY * 12f * depth;
        ctx.pose().pushMatrix();
        ctx.pose().translate((width - w) / 2f + dx, height - h + dy + 6f * depth);
        ctx.pose().scale(scale, scale);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, 960, 540, 960, 540, 960, 540, color);
        ctx.pose().popMatrix();
    }

    /** A horizontal fog band (960x200) centred at {@code yFrac} of the height, drifting slowly. */
    private static void fog(GuiGraphicsExtractor ctx, int width, int height, float yFrac, float t, float speed, int color) {
        float scale = width / 960f * 1.3f;
        float w = 960f * scale;
        float h = 200f * scale * 0.7f;
        float drift = (float) Math.sin(t * speed) * width * 0.08f - parallaxX * 30f;
        ctx.pose().pushMatrix();
        ctx.pose().translate((width - w) / 2f + drift, height * yFrac - h / 2f);
        ctx.pose().scale(scale, scale * 0.7f);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, FOG, 0, 0, 0f, 0f, 960, 200, 960, 200, 960, 200, color);
        ctx.pose().popMatrix();
    }

    private static void auroraPeaks(GuiGraphicsExtractor ctx, int width, int height) {
        Palette p = palette();
        float t = sceneTime();
        stepParallax();
        gradient(ctx, width, height, p.top, p.mid, p.bottom);
        stars(ctx, width, height, t, 1f);
        // Two aurora curtains drifting against each other and slowly breathing.
        float ax = (float) Math.sin(t * 0.05) * 30f - parallaxX * 12f;
        float ay = -parallaxY * 6f;
        int a1 = 0xD0 + Math.round(0x2F * (float) Math.sin(t * 0.33));
        int a2 = 0xB0 + Math.round(0x40 * (float) Math.sin(t * 0.27 + 1.7));
        int c1 = ColorUtil.lerp(0xFF000000 | p.nebula, 0xFFFFFFFF, 0.18f);
        int c2 = ColorUtil.lerp(0xFF000000 | p.nebula2, 0xFFFFFFFF, 0.10f);
        // Each curtain twice: the second pass makes the bright rays glow instead of looking washed out.
        Fx.cover(ctx, AURORA2, 960, 540, width, height, 0.12f, -ax * 0.8f, ay, ColorUtil.withAlpha(c2, a2));
        Fx.cover(ctx, AURORA2, 960, 540, width, height, 0.12f, -ax * 0.8f, ay, ColorUtil.withAlpha(c2, a2 / 2));
        Fx.cover(ctx, AURORA, 960, 540, width, height, 0.10f, ax, ay, ColorUtil.withAlpha(c1, a1));
        Fx.cover(ctx, AURORA, 960, 540, width, height, 0.10f, ax, ay, ColorUtil.withAlpha(c1, a1 / 2));
        // Glow on the horizon behind the mountains.
        Fx.glowRect(ctx, -width / 4, Math.round(height * 0.40f), width + width / 4, Math.round(height * 1.05f), ColorUtil.withAlpha(p.nebula, 0x55));
        ground(ctx, PEAKS_FAR, width, height, 0.5f, shade(p.mid, 0.48f));
        fog(ctx, width, height, 0.70f, t, 0.04f, ColorUtil.withAlpha(p.wisps, 0x48));
        ground(ctx, PEAKS_NEAR, width, height, 1f, shade(p.bottom, 0.35f));
    }

    private static void nightForest(GuiGraphicsExtractor ctx, int width, int height) {
        Palette p = palette();
        float t = sceneTime();
        stepParallax();
        gradient(ctx, width, height, p.top, p.mid, p.bottom);
        stars(ctx, width, height, t, 0.9f);
        // Moon with a wide halo.
        float mx = width * 0.74f - parallaxX * 10f;
        float my = height * 0.22f - parallaxY * 6f;
        Fx.glowSmooth(ctx, mx, my, Math.min(width, height) * 0.55f, ColorUtil.withAlpha(p.wisps, 0x48));
        Fx.glowSmooth(ctx, mx, my, Math.min(width, height) * 0.10f, ColorUtil.withAlpha(0xFFFFFF, 0xE0));
        Fx.glowSmooth(ctx, mx, my, Math.min(width, height) * 0.045f, 0xFFFFFFFF);
        ground(ctx, FOREST_FAR, width, height, 0.35f, shade(p.mid, 0.78f));
        fog(ctx, width, height, 0.62f, t, 0.035f, ColorUtil.withAlpha(p.wisps, 0x40));
        ground(ctx, FOREST_MID, width, height, 0.65f, shade(p.mid, 0.42f));
        fog(ctx, width, height, 0.76f, t + 40f, 0.05f, ColorUtil.withAlpha(p.wisps, 0x26));
        ground(ctx, FOREST_NEAR, width, height, 1f, shade(p.bottom, 0.38f));
    }

    private static void deepSpace(GuiGraphicsExtractor ctx, int width, int height) {
        Palette p = palette();
        float t = sceneTime();
        stepParallax();
        gradient(ctx, width, height, shade(p.top, 0.7f) & 0xFFFFFF, p.top, p.bottom);
        float sx = (float) Math.sin(t * 0.018) * 12f - parallaxX * 10f;
        float sy = (float) Math.cos(t * 0.015) * 7f - parallaxY * 6f;
        Fx.cover(ctx, NEBULA_A, 640, 360, width, height, 0.08f, sx, sy, ColorUtil.withAlpha(p.nebula, 0x90));
        Fx.cover(ctx, NEBULA_B, 640, 360, width, height, 0.10f, sx * 1.3f, sy * 0.8f, ColorUtil.withAlpha(p.nebula2, 0x70));
        stars(ctx, width, height, t, 1.2f);
        shootingStar(ctx, width, height, t, p);
        // Planet with an atmosphere glow, slowly turning its lit side towards you.
        // Top right, partly off screen: clear of the greeting (left) and the play panel (bottom right).
        float size = Math.min(width, height) * 0.58f;
        float px = width * 0.86f - parallaxX * 34f;
        float py = height * 0.20f - parallaxY * 20f;
        Fx.glowSmooth(ctx, px, py, size * 0.95f, ColorUtil.withAlpha(p.nebula, 0x50));
        ctx.pose().pushMatrix();
        ctx.pose().translate(px - size / 2f, py - size / 2f);
        ctx.pose().scale(size / 512f, size / 512f);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PLANET, 0, 0, 0f, 0f, 512, 512, 512, 512, 512, 512,
                ColorUtil.lerp(0xFFFFFFFF, 0xFF000000 | p.wisps, 0.55f));
        ctx.pose().popMatrix();
    }

    /** One shooting star every few seconds, at a random place, fading along its tail. */
    private static void shootingStar(GuiGraphicsExtractor ctx, int width, int height, float t, Palette p) {
        float period = 5.5f;
        int index = (int) Math.floor(t / period);
        float phase = (t - index * period) / 0.9f; // visible for 0.9 s of each period
        if (phase > 1f) {
            return;
        }
        Random r = new Random(index * 0x9E3779B97F4A7C15L);
        float x0 = width * (0.1f + r.nextFloat() * 0.6f);
        float y0 = height * (0.05f + r.nextFloat() * 0.3f);
        float len = Math.min(width, height) * (0.18f + r.nextFloat() * 0.1f);
        float travel = Math.min(width, height) * 0.35f * phase;
        float angle = 0.45f + r.nextFloat() * 0.3f;
        int alpha = Math.round(255 * (float) Math.sin(Math.PI * phase));
        ctx.pose().pushMatrix();
        ctx.pose().translate(x0 + (float) Math.cos(angle) * travel, y0 + (float) Math.sin(angle) * travel);
        ctx.pose().rotate(angle);
        RenderUtil.horizontalGradient(ctx, Math.round(-len), 0, 0, 1, 0x00FFFFFF & p.wisps, ColorUtil.withAlpha(0xFFFFFF, alpha));
        ctx.pose().popMatrix();
    }

    private static void nebulaSky(GuiGraphicsExtractor ctx, int width, int height) {
        Palette p = palette();
        float t = sceneTime();
        // Base gradient (two halves meeting a little below the middle).
        int split = Math.round(height * 0.55f);
        ctx.fillGradient(0, 0, width, split, 0xFF000000 | p.top, 0xFF000000 | p.mid);
        ctx.fillGradient(0, split, width, height, 0xFF000000 | p.mid, 0xFF000000 | p.bottom);
        stepParallax();
        float sx = (float) Math.sin(t * 0.021) * 14f - parallaxX * 16f;
        float sy = (float) Math.cos(t * 0.017) * 8f - parallaxY * 10f;
        Fx.cover(ctx, NEBULA_A, 640, 360, width, height, 0.08f, sx, sy, ColorUtil.withAlpha(p.nebula, 0xC0));
        Fx.cover(ctx, NEBULA_B, 640, 360, width, height, 0.10f, sx * 1.3f, sy * 0.8f, ColorUtil.withAlpha(p.nebula2, 0xA8));
        float wx = (float) Math.sin(t * 0.035 + 1.3) * -22f - parallaxX * 26f;
        float wy = (float) Math.cos(t * 0.028 + 0.4) * 10f - parallaxY * 14f;
        int wispAlpha = 0x60 + Math.round(0x20 * (float) Math.sin(t * 0.25));
        Fx.cover(ctx, WISPS, 640, 360, width, height, 0.14f, wx, wy, ColorUtil.withAlpha(p.wisps, wispAlpha));
        int starAlpha = 0xB0 + Math.round(0x30 * (float) Math.sin(t * 0.6));
        Fx.cover(ctx, STARS, 1280, 720, width, height, 0.04f, sx * 0.4f, sy * 0.4f, ColorUtil.withAlpha(0xFFFFFF, starAlpha));
        Fx.glowRect(ctx, Math.round(width * 0.15f), Math.round(height * -0.25f), Math.round(width * 0.85f), Math.round(height * 0.75f),
                ColorUtil.withAlpha(p.nebula, 0x22));
    }

    /** Soft floating lights (sub-pixel smooth). */
    public static void lights(GuiGraphicsExtractor ctx, int width, int height) {
        if (!com.nightshade.module.performance.MenuOptimizer.allowLights()) {
            return;
        }
        if (!lightsEnabled()) {
            return;
        }
        Palette p = palette();
        float t = sceneTime();
        int span = height + 24;
        for (float[] m : MOTE) {
            float progress = (t * m[1] + m[5]) % 1f;
            float y = height + 12 - progress * span;
            float x = m[0] * width + (float) Math.sin(t * 0.3f + m[2]) * 12f;
            float edge = Math.min(1f, Math.min(progress / 0.2f, (1f - progress) / 0.25f));
            float breathe = 0.7f + 0.3f * (float) Math.sin(t * 0.9f + m[2] * 3f);
            int alpha = Math.round(0x58 * edge * breathe);
            if (alpha > 2) {
                int color = m[4] > 0.5f ? ColorUtil.lerp(0xFF000000 | p.lights, 0xFFFFFFFF, 0.35f) & 0xFFFFFF : p.lights;
                Fx.glowSmooth(ctx, x, y, m[3] * 2.6f, ColorUtil.withAlpha(color, alpha));
            }
        }
    }

    /** Accent colour (the client colour). */
    public static int accent() {
        return Theme.accent();
    }

    /** Lighter accent for icons and text highlights. */
    public static int accentLight() {
        return Theme.accentLight();
    }

    /** Main colour of a palette (the accent it gives the client). */
    public static int accentOf(Palette p) {
        return 0xFF000000 | p.nebula;
    }
}
