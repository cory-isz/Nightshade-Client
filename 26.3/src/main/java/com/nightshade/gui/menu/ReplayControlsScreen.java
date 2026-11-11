package com.nightshade.gui.menu;

import com.nightshade.gui.NsScreen;
import com.nightshade.replay.ReplayPlayer;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Shown while a replay plays: the world stays visible, with a timeline bar at the bottom.
 * Space pause, arrows seek 5 s, Up/Down speed, click the bar to jump, Esc to stop.
 */
public final class ReplayControlsScreen extends NsScreen {
    private int barX1, barX2, barY;

    public ReplayControlsScreen() {
        super(Component.literal("Replay"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // keep the world fully visible
    }

    @Override
    public void tick() {
        ReplayPlayer.tickPlayback();
        if (!ReplayPlayer.active()) {
            minecraft.gui.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ReplayPlayer p = ReplayPlayer.current();
        if (p == null) {
            return;
        }
        int w = Math.min(width - 40, 420);
        int x1 = (width - w) / 2;
        int y1 = height - 46;
        com.nightshade.module.HudModule.panel(ctx, x1, y1, x1 + w, y1 + 36, 0xC0100A1E);
        String state = p.paused() ? "Paused" : "Playing";
        String time = String.format(Locale.ROOT, "%s   %.1fs / %.1fs   %sx   %d players", state, p.seconds(), p.length(),
                p.speed() == (int) p.speed() ? Integer.toString((int) p.speed()) : Float.toString(p.speed()), p.players());
        Fonts.draw(ctx, time, x1 + 10, y1 + 6, Theme.TEXT, false);
        String keys = "Space pause  ·  ← → 5s  ·  ↑ ↓ speed  ·  Esc stop";
        Fonts.draw(ctx, keys, x1 + w - 10 - Fonts.width(keys), y1 + 6, Theme.TEXT_DIM, false);
        barX1 = x1 + 10;
        barX2 = x1 + w - 10;
        barY = y1 + 22;
        RenderUtil.roundedRect(ctx, barX1, barY, barX2, barY + 5, 2, 0x60000000);
        int fill = barX1 + (int) Math.round((barX2 - barX1) * p.progress());
        RenderUtil.roundedRect(ctx, barX1, barY, Math.max(barX1 + 3, fill), barY + 5, 2, Theme.accent());
        RenderUtil.roundedRect(ctx, fill - 3, barY - 2, fill + 3, barY + 7, 3, ColorUtil.lerp(Theme.accentLight(), 0xFFFFFFFF, 0.4f));
        String title = "FIGHT REPLAY";
        Fonts.draw(ctx, title, x1 + 10, y1 - 12, ColorUtil.withAlpha(Theme.accentLight(), 0xE0), true);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        ReplayPlayer p = ReplayPlayer.current();
        if (p != null && button == 0 && mouseY >= barY - 4 && mouseY <= barY + 9 && mouseX >= barX1 && mouseX <= barX2) {
            p.seekTo((mouseX - barX1) / (double) (barX2 - barX1) * p.length() * 20);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        ReplayPlayer p = ReplayPlayer.current();
        if (p == null) {
            return false;
        }
        switch (keyCode) {
            case com.mojang.blaze3d.platform.InputConstants.KEY_SPACE -> p.togglePause();
            case com.mojang.blaze3d.platform.InputConstants.KEY_LEFT -> p.seek(-5);
            case com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT -> p.seek(5);
            case com.mojang.blaze3d.platform.InputConstants.KEY_UP -> p.faster();
            case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN -> p.slower();
            case com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE -> onClose();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        ReplayPlayer.stop();
        minecraft.gui.setScreen(null);
    }
}
