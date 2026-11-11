package com.nightshade.gui.menu;

import com.mojang.realmsclient.RealmsMainScreen;
import com.nightshade.NightshadeClient;
import com.nightshade.account.Account;
import com.nightshade.account.AccountManager;
import com.nightshade.gui.ClickGuiScreen;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.NsScreen;
import com.nightshade.gui.mods.ModManagerScreen;
import com.nightshade.module.interfaces.MainMenuModule;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.BrandLogo;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.MenuBackground;
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
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Nightshade main menu: animated panorama with aurora light and drifting motes, a glowing brand mark, two large play
 * tiles, a row of shortcut tiles, a one-click "Continue" for the last world or server, and the account card.
 * Everything eases in on open and responds to hover. Replaces the vanilla title screen while the Main Menu module is
 * enabled; the vanilla screen stays one click away for compatibility with mods that extend it.
 */
public final class PreviousMenuScreen extends NsMenuScreen {
    /** Set when the user explicitly asks for the vanilla title screen, so the replacement mixin lets it through once. */

    private final List<NavItem> items = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Animation accountHover = new Animation(16f, 0f);
    private final Animation continueHover = new Animation(16f, 0f);
    private final Animation quitHover = new Animation(16f, 0f);
    private final Animation vanillaHover = new Animation(16f, 0f);
    private final int modCount;
    private long openedAt = System.nanoTime();
    private int accountX1;
    private int accountY1;
    private int accountX2;
    private int accountY2;
    private int areaX1;
    private int areaY1;
    private int areaX2;
    private int areaY2;
    private int modsX1;
    private int modsY1;
    private int modsX2;
    private int modsY2;
    private LastPlayed.Entry lastPlayed;

    private record NavItem(String label, Runnable action, BooleanSupplier enabled, String disabledReason, Animation hover, String[] icon) {
    }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
    }

    public PreviousMenuScreen() {
        super(Component.literal("Nightshade Client"));
        modCount = (int) FabricLoader.getInstance().getAllMods().stream().filter(m -> m.getContainingMod().isEmpty()).count();
    }

    private AccountManager accounts() {
        return NightshadeClient.client().accounts();
    }

    @Override
    protected void init() {
        items.clear();
        add("Singleplayer", PixelIcons.MENU_SINGLEPLAYER, () -> minecraft.setScreen(new WorldsScreen(this)));
        add("Multiplayer", PixelIcons.MENU_MULTIPLAYER, () -> minecraft.setScreen(new ServersScreen(this)));
        MainMenuModule module = NightshadeClient.client().modules().get(MainMenuModule.class);
        if (module == null || module.showRealms()) {
            items.add(new NavItem("Realms", () -> minecraft.setScreen(new RealmsMainScreen(this)), () -> accounts().isPremium(),
                    Lang.t("menu.realms_needs_ms"), new Animation(16f, 0f), PixelIcons.MENU_REALMS));
        }
        add("Mods", PixelIcons.MENU_MODS, () -> minecraft.setScreen(new ModManagerScreen(this)));
        add("Accounts", PixelIcons.MENU_ACCOUNTS, () -> minecraft.setScreen(new AccountsScreen(this)));
        add("Client Settings", PixelIcons.MENU_SETTINGS, () -> minecraft.setScreen(new ClickGuiScreen(this)));
        add("Options", PixelIcons.MENU_OPTIONS, () -> minecraft.setScreen(new NightshadeOptionsScreen(this)));
        add("Quit Game", PixelIcons.MENU_QUIT, () -> minecraft.stop());
        lastPlayed = LastPlayed.get();
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
    }

    private void add(String label, String[] icon, Runnable action) {
        items.add(new NavItem(label, action, () -> true, null, new Animation(16f, 0f), icon));
    }

    private NavItem item(String label) {
        for (NavItem item : items) {
            if (item.label().equals(label)) {
                return item;
            }
        }
        return null;
    }

    /** Translated label for a navigation entry id. */
    private static String display(String id) {
        return Lang.t("menu." + id.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private float elapsed() {
        return (System.nanoTime() - openedAt) / 1_000_000_000f;
    }

    /** Entrance progress (0..1) of the element with stagger index {@code i}. */
    private float appear(int i) {
        return Fx.easeOutCubic((elapsed() - 0.06f * i) / 0.5f);
    }

    // ------------------------------------------------------------------ background

    // ------------------------------------------------------------------ layout

    private boolean compact() {
        return height < 290 || width < 380;
    }

    private int contentWidth() {
        return compact() ? Math.min(300, width - 20) : Math.min(316, width - 28);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        hits.clear();
        boolean compact = compact();
        float t = Fx.time();

        int cw = contentWidth();
        int cx1 = (width - cw) / 2;
        int logo = compact ? 26 : 36;
        float titleScale = compact ? 1.9f : 2.6f;
        int titleH = Math.round(Fonts.height() * titleScale);
        int heroH = Math.max(logo, titleH) + 14;
        int primaryH = compact ? 36 : 46;
        int secondaryH = compact ? 30 : 42;
        int gap = compact ? 6 : 8;
        boolean showContinue = lastPlayed != null;
        int continueH = 20;
        int total = heroH + (compact ? 12 : 22) + primaryH + gap + secondaryH + (showContinue ? gap + 4 + continueH : 0);
        int top = Math.max(compact ? 10 : 18, (height - total) / 2 - (compact ? 0 : 10));
        if (width < cw + 2 * 172) {
            top = Math.max(top, 52); // keep clear of the account card on narrow screens
        }
        top = Math.min(top, Math.max(4, height - 26 - total));

        renderHero(ctx, t, top, logo, titleScale, titleH, appear(0));

        // Primary tiles
        int py = top + heroH + (compact ? 12 : 22);
        int pw = (cw - gap) / 2;
        String tooltip = null;
        NavItem sp = item("Singleplayer");
        NavItem mp = item("Multiplayer");
        tooltip = primaryTile(ctx, sp, cx1, py, pw, primaryH, Lang.t("menu.sub.singleplayer"), mouseX, mouseY, appear(2), tooltip);
        tooltip = primaryTile(ctx, mp, cx1 + cw - pw, py, pw, primaryH, Lang.t("menu.sub.multiplayer"), mouseX, mouseY, appear(3), tooltip);

        // Secondary tiles
        List<NavItem> row = new ArrayList<>();
        for (String id : new String[]{"Realms", "Mods", "Accounts", "Client Settings", "Options"}) {
            NavItem item = item(id);
            if (item != null) {
                row.add(item);
            }
        }
        int sy = py + primaryH + gap;
        int n = row.size();
        int sw = (cw - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            NavItem item = row.get(i);
            int sx = cx1 + i * (sw + gap);
            int w = i == n - 1 ? cx1 + cw - sx : sw;
            tooltip = secondaryTile(ctx, item, sx, sy, w, secondaryH, mouseX, mouseY, appear(4 + i), tooltip);
            if (item.label().equals("Mods")) {
                modsX1 = sx;
                modsY1 = sy;
                modsX2 = sx + w;
                modsY2 = sy + secondaryH;
            }
        }
        areaX1 = cx1;
        areaY1 = py;
        areaX2 = cx1 + cw;
        areaY2 = sy + secondaryH;

        if (showContinue) {
            renderContinue(ctx, cx1, sy + secondaryH + gap + 4, cw, continueH, mouseX, mouseY, appear(4 + n));
        }

        renderAccountCard(ctx, mouseX, mouseY, appear(1));
        renderGreeting(ctx, appear(1));
        renderFooter(ctx, mouseX, mouseY, appear(5 + n));

        if (tourActive()) {
            renderTour(ctx, mouseX, mouseY);
        }
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null && !tourActive()) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private void renderHero(GuiGraphicsExtractor ctx, float t, int top, int logo, float titleScale, int titleH, float a) {
        String title = "Nightshade";
        int titleW = Math.round(Fonts.width(title) * titleScale);
        int groupW = logo + 10 + titleW;
        int gx = (width - groupW) / 2;
        int lift = Math.round((1f - a) * 10f);
        int rowH = Math.max(logo, titleH);
        int ly = top + (rowH - logo) / 2 + lift;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.3f);

        // Halo behind the whole brand, then a brighter one behind the logo.
        Fx.glowRect(ctx, gx - 40, top - 26 + lift, gx + groupW + 40, top + rowH + 30 + lift,
                ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accent(), 0x30 + Math.round(0x14 * pulse)), a));
        Fx.glow(ctx, gx + logo / 2f, ly + logo / 2f, logo * (1.25f + 0.12f * pulse),
                ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accentLight(), 0x70), a));
        BrandLogo.draw(ctx, gx, ly, logo, ColorUtil.fade(0xFFFFFFFF, a));

        // Title with a slow light sweep across the letters.
        int tx = gx + logo + 10;
        int ty = top + (rowH - titleH) / 2 + lift + 1;
        ctx.pose().pushMatrix();
        ctx.pose().translate(tx, ty);
        ctx.pose().scale(titleScale, titleScale);
        float sweep = ((t * 0.45f) % 1.6f) * (title.length() + 6) - 3;
        int x = 0;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float s = Math.max(0f, 1f - Math.abs(i - sweep) / 2.5f);
            int color = ColorUtil.lerp(MenuBackground.accentLight(), 0xFFFFFFFF, s * 0.85f);
            Fonts.draw(ctx, ch, x, 0, ColorUtil.fade(color, a), true);
            x += Fonts.width(ch);
        }
        ctx.pose().popMatrix();

        String sub = "C L I E N T   ·   v" + NightshadeClient.VERSION;
        int subY = top + rowH + 5 + lift;
        Fonts.drawCentered(ctx, sub, width / 2, subY, ColorUtil.fade(Theme.TEXT_MUTED, a * 0.9f), false);
    }

    /** Glass card shared by all tiles. Returns the hover progress. */
    private float card(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, float h, float a, boolean danger) {
        int accent = danger ? 0xFFFF6B88 : MenuBackground.accent();
        Fx.shadow(ctx, x1, y1 + 2, x2, y2 + 2, 7, ColorUtil.fade(0x9A000000, a));
        if (h > 0.01f) {
            Fx.glowRect(ctx, x1 - 26, y1 - 18, x2 + 26, y2 + 18, ColorUtil.fade(ColorUtil.withAlpha(accent, Math.round(0x46 * h)), a));
        }
        RenderUtil.roundedRect(ctx, x1, y1, x2, y2, 6, ColorUtil.fade(ColorUtil.lerp(0xB0120A22, com.nightshade.render.Theme.tint(0xDC24173F), h), a));
        // Inner highlight on the upper edge sells the glass look.
        ctx.fill(x1 + 6, y1 + 1, x2 - 6, y1 + 2, ColorUtil.fade(ColorUtil.withAlpha(0xFFFFFF, 0x16 + Math.round(0x22 * h)), a));
        RenderUtil.roundedOutline(ctx, x1, y1, x2, y2, 6,
                ColorUtil.fade(ColorUtil.lerp(0x2EFFFFFF, ColorUtil.withAlpha(accent, 0xD0), h), a));
        if (h > 0.05f) {
            // A light travelling along the top and bottom edges of the hovered tile.
            float t = (Fx.time() * 0.55f) % 1f;
            int w = x2 - x1 - 12;
            int seg = Math.max(18, w / 4);
            int sx = x1 + 6 + Math.round(t * (w + seg)) - seg;
            int light = ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accentLight(), Math.round(0xE0 * h)), a);
            int c1 = Math.max(x1 + 6, sx);
            int c2 = Math.min(x2 - 6, sx + seg);
            if (c2 > c1) {
                RenderUtil.horizontalGradient(ctx, c1, y1, (c1 + c2) / 2, y1 + 1, light & 0x00FFFFFF, light);
                RenderUtil.horizontalGradient(ctx, (c1 + c2) / 2, y1, c2, y1 + 1, light, light & 0x00FFFFFF);
            }
            int bx = x2 - 6 - (sx - x1 - 6) - seg;
            int d1 = Math.max(x1 + 6, bx);
            int d2 = Math.min(x2 - 6, bx + seg);
            if (d2 > d1) {
                RenderUtil.horizontalGradient(ctx, d1, y2 - 1, (d1 + d2) / 2, y2, light & 0x00FFFFFF, light);
                RenderUtil.horizontalGradient(ctx, (d1 + d2) / 2, y2 - 1, d2, y2, light, light & 0x00FFFFFF);
            }
        }
        return h;
    }

    private String primaryTile(GuiGraphicsExtractor ctx, NavItem item, int x, int y, int w, int h, String subtitle,
                               int mouseX, int mouseY, float a, String tooltip) {
        boolean hovered = !tourActive() && RenderUtil.isHovered(mouseX, mouseY, x, y, x + w, y + h);
        float p = item.hover().setTarget(hovered ? 1f : 0f).update();
        int lift = Math.round((1f - a) * 14f) - Math.round(p * 2f);
        int y1 = y + lift;
        card(ctx, x, y1, x + w, y1 + h, p, a, false);
        int chip = h >= 40 ? 26 : 20;
        int chipX = x + 10;
        int chipY = y1 + (h - chip) / 2;
        RenderUtil.roundedRect(ctx, chipX, chipY, chipX + chip, chipY + chip, 5,
                ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accent(), 0x48 + Math.round(0x40 * p)), a));
        int scale = chip >= 26 ? 2 : 2;
        int iconSize = 9 * scale;
        PixelIcons.menu(ctx, item.icon(), chipX + (chip - iconSize) / 2, chipY + (chip - iconSize) / 2, scale,
                ColorUtil.fade(0xFFE9E4FF, a), ColorUtil.fade(ColorUtil.lerp(MenuBackground.accentLight(), 0xFFFFFFFF, p * 0.6f), a));
        int textX = chipX + chip + 10 + Math.round(p * 2f);
        int textY = h >= 40 ? y1 + h / 2 - Fonts.height() : y1 + (h - Fonts.height()) / 2 + 1;
        Fonts.draw(ctx, display(item.label()), textX, textY, ColorUtil.fade(ColorUtil.lerp(0xFFE8E4F6, 0xFFFFFFFF, p), a), false);
        if (h >= 40) {
            Fonts.draw(ctx, Fonts.trim(subtitle, x + w - textX - 18), textX, textY + Fonts.height() + 3,
                    ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, p), a), false);
        }
        // Play arrow slides in on hover.
        if (p > 0.02f) {
            PixelIcons.menu(ctx, PixelIcons.MENU_PLAY, x + w - 16 + Math.round((1f - p) * -6f), y1 + (h - 9) / 2, 1,
                    ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accentLight(), Math.round(255 * p)), a), 0);
        }
        hits.add(new Hit(x, y, x + w, y + h, item.action()));
        return tooltip;
    }

    private String secondaryTile(GuiGraphicsExtractor ctx, NavItem item, int x, int y, int w, int h, int mouseX, int mouseY, float a, String tooltip) {
        boolean enabled = item.enabled().getAsBoolean();
        boolean hovered = !tourActive() && RenderUtil.isHovered(mouseX, mouseY, x, y, x + w, y + h);
        float p = item.hover().setTarget(hovered && enabled ? 1f : 0f).update();
        if (hovered && !enabled) {
            tooltip = item.disabledReason();
        }
        float alpha = enabled ? a : a * 0.55f;
        int lift = Math.round((1f - a) * 14f) - Math.round(p * 2f);
        int y1 = y + lift;
        card(ctx, x, y1, x + w, y1 + h, p, alpha, false);
        String label = item.label().equals("Client Settings") ? Lang.t("menu.settings") : display(item.label());
        label = Fonts.trim(label, w - 6);
        boolean tall = h >= 28;
        int scale = h >= 38 ? 2 : 1;
        int iconSize = 9 * scale;
        int iconGap = scale == 2 ? 4 : 3;
        int contentH = iconSize + iconGap + Fonts.height();
        if (tall) {
            int iy = y1 + (h - contentH) / 2 + 1;
            PixelIcons.menu(ctx, item.icon(), x + (w - iconSize) / 2, iy, scale,
                    ColorUtil.fade(ColorUtil.lerp(0xFFB9AEDB, 0xFFFFFFFF, p), alpha), ColorUtil.fade(MenuBackground.accentLight(), alpha));
            Fonts.drawCentered(ctx, label, x + w / 2, iy + iconSize + iconGap, ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, p), alpha), false);
        } else {
            int rowW = iconSize + 5 + Fonts.width(label);
            int ix = x + (w - rowW) / 2;
            if (rowW > w - 6) {
                Fonts.drawCentered(ctx, label, x + w / 2, y1 + (h - Fonts.height()) / 2 + 1,
                        ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, p), alpha), false);
            } else {
                PixelIcons.menu(ctx, item.icon(), ix, y1 + (h - iconSize) / 2, scale,
                        ColorUtil.fade(ColorUtil.lerp(0xFFB9AEDB, 0xFFFFFFFF, p), alpha), ColorUtil.fade(MenuBackground.accentLight(), alpha));
                Fonts.draw(ctx, label, ix + iconSize + 5, y1 + (h - Fonts.height()) / 2 + 1,
                        ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, p), alpha), false);
            }
        }
        if (item.label().equals("Mods")) {
            String count = Integer.toString(modCount);
            int bw = Fonts.width(count) + 6;
            int bx = x + w - bw + 3;
            int by = y1 - 4;
            RenderUtil.roundedRect(ctx, bx, by, bx + bw, by + 11, 5, ColorUtil.fade(MenuBackground.accent(), alpha));
            RenderUtil.roundedOutline(ctx, bx, by, bx + bw, by + 11, 5, ColorUtil.fade(0x40FFFFFF, alpha));
            Fonts.draw(ctx, count, bx + 3, by + 2, ColorUtil.fade(0xFFFFFFFF, alpha), false);
        }
        if (enabled) {
            hits.add(new Hit(x, y, x + w, y + h, item.action()));
        }
        return tooltip;
    }

    private void renderContinue(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY, float a) {
        LastPlayed.Entry entry = lastPlayed;
        String kind = Lang.t(entry.kind() == LastPlayed.Kind.WORLD ? "menu.continue.world" : "menu.continue.server");
        String head = Lang.t("menu.continue");
        int maxName = w - Fonts.width(head) - Fonts.width(kind) - 52;
        String name = Fonts.trim(entry.name(), Math.max(30, maxName));
        int rowW = 9 + 6 + Fonts.width(head) + 8 + Fonts.width(name) + 10 + Fonts.width(kind) + 8;
        int pw = Math.min(w, rowW + 20);
        int px = x + (w - pw) / 2;
        boolean hovered = !tourActive() && RenderUtil.isHovered(mouseX, mouseY, px, y, px + pw, y + h);
        float p = continueHover.setTarget(hovered ? 1f : 0f).update();
        int lift = Math.round((1f - a) * 10f);
        int y1 = y + lift;
        Fx.shadow(ctx, px, y1 + 1, px + pw, y1 + h + 1, 5, ColorUtil.fade(0x80000000, a));
        if (p > 0.01f) {
            Fx.glowRect(ctx, px - 20, y1 - 12, px + pw + 20, y1 + h + 12, ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accent(), Math.round(0x40 * p)), a));
        }
        RenderUtil.roundedRect(ctx, px, y1, px + pw, y1 + h, h / 2, ColorUtil.fade(ColorUtil.lerp(0xA0120A22, com.nightshade.render.Theme.tint(0xD82A1A4C), p), a));
        RenderUtil.roundedOutline(ctx, px, y1, px + pw, y1 + h, h / 2, ColorUtil.fade(ColorUtil.lerp(0x2AFFFFFF, ColorUtil.withAlpha(MenuBackground.accent(), 0xC0), p), a));
        int cx = px + 10;
        int ty = y1 + (h - Fonts.height()) / 2 + 1;
        PixelIcons.menu(ctx, PixelIcons.MENU_PLAY, cx + Math.round(p * 2f), y1 + (h - 9) / 2, 1, ColorUtil.fade(MenuBackground.accentLight(), a), 0);
        cx += 15;
        Fonts.draw(ctx, head, cx, ty, ColorUtil.fade(Theme.TEXT, a), false);
        cx += Fonts.width(head) + 8;
        Fonts.draw(ctx, name, cx, ty, ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, p), a), false);
        cx += Fonts.width(name) + 10;
        int bw = Fonts.width(kind) + 8;
        RenderUtil.roundedRect(ctx, cx, y1 + 4, cx + bw, y1 + h - 4, 3, ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accent(), 0x50), a));
        Fonts.draw(ctx, kind, cx + 4, ty, ColorUtil.fade(Theme.TEXT, a), false);
        hits.add(new Hit(px, y, px + pw, y + h, this::continueLast));
    }

    private void continueLast() {
        LastPlayed.Entry entry = lastPlayed;
        if (entry == null) {
            return;
        }
        if (entry.kind() == LastPlayed.Kind.WORLD) {
            minecraft.createWorldOpenFlows().openWorld(entry.id(), () -> minecraft.setScreen(this));
        } else {
            ServerData data = new ServerData(entry.name(), entry.id(), ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(entry.id()), data, false, null);
        }
    }

    private void renderGreeting(GuiGraphicsExtractor ctx, float a) {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        String key = hour < 5 ? "night" : hour < 12 ? "morning" : hour < 18 ? "afternoon" : hour < 22 ? "evening" : "night";
        String greeting = Lang.t("menu.greeting." + key);
        String time = String.format("%02d:%02d", hour, now.getMinute());
        int x = 12 - Math.round((1f - a) * 10f);
        Fonts.draw(ctx, time, x, 10, ColorUtil.fade(Theme.TEXT, a), true);
        Fonts.draw(ctx, greeting, x, 22, ColorUtil.fade(Theme.TEXT_MUTED, a), true);
    }

    private void renderAccountCard(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float a) {
        Account account = accounts().active();
        accountX2 = width - 10 + Math.round((1f - a) * 12f);
        accountX1 = accountX2 - Math.min(156, Math.max(120, width / 3));
        accountY1 = 10;
        accountY2 = accountY1 + 36;
        boolean hovered = !tourActive() && RenderUtil.isHovered(mouseX, mouseY, accountX1, accountY1, accountX2, accountY2);
        float h = accountHover.setTarget(hovered ? 1f : 0f).update();
        card(ctx, accountX1, accountY1, accountX2, accountY2, h, a, false);
        int face = 22;
        int fx = accountX1 + 7;
        int fy = accountY1 + 7;
        RenderUtil.roundedRect(ctx, fx - 1, fy - 1, fx + face + 1, fy + face + 1, 3, ColorUtil.fade(ColorUtil.withAlpha(MenuBackground.accent(), 0x90), a));
        PlayerFaceExtractor.extractRenderState(ctx, accounts().skin(account), fx, fy, face);
        int textX = fx + face + 8;
        Fonts.draw(ctx, Fonts.trim(account.name(), accountX2 - textX - 8), textX, accountY1 + 7, ColorUtil.fade(Theme.TEXT, a), false);
        String badge = account.type().label();
        int bw = Fonts.width(badge) + 8;
        RenderUtil.roundedRect(ctx, textX, accountY1 + 19, textX + bw, accountY1 + 30, 3, ColorUtil.fade(ColorUtil.withAlpha(account.type().color(), 0x60), a));
        Fonts.draw(ctx, badge, textX + 4, accountY1 + 21, ColorUtil.fade(Theme.TEXT, a), false);
        String hint = accounts().isSwitching() ? Lang.t("menu.switching") : Lang.t("menu.switch");
        int hx = accountX2 - Fonts.width(hint) - 8;
        if (hx > textX + bw + 6) {
            Fonts.draw(ctx, hint, hx, accountY1 + 21, ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_DIM, MenuBackground.accentLight(), h), a), false);
        }
        hits.add(new Hit(accountX1, accountY1, accountX2, accountY2, () -> minecraft.setScreen(new AccountsScreen(this))));
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float a) {
        String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Fabric  ·  " + modCount + " mods";
        Fonts.draw(ctx, version, width - Fonts.width(version) - 10, height - 22, ColorUtil.fade(Theme.TEXT_MUTED, a), true);
        String copyright = "Copyright Mojang AB. Do not distribute!";
        Fonts.draw(ctx, copyright, width - Fonts.width(copyright) - 10, height - 11, ColorUtil.fade(Theme.TEXT_DIM, a), true);

        int y = height - 24;
        int x = 10;
        NavItem quit = item("Quit Game");
        String quitLabel = Lang.t("menu.quit");
        int qw = 9 + 5 + Fonts.width(quitLabel) + 14;
        boolean qh = !tourActive() && RenderUtil.isHovered(mouseX, mouseY, x, y, x + qw, y + 16);
        float qp = quitHover.setTarget(qh ? 1f : 0f).update();
        pill(ctx, x, y, qw, 16, qp, a, true);
        PixelIcons.menu(ctx, PixelIcons.MENU_QUIT, x + 6, y + 4, 1, ColorUtil.fade(ColorUtil.lerp(0xFFD9667E, 0xFFFF9DB0, qp), a), 0);
        Fonts.draw(ctx, quitLabel, x + 20, y + 4, ColorUtil.fade(ColorUtil.lerp(0xFFE7A1B0, 0xFFFFFFFF, qp), a), false);
        hits.add(new Hit(x, y, x + qw, y + 16, quit.action()));

        String vanilla = Lang.t("menu.vanilla");
        int vx = x + qw + 6;
        int vw = Fonts.width(vanilla) + 14;
        boolean vh = !tourActive() && RenderUtil.isHovered(mouseX, mouseY, vx, y, vx + vw, y + 16);
        float vp = vanillaHover.setTarget(vh ? 1f : 0f).update();
        pill(ctx, vx, y, vw, 16, vp, a, false);
        Fonts.draw(ctx, vanilla, vx + 7, y + 4, ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, vp), a), false);
        hits.add(new Hit(vx, y, vx + vw, y + 16, () -> {
            NightshadeMenuScreen.vanillaRequested = true;
            minecraft.setScreen(new TitleScreen());
        }));
    }

    private void pill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float p, float a, boolean danger) {
        int accent = danger ? 0xFFFF6B88 : MenuBackground.accent();
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, h / 2, ColorUtil.fade(ColorUtil.lerp(0x900E081A, danger ? 0xD03A1426 : com.nightshade.render.Theme.tint(0xD0231740), p), a));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, h / 2, ColorUtil.fade(ColorUtil.lerp(0x26FFFFFF, ColorUtil.withAlpha(accent, 0xB0), p), a));
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
        ctx.fill(0, 0, width, height, com.nightshade.render.Theme.tint(0x88050210));
        switch (tourStep) {
            case 1 -> RenderUtil.roundedOutline(ctx, areaX1 - 4, areaY1 - 4, areaX2 + 4, areaY2 + 4, 8, MenuBackground.accentLight());
            case 2 -> RenderUtil.roundedOutline(ctx, accountX1 - 3, accountY1 - 3, accountX2 + 3, accountY2 + 3, 8, MenuBackground.accentLight());
            case 3 -> RenderUtil.roundedOutline(ctx, modsX1 - 3, modsY1 - 3, modsX2 + 3, modsY2 + 3, 8, MenuBackground.accentLight());
            default -> {
            }
        }
        int cardW = Math.min(width - 40, 260);
        int cardX = (width - cardW) / 2;
        String title = Lang.t("tour.title." + tourStep);
        java.util.List<net.minecraft.util.FormattedCharSequence> body = font.split(Fonts.text(Lang.t("tour.body." + tourStep)), cardW - 24);
        int cardH = 44 + body.size() * (Fonts.height() + 2) + 22;
        // Put the card where it does not cover the highlighted element.
        int cardY = switch (tourStep) {
            case 1, 3 -> areaY1 - cardH - 10 >= 4 ? areaY1 - cardH - 10 : Math.min(height - cardH - 4, areaY2 + 10);
            case 2 -> Math.min(height - cardH - 4, accountY2 + 12);
            default -> Math.max(10, height / 2 - cardH / 2);
        };
        Fx.shadow(ctx, cardX, cardY + 2, cardX + cardW, cardY + cardH + 2, 8, 0xA0000000);
        RenderUtil.roundedRect(ctx, cardX, cardY, cardX + cardW, cardY + cardH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, cardX, cardY, cardX + cardW, cardY + cardH, 8, MenuBackground.accent());
        Fonts.draw(ctx, title, cardX + 12, cardY + 12, Theme.TEXT, false);
        int y = cardY + 28;
        for (net.minecraft.util.FormattedCharSequence line : body) {
            ctx.text(font, line, cardX + 12, y, Theme.TEXT_MUTED, false);
            y += Fonts.height() + 2;
        }
        for (int i = 0; i < TOUR_STEPS; i++) {
            int dx = cardX + 12 + i * 9;
            RenderUtil.roundedRect(ctx, dx, cardY + cardH - 16, dx + 6, cardY + cardH - 10, 2, i == tourStep ? MenuBackground.accent() : Theme.SWITCH_OFF);
        }
        tourNext.label(tourStep == TOUR_STEPS - 1 ? Lang.t("tour.done") : Lang.t("tour.next"));
        tourSkip.label(Lang.t("tour.skip"));
        int nw = Math.max(60, tourNext.preferredWidth());
        tourNext.render(ctx, cardX + cardW - 12 - nw, cardY + cardH - 22, nw, 16, mouseX, mouseY);
        if (tourStep < TOUR_STEPS - 1) {
            int sw = Math.max(50, tourSkip.preferredWidth());
            tourSkip.render(ctx, cardX + cardW - 18 - nw - sw, cardY + cardH - 22, sw, 16, mouseX, mouseY);
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

    /** Clicks a navigation entry by label (used by the automated test). */
    public boolean activate(String label) {
        for (NavItem item : items) {
            if (item.label().equals(label) && item.enabled().getAsBoolean()) {
                item.action().run();
                return true;
            }
        }
        return false;
    }

    /** Test hook: whether the Continue card is offered (a last world or server is known). */
    public boolean hasContinue() {
        return lastPlayed != null;
    }
}
