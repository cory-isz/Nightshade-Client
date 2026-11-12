package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.hud.HudPresets;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleSetting;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Credits;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.MenuBackground;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * First-launch setup: how you play (modules + HUD layout), your look (theme and menu background, previewed live) and
 * a performance level. Shown once; Skip keeps everything as it is. Can be run again from Client Settings > Main Menu.
 */
public final class SetupWizardScreen extends NsMenuScreen {
    public static final String FLAG = "setupDone";
    private static final int STEPS = 4;

    private record Choice(String title, String description, net.minecraft.world.item.Item icon) {
    }

    private static final Choice[] PLAYSTYLES = {
            new Choice("PvP", "Keystrokes, CPS, target HUD, cooldowns and a clean crosshair", Items.DIAMOND_SWORD),
            new Choice("Survival", "Minimap, coordinates, biome, waypoints and light levels", Items.GRASS_BLOCK),
            new Choice("Streamer", "Big readable stats, no coordinates or server address, Discord status", Items.JUKEBOX),
            new Choice("Minimal", "Just FPS, potions and armour - out of the way", Items.FEATHER)};
    private static final Choice[] PERFORMANCE = {
            new Choice("Quality", "Everything looks like vanilla; only things you cannot see are skipped", Items.DIAMOND),
            new Choice("Balanced", "Fewer particles and distant extras - recommended", Items.GOLD_INGOT),
            new Choice("Max FPS", "Fewest particles, no clouds or rain, slower water animation", Items.REDSTONE)};
    private static final String[] BACKGROUNDS = {"Aurora Peaks", "Night Forest", "Deep Space", "Nightshade Sky"};

    private int step;
    private int playstyle = 0;
    private int performance = 1;
    private final int[][] optionRects = new int[8][4];
    private final int[][] swatchRects = new int[16][4];
    private final int[][] backgroundRects = new int[BACKGROUNDS.length][4];
    private final NsButton next = new NsButton("Next", NsButton.Style.PRIMARY, this::next);
    private final NsButton back = new NsButton("Back", NsButton.Style.SECONDARY, () -> step = Math.max(0, step - 1));
    private final NsButton skip = new NsButton("Skip setup", NsButton.Style.GHOST, this::finishSkipped);

    public SetupWizardScreen() {
        super(Component.literal("Nightshade Setup"));
    }

    // ------------------------------------------------------------------ settings helpers

    private static Module module(String name) {
        for (Module m : NightshadeClient.client().modules().getModules()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    private static ModuleSetting<?> setting(String moduleName, String settingName) {
        Module m = module(moduleName);
        if (m != null) {
            for (ModuleSetting<?> s : m.getSettings()) {
                if (s.getName().equals(settingName)) {
                    return s;
                }
            }
        }
        return null;
    }

    private static void enable(String name, boolean on) {
        Module m = module(name);
        if (m != null && m.isToggleable() && m.isEnabled() != on) {
            NightshadeClient.client().modules().setEnabled(m, on);
        }
    }

    private static String[] themes() {
        return MenuBackground.Palette.labels();
    }

    private static void setTheme(String label) {
        if (setting("ClickGUI", "Theme") instanceof ModeSetting mode) {
            mode.set(label);
        }
    }

    private static String currentTheme() {
        return setting("ClickGUI", "Theme") instanceof ModeSetting mode ? mode.get() : "";
    }

    private static void setBackground(String label) {
        if (setting("Main Menu", "Background") instanceof ModeSetting mode) {
            mode.set(label);
        }
    }

    private static String currentBackground() {
        return setting("Main Menu", "Background") instanceof ModeSetting mode ? mode.get() : "";
    }

    // ------------------------------------------------------------------ applying

    /** Turns on the modules for a playstyle and applies its HUD layout. */
    public static void applyPlaystyle(int index) {
        String name = PLAYSTYLES[index].title();
        HudPresets.apply(name);
        switch (name) {
            case "PvP" -> {
                for (String m : new String[]{"Custom Crosshair", "Hit Color", "Toggle Sprint", "Totem Pop Counter", "Shield Status"}) {
                    enable(m, true);
                }
            }
            case "Survival" -> {
                for (String m : new String[]{"Minimap", "Waypoints", "Durability Warning"}) {
                    enable(m, true);
                }
            }
            case "Streamer" -> enable("Discord Rich Presence", true);
            default -> {
            }
        }
    }

    /** Sets the performance modules for a level: 0 Quality, 1 Balanced, 2 Max FPS. */
    public static void applyPerformance(int level) {
        enable("Entity Culling", true);
        enable("Block Entity Culling", true);
        enable("Shadow Culling", true);
        enable("Particle Optimizer", level >= 1);
        enable("Entity Render Limiter", level >= 1);
        enable("Weather & Clouds", level >= 2);
        enable("Animation Optimizer", level >= 2);
        if (setting("Particle Optimizer", "Keep") instanceof NumberSetting keep) {
            keep.set(level >= 2 ? 25.0 : 60.0);
        }
        if (setting("Particle Optimizer", "Mode") instanceof ModeSetting mode) {
            mode.set("Reduce");
        }
        if (setting("Menu Optimizer", "Skip Blur") instanceof BooleanSetting blur) {
            blur.set(level >= 2);
        }
    }

    private void next() {
        if (step < STEPS - 1) {
            step++;
        } else {
            finish();
        }
    }

    /** Applies the choices, remembers that setup is done and goes to the main menu. */
    public void finish() {
        applyPlaystyle(playstyle);
        applyPerformance(performance);
        done();
        NightshadeClient.client().notifications().success("Nightshade", "All set - press Right Shift any time to change things");
    }

    private void finishSkipped() {
        done();
    }

    private void done() {
        var config = NightshadeClient.client().config();
        config.setFlag(FLAG);
        config.saveDirty();
        minecraft.gui.setScreen(com.nightshade.module.interfaces.MainMenuModule.createMenu());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int w = Math.min(width - 24, 600);
        int h = Math.min(height - 24, 330);
        int x1 = (width - w) / 2;
        int y1 = (height - h) / 2;
        int x2 = x1 + w;
        int y2 = y1 + h;
        Fx.shadow(ctx, x1, y1 + 4, x2, y2 + 4, 14, 0xA0000000);
        RenderUtil.roundedRect(ctx, x1, y1, x2, y2, 10, ColorUtil.withAlpha(Theme.BACKGROUND, 0xDC));
        RenderUtil.roundedOutline(ctx, x1, y1, x2, y2, 10, ColorUtil.withAlpha(Theme.accent(), 0x90));

        // header: step dots and title
        String[] titles = {"How do you play?", "Pick your look", "Performance", "You're all set"};
        String[] subs = {"We'll turn on the right modules and lay out your HUD. You can change anything later.",
                "Your client colour recolours the menus, HUD and notifications. Click to preview.",
                "Choose how much to trade looks for frames. Entity culling is always on - it only skips what you can't see.",
                "Here's what we set up. Press Right Shift in game for every module and setting."};
        Fonts.draw(ctx, "WELCOME TO NIGHTSHADE", x1 + 18, y1 + 14, Theme.accentLight(), false);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x1 + 18, y1 + 26);
        ctx.pose().scale(1.6f, 1.6f);
        Fonts.draw(ctx, titles[step], 0, 0, 0xFFFFFFFF, false);
        ctx.pose().popMatrix();
        int sy = y1 + 46;
        for (FormattedCharSequence line : font.split(Fonts.text(subs[step]), w - 36)) {
            ctx.text(font, line, x1 + 18, sy, Theme.TEXT_MUTED, false);
            sy += Fonts.height() + 2;
        }
        for (int i = 0; i < STEPS; i++) {
            int dx = x2 - 18 - (STEPS - i) * 14;
            RenderUtil.roundedRect(ctx, dx, y1 + 16, dx + (i == step ? 18 : 8), y1 + 22, 3, i <= step ? Theme.accent() : 0x40FFFFFF);
        }

        int top = y1 + 78;
        int bottom = y2 - 40;
        switch (step) {
            case 0 -> renderOptions(ctx, PLAYSTYLES, playstyle, x1 + 18, top, x2 - 18, bottom, mouseX, mouseY);
            case 1 -> renderLook(ctx, x1 + 18, top, x2 - 18, mouseX, mouseY);
            case 2 -> renderOptions(ctx, PERFORMANCE, performance, x1 + 18, top, x2 - 18, bottom, mouseX, mouseY);
            default -> renderSummary(ctx, x1 + 18, top, x2 - 18);
        }

        // footer
        int fy = y2 - 30;
        skip.render(ctx, x1 + 18, fy, 84, 18, mouseX, mouseY);
        next.label(step == STEPS - 1 ? "Let's go" : "Next");
        next.render(ctx, x2 - 18 - 90, fy, 90, 18, mouseX, mouseY);
        if (step > 0) {
            back.render(ctx, x2 - 18 - 90 - 6 - 70, fy, 70, 18, mouseX, mouseY);
        }
        Credits.draw(ctx, width / 2, y2 + 6, 0.6f);
        Notifications.renderIfEnabled(ctx);
    }

    private void renderOptions(GuiGraphicsExtractor ctx, Choice[] choices, int selected, int x1, int top, int x2, int bottom, int mouseX, int mouseY) {
        int n = choices.length;
        int gap = 10;
        int cw = (x2 - x1 - gap * (n - 1)) / n;
        int ch = Math.min(bottom - top, 150);
        for (int i = 0; i < n; i++) {
            int x = x1 + i * (cw + gap);
            optionRects[i] = new int[]{x, top, x + cw, top + ch};
            boolean sel = i == selected;
            boolean hov = RenderUtil.isHovered(mouseX, mouseY, x, top, x + cw, top + ch);
            if (sel) {
                Fx.glowRect(ctx, x - 10, top - 8, x + cw + 10, top + ch + 8, ColorUtil.withAlpha(Theme.accent(), 0x40));
            }
            RenderUtil.roundedRect(ctx, x, top, x + cw, top + ch, 8, sel ? ColorUtil.withAlpha(Theme.accent(), 0x45) : hov ? 0x30FFFFFF : 0x22000000);
            RenderUtil.roundedOutline(ctx, x, top, x + cw, top + ch, 8, sel ? Theme.accentLight() : hov ? ColorUtil.withAlpha(Theme.accent(), 0x90) : 0x1CFFFFFF);
            // big icon
            ctx.pose().pushMatrix();
            ctx.pose().translate(x + cw / 2f - 16, top + 16);
            ctx.pose().scale(2f, 2f);
            if (!com.nightshade.gui.ModuleIcons.drawItem(ctx, choices[i].icon(), 0, 0)) {
                Fonts.drawCentered(ctx, choices[i].title().substring(0, 1), 8, 4, Theme.accentLight(), false);
            }
            ctx.pose().popMatrix();
            Fonts.drawCentered(ctx, choices[i].title(), x + cw / 2, top + 56, sel ? 0xFFFFFFFF : Theme.TEXT, false);
            int ly = top + 72;
            for (FormattedCharSequence line : font.split(Fonts.text(choices[i].description()), cw - 16)) {
                ctx.text(font, line, x + cw / 2 - font.width(line) / 2, ly, Theme.TEXT_MUTED, false);
                ly += Fonts.height() + 2;
            }
            if (choices == PERFORMANCE && i == 1) {
                RenderUtil.roundedRect(ctx, x + cw / 2 - 34, top + ch - 18, x + cw / 2 + 34, top + ch - 6, 4, ColorUtil.withAlpha(Theme.SUCCESS, 0x40));
                Fonts.drawCentered(ctx, "Recommended", x + cw / 2, top + ch - 16, Theme.SUCCESS, false);
            }
        }
    }

    private void renderLook(GuiGraphicsExtractor ctx, int x1, int top, int x2, int mouseX, int mouseY) {
        Fonts.draw(ctx, "Client colour", x1, top, Theme.TEXT, false);
        String[] themes = themes();
        String current = currentTheme();
        int size = 26;
        int gap = 8;
        int y = top + 14;
        for (int i = 0; i < themes.length && i < swatchRects.length; i++) {
            int x = x1 + i * (size + gap);
            if (x + size > x2) {
                break;
            }
            int color = MenuBackground.accentOf(MenuBackground.Palette.byLabel(themes[i]));
            swatchRects[i] = new int[]{x, y, x + size, y + size};
            boolean sel = themes[i].equals(current);
            if (sel) {
                RenderUtil.roundedRect(ctx, x - 3, y - 3, x + size + 3, y + size + 3, 8, 0xFFFFFFFF);
            }
            RenderUtil.roundedRect(ctx, x, y, x + size, y + size, 7, color);
            if (RenderUtil.isHovered(mouseX, mouseY, x, y, x + size, y + size)) {
                Fonts.drawCentered(ctx, themes[i], x + size / 2, y + size + 5, Theme.TEXT_MUTED, false);
            }
        }
        Fonts.draw(ctx, "Theme: " + current, x1, y + size + 20, Theme.accentLight(), false);

        int by = y + size + 42;
        Fonts.draw(ctx, "Menu background", x1, by, Theme.TEXT, false);
        by += 14;
        String bg = currentBackground();
        int bw = (x2 - x1 - 3 * 8) / 4;
        for (int i = 0; i < BACKGROUNDS.length; i++) {
            int x = x1 + i * (bw + 8);
            backgroundRects[i] = new int[]{x, by, x + bw, by + 26};
            boolean sel = BACKGROUNDS[i].equals(bg);
            boolean hov = RenderUtil.isHovered(mouseX, mouseY, x, by, x + bw, by + 26);
            RenderUtil.roundedRect(ctx, x, by, x + bw, by + 26, 6, sel ? ColorUtil.withAlpha(Theme.accent(), 0x55) : hov ? 0x30FFFFFF : 0x22000000);
            RenderUtil.roundedOutline(ctx, x, by, x + bw, by + 26, 6, sel ? Theme.accentLight() : 0x1CFFFFFF);
            Fonts.drawCentered(ctx, BACKGROUNDS[i], x + bw / 2, by + 9, sel ? 0xFFFFFFFF : Theme.TEXT_MUTED, false);
        }
    }

    private void renderSummary(GuiGraphicsExtractor ctx, int x1, int top, int x2) {
        String[][] rows = {
                {"Playstyle", PLAYSTYLES[playstyle].title() + " - " + PLAYSTYLES[playstyle].description()},
                {"Look", currentTheme() + " theme, " + currentBackground() + " background"},
                {"Performance", PERFORMANCE[performance].title()},
                {"Right Shift", "Every module and setting"},
                {"M", "Full-screen world map"},
                {"Edit HUD", "Move and resize anything, or pick another layout"}};
        int y = top;
        for (String[] row : rows) {
            RenderUtil.roundedRect(ctx, x1, y, x2, y + 20, 5, 0x22000000);
            Fonts.draw(ctx, row[0], x1 + 8, y + 6, Theme.accentLight(), false);
            Fonts.draw(ctx, Fonts.trim(row[1], x2 - x1 - 110), x1 + 100, y + 6, Theme.TEXT, false);
            y += 24;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (next.click(mouseX, mouseY, button) || skip.click(mouseX, mouseY, button) || (step > 0 && back.click(mouseX, mouseY, button))) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (step == 0 || step == 2) {
            int n = step == 0 ? PLAYSTYLES.length : PERFORMANCE.length;
            for (int i = 0; i < n; i++) {
                int[] r = optionRects[i];
                if (RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    if (step == 0) {
                        playstyle = i;
                    } else {
                        performance = i;
                    }
                    return true;
                }
            }
        } else if (step == 1) {
            String[] themes = themes();
            for (int i = 0; i < themes.length && i < swatchRects.length; i++) {
                int[] r = swatchRects[i];
                if (r[2] > r[0] && RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    setTheme(themes[i]);
                    return true;
                }
            }
            for (int i = 0; i < BACKGROUNDS.length; i++) {
                int[] r = backgroundRects[i];
                if (RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    setBackground(BACKGROUNDS[i]);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Skip is the way out, so nothing is half applied
    }

    // ------------------------------------------------------------------ test hooks

    public void chooseForTest(int playstyle, int performance, int step) {
        this.playstyle = playstyle;
        this.performance = performance;
        this.step = step;
    }
}
