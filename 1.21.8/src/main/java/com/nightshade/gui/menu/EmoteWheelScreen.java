package com.nightshade.gui.menu;

import com.nightshade.cosmetic.Emotes;
import com.nightshade.gui.NsScreen;
import com.nightshade.module.visual.EmoteWheel;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The emote wheel: a ring of eight slices around a centre disc. Point at a slice (it lights up and pops out) and
 * click, or hold the emote key, point and let go, or press 1-8. Esc or right click closes it. The game keeps
 * running behind it.
 */
public final class EmoteWheelScreen extends NsScreen {
    private static final Emotes.Emote[] EMOTES = Emotes.Emote.values();
    private static final ResourceLocation RING = tex("ring");
    private static final ResourceLocation SLICE = tex("slice");
    private static final ResourceLocation CENTER = tex("center");
    private static final ResourceLocation ICONS = tex("icons");
    private static final float INNER = 0.36f;   // inner radius of the ring, as a share of the outer radius
    private static final float OUTER = 0.96f;

    private final long openedAt = System.currentTimeMillis();
    private final float[] glow = new float[EMOTES.length];
    private long lastFrame = System.nanoTime();
    private int hovered = -1;

    public EmoteWheelScreen() {
        super(Component.literal("Emotes"));
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("nightshade", "textures/gui/emotes/" + name + ".png");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // keep the world visible
    }

    /** Half the wheel's size in GUI pixels. */
    private int half() {
        return Math.max(80, Math.min(135, (int) (Math.min(width, height) * 0.42f)));
    }

    /** Which slice the mouse points at, or -1 in the middle or outside the ring. */
    private int sliceAt(double mx, double my) {
        double dx = mx - width / 2.0;
        double dy = my - height / 2.0;
        double r = Math.sqrt(dx * dx + dy * dy);
        if (r < half() * INNER * 0.9 || r > half() * 1.15) {
            return -1;
        }
        double a = Math.toDegrees(Math.atan2(dx, -dy)); // 0 = up, clockwise
        a = (a + 360 + 180.0 / EMOTES.length) % 360;
        return (int) (a / (360.0 / EMOTES.length));
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 160f);
        float appear = 1f - (1f - t) * (1f - t) * (1f - t);
        int mh = sliceAt(mouseX, mouseY);
        if (mh >= 0 || !forcedHover) {
            hovered = mh;
        }
        for (int i = 0; i < glow.length; i++) {
            glow[i] += ((i == hovered ? 1f : 0f) - glow[i]) * Math.min(1f, dt * 14f);
        }
        int cx = width / 2;
        int cy = height / 2;
        int h = half();
        int alpha = Math.round(255 * appear);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        float scale = 0.82f + 0.18f * appear;
        ctx.pose().scale(scale, scale);
        // ring
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, RING, -h, -h, 0f, 0f, h * 2, h * 2, 512, 512, 512, 512, ColorUtil.withAlpha(0xFFFFFFFF, alpha));
        // lit slices (fade in and pop out a little)
        for (int i = 0; i < EMOTES.length; i++) {
            if (glow[i] < 0.02f) {
                continue;
            }
            ctx.pose().pushMatrix();
            ctx.pose().rotate((float) Math.toRadians(i * 360.0 / EMOTES.length));
            ctx.pose().translate(0, -5f * glow[i]);
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, SLICE, -h, -h, 0f, 0f, h * 2, h * 2, 512, 512, 512, 512,
                    ColorUtil.withAlpha(0xFFFFFFFF, Math.round(alpha * glow[i])));
            ctx.pose().popMatrix();
        }
        // pose icons in the middle of each slice
        float mid = h * (INNER + OUTER) / 2f;
        for (int i = 0; i < EMOTES.length; i++) {
            double a = Math.toRadians(i * 360.0 / EMOTES.length);
            float ix = (float) Math.sin(a) * (mid + 5f * glow[i]);
            float iy = (float) -Math.cos(a) * (mid + 5f * glow[i]);
            int size = Math.round(h * 0.26f * (1f + 0.18f * glow[i]));
            ctx.pose().pushMatrix();
            ctx.pose().translate(ix, iy);
            int tint = ColorUtil.lerp(0xFFC9BDE6, 0xFFFFFFFF, glow[i]);
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ICONS, -size / 2, -size / 2, i * 64f, 0f, size, size, 64, 64, 512, 64,
                    ColorUtil.withAlpha(tint, alpha));
            ctx.pose().popMatrix();
        }
        // centre disc: the name of what you point at
        int cr = Math.round(h * INNER * 0.94f);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CENTER, -cr, -cr, 0f, 0f, cr * 2, cr * 2, 256, 256, 256, 256, ColorUtil.withAlpha(0xFFFFFFFF, alpha));
        String title = hovered >= 0 ? EMOTES[hovered].label : Emotes.playing() ? Emotes.current().label : "Emotes";
        String sub = hovered >= 0 ? (hovered + 1) + "  ·  click" : Emotes.playing() ? "playing" : "pick one";
        ctx.pose().pushMatrix();
        float ts = Math.min(1.5f, (cr * 1.6f) / Math.max(1, Fonts.width(title)));
        ctx.pose().scale(ts, ts);
        Fonts.drawCentered(ctx, title, 0, -Math.round(Fonts.height() * 0.85f), ColorUtil.withAlpha(0xFFFFFFFF, alpha), true);
        ctx.pose().popMatrix();
        Fonts.drawCentered(ctx, Fonts.trim(sub, cr * 2 - 8), 0, Math.round(cr * 0.28f), ColorUtil.withAlpha(Theme.TEXT_DIM, alpha), false);
        ctx.pose().popMatrix();
        String hint = "Click, press 1-8, or hold the key and let go  ·  only you see emotes  ·  Esc to close";
        Fonts.drawCentered(ctx, Fonts.trim(hint, width - 16), cx, Math.min(height - 12, cy + Math.round(h * scale) + 8), ColorUtil.withAlpha(Theme.TEXT_DIM, alpha), true);
    }

    private void pick(int i) {
        if (i >= 0 && i < EMOTES.length) {
            Emotes.play(EMOTES[i]);
        }
        minecraft.setScreen(null);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        int i = sliceAt(mouseX, mouseY);
        if (button == 0 && i >= 0) {
            pick(i);
            return true;
        }
        if (button == 1) {
            Emotes.stop();
            minecraft.setScreen(null);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_8) {
            pick(keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        // hold the emote key, point, let go: plays what you point at (a quick tap just leaves the wheel open)
        EmoteWheel m = EmoteWheel.instance();
        if (m != null && m.getKeybind().matchesKey(keyCode) && hovered >= 0 && System.currentTimeMillis() - openedAt > 250) {
            pick(hovered);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ test hooks

    private boolean forcedHover;

    public void pickForTest(int i) {
        pick(i);
    }

    /** Light a slice as if the mouse pointed at it (for the screenshot). */
    public void hoverForTest(int i) {
        forcedHover = true;
        hovered = i;
    }
}
