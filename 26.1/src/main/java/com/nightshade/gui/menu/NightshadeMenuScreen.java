package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.account.Account;
import com.nightshade.account.AccountManager;
import com.nightshade.gui.ClickGuiScreen;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.mods.ModManagerScreen;
import com.nightshade.module.interfaces.MainMenuModule;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.BrandLogo;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Lang;
import com.nightshade.util.LastPlayed;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nightshade main menu ("Modern" style): a slim top bar (brand, sections, account), a greeting on the left and a play
 * panel on the right that lists your most recent worlds or servers so you can jump straight back in. Every element
 * opens something real; the two older menus stay available under Client Settings -> Main Menu -> Menu Style.
 */
public final class NightshadeMenuScreen extends NsMenuScreen {
    /** Set when the user explicitly asks for the vanilla title screen, so the replacement mixin lets it through once. */
    public static boolean vanillaRequested;

    private static final String HINT_FLAG = "modernMenuHintDone";
    private static final int BAR_H = 26;
    private static final int MAX_ROWS = 3;

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Animation> hover = new HashMap<>();
    private final Animation tabSlide = new Animation(14f, 0f);
    private final int modCount;
    private long openedAt = System.nanoTime();

    /** 0 = singleplayer, 1 = multiplayer. */
    private int mode;
    private final List<LevelSummary> worlds = new ArrayList<>();
    private final List<ServerData> servers = new ArrayList<>();
    private int worldTotal;
    private int serverTotal;
    private boolean worldsLoading = true;
    private int selectedRow;
    private long lastRowClick;
    private int lastRowIndex = -1;
    private LastPlayed.Entry lastPlayed;
    private final Map<String, net.minecraft.client.gui.screens.FaviconTexture> icons = new HashMap<>();

    // Rectangles remembered for the tour highlight.
    private int panelX1, panelY1, panelX2, panelY2;
    private int accountX1, accountY1, accountX2, accountY2;
    private int modsX1, modsY1, modsX2, modsY2;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
    }

    private record Tab(String id, String label, Runnable action) {
    }

    public NightshadeMenuScreen() {
        super(Component.literal("Nightshade Client"));
        modCount = (int) FabricLoader.getInstance().getAllMods().stream().filter(m -> m.getContainingMod().isEmpty()).count();
    }

    private AccountManager accounts() {
        return NightshadeClient.client().accounts();
    }

    @Override
    protected void init() {
        if (MainMenuModule.checkUpdates()) {
            com.nightshade.update.UpdateChecker.checkOnce(MainMenuModule.includeBetas());
        }
        lastPlayed = LastPlayed.get();
        loadServers();
        loadWorlds();
        if (lastPlayed != null && lastPlayed.kind() == LastPlayed.Kind.SERVER) {
            mode = 1;
            tabSlide.snap(1f);
        }
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
    }

    @org.jetbrains.annotations.Nullable
    private net.minecraft.resources.Identifier worldIcon(LevelSummary world) {
        String key = "w:" + world.getLevelId();
        net.minecraft.client.gui.screens.FaviconTexture icon = icons.get(key);
        if (icon != null) {
            return icon.isClosed() ? null : icon.textureLocation();
        }
        icon = net.minecraft.client.gui.screens.FaviconTexture.forWorld(minecraft.getTextureManager(), world.getLevelId());
        icons.put(key, icon);
        try {
            if (world.getIcon() != null && java.nio.file.Files.isRegularFile(world.getIcon())) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(world.getIcon())) {
                    icon.upload(com.mojang.blaze3d.platform.NativeImage.read(in));
                }
                return icon.textureLocation();
            }
        } catch (Exception ignored) {
            // no icon: the row shows the generic one
        }
        icon.close();
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private net.minecraft.resources.Identifier serverIcon(ServerData data) {
        byte[] bytes = data.getIconBytes();
        if (bytes == null) {
            return null;
        }
        String key = "s:" + data.ip;
        net.minecraft.client.gui.screens.FaviconTexture icon = icons.get(key);
        if (icon == null) {
            icon = net.minecraft.client.gui.screens.FaviconTexture.forServer(minecraft.getTextureManager(), data.ip);
            icons.put(key, icon);
            try {
                icon.upload(com.mojang.blaze3d.platform.NativeImage.read(bytes));
            } catch (Exception e) {
                icon.close();
                return null;
            }
        }
        return icon.isClosed() ? null : icon.textureLocation();
    }

    @Override
    public void removed() {
        icons.values().forEach(net.minecraft.client.gui.screens.FaviconTexture::close);
        icons.clear();
        super.removed();
    }

    private void loadServers() {
        servers.clear();
        try {
            ServerList list = new ServerList(minecraft);
            list.load();
            serverTotal = list.size();
            List<ServerData> all = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                all.add(list.get(i));
            }
            // The server you played last goes first.
            if (lastPlayed != null && lastPlayed.kind() == LastPlayed.Kind.SERVER) {
                all.sort((a, b) -> Boolean.compare(b.ip.equalsIgnoreCase(lastPlayed.id()), a.ip.equalsIgnoreCase(lastPlayed.id())));
            }
            servers.addAll(all.subList(0, Math.min(MAX_ROWS, all.size())));
        } catch (Exception e) {
            serverTotal = 0;
        }
    }

    private void loadWorlds() {
        worldsLoading = true;
        LevelStorageSource source = minecraft.getLevelSource();
        try {
            source.loadLevelSummaries(source.findLevelCandidates()).whenComplete((list, failure) -> minecraft.execute(() -> {
                worldsLoading = false;
                worlds.clear();
                if (failure != null || list == null) {
                    worldTotal = 0;
                    return;
                }
                List<LevelSummary> sorted = new ArrayList<>(list);
                sorted.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
                worldTotal = sorted.size();
                worlds.addAll(sorted.subList(0, Math.min(MAX_ROWS, sorted.size())));
            }));
        } catch (Exception e) {
            worldsLoading = false;
            worldTotal = 0;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private float elapsed() {
        return (System.nanoTime() - openedAt) / 1_000_000_000f;
    }

    private float appear(int i) {
        return Fx.easeOutCubic((elapsed() - 0.05f * i) / 0.45f);
    }

    private float hover(String id, boolean hovered) {
        return hover.computeIfAbsent(id, k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
    }

    private boolean blocked() {
        return tourActive();
    }

    private boolean over(int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
        return !blocked() && RenderUtil.isHovered(mouseX, mouseY, x1, y1, x2, y2);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        String tooltip = renderTopBar(ctx, mouseX, mouseY, appear(0));
        renderGreeting(ctx, appear(1));
        renderPlayPanel(ctx, mouseX, mouseY, appear(2));
        renderFooter(ctx, appear(3));
        if (!tourActive()) {
            renderNotices(ctx, mouseX, mouseY, appear(4));
        }
        if (tourActive()) {
            renderTour(ctx, mouseX, mouseY);
        }
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null && !tourActive()) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    /** Glass panel used across the menu. */
    private static void glass(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int radius, float a) {
        Fx.shadow(ctx, x1, y1 + 2, x2, y2 + 2, 8, ColorUtil.fade(0x90000000, a));
        RenderUtil.roundedRect(ctx, x1, y1, x2, y2, radius, ColorUtil.fade(ColorUtil.withAlpha(Theme.BACKGROUND, 0xD0), a));
        ctx.fill(x1 + radius, y1 + 1, x2 - radius, y1 + 2, ColorUtil.fade(0x14FFFFFF, a));
        RenderUtil.roundedOutline(ctx, x1, y1, x2, y2, radius, ColorUtil.fade(Theme.BORDER, a));
        int mid = (x1 + x2) / 2;
        RenderUtil.horizontalGradient(ctx, x1 + radius, y1, mid, y1 + 1, 0x00000000, ColorUtil.fade(ColorUtil.withAlpha(Theme.accentLight(), 0xB0), a));
        RenderUtil.horizontalGradient(ctx, mid, y1, x2 - radius, y1 + 1, ColorUtil.fade(ColorUtil.withAlpha(Theme.accentLight(), 0xB0), a), 0x00000000);
    }

    private String renderTopBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float a) {
        String tooltip = null;
        int lift = Math.round((1f - a) * -8f);
        // Soft dark band that fades into the background.
        Fx.verticalGradient(ctx, 0, 0, width, BAR_H + 14, ColorUtil.fade(ColorUtil.withAlpha(Theme.BACKGROUND, 0xC8), a), 0x00000000);
        int cy = BAR_H / 2 + lift;

        // Brand
        boolean narrow = width < 430;
        BrandLogo.draw(ctx, 10, cy - 7, 14, ColorUtil.fade(0xFFFFFFFF, a));
        if (!narrow) {
            drawSpaced(ctx, "NIGHTSHADE", 30, cy - 3, ColorUtil.fade(Theme.TEXT, a));
        }

        // Sections: each opens a real screen.
        List<Tab> tabs = List.of(
                new Tab("play", "Play", () -> { }),
                new Tab("mods", "Mods", () -> minecraft.setScreen(new ModManagerScreen(this))),
                new Tab("client", "Client", () -> minecraft.setScreen(new ClickGuiScreen(this))),
                new Tab("cosmetics", "Cosmetics", () -> minecraft.setScreen(new CosmeticsScreen(this))),
                new Tab("shots", "Screenshots", () -> minecraft.setScreen(new ScreenshotsScreen(this))),
                new Tab("options", "Options", () -> minecraft.setScreen(new NightshadeOptionsScreen(this))));
        int gap = narrow ? 10 : 16;
        int total = -gap;
        for (Tab tab : tabs) {
            total += Fonts.width(tab.label()) + gap;
        }
        int tx = narrow ? 30 : (width - total) / 2;
        for (Tab tab : tabs) {
            int w = Fonts.width(tab.label());
            boolean active = tab.id().equals("play");
            boolean hovered = !active && over(mouseX, mouseY, tx - 4, 0, tx + w + 4, BAR_H);
            float p = hover("tab." + tab.id(), hovered);
            int color = active ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, p);
            Fonts.draw(ctx, tab.label(), tx, cy - 3, ColorUtil.fade(color, a), false);
            if (active) {
                RenderUtil.roundedRect(ctx, tx + w / 2 - 6, cy + 7, tx + w / 2 + 6, cy + 9, 1, ColorUtil.fade(Theme.accent(), a));
            } else if (p > 0.01f) {
                int half = Math.round(6 * p);
                ctx.fill(tx + w / 2 - half, cy + 7, tx + w / 2 + half, cy + 8, ColorUtil.fade(ColorUtil.withAlpha(Theme.accentLight(), Math.round(0xA0 * p)), a));
            }
            if (!active) {
                hits.add(new Hit(tx - 4, 0, tx + w + 4, BAR_H, tab.action()));
            }
            if (tab.id().equals("mods")) {
                modsX1 = tx - 4;
                modsY1 = 2;
                modsX2 = tx + w + 4;
                modsY2 = BAR_H - 2;
            }
            tx += w + gap;
        }

        // Quit (x) on the far right, account chip beside it.
        int qx2 = width - 8;
        int qx1 = qx2 - 16;
        boolean qh = over(mouseX, mouseY, qx1, cy - 8, qx2, cy + 8);
        float qp = hover("quit", qh);
        if (qp > 0.01f) {
            RenderUtil.roundedRect(ctx, qx1, cy - 8, qx2, cy + 8, 4, ColorUtil.fade(ColorUtil.withAlpha(Theme.DANGER, Math.round(0x50 * qp)), a));
        }
        PixelIcons.menu(ctx, PixelIcons.MENU_QUIT, qx1 + 4, cy - 4, 1, ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, qp), a), 0);
        hits.add(new Hit(qx1, cy - 8, qx2, cy + 8, minecraft::stop));
        if (qh) {
            tooltip = Lang.t("menu.quit");
        }

        Account account = accounts().active();
        String name = Fonts.trim(account.name(), 90);
        int chipW = 16 + 6 + Fonts.width(name) + 8;
        accountX2 = qx1 - 6;
        accountX1 = accountX2 - chipW;
        accountY1 = cy - 9;
        accountY2 = cy + 9;
        boolean ah = over(mouseX, mouseY, accountX1, accountY1, accountX2, accountY2);
        float ap = hover("account", ah);
        RenderUtil.roundedRect(ctx, accountX1, accountY1, accountX2, accountY2, 9,
                ColorUtil.fade(ColorUtil.lerp(0x50000000, ColorUtil.withAlpha(Theme.accent(), 0x60), ap), a));
        RenderUtil.roundedOutline(ctx, accountX1, accountY1, accountX2, accountY2, 9, ColorUtil.fade(ColorUtil.lerp(Theme.BORDER, ColorUtil.withAlpha(Theme.accent(), 0xC0), ap), a));
        PlayerFaceExtractor.extractRenderState(ctx, accounts().skin(account), accountX1 + 2, accountY1 + 2, 14);
        Fonts.draw(ctx, name, accountX1 + 22, cy - 3, ColorUtil.fade(Theme.TEXT, a), false);
        hits.add(new Hit(accountX1, accountY1, accountX2, accountY2, () -> minecraft.setScreen(new AccountsScreen(this))));
        if (ah) {
            tooltip = account.type().label() + " account - " + (accounts().isSwitching() ? Lang.t("menu.switching") : Lang.t("menu.switch"));
        }
        return tooltip;
    }

    private static void drawSpaced(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            Fonts.draw(ctx, ch, x, y, color, false);
            x += Fonts.width(ch) + 1;
        }
    }

    private void renderGreeting(GuiGraphicsExtractor ctx, float a) {
        int hour = LocalTime.now().getHour();
        String key = hour < 5 ? "night" : hour < 12 ? "morning" : hour < 18 ? "afternoon" : hour < 22 ? "evening" : "night";
        int panelW = panelWidth();
        int maxW = width - panelW - 50;
        if (maxW < 120) {
            greetingBottom = BAR_H;
            return; // not enough room beside the play panel
        }
        int x = 22 - Math.round((1f - a) * 12f);
        boolean hint = noticeShown();
        int y = Math.max(BAR_H + 18, hint ? Math.min(height / 2 - 40, height - 22 - 66 - 8 - 75) : height / 2 - 40);
        Fonts.draw(ctx, Lang.t("menu.greeting." + key).toUpperCase(java.util.Locale.ROOT), x, y, ColorUtil.fade(Theme.accentLight(), a), false);
        float scale = maxW > 220 ? 2.5f : 2f;
        String name = accounts().active().name();
        while (name.length() > 3 && Fonts.width(name) * scale > maxW) {
            name = name.substring(0, name.length() - 1);
        }
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y + 13);
        ctx.pose().scale(scale, scale);
        Fonts.draw(ctx, name, 0, 0, ColorUtil.fade(0xFFFFFFFF, a), true);
        ctx.pose().popMatrix();
        int ly = y + 13 + Math.round(Fonts.height() * scale) + 6;
        Fonts.draw(ctx, "Nightshade v" + NightshadeClient.VERSION, x, ly, ColorUtil.fade(Theme.TEXT_MUTED, a), false);
        String what = lastPlayed == null ? "Pick a world or server to start playing."
                : "Last played " + Fonts.trim(lastPlayed.name(), Math.max(40, maxW - 90)) + " " + ago(lastPlayed.time()) + ".";
        Fonts.draw(ctx, what, x, ly + 12, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        greetingBottom = ly + 22;
    }

    private int greetingBottom = BAR_H;

    private int panelWidth() {
        return Math.max(170, Math.min(236, (int) (width * 0.4f)));
    }

    private void renderPlayPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float a) {
        boolean sp = mode == 0;
        int rowH = 24;
        int w = panelWidth();
        int pad = 8;
        int rows = Math.max(1, sp ? worlds.size() : servers.size());
        int h = pad + 18 + 8 + rows * (rowH + 3) - 3 + 8 + 24 + 6 + 10 + pad;
        int x2 = width - 14 + Math.round((1f - a) * 16f);
        int x1 = x2 - w;
        int y2 = height - 24;
        int y1 = Math.max(BAR_H + 10, y2 - h);
        panelX1 = x1;
        panelY1 = y1;
        panelX2 = x2;
        panelY2 = y2;
        glass(ctx, x1, y1, x2, y2, 8, a);

        // Segmented control
        int sx1 = x1 + pad;
        int sx2 = x2 - pad;
        int sy = y1 + pad;
        int half = (sx2 - sx1) / 2;
        RenderUtil.roundedRect(ctx, sx1, sy, sx2, sy + 18, 5, ColorUtil.fade(0x60000000, a));
        float slide = tabSlide.setTarget(mode).update();
        int kx = sx1 + 2 + Math.round(slide * (half - 2));
        RenderUtil.roundedRect(ctx, kx, sy + 2, kx + half - 2, sy + 16, 4, ColorUtil.fade(Theme.accent(), a));
        String[] labels = {"Singleplayer", "Multiplayer"};
        for (int i = 0; i < 2; i++) {
            int cx = sx1 + half * i + half / 2;
            boolean hovered = mode != i && over(mouseX, mouseY, sx1 + half * i, sy, sx1 + half * (i + 1), sy + 18);
            float p = hover("seg" + i, hovered);
            int color = mode == i ? 0xFFFFFFFF : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, p);
            Fonts.drawCentered(ctx, Fonts.trim(labels[i], half - 6), cx, sy + 5, ColorUtil.fade(color, a), false);
            int idx = i;
            hits.add(new Hit(sx1 + half * i, sy, sx1 + half * (i + 1), sy + 18, () -> selectMode(idx)));
        }

        // Recent entries
        int ry = sy + 18 + 8;
        int count = sp ? worlds.size() : servers.size();
        if (count == 0) {
            int ey = ry;
            RenderUtil.roundedRect(ctx, sx1, ey, sx1 + rowH, ey + rowH, 5, ColorUtil.fade(0x40FFFFFF & 0x30FFFFFF, a));
            PixelIcons.menu(ctx, sp ? PixelIcons.MENU_SINGLEPLAYER : PixelIcons.MENU_MULTIPLAYER, sx1 + 3, ey + 3, 2,
                    ColorUtil.fade(Theme.TEXT_MUTED, a), ColorUtil.fade(Theme.accentLight(), a));
            String title = sp ? (worldsLoading ? "Loading worlds..." : "No worlds yet") : "No servers yet";
            String sub = sp ? (worldsLoading ? "" : "Create one to start playing") : "Add one to start playing";
            Fonts.draw(ctx, title, sx1 + rowH + 8, ey + 3, ColorUtil.fade(Theme.TEXT, a), false);
            Fonts.draw(ctx, sub, sx1 + rowH + 8, ey + 14, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        }
        selectedRow = Math.min(selectedRow, Math.max(0, count - 1));
        for (int i = 0; i < count; i++) {
            int y = ry + i * (rowH + 3);
            boolean selected = i == selectedRow;
            boolean hovered = over(mouseX, mouseY, sx1, y, sx2, y + rowH);
            float p = hover("row" + mode + "." + i, hovered || selected);
            RenderUtil.roundedRect(ctx, sx1, y, sx2, y + rowH, 5, ColorUtil.fade(ColorUtil.lerp(0x00000000, selected ? ColorUtil.withAlpha(Theme.accent(), 0x40) : 0x22FFFFFF, p), a));
            if (selected) {
                RenderUtil.roundedOutline(ctx, sx1, y, sx2, y + rowH, 5, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 0xB0), a));
            }
            net.minecraft.resources.Identifier iconTex = sp ? worldIcon(worlds.get(i)) : serverIcon(servers.get(i));
            if (iconTex != null) {
                int is = rowH - 6;
                ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, iconTex, sx1 + 3, y + 3, 0f, 0f, is, is, 64, 64, 64, 64,
                        ColorUtil.fade(0xFFFFFFFF, a));
                RenderUtil.roundedOutline(ctx, sx1 + 3, y + 3, sx1 + 3 + is, y + 3 + is, 3, ColorUtil.fade(0x30FFFFFF, a));
            } else {
                RenderUtil.roundedRect(ctx, sx1 + 3, y + 3, sx1 + rowH - 3, y + rowH - 3, 4, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 0x50), a));
                PixelIcons.menu(ctx, sp ? PixelIcons.MENU_SINGLEPLAYER : PixelIcons.MENU_MULTIPLAYER, sx1 + (rowH - 9) / 2, y + (rowH - 9) / 2, 1,
                        ColorUtil.fade(0xFFE9E4FF, a), ColorUtil.fade(Theme.accentLight(), a));
            }
            String title;
            String sub;
            if (sp) {
                LevelSummary wld = worlds.get(i);
                title = wld.getLevelName();
                String gm = wld.isHardcore() ? "Hardcore" : capitalise(wld.getGameMode().getName());
                sub = gm + "  ·  " + ago(wld.getLastPlayed());
            } else {
                ServerData srv = servers.get(i);
                title = srv.name;
                sub = srv.ip;
            }
            int tx = sx1 + rowH + 5;
            Fonts.draw(ctx, Fonts.trim(title, sx2 - tx - 16), tx, y + 4, ColorUtil.fade(Theme.TEXT, a), false);
            Fonts.draw(ctx, Fonts.trim(sub, sx2 - tx - 16), tx, y + 14, ColorUtil.fade(Theme.TEXT_DIM, a), false);
            if (p > 0.02f) {
                PixelIcons.menu(ctx, PixelIcons.MENU_PLAY, sx2 - 13, y + (rowH - 9) / 2, 1,
                        ColorUtil.fade(ColorUtil.withAlpha(Theme.accentLight(), Math.round(255 * p)), a), 0);
            }
            int idx = i;
            hits.add(new Hit(sx1, y, sx2, y + rowH, () -> clickRow(idx)));
        }

        // Primary action
        int by = ry + rows * (rowH + 3) - 3 + 8;
        String label;
        Runnable action;
        if (count > 0) {
            String target = sp ? worlds.get(selectedRow).getLevelName() : servers.get(selectedRow).name;
            label = (sp ? "Play " : "Join ") + target;
            action = this::playSelected;
        } else {
            label = sp ? "Choose a world" : "Choose a server";
            action = sp ? this::openWorlds : this::openServers;
        }
        boolean bh = over(mouseX, mouseY, sx1, by, sx2, by + 24);
        float bp = hover("primary", bh);
        if (bp > 0.01f) {
            Fx.glowRect(ctx, sx1 - 18, by - 10, sx2 + 18, by + 34, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), Math.round(0x50 * bp)), a));
        }
        RenderUtil.roundedRect(ctx, sx1, by, sx2, by + 24, 6, ColorUtil.fade(ColorUtil.lerp(Theme.accent(), Theme.accentLight(), bp * 0.35f), a));
        // Lighter right half and a slow light sweep across the button.
        RenderUtil.horizontalGradient(ctx, sx1 + 6, by, sx2 - 6, by + 24, 0x00FFFFFF, ColorUtil.fade(0x26FFFFFF, a));
        float sweep = (Fx.time() * 0.35f) % 1.6f;
        int swx = sx1 + Math.round((sx2 - sx1 + 40) * sweep) - 40;
        int s1 = Math.max(sx1 + 4, swx);
        int s2 = Math.min(sx2 - 4, swx + 40);
        if (s2 > s1) {
            RenderUtil.horizontalGradient(ctx, s1, by + 1, (s1 + s2) / 2, by + 23, 0x00FFFFFF, ColorUtil.fade(0x2AFFFFFF, a));
            RenderUtil.horizontalGradient(ctx, (s1 + s2) / 2, by + 1, s2, by + 23, ColorUtil.fade(0x2AFFFFFF, a), 0x00FFFFFF);
        }
        ctx.fill(sx1 + 6, by + 1, sx2 - 6, by + 2, ColorUtil.fade(0x30FFFFFF, a));
        Fonts.drawCentered(ctx, Fonts.trim(label, sx2 - sx1 - 12), (sx1 + sx2) / 2, by + 8, ColorUtil.fade(0xFFFFFFFF, a), false);
        hits.add(new Hit(sx1, by, sx2, by + 24, action));

        // Footer: count and "All worlds" / "All servers"
        int fy = by + 24 + 7;
        int total = sp ? worldTotal : serverTotal;
        String countText = sp ? (worldsLoading ? "" : total + (total == 1 ? " world" : " worlds")) : total + (total == 1 ? " server" : " servers");
        Fonts.draw(ctx, countText, sx1 + 1, fy, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        String all = sp ? "All worlds →" : "All servers →";
        int aw = Fonts.width(all);
        boolean lh = over(mouseX, mouseY, sx2 - aw - 2, fy - 2, sx2, fy + 10);
        float lp = hover("all", lh);
        Fonts.draw(ctx, all, sx2 - aw, fy, ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accentLight(), lp), a), false);
        hits.add(new Hit(sx2 - aw - 2, fy - 2, sx2, fy + 10, sp ? this::openWorlds : this::openServers));
    }

    private void renderFooter(GuiGraphicsExtractor ctx, float a) {
        String left = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Fabric  ·  " + modCount + " mods";
        Fonts.draw(ctx, left, 8, height - 12, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        String copyright = "Copyright Mojang AB. Do not distribute!";
        Fonts.draw(ctx, copyright, width - Fonts.width(copyright) - 8, height - 12, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        int cw = com.nightshade.render.Credits.width();
        int leftEnd = 8 + Fonts.width(left) + 10;
        int rightStart = width - Fonts.width(copyright) - 18;
        if (rightStart - leftEnd >= cw) {
            com.nightshade.render.Credits.draw(ctx, (leftEnd + rightStart) / 2, height - 12, a);
        } else {
            com.nightshade.render.Credits.drawAt(ctx, 8, height - 22, a);
        }
    }

    /** One-time card pointing to the older menus. */
    private int hintX1, hintY1, hintX2, hintY2;

    private void renderStyleHint(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float a) {
        int w = Math.min(206, width - panelWidth() - 40);
        if (w < 150) {
            return;
        }
        int x1 = 12;
        int h = 66;
        int y1 = height - 22 - h;
        if (y1 < greetingBottom + 8) {
            return; // no room under the greeting
        }
        hintX1 = x1;
        hintY1 = y1;
        hintX2 = x1 + w;
        hintY2 = y1 + h;
        glass(ctx, x1, y1, x1 + w, y1 + h, 6, a);
        Fonts.draw(ctx, "Prefer the old menu?", x1 + 8, y1 + 7, ColorUtil.fade(Theme.TEXT, a), false);
        Fonts.draw(ctx, Fonts.trim("Switch any time: Client > Main Menu", w - 16), x1 + 8, y1 + 19, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        Fonts.draw(ctx, Fonts.trim("> Menu Style.", w - 16), x1 + 8, y1 + 29, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        // close
        int cx = x1 + w - 14;
        boolean ch = RenderUtil.isHovered(mouseX, mouseY, cx - 2, y1 + 4, cx + 10, y1 + 16);
        Fonts.draw(ctx, "×", cx + 1, y1 + 6, ColorUtil.fade(ch ? Theme.TEXT : Theme.TEXT_DIM, a), false);
        hits.add(0, new Hit(cx - 2, y1 + 4, cx + 10, y1 + 16, this::dismissHint));
        int by = y1 + h - 22;
        int bx = x1 + 8;
        bx = hintButton(ctx, bx, by, "Aurora menu", mouseX, mouseY, a, () -> switchStyle(MainMenuModule.STYLE_PREVIOUS));
        bx = hintButton(ctx, bx, by, "Classic menu", mouseX, mouseY, a, () -> switchStyle(MainMenuModule.STYLE_CLASSIC));
        if (bx + Fonts.width("Vanilla") + 12 <= x1 + w - 8) {
            hintButton(ctx, bx, by, "Vanilla", mouseX, mouseY, a, this::openVanilla);
        }
    }

    // ------------------------------------------------------------------ notices (crash report, update, old-menu hint)

    private java.nio.file.Path crashReport = com.nightshade.update.CrashReports.unseen().orElse(null);
    private static boolean updateDismissed;

    private boolean noticeShown() {
        return crashReport != null || (com.nightshade.update.UpdateChecker.available() != null && !updateDismissed)
                || !NightshadeClient.client().config().hasFlag(HINT_FLAG);
    }

    private void renderNotices(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float a) {
        if (crashReport != null) {
            java.nio.file.Path report = crashReport;
            renderNotice(ctx, mouseX, mouseY, a, "Minecraft crashed last time", "Copy a short report to share in Discord,",
                    "or open the crash-reports folder.", () -> {
                        com.nightshade.update.CrashReports.dismiss(report);
                        crashReport = null;
                    }, new String[]{"Copy report", "Open folder"}, new Runnable[]{
                            () -> {
                                minecraft.keyboardHandler.setClipboard(com.nightshade.update.CrashReports.summary(report));
                                NightshadeClient.client().notifications().success("Crash report", "Copied - paste it in Discord or an issue");
                            },
                            () -> net.minecraft.util.Util.getPlatform().openFile(com.nightshade.update.CrashReports.folderPath().toFile())});
            return;
        }
        com.nightshade.update.UpdateChecker.Update update = com.nightshade.update.UpdateChecker.available();
        if (update != null && !updateDismissed) {
            renderNotice(ctx, mouseX, mouseY, a, "Nightshade " + update.version() + " is out", "You have v" + NightshadeClient.VERSION
                    + ".", "Download it from Modrinth.", () -> updateDismissed = true,
                    new String[]{"Get update"}, new Runnable[]{
                            () -> net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(update.url()))});
            return;
        }
        if (!NightshadeClient.client().config().hasFlag(HINT_FLAG)) {
            renderStyleHint(ctx, mouseX, mouseY, a);
        }
    }

    /** A small glass card in the bottom-left corner with a title, two lines, buttons and a close cross. */
    private void renderNotice(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float a, String title, String line1, String line2,
                              Runnable close, String[] labels, Runnable[] actions) {
        int w = Math.min(214, width - panelWidth() - 40);
        if (w < 150) {
            return;
        }
        int x1 = 12;
        int h = 66;
        int y1 = height - 22 - h;
        if (y1 < greetingBottom + 8) {
            return;
        }
        glass(ctx, x1, y1, x1 + w, y1 + h, 6, a);
        RenderUtil.roundedRect(ctx, x1, y1 + 6, x1 + 2, y1 + h - 6, 1, ColorUtil.fade(Theme.accent(), a));
        Fonts.draw(ctx, Fonts.trim(title, w - 30), x1 + 8, y1 + 7, ColorUtil.fade(Theme.TEXT, a), false);
        Fonts.draw(ctx, Fonts.trim(line1, w - 16), x1 + 8, y1 + 19, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        Fonts.draw(ctx, Fonts.trim(line2, w - 16), x1 + 8, y1 + 29, ColorUtil.fade(Theme.TEXT_DIM, a), false);
        int cx = x1 + w - 14;
        boolean ch = RenderUtil.isHovered(mouseX, mouseY, cx - 2, y1 + 4, cx + 10, y1 + 16);
        Fonts.draw(ctx, "\u00D7", cx + 1, y1 + 6, ColorUtil.fade(ch ? Theme.TEXT : Theme.TEXT_DIM, a), false);
        hits.add(0, new Hit(cx - 2, y1 + 4, cx + 10, y1 + 16, close));
        int bx = x1 + 8;
        for (int i = 0; i < labels.length; i++) {
            if (bx + Fonts.width(labels[i]) + 12 > x1 + w - 8) {
                break;
            }
            bx = hintButton(ctx, bx, y1 + h - 22, labels[i], mouseX, mouseY, a, actions[i]);
        }
    }

    /** Test hooks. */
    public boolean crashNoticeForTest() {
        return crashReport != null;
    }

    private int hintButton(GuiGraphicsExtractor ctx, int x, int y, String label, int mouseX, int mouseY, float a, Runnable action) {
        int w = Fonts.width(label) + 12;
        boolean hovered = RenderUtil.isHovered(mouseX, mouseY, x, y, x + w, y + 15);
        float p = hover("hint." + label, hovered);
        RenderUtil.roundedRect(ctx, x, y, x + w, y + 15, 4, ColorUtil.fade(ColorUtil.lerp(0x40FFFFFF & 0x28FFFFFF, ColorUtil.withAlpha(Theme.accent(), 0xC0), p), a));
        Fonts.draw(ctx, label, x + 6, y + 4, ColorUtil.fade(Theme.TEXT, a), false);
        hits.add(0, new Hit(x, y, x + w, y + 15, action));
        return x + w + 4;
    }

    private void dismissHint() {
        NightshadeClient.client().config().setFlag(HINT_FLAG);
        NightshadeClient.client().config().saveDirty();
    }

    private void switchStyle(String style) {
        dismissHint();
        MainMenuModule module = NightshadeClient.client().modules().get(MainMenuModule.class);
        if (module != null) {
            module.setStyle(style);
            NightshadeClient.client().config().saveDirty();
        }
        minecraft.setScreen(MainMenuModule.createMenu());
    }

    private void openVanilla() {
        dismissHint();
        vanillaRequested = true;
        minecraft.setScreen(new TitleScreen());
    }

    // ------------------------------------------------------------------ actions

    private void selectMode(int newMode) {
        mode = newMode;
        selectedRow = 0;
        lastRowIndex = -1;
    }

    private void clickRow(int index) {
        long now = System.currentTimeMillis();
        if (index == lastRowIndex && now - lastRowClick < 350) {
            selectedRow = index;
            playSelected();
            return;
        }
        selectedRow = index;
        lastRowIndex = index;
        lastRowClick = now;
    }

    private void playSelected() {
        if (mode == 0) {
            if (selectedRow < worlds.size()) {
                minecraft.createWorldOpenFlows().openWorld(worlds.get(selectedRow).getLevelId(), () -> minecraft.setScreen(this));
            }
        } else if (selectedRow < servers.size()) {
            ServerData data = servers.get(selectedRow);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
        }
    }

    private void openWorlds() {
        minecraft.setScreen(new WorldsScreen(this));
    }

    private void openServers() {
        minecraft.setScreen(new ServersScreen(this));
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String ago(long time) {
        if (time <= 0) {
            return "never";
        }
        long mins = Math.max(0, (System.currentTimeMillis() - time) / 60000);
        if (mins < 1) {
            return "just now";
        }
        if (mins < 60) {
            return mins + "m ago";
        }
        long hours = mins / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        return days == 1 ? "yesterday" : days + " days ago";
    }

    // ------------------------------------------------------------------ first-launch tour

    private static final String TOUR_FLAG = "tourDone";
    private static final int TOUR_STEPS = 5;
    private int tourStep = -1;
    private final com.nightshade.gui.widget.NsButton tourNext = new com.nightshade.gui.widget.NsButton("", com.nightshade.gui.widget.NsButton.Style.PRIMARY, this::nextTourStep);
    private final com.nightshade.gui.widget.NsButton tourSkip = new com.nightshade.gui.widget.NsButton("", com.nightshade.gui.widget.NsButton.Style.GHOST, this::endTour);

    private boolean tourActive() {
        if (tourStep < 0 && !NightshadeClient.client().config().hasFlag(TOUR_FLAG)) {
            tourStep = 0;
        }
        return tourStep >= 0 && tourStep < TOUR_STEPS;
    }

    private void nextTourStep() {
        tourStep++;
        if (tourStep >= TOUR_STEPS) {
            endTour();
        }
    }

    private void endTour() {
        tourStep = TOUR_STEPS;
        NightshadeClient.client().config().setFlag(TOUR_FLAG);
        NightshadeClient.client().config().saveDirty();
    }

    /** Test hooks. */
    public int tourStepForTest() {
        return tourActive() ? tourStep : -1;
    }

    public void tourNextForTest() {
        nextTourStep();
    }

    private void renderTour(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, width, height, Theme.tint(0x88050210));
        switch (tourStep) {
            case 1 -> RenderUtil.roundedOutline(ctx, panelX1 - 4, panelY1 - 4, panelX2 + 4, panelY2 + 4, 10, Theme.accentLight());
            case 2 -> RenderUtil.roundedOutline(ctx, accountX1 - 3, accountY1 - 3, accountX2 + 3, accountY2 + 3, 10, Theme.accentLight());
            case 3 -> RenderUtil.roundedOutline(ctx, modsX1 - 3, modsY1 - 3, modsX2 + 3, modsY2 + 3, 6, Theme.accentLight());
            default -> {
            }
        }
        int cardW = Math.min(width - 40, 250);
        String title = Lang.t("tour.title." + tourStep);
        List<net.minecraft.util.FormattedCharSequence> body = font.split(Fonts.text(Lang.t("tour.body." + tourStep)), cardW - 24);
        int cardH = 44 + body.size() * (Fonts.height() + 2) + 22;
        int cardX = switch (tourStep) {
            case 1 -> Math.max(10, panelX1 - cardW - 14);
            case 2 -> Math.max(10, accountX2 - cardW);
            default -> (width - cardW) / 2;
        };
        int cardY = switch (tourStep) {
            case 1 -> Math.max(BAR_H + 6, Math.min(height - cardH - 6, panelY1));
            case 2, 3 -> BAR_H + 10;
            default -> Math.max(10, height / 2 - cardH / 2);
        };
        Fx.shadow(ctx, cardX, cardY + 2, cardX + cardW, cardY + cardH + 2, 8, 0xA0000000);
        RenderUtil.roundedRect(ctx, cardX, cardY, cardX + cardW, cardY + cardH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, cardX, cardY, cardX + cardW, cardY + cardH, 8, Theme.accent());
        Fonts.draw(ctx, "Tour · " + (tourStep + 1) + " of " + TOUR_STEPS, cardX + 12, cardY + 8, Theme.TEXT_DIM, false);
        Fonts.draw(ctx, title, cardX + 12, cardY + 19, Theme.TEXT, false);
        int y = cardY + 33;
        for (net.minecraft.util.FormattedCharSequence line : body) {
            ctx.text(font, line, cardX + 12, y, Theme.TEXT_MUTED, false);
            y += Fonts.height() + 2;
        }
        tourNext.label(tourStep == TOUR_STEPS - 1 ? Lang.t("tour.done") : Lang.t("tour.next"));
        tourSkip.label(Lang.t("tour.skip"));
        int nw = Math.max(60, tourNext.preferredWidth());
        tourNext.render(ctx, cardX + cardW - 12 - nw, cardY + cardH - 22, nw, 16, mouseX, mouseY);
        if (tourStep < TOUR_STEPS - 1) {
            int sw = Math.max(50, tourSkip.preferredWidth());
            tourSkip.render(ctx, cardX + 12, cardY + cardH - 22, sw, 16, mouseX, mouseY);
        }
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (tourActive()) {
            if (!tourNext.click(mouseX, mouseY, button)) {
                tourSkip.click(mouseX, mouseY, button);
            }
            return true; // the tour is modal
        }
        if (button == 0) {
            for (Hit hit : List.copyOf(hits)) {
                if (RenderUtil.isHovered(mouseX, mouseY, hit.x1(), hit.y1(), hit.x2(), hit.y2())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    /** Opens a section by its old label (used by the automated test and kept for compatibility). */
    public boolean activate(String label) {
        switch (label) {
            case "Singleplayer" -> openWorlds();
            case "Multiplayer" -> openServers();
            case "Mods" -> minecraft.setScreen(new ModManagerScreen(this));
            case "Accounts" -> minecraft.setScreen(new AccountsScreen(this));
            case "Client Settings" -> minecraft.setScreen(new ClickGuiScreen(this));
            case "Options" -> minecraft.setScreen(new NightshadeOptionsScreen(this));
            case "Quit Game" -> minecraft.stop();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Test hook: whether a one-click way back into the last world or server is offered. */
    public boolean hasContinue() {
        return lastPlayed != null;
    }
}
