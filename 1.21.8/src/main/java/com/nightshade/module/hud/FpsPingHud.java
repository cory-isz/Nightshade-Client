package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * FPS and server latency display. FPS comes from Minecraft's own once-per-second counter and ping is polled at
 * most twice per second; the display string is rebuilt only when a value actually changes.
 */
public final class FpsPingHud extends HudModule {
    private static final long PING_POLL_NANOS = 500_000_000L;

    private final BooleanSetting showFps = add(new BooleanSetting("Show FPS", "Display frames per second", true));
    private final BooleanSetting showPing = add(new BooleanSetting("Show Ping", "Display server latency", true));
    private final ModeSetting format = add(new ModeSetting("Format", "Display format", "Labeled",
            "Labeled", "Suffix", "Compact"));
    private final BooleanSetting colorQuality = add(new BooleanSetting("Color By Quality", "Colour values green/yellow/red by quality", true));
    private final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of labels", 0xFFB794FF));
    private final BooleanSetting frameGraph = add(new BooleanSetting("Frame Graph", "Graph of recent frame times, so you can see stutters", false));

    private static final int GRAPH_SAMPLES = 90;
    private static final int GRAPH_H = 16;
    private final float[] frameMs = new float[GRAPH_SAMPLES];
    private int frameIndex;
    private long lastFrameNanos;

    private int fps = -1;
    private int ping = Integer.MIN_VALUE;
    private long lastPingPoll;
    private String fpsLabel = "";
    private String fpsValue = "";
    private String pingLabel = "";
    private String pingValue = "";
    private String separator = "";
    private int width = 40;
    private String lastFormat = "";
    private boolean lastShowFps;
    private boolean lastShowPing;

    public FpsPingHud() {
        super("FPS & Ping", "Shows real-time FPS and server latency", ModuleCategory.HUD, 0.0, 0.075);
        enabledByDefault();
    }

    @Override
    protected void refresh() {
        int newFps = mc.getFps();
        long now = System.nanoTime();
        int newPing = ping;
        if (now - lastPingPoll >= PING_POLL_NANOS || ping == Integer.MIN_VALUE) {
            lastPingPoll = now;
            newPing = readPing();
        }
        boolean layoutChanged = !format.get().equals(lastFormat) || showFps.get() != lastShowFps || showPing.get() != lastShowPing;
        if (newFps == fps && newPing == ping && !layoutChanged) {
            return;
        }
        fps = newFps;
        ping = newPing;
        lastFormat = format.get();
        lastShowFps = showFps.get();
        lastShowPing = showPing.get();
        rebuild();
    }

    /** Returns latency in ms, -1 in singleplayer, -2 when unknown. */
    private int readPing() {
        if (mc.isLocalServer()) {
            return -1;
        }
        ClientPacketListener handler = mc.getConnection();
        if (handler == null || mc.player == null) {
            return -2;
        }
        PlayerInfo entry = handler.getPlayerInfo(mc.player.getUUID());
        return entry == null ? -2 : Math.max(0, entry.getLatency());
    }

    private void rebuild() {
        String pingText = ping == -1 ? "SP" : ping == -2 ? "?" : Integer.toString(ping);
        switch (format.get()) {
            case "Labeled" -> {
                fpsLabel = "FPS ";
                fpsValue = Integer.toString(fps);
                pingLabel = "Ping ";
                pingValue = ping >= 0 ? pingText + "ms" : pingText;
                separator = "  ";
            }
            case "Suffix" -> {
                fpsLabel = "";
                fpsValue = fps + " fps";
                pingLabel = "";
                pingValue = ping >= 0 ? pingText + " ms" : pingText;
                separator = "  ";
            }
            default -> {
                fpsLabel = "";
                fpsValue = Integer.toString(fps);
                pingLabel = "";
                pingValue = pingText;
                separator = " | ";
            }
        }
        int w = 0;
        if (showFps.get()) {
            w += Fonts.width(fpsLabel) + Fonts.width(fpsValue);
        }
        if (showFps.get() && showPing.get()) {
            w += Fonts.width(separator);
        }
        if (showPing.get()) {
            w += Fonts.width(pingLabel) + Fonts.width(pingValue);
        }
        width = Math.max(20, w + 8);
    }

    @Override
    public int getWidth() {
        return frameGraph.get() ? Math.max(width, GRAPH_SAMPLES + 8) : width;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6 + (frameGraph.get() ? GRAPH_H + 12 : 0);
    }

    /** Records this frame's time (the HUD is drawn once per frame) and draws the last {@value #GRAPH_SAMPLES} frames. */
    private void renderGraph(GuiGraphics context, int top) {
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            frameMs[frameIndex] = (now - lastFrameNanos) / 1_000_000f;
            frameIndex = (frameIndex + 1) % GRAPH_SAMPLES;
        }
        lastFrameNanos = now;
        int x0 = (getWidth() - GRAPH_SAMPLES) / 2;
        context.fill(x0, top, x0 + GRAPH_SAMPLES, top + GRAPH_H, 0x30000000);
        // reference line at 60 fps (16.7 ms), graph scale 0-50 ms
        int ref = top + GRAPH_H - Math.round(GRAPH_H * 16.7f / 50f);
        context.fill(x0, ref, x0 + GRAPH_SAMPLES, ref + 1, 0x30FFFFFF);
        float worst = 0f;
        for (int i = 0; i < GRAPH_SAMPLES; i++) {
            float ms = frameMs[(frameIndex + i) % GRAPH_SAMPLES];
            worst = Math.max(worst, ms);
            if (ms <= 0f) {
                continue;
            }
            int h = Math.max(1, Math.min(GRAPH_H, Math.round(GRAPH_H * ms / 50f)));
            int c = ms <= 16.7f ? 0xC05BE49B : ms <= 33.4f ? 0xC0FFC857 : 0xD0FF5C7A;
            context.fill(x0 + i, top + GRAPH_H - h, x0 + i + 1, top + GRAPH_H, c);
        }
        String label = String.format(java.util.Locale.ROOT, "worst %.0f ms", worst);
        Fonts.draw(context, label, x0, top + GRAPH_H + 2, Theme.TEXT_DIM, shadow());
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        if (!showFps.get() && !showPing.get()) {
            if (preview) {
                drawBackground(context, getWidth(), getHeight());
                Fonts.draw(context, "-", 4, 4, textColor.argb(), shadow());
            }
            return;
        }
        drawBackground(context, getWidth(), getHeight());
        int fpsColor = colorQuality.get() ? (fps >= 60 ? Theme.SUCCESS : fps >= 30 ? Theme.WARNING : Theme.DANGER) : textColor.argb();
        int pingColor = !colorQuality.get() || ping < 0 ? textColor.argb()
                : ping <= 80 ? Theme.SUCCESS : ping <= 180 ? Theme.WARNING : Theme.DANGER;
        boolean shadow = shadow();
        int x = 4;
        int y = 4;
        if (showFps.get()) {
            x = Fonts.draw(context, fpsLabel, x, y, labelColor.argb(), shadow) - (fpsLabel.isEmpty() ? 0 : 1);
            x = Fonts.draw(context, fpsValue, x, y, fpsColor, shadow) - 1;
        }
        if (showFps.get() && showPing.get()) {
            x = Fonts.draw(context, separator, x, y, Theme.TEXT_DIM, shadow) - 1;
        }
        if (showPing.get()) {
            x = Fonts.draw(context, pingLabel, x, y, labelColor.argb(), shadow) - (pingLabel.isEmpty() ? 0 : 1);
            Fonts.draw(context, pingValue, x, y, pingColor, shadow);
        }
        if (frameGraph.get()) {
            renderGraph(context, Fonts.height() + 6);
        }
    }
}
