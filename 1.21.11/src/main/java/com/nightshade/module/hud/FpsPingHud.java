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
        return width;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
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
    }
}
