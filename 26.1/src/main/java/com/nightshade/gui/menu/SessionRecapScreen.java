package com.nightshade.gui.menu;

import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.module.pvp.SessionRecap;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** The session recap: your totals as cards, then your recent fights, newest first. */
public final class SessionRecapScreen extends NsMenuScreen {
    @Nullable
    private final Screen parent;
    private final SessionRecap recap;
    private final NsButton done = new NsButton("Done", NsButton.Style.PRIMARY, this::onClose);
    private final NsButton clear = new NsButton("Reset", NsButton.Style.GHOST, this::reset);

    public SessionRecapScreen(@Nullable Screen parent, SessionRecap recap) {
        super(Component.literal("Session Recap"));
        this.parent = parent;
        this.recap = recap;
    }

    private void reset() {
        recap.reset();
    }

    @Override
    public void onClose() {
        Screens.open(parent);
    }

    private int x1() {
        return (width - Math.min(width - 32, 560)) / 2;
    }

    private int x2() {
        return x1() + Math.min(width - 32, 560);
    }

    private static String time(long millis) {
        long s = millis / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%dh %02dm", s / 3600, s / 60 % 60) : String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60);
    }

    private void card(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, String value, int color) {
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, 6, Theme.tint(0xB0140C26));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, 6, Theme.BORDER);
        RenderUtil.rect(ctx, x + 8, y + 6, x + 22, y + 7, ColorUtil.withAlpha(color, 0xC0));
        Fonts.draw(ctx, value, x + 8, y + 11, color, false);
        Fonts.draw(ctx, label, x + 8, y + h - Fonts.height() - 6, Theme.TEXT_DIM, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int x1 = x1();
        int x2 = x2();
        renderPage(ctx, x1, x2);
        RenderUtil.roundedRect(ctx, x1, 9, x1 + 22, 31, 6, ColorUtil.withAlpha(Theme.accent(), 0x55));
        com.nightshade.render.PixelIcons.category(ctx, com.nightshade.module.ModuleCategory.PVP, x1 + 7, 16, 0xFFFFFFFF);
        Fonts.draw(ctx, "Session Recap", x1 + 30, 11, 0xFFFFFFFF, false);
        String where = recap.sessionName().isEmpty() ? "This session" : recap.sessionName();
        Fonts.draw(ctx, Fonts.trim(where + "  ·  " + time(recap.sessionMillis()) + " played  ·  "
                + recap.fightsWon() + " of " + recap.fights().size() + " fights won", x2 - x1 - 30), x1 + 30, 22, Theme.TEXT_DIM, false);

        int gap = 6;
        int cw = (x2 - x1 - gap * 3) / 4;
        int ch = 38;
        int y = 46;
        int k = recap.kills(), d = recap.deaths();
        card(ctx, x1, y, cw, ch, "Kills", String.valueOf(k), Theme.SUCCESS);
        card(ctx, x1 + (cw + gap), y, cw, ch, "Deaths", String.valueOf(d), Theme.DANGER);
        card(ctx, x1 + (cw + gap) * 2, y, cw, ch, "K/D", recap.kd(), Theme.accentLight());
        card(ctx, x1 + (cw + gap) * 3, y, cw, ch, "Accuracy", recap.swings() == 0 ? "-" : recap.accuracy() + "%", Theme.WARNING);
        y += ch + gap;
        card(ctx, x1, y, cw, ch, "Critical hits", String.valueOf(recap.crits()), 0xFFFF9F5A);
        card(ctx, x1 + (cw + gap), y, cw, ch, "Best combo", String.valueOf(recap.bestCombo()), 0xFF7FD3FF);
        card(ctx, x1 + (cw + gap) * 2, y, cw, ch, "Hits landed", String.valueOf(recap.landed()), Theme.TEXT);
        card(ctx, x1 + (cw + gap) * 3, y, cw, ch, "Hits taken", String.valueOf(recap.taken()), Theme.TEXT);
        y += ch + 12;

        Fonts.draw(ctx, "Recent fights", x1, y, Theme.accentLight(), false);
        y += 13;
        List<SessionRecap.Fight> fights = recap.fights();
        int rowH = 18;
        int bottom = height - 34;
        if (fights.isEmpty()) {
            Fonts.draw(ctx, "No fights yet. Hit another player and your fights show up here.", x1, y + 4, Theme.TEXT_DIM, false);
        }
        int[] cols = {x1 + 8, x1 + 60, x1 + (x2 - x1) * 45 / 100, x1 + (x2 - x1) * 60 / 100, x1 + (x2 - x1) * 72 / 100, x1 + (x2 - x1) * 86 / 100};
        if (!fights.isEmpty()) {
            Fonts.draw(ctx, "Opponent", cols[1], y, Theme.TEXT_DIM, false);
            Fonts.draw(ctx, "Hits", cols[2], y, Theme.TEXT_DIM, false);
            Fonts.draw(ctx, "Crits", cols[3], y, Theme.TEXT_DIM, false);
            Fonts.draw(ctx, "Accuracy", cols[4], y, Theme.TEXT_DIM, false);
            Fonts.draw(ctx, "Time", cols[5], y, Theme.TEXT_DIM, false);
            y += 11;
        }
        for (int i = 0; i < fights.size() && y + rowH <= bottom; i++) {
            SessionRecap.Fight f = fights.get(i);
            RenderUtil.roundedRect(ctx, x1, y, x2, y + rowH - 2, 4, i % 2 == 0 ? Theme.tint(0x80140C26) : Theme.tint(0x50140C26));
            int c = switch (f.result) {
                case WON -> Theme.SUCCESS;
                case LOST -> Theme.DANGER;
                case ENDED -> Theme.TEXT_DIM;
            };
            String label = switch (f.result) {
                case WON -> "WON";
                case LOST -> "LOST";
                case ENDED -> "DRAW";
            };
            RenderUtil.roundedRect(ctx, cols[0] - 4, y + 3, cols[0] + 38, y + rowH - 5, 3, ColorUtil.withAlpha(c, 0x40));
            Fonts.drawCentered(ctx, label, cols[0] + 17, y + 4, c, false);
            int ty = y + 4;
            Fonts.draw(ctx, Fonts.trim(f.opponent, cols[2] - cols[1] - 6), cols[1], ty, 0xFFFFFFFF, false);
            Fonts.draw(ctx, f.landed + " : " + f.taken, cols[2], ty, Theme.TEXT, false);
            Fonts.draw(ctx, String.valueOf(f.crits), cols[3], ty, Theme.TEXT, false);
            Fonts.draw(ctx, f.swings == 0 ? "-" : f.accuracy() + "%", cols[4], ty, Theme.TEXT, false);
            Fonts.draw(ctx, f.seconds() + "s", cols[5], ty, Theme.TEXT, false);
            y += rowH;
        }
        int fy = height - 26;
        clear.render(ctx, x1, fy, 70, 18, mouseX, mouseY);
        done.render(ctx, x2 - 90, fy, 90, 18, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return done.click(mouseX, mouseY, button) || clear.click(mouseX, mouseY, button);
    }
}
