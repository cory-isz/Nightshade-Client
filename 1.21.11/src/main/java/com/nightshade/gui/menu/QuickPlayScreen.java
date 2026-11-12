package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.module.utility.HypixelTools;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Quick Play: every popular Hypixel game mode as a button; one click sends the /play command for it. */
public final class QuickPlayScreen extends NsMenuScreen {
    private record Mode(String label, String id) {
    }

    private record Section(String title, Mode[] modes) {
    }

    private static final Section[] SECTIONS = {
            new Section("Bed Wars", new Mode[]{new Mode("Solo", "bedwars_eight_one"), new Mode("Doubles", "bedwars_eight_two"),
                    new Mode("3v3v3v3", "bedwars_four_three"), new Mode("4v4v4v4", "bedwars_four_four"), new Mode("4v4", "bedwars_two_four")}),
            new Section("SkyWars", new Mode[]{new Mode("Solo Normal", "solo_normal"), new Mode("Solo Insane", "solo_insane"),
                    new Mode("Teams Normal", "teams_normal"), new Mode("Teams Insane", "teams_insane")}),
            new Section("Duels", new Mode[]{new Mode("Classic", "duels_classic_duel"), new Mode("Sumo", "duels_sumo_duel"),
                    new Mode("Bridge", "duels_bridge_duel"), new Mode("UHC", "duels_uhc_duel"), new Mode("OP", "duels_op_duel"),
                    new Mode("Bow", "duels_bow_duel"), new Mode("Combo", "duels_combo_duel"), new Mode("Skywars", "duels_sw_duel")}),
            new Section("More", new Mode[]{new Mode("The Pit", "pit"), new Mode("Murder Mystery", "murder_classic"),
                    new Mode("Build Battle", "build_battle_solo_normal"), new Mode("TNT Run", "tnt_tntrun"), new Mode("SkyBlock", "skyblock")}),
    };

    @Nullable
    private final Screen parent;
    private final List<Object[]> buttons = new ArrayList<>();
    private final NsButton back = new NsButton("Back", NsButton.Style.GHOST, this::onClose);
    private final NsButton lobby = new NsButton("Lobby", NsButton.Style.SECONDARY, () -> send("lobby", "Lobby"));
    private String lastSent;

    public QuickPlayScreen(@Nullable Screen parent) {
        super(Component.literal("Quick Play"));
        this.parent = parent;
        for (Section section : SECTIONS) {
            for (Mode mode : section.modes()) {
                NsButton b = new NsButton(mode.label(), NsButton.Style.SECONDARY, () -> send("play " + mode.id(), section.title() + " " + mode.label()));
                buttons.add(new Object[]{section, mode, b});
            }
        }
    }

    private void send(String command, String what) {
        if (!HypixelTools.onHypixel()) {
            NightshadeClient.client().notifications().info("Quick Play", "Join Hypixel first - it is in Multiplayer > Featured");
            return;
        }
        minecraft.getConnection().sendCommand(command);
        lastSent = command;
        NightshadeClient.client().notifications().success("Quick Play", "Joining " + what);
        minecraft.setScreen(null);
    }

    /** Test hook: the last command sent. */
    public String lastSentForTest() {
        return lastSent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int x1() {
        return (width - Math.min(width - 32, 560)) / 2;
    }

    private int x2() {
        return x1() + Math.min(width - 32, 560);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int x1 = x1();
        int x2 = x2();
        renderPage(ctx, x1, x2);
        RenderUtil.roundedRect(ctx, x1, 9, x1 + 22, 31, 6, ColorUtil.withAlpha(Theme.accent(), 0x55));
        PixelIcons.menu(ctx, PixelIcons.MENU_PLAY, x1 + 2, 11, 2, 0xFFE9E4FF, Theme.accentLight());
        Fonts.draw(ctx, "Quick Play", x1 + 30, 11, 0xFFFFFFFF, false);
        boolean on = HypixelTools.onHypixel();
        Fonts.draw(ctx, on ? "Pick a game - you join it right away" : "Hypixel only - join Hypixel first (Multiplayer > Featured)",
                x1 + 30, 22, on ? Theme.TEXT_DIM : Theme.WARNING, false);
        int y = 46;
        int cols = 4;
        int gap = 6;
        int bw = (x2 - x1 - gap * (cols - 1)) / cols;
        Section current = null;
        int col = 0;
        for (Object[] entry : buttons) {
            Section section = (Section) entry[0];
            if (section != current) {
                if (current != null) {
                    y += 22;
                }
                current = section;
                col = 0;
                Fonts.draw(ctx, section.title(), x1, y, Theme.accentLight(), false);
                y += 13;
            } else if (col == cols) {
                col = 0;
                y += 22;
            }
            NsButton b = (NsButton) entry[2];
            b.enabled(on);
            b.render(ctx, x1 + col * (bw + gap), y, bw, 18, mouseX, mouseY);
            col++;
        }
        lobby.enabled(on);
        int fy = height - 26;
        back.render(ctx, x1, fy, 70, 18, mouseX, mouseY);
        lobby.render(ctx, x2 - 90, fy, 90, 18, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (back.click(mouseX, mouseY, button) || lobby.click(mouseX, mouseY, button)) {
            return true;
        }
        for (Object[] entry : buttons) {
            if (((NsButton) entry[2]).click(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }
}
