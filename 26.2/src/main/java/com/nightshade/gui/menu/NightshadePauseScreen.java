package com.nightshade.gui.menu;

import com.nightshade.gui.ClickGuiScreen;
import com.nightshade.hud.HudEditorScreen;
import com.nightshade.module.interfaces.MainMenuModule;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nightshade pause menu: your character on the left, and on the right the world or server you are in, a big
 * "Back to Game", a clean list of everything the old tile menu offered, and Save and Quit. FPS, ping and coordinates
 * sit in a small chip at the bottom (each can be turned off under Client Settings -> Main Menu).
 */
public final class NightshadePauseScreen extends NsMenuScreen {
    private static final int MOD_COUNT = (int) net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream()
            .filter(m -> m.getContainingMod().isEmpty()).count();
    private static long sessionStart = System.currentTimeMillis();
    private static String sessionKey = "";
    private final Map<String, Animation> hover = new HashMap<>();
    private final List<Row> hits = new ArrayList<>();
    private long openedAt = System.nanoTime();

    private record Row(String id, String label, String detail, String[] icon, Runnable action, boolean enabled, boolean danger,
                       int[] box) {
        Row(String id, String label, String detail, String[] icon, Runnable action, boolean enabled, boolean danger) {
            this(id, label, detail, icon, action, enabled, danger, new int[4]);
        }
    }

    public NightshadePauseScreen() {
        super(Component.translatable("menu.game"));
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
        String key = minecraft == null ? "" : minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : String.valueOf(minecraft.getSingleplayerServer());
        if (!key.equals(sessionKey)) {
            sessionKey = key;
            sessionStart = System.currentTimeMillis();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) {
            super.extractBackground(ctx, mouseX, mouseY, delta);
            return;
        }
        boolean blur = com.nightshade.module.performance.MenuOptimizer.allowBlur();
        if (blur) {
            extractBlurredBackground(ctx);
        }
        // Light on the left (the player), darker under the menu column on the right.
        RenderUtil.horizontalGradient(ctx, 0, 0, width, height, ColorUtil.withAlpha(Theme.BACKGROUND, blur ? 0x30 : 0x98), ColorUtil.withAlpha(Theme.BACKGROUND, 0xE0));
        Fx.vignette(ctx, width, height, 0x90050210);
    }

    private boolean singleplayer() {
        return minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null;
    }

    private float appear(int i) {
        return Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1e9f - 0.035f * i) / 0.35f);
    }

    private List<Row> rows() {
        List<Row> list = new ArrayList<>();
        list.add(new Row("options", "Options", "", PixelIcons.MENU_OPTIONS, () -> minecraft.gui.setScreen(new NightshadeOptionsScreen(this)), true, false));
        list.add(new Row("client", "Client Settings", "Modules", PixelIcons.MENU_SETTINGS, () -> minecraft.gui.setScreen(new ClickGuiScreen(this)), true, false));
        list.add(new Row("hud", "Edit HUD", "", PixelIcons.ICON_HUD, () -> minecraft.gui.setScreen(new HudEditorScreen(this)), true, false));
        String profile = com.nightshade.NightshadeClient.client().profiles().activeServerProfile();
        list.add(new Row("profiles", "Profiles", profile == null ? "" : profile, PixelIcons.ICON_STAR,
                () -> minecraft.gui.setScreen(new ProfilesScreen(this)), true, false));
        list.add(new Row("cosmetics", "Cosmetics", "", PixelIcons.ICON_SKIN, () -> minecraft.gui.setScreen(new CosmeticsScreen(this)), true, false));
        list.add(new Row("replays", "Replays", "", PixelIcons.MENU_PLAY, () -> minecraft.gui.setScreen(new ReplaysScreen(this)), true, false));
        list.add(new Row("shots", "Screenshots", "", PixelIcons.ICON_EYE, () -> minecraft.gui.setScreen(new ScreenshotsScreen(this)), true, false));
        list.add(new Row("mods", "Mods", MOD_COUNT + " installed", PixelIcons.MENU_MODS,
                () -> minecraft.gui.setScreen(new com.nightshade.gui.mods.ModManagerScreen(this)), true, false));
        list.add(new Row("adv", "Advancements", "", PixelIcons.ICON_TROPHY,
                () -> minecraft.gui.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements(), this)), minecraft.player != null, false));
        list.add(new Row("stats", "Statistics", "", PixelIcons.ICON_STATS,
                () -> minecraft.gui.setScreen(new StatsScreen(this, minecraft.player.getStats())), minecraft.player != null, false));
        if (singleplayer()) {
            boolean published = minecraft.getSingleplayerServer().isPublished();
            list.add(new Row("lan", "Multiplayer Options", published ? "Port " + minecraft.getSingleplayerServer().getPort() : "Invite friends",
                    PixelIcons.ICON_LAN, () -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.MultiplayerOptionsScreen(this)), true, false));
        } else {
            int online = minecraft.getConnection() == null ? 0 : minecraft.getConnection().getOnlinePlayers().size();
            list.add(new Row("social", "Players", online + " online", PixelIcons.MENU_MULTIPLAYER, () -> minecraft.gui.setScreen(new SocialInteractionsScreen(this)), true, false));
        }
        return list;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        int colW = Math.min(220, Math.max(170, (int) (width * 0.36f)));
        int x2 = width - Math.max(16, (int) (width * 0.08f));
        int x1 = x2 - colW;
        boolean compact = height < 300;
        int rowH = compact ? 15 : 18;
        int resumeH = compact ? 22 : 26;
        List<Row> rows = rows();
        int headerH = 46;
        int total = headerH + resumeH + 8 + rows.size() * rowH + 9 + rowH;
        int top = Math.max(8, (height - total) / 2);

        // Character on the left.
        if (minecraft.player != null && MainMenuModule.pausePlayer() && x1 > 140) {
            int area = Math.min(x1 - 20, (int) (width * 0.45f));
            int px1 = (x1 - area) / 2;
            int size = Math.min(area / 2, (int) (height * 0.28f));
            float pa = appear(0);
            Fx.glow(ctx, px1 + area / 2f, height * 0.62f, size * 1.6f, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 0x40), pa));
            InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, px1, (int) (height * 0.18f), px1 + area, (int) (height * 0.92f), size, 0.0625f,
                    mouseX, mouseY, minecraft.player);
            String name = minecraft.player.getGameProfile().name();
            int nw = Fonts.width(name) + 12;
            int ny = (int) (height * 0.18f) - 6;
            RenderUtil.roundedRect(ctx, px1 + area / 2 - nw / 2, ny, px1 + area / 2 + nw / 2, ny + 13, 4, ColorUtil.fade(0x90000000, pa));
            Fonts.drawCentered(ctx, name, px1 + area / 2, ny + 3, ColorUtil.fade(Theme.TEXT, pa), false);
        }

        // Header: where you are.
        float a0 = appear(0);
        int lift = Math.round((1 - a0) * 8);
        String kind = singleplayer() ? "SINGLEPLAYER" : "MULTIPLAYER";
        drawSpaced(ctx, kind, x1, top + lift, ColorUtil.fade(Theme.accentLight(), a0));
        String name = singleplayer() ? minecraft.getSingleplayerServer().getWorldData().getLevelName()
                : minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().name : "Multiplayer";
        ctx.pose().pushMatrix();
        ctx.pose().translate(x1, top + 11 + lift);
        ctx.pose().scale(1.5f, 1.5f);
        Fonts.draw(ctx, Fonts.trim(name, (int) (colW / 1.5f)), 0, 0, ColorUtil.fade(0xFFFFFFFF, a0), true);
        ctx.pose().popMatrix();
        String sub;
        if (singleplayer() && minecraft.level != null && minecraft.gameMode != null) {
            String gm = minecraft.level.getLevelData().isHardcore() ? "Hardcore" : capitalise(minecraft.gameMode.getPlayerMode().getName());
            sub = gm + "  ·  " + capitalise(minecraft.level.getDifficulty().getSerializedName());
        } else {
            sub = minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "";
        }
        Fonts.draw(ctx, Fonts.trim(sub, colW), x1, top + 26 + lift, ColorUtil.fade(Theme.TEXT_MUTED, a0), false);
        long secs = (System.currentTimeMillis() - sessionStart) / 1000;
        String played = secs >= 3600 ? String.format("%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60) : String.format("%d:%02d", secs / 60, secs % 60);
        ctx.fill(x1, top + 37 + lift, x1 + 1, top + 44 + lift, ColorUtil.fade(Theme.TEXT_DIM, a0));
        ctx.fill(x1 + 3, top + 37 + lift, x1 + 4, top + 44 + lift, ColorUtil.fade(Theme.TEXT_DIM, a0));
        Fonts.draw(ctx, "Game paused  ·  " + played + " played", x1 + 8, top + 37 + lift, ColorUtil.fade(Theme.TEXT_DIM, a0), false);

        // Back to Game
        int y = top + headerH;
        float a1 = appear(1);
        boolean rh = RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + resumeH);
        float rp = hover("resume", rh);
        int ry = y + Math.round((1 - a1) * 8);
        if (rp > 0.01f) {
            Fx.glowRect(ctx, x1 - 20, ry - 12, x2 + 20, ry + resumeH + 12, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), Math.round(0x50 * rp)), a1));
        }
        RenderUtil.roundedRect(ctx, x1, ry, x2, ry + resumeH, 6, ColorUtil.fade(ColorUtil.lerp(Theme.accent(), Theme.accentLight(), rp * 0.35f), a1));
        ctx.fill(x1 + 6, ry + 1, x2 - 6, ry + 2, ColorUtil.fade(0x30FFFFFF, a1));
        Fonts.drawCentered(ctx, "Back to Game", (x1 + x2) / 2, ry + resumeH / 2 - (compact ? 4 : 7), ColorUtil.fade(0xFFFFFFFF, a1), false);
        if (!compact) {
            Fonts.drawCentered(ctx, "Esc", (x1 + x2) / 2, ry + resumeH / 2 + 3, ColorUtil.fade(0xB0FFFFFF, a1), false);
        }
        hits.add(new Row("resume", "", "", null, this::onClose, true, false, new int[]{x1, y, x2, y + resumeH}));
        y += resumeH + 8;

        // Rows
        for (int i = 0; i < rows.size(); i++) {
            drawRow(ctx, rows.get(i), x1, y, x2, rowH, mouseX, mouseY, appear(2 + i));
            y += rowH;
        }
        float ad = appear(2 + rows.size());
        ctx.fill(x1, y + 4, x2, y + 5, ColorUtil.fade(Theme.BORDER, ad));
        y += 9;
        String quit = singleplayer() ? "Save and Quit to Title" : "Disconnect";
        drawRow(ctx, new Row("quit", quit, "", PixelIcons.MENU_QUIT, this::quit, true, true), x1, y, x2, rowH, mouseX, mouseY, appear(3 + rows.size()));

        renderStats(ctx, appear(4 + rows.size()));
        com.nightshade.render.Credits.drawAt(ctx, width - com.nightshade.render.Credits.width() - 10, height - 14, appear(4 + rows.size()));
        Notifications.renderIfEnabled(ctx);
    }

    private void drawRow(GuiGraphicsExtractor ctx, Row row, int x1, int y, int x2, int h, int mouseX, int mouseY, float a) {
        boolean hovered = row.enabled() && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + h);
        float p = hover(row.id(), hovered);
        float alpha = row.enabled() ? a : a * 0.5f;
        int shift = Math.round((1 - a) * 10);
        if (p > 0.01f) {
            RenderUtil.roundedRect(ctx, x1 - 4, y, x2 + 4, y + h, 4,
                    ColorUtil.fade(ColorUtil.withAlpha(row.danger() ? Theme.DANGER : Theme.accent(), Math.round(0x38 * p)), alpha));
        }
        int iconColor = ColorUtil.fade(ColorUtil.lerp(row.danger() ? 0xFFE7A1B0 : Theme.TEXT_MUTED, 0xFFFFFFFF, p), alpha);
        PixelIcons.menu(ctx, row.icon(), x1 + 2 + shift + Math.round(p * 2), y + (h - 9) / 2, 1, iconColor,
                ColorUtil.fade(row.danger() ? 0xFFFF9DB0 : Theme.accentLight(), alpha));
        int textColor = ColorUtil.fade(ColorUtil.lerp(row.danger() ? 0xFFE7A1B0 : Theme.TEXT, 0xFFFFFFFF, p), alpha);
        Fonts.draw(ctx, row.label(), x1 + 17 + shift + Math.round(p * 2), y + (h - Fonts.height()) / 2 + 1, textColor, false);
        if (!row.detail().isEmpty()) {
            Fonts.draw(ctx, row.detail(), x2 - Fonts.width(row.detail()), y + (h - Fonts.height()) / 2 + 1, ColorUtil.fade(Theme.TEXT_DIM, alpha), false);
        }
        if (row.enabled()) {
            row.box()[0] = x1 - 4;
            row.box()[1] = y;
            row.box()[2] = x2 + 4;
            row.box()[3] = y + h;
            hits.add(row);
        }
    }

    private void renderStats(GuiGraphicsExtractor ctx, float a) {
        if (!MainMenuModule.pauseStats()) {
            return;
        }
        StringBuilder stats = new StringBuilder(minecraft.getFps() + " FPS");
        if (!singleplayer() && minecraft.getConnection() != null && minecraft.player != null) {
            var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            if (info != null) {
                stats.append("  ·  ").append(info.getLatency()).append(" ms");
            }
        }
        if (minecraft.player != null && MainMenuModule.pauseCoordinates()) {
            stats.append(String.format("  ·  %d, %d, %d", minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ()));
        }
        String s = stats.toString();
        int w = Fonts.width(s) + 16;
        int x = 12;
        int y = height - 26;
        RenderUtil.roundedRect(ctx, x, y, x + w, y + 16, 5, ColorUtil.fade(ColorUtil.withAlpha(Theme.BACKGROUND, 0xC0), a));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + 16, 5, ColorUtil.fade(Theme.BORDER, a));
        Fonts.draw(ctx, s, x + 8, y + 4, ColorUtil.fade(Theme.TEXT_MUTED, a), false);
    }

    private float hover(String id, boolean hovered) {
        return hover.computeIfAbsent(id, k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
    }

    /** Letter-spaced capitals: each letter placed from the width of the whole text before it, so the gaps stay even. */
    private static void drawSpaced(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        for (int i = 0; i < s.length(); i++) {
            Fonts.draw(ctx, String.valueOf(s.charAt(i)), x + Fonts.width(s.substring(0, i)) + i * 2, y, color, false);
        }
    }

    private static String capitalise(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void quit() {
        minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (Row row : List.copyOf(hits)) {
            int[] b = row.box();
            if (RenderUtil.isHovered(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                row.action().run();
                return true;
            }
        }
        return false;
    }
}
