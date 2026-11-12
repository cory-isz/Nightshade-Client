package com.nightshade.gui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.MenuBackground;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nightshade multiplayer screen: a full-page server browser with your saved servers and a list of featured PvP
 * servers, live status (coloured MOTD, players, ping), search, hover-to-join and a right-click menu.
 */
public final class ServersScreen extends NsMenuScreen {
    private static final int CARD_H = 50;
    private static final int GAP = 6;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 34;
    private static final ExecutorService PING_POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Nightshade server ping");
        t.setDaemon(true);
        return t;
    });

    /** Popular PvP servers, shown in the Featured tab with their live status. */
    private static final String[][] FEATURED = {
            {"Hypixel", "mc.hypixel.net"},
            {"Minemen Club", "minemen.club"},
            {"PvP Legacy", "pvplegacy.net"},
            {"Hoplite", "hoplite.gg"},
            {"CubeCraft", "play.cubecraft.net"},
            {"MCC Island", "play.mccisland.net"},
    };

    private enum Tab { MINE, FEATURED }

    @Nullable
    private final Screen parent;
    private ServerList servers;
    private final List<ServerData> featured = new ArrayList<>();
    private boolean featuredPinged;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final Map<ServerData, FaviconTexture> icons = new HashMap<>();
    private final Map<ServerData, byte[]> iconBytes = new HashMap<>();
    private final Map<ServerData, Animation> hover = new HashMap<>();
    private final Animation scroll = new Animation(18f, 0f);
    private final Animation tabLine = new Animation(14f, 0f);
    private boolean tabLineReady;
    private double scrollTarget;
    private Tab tab = Tab.MINE;
    @Nullable
    private ServerData selected;
    private long lastClick;
    private long openedAt = System.nanoTime();
    private EditBox search;

    private final NsButton join = new NsButton("Join server", NsButton.Style.PRIMARY, this::joinSelected);
    private final NsButton direct = new NsButton("Direct connect", NsButton.Style.SECONDARY, this::directConnect);
    private final NsButton add = new NsButton("Add server", NsButton.Style.SECONDARY, this::addServer);
    private final NsButton refresh = new NsButton("Refresh", NsButton.Style.GHOST, this::refresh);
    private final NsButton back = new NsButton("Back", NsButton.Style.GHOST, this::onClose);

    // layout
    private int cx1;
    private int cx2;
    private int listTop;
    private int listBottom;
    private int searchX1;
    private int searchX2;
    private final int[][] tabRects = new int[2][];
    /** Visible cards this frame: {x1, y1, x2, y2} and the Join pill {x1, y1, x2, y2}, by server. */
    private final List<Object[]> cardRects = new ArrayList<>();

    // right-click menu
    private record MenuItem(String label, int color, Runnable action) {
    }

    private final List<MenuItem> menu = new ArrayList<>();
    private int menuX;
    private int menuY;
    private int menuW;

    public ServersScreen(@Nullable Screen parent) {
        super(Component.literal("Multiplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        if (servers == null) {
            servers = new ServerList(minecraft);
            servers.load();
            pingAll(mine());
            if (servers.size() > 0) {
                selected = servers.get(0);
            }
            for (String[] f : FEATURED) {
                featured.add(new ServerData(f[0], f[1], ServerData.Type.OTHER));
            }
        }
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, searchX1 + 16, 14, searchX2 - searchX1 - 20, 12, Component.literal("Search"));
        search.setBordered(false);
        search.setTextColor(Theme.TEXT);
        search.setHint(Component.literal("Search servers..."));
        search.setValue(query);
        search.setResponder(text -> scrollTarget = 0);
        addWidget(search);
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
    }

    private void layout() {
        int w = Math.min(width - 32, 600);
        cx1 = (width - w) / 2;
        cx2 = cx1 + w;
        listTop = HEADER_H + 22;
        listBottom = height - FOOTER_H - 4;
        searchX2 = width - 12;
        searchX1 = searchX2 - Math.max(90, Math.min(150, width / 5));
        if (search != null) {
            search.setX(searchX1 + 16);
            search.setWidth(searchX2 - searchX1 - 20);
        }
    }

    // ------------------------------------------------------------------ data

    private List<ServerData> mine() {
        List<ServerData> list = new ArrayList<>(servers.size());
        for (int i = 0; i < servers.size(); i++) {
            list.add(servers.get(i));
        }
        return list;
    }

    /** The servers on the current tab that match the search. */
    private List<ServerData> visible() {
        List<ServerData> source = tab == Tab.MINE ? mine() : featured;
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return source;
        }
        List<ServerData> out = new ArrayList<>();
        for (ServerData s : source) {
            if (s.name.toLowerCase(Locale.ROOT).contains(q) || s.ip.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(s);
            }
        }
        return out;
    }

    private boolean isMine(ServerData data) {
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i) == data) {
                return true;
            }
        }
        return false;
    }

    private void pingAll(List<ServerData> list) {
        list.forEach(this::ping);
    }

    private void ping(ServerData data) {
        ping(pinger, data);
    }

    /** Pings one server in the background; the result lands in {@code data} (MOTD, players, ping, state). */
    static void ping(ServerStatusPinger pinger, ServerData data) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        data.setState(ServerData.State.PINGING);
        data.motd = Component.literal("Pinging...");
        data.ping = -1L;
        data.playerList = List.of();
        PING_POOL.submit(() -> {
            try {
                pinger.pingServer(data, () -> minecraft.execute(() -> { }), () -> {
                    data.setState(data.protocol == net.minecraft.SharedConstants.getCurrentVersion().protocolVersion()
                            ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE);
                }, EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
            } catch (Exception e) {
                data.setState(ServerData.State.UNREACHABLE);
                data.ping = -1L;
                data.motd = Component.literal("Can't connect to server");
            }
        });
    }

    /** Short live status for a server row: "1,234 online  ·  42 ms", or null while pinging / offline. */
    @Nullable
    static String shortStatus(ServerData s) {
        if (!online(s)) {
            return null;
        }
        return String.format(Locale.ROOT, "%,d online  \u00B7  %d ms", s.players.online(), s.ping);
    }

    /** Colour of a server's status dot: green/yellow/orange by ping, red offline, grey while pinging. */
    static int statusColor(ServerData s) {
        if (pinging(s)) {
            return 0x80FFFFFF;
        }
        if (s.ping < 0) {
            return 0xFFFF6B81;
        }
        return s.ping < 80 ? 0xFF5BE49B : s.ping < 160 ? 0xFFB9E45B : s.ping < 300 ? 0xFFFFC857 : 0xFFFF8A3D;
    }

    private void refresh() {
        pinger.removeAll();
        pingAll(tab == Tab.MINE ? mine() : featured);
    }

    private void switchTab(Tab next) {
        if (tab == next) {
            return;
        }
        tab = next;
        scrollTarget = 0;
        closeMenu();
        List<ServerData> list = visible();
        selected = list.isEmpty() ? null : list.get(0);
        if (next == Tab.FEATURED && !featuredPinged) {
            featuredPinged = true;
            pingAll(featured);
        }
    }

    private static boolean pinging(ServerData s) {
        return s.ping < 0 && s.motd != null && s.motd.getString().equals("Pinging...");
    }

    private static boolean online(ServerData s) {
        return s.ping >= 0 && s.players != null;
    }

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
    }

    // ------------------------------------------------------------------ actions

    private void join(ServerData data) {
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    private void joinSelected() {
        if (selected != null) {
            join(selected);
        }
    }

    private void directConnect() {
        ServerData data = new ServerData("Direct connection", "", ServerData.Type.OTHER);
        minecraft.gui.setScreen(new DirectJoinServerScreen(this, ok -> {
            if (ok) {
                join(data);
            } else {
                minecraft.gui.setScreen(this);
            }
        }, data));
    }

    private void addServer() {
        ServerData data = new ServerData("Minecraft Server", "", ServerData.Type.OTHER);
        minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), ok -> {
            if (ok) {
                servers.add(data, false);
                servers.save();
                tab = Tab.MINE;
                selected = data;
                ping(data);
            }
            minecraft.gui.setScreen(this);
        }, data));
    }

    private void addFeatured(ServerData data) {
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).ip.equalsIgnoreCase(data.ip)) {
                NightshadeClient.client().notifications().info("Multiplayer", data.name + " is already in your servers");
                return;
            }
        }
        ServerData copy = new ServerData(data.name, data.ip, ServerData.Type.OTHER);
        servers.add(copy, false);
        servers.save();
        ping(copy);
        NightshadeClient.client().notifications().info("Multiplayer", data.name + " added to your servers");
    }

    private void edit(ServerData original) {
        ServerData copy = new ServerData(original.name, original.ip, ServerData.Type.OTHER);
        copy.copyFrom(original);
        minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), ok -> {
            if (ok) {
                original.copyNameIconFrom(copy);
                original.ip = copy.ip;
                original.setResourcePackStatus(copy.getResourcePackStatus());
                servers.save();
                ping(original);
            }
            minecraft.gui.setScreen(this);
        }, copy));
    }

    private void delete(ServerData data) {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                servers.remove(data);
                servers.save();
                FaviconTexture icon = icons.remove(data);
                if (icon != null && !icon.isClosed()) {
                    icon.close();
                }
                if (selected == data) {
                    selected = servers.size() > 0 ? servers.get(0) : null;
                }
            }
            minecraft.gui.setScreen(this);
        }, Component.literal("Remove \"" + data.name + "\"?"), Component.literal("It will be removed from your server list."),
                Component.literal("Remove"), Component.literal("Cancel")));
    }

    private void move(ServerData data, int by) {
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i) == data) {
                int j = i + by;
                if (j >= 0 && j < servers.size()) {
                    servers.swap(i, j);
                    servers.save();
                }
                return;
            }
        }
    }

    /** Next profile for a server: none -> each saved profile -> none. */
    private void cycleProfile(ServerData s) {
        var profiles = NightshadeClient.client().profiles();
        List<String> names = profiles.list();
        String current = profiles.profileForServer(s.ip);
        int index = current == null ? -1 : names.indexOf(current);
        String next = index + 1 < names.size() ? names.get(index + 1) : null;
        profiles.linkServer(s.ip, next);
        NightshadeClient.client().notifications().info("Profiles", next == null ? s.name + ": no profile" : "'" + next + "' loads when you join " + s.name);
    }

    private void openMenu(ServerData s, int x, int y) {
        menu.clear();
        menu.add(new MenuItem("Join", Theme.TEXT, () -> join(s)));
        if (isMine(s)) {
            menu.add(new MenuItem("Edit", Theme.TEXT, () -> edit(s)));
            menu.add(new MenuItem("Copy address", Theme.TEXT, () -> copy(s)));
            menu.add(new MenuItem("Move up", Theme.TEXT, () -> move(s, -1)));
            menu.add(new MenuItem("Move down", Theme.TEXT, () -> move(s, 1)));
            if (!NightshadeClient.client().profiles().list().isEmpty()) {
                String linked = NightshadeClient.client().profiles().profileForServer(s.ip);
                menu.add(new MenuItem(linked == null ? "Profile: none" : "Profile: " + linked, Theme.TEXT, () -> cycleProfile(s)));
            }
            menu.add(new MenuItem("Remove", 0xFFFF6B81, () -> delete(s)));
        } else {
            menu.add(new MenuItem("Add to my servers", Theme.TEXT, () -> addFeatured(s)));
            menu.add(new MenuItem("Copy address", Theme.TEXT, () -> copy(s)));
        }
        menuW = 0;
        for (MenuItem item : menu) {
            menuW = Math.max(menuW, Fonts.width(item.label()) + 20);
        }
        menuX = Math.min(x, width - menuW - 4);
        menuY = Math.min(y, height - menu.size() * 14 - 8);
    }

    private void closeMenu() {
        menu.clear();
    }

    private void copy(ServerData s) {
        minecraft.keyboardHandler.setClipboard(s.ip);
        NightshadeClient.client().notifications().info("Multiplayer", "Copied " + s.ip);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        pinger.removeAll();
        icons.values().forEach(icon -> {
            if (!icon.isClosed()) {
                icon.close(); // 26.3 throws when an icon is closed twice
            }
        });
        icons.clear();
        iconBytes.clear();
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Nullable
    private FaviconTexture icon(ServerData data) {
        byte[] bytes = data.getIconBytes();
        FaviconTexture icon = icons.get(data);
        if (bytes == null) {
            return null;
        }
        if (icon == null || !Arrays.equals(bytes, iconBytes.get(data))) {
            if (icon == null) {
                icon = FaviconTexture.forServer(minecraft.getTextureManager(), data.ip);
                icons.put(data, icon);
            }
            try {
                icon.upload(NativeImage.read(bytes));
                iconBytes.put(data, bytes);
            } catch (Exception e) {
                iconBytes.put(data, bytes);
                return null;
            }
        }
        return icon;
    }

    private float appear() {
        return Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1_000_000_000f) / 0.35f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        int accent = MenuBackground.accent();
        boolean menuOpen = !menu.isEmpty();
        int mx = menuOpen ? -1 : mouseX;
        int my = menuOpen ? -1 : mouseY;

        // Header: title, tabs, search
        ctx.fillGradient(0, 0, width, HEADER_H, Theme.tint(0xC00A0614), 0x000A0614);
        RenderUtil.roundedRect(ctx, 12, 9, 34, 31, 6, ColorUtil.withAlpha(accent, 0x55));
        PixelIcons.menu(ctx, PixelIcons.MENU_MULTIPLAYER, 14, 11, 2, 0xFFE9E4FF, MenuBackground.accentLight());
        Fonts.draw(ctx, "Multiplayer", 42, 11, 0xFFFFFFFF, false);
        int onlineCount = 0;
        for (int i = 0; i < servers.size(); i++) {
            if (online(servers.get(i))) {
                onlineCount++;
            }
        }
        Fonts.draw(ctx, servers.size() + (servers.size() == 1 ? " server" : " servers") + "  ·  " + onlineCount + " online", 42, 22, Theme.TEXT_DIM, false);
        renderTabs(ctx, mx, my, accent);
        boolean sf = search.isFocused();
        boolean sh = RenderUtil.isHovered(mx, my, searchX1, 9, searchX2, 29);
        RenderUtil.roundedRect(ctx, searchX1, 9, searchX2, 29, 10, sf ? Theme.tint(0xE0261848) : sh ? Theme.tint(0xB01C1236) : Theme.tint(0x90140B24));
        RenderUtil.roundedOutline(ctx, searchX1, 9, searchX2, 29, 10, sf ? ColorUtil.withAlpha(accent, 0xD0) : 0x30FFFFFF);
        PixelIcons.search(ctx, searchX1 + 6, 15, sf ? Theme.accentLight() : Theme.TEXT_DIM);
        search.extractRenderState(ctx, mouseX, mouseY, delta);

        // List
        float a = appear();
        int lift = Math.round((1f - a) * 10f);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, lift);
        String tooltip = renderList(ctx, mx, my - lift, accent);
        ctx.pose().popMatrix();

        // Footer
        int fy = height - FOOTER_H;
        ctx.fillGradient(0, fy - 8, width, height, 0x000A0614, Theme.tint(0xD00A0614));
        join.enabled(selected != null);
        NsButton[] buttons = tab == Tab.MINE ? new NsButton[]{back, refresh, direct, add, join} : new NsButton[]{back, refresh, direct, join};
        int bx = width - 12;
        for (int i = buttons.length - 1; i >= 0; i--) {
            int bw = Math.max(54, buttons[i].preferredWidth());
            bx -= bw;
            buttons[i].render(ctx, bx, fy + 8, bw, 18, mx, my);
            bx -= 6;
        }
        String hint = "Double-click to join  \u00B7  Right-click for more  \u00B7  F5 to refresh";
        if (14 + Fonts.width(hint) + 12 < bx) {
            Fonts.draw(ctx, hint, 14, fy + 13, Theme.TEXT_DIM, false);
        } else if (14 + Fonts.width("Right-click for more") + 12 < bx) {
            Fonts.draw(ctx, "Right-click for more", 14, fy + 13, Theme.TEXT_DIM, false);
        }

        if (menuOpen) {
            renderMenu(ctx, mouseX, mouseY, accent);
        }
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null && !menuOpen) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private void renderTabs(GuiGraphicsExtractor ctx, int mx, int my, int accent) {
        String[] labels = {"My servers  " + servers.size(), "Featured"};
        int total = Fonts.width(labels[0]) + Fonts.width(labels[1]) + 28;
        int x = width / 2 - total / 2;
        float lineTarget = 0;
        float lineW = 0;
        for (int i = 0; i < 2; i++) {
            int w = Fonts.width(labels[i]);
            tabRects[i] = new int[]{x - 6, 8, x + w + 6, 30};
            boolean active = tab.ordinal() == i;
            boolean hov = RenderUtil.isHovered(mx, my, x - 6, 8, x + w + 6, 30);
            Fonts.draw(ctx, labels[i], x, 15, active ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (active) {
                lineTarget = x;
                lineW = w;
            }
            x += w + 28;
        }
        if (!tabLineReady) {
            tabLine.snap(lineTarget);
            tabLineReady = true;
        }
        float lx = tabLine.setTarget(lineTarget).update();
        RenderUtil.roundedRect(ctx, Math.round(lx), 27, Math.round(lx + lineW), 29, 1, accent);
    }

    private String renderList(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int accent) {
        cardRects.clear();
        List<ServerData> list = visible();
        String tooltip = null;
        // section title
        String section = tab == Tab.MINE ? "Your servers" : "Featured PvP servers";
        Fonts.draw(ctx, section, cx1 + 2, HEADER_H + 6, Theme.TEXT, false);
        if (tab == Tab.FEATURED) {
            Fonts.draw(ctx, "Popular servers, with live status", cx1 + 6 + Fonts.width(section), HEADER_H + 6, Theme.TEXT_DIM, false);
        }
        int total = list.size() * (CARD_H + GAP);
        double max = Math.max(0, total - (listBottom - listTop));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        int offset = Math.round(scroll.setTarget((float) scrollTarget).update());
        boolean inside = mouseY >= listTop && mouseY < listBottom;
        ctx.enableScissor(cx1 - 4, listTop - 2, cx2 + 8, listBottom);
        for (int i = 0; i < list.size(); i++) {
            ServerData s = list.get(i);
            int y = listTop + i * (CARD_H + GAP) - offset;
            if (y + CARD_H < listTop || y > listBottom) {
                continue;
            }
            String t = renderCard(ctx, s, cx1, y, cx2, inside ? mouseX : -1, inside ? mouseY : -1, accent);
            if (t != null) {
                tooltip = t;
            }
        }
        ctx.disableScissor();
        if (list.isEmpty()) {
            int cy = (listTop + listBottom) / 2 - 20;
            RenderUtil.roundedRect(ctx, width / 2 - 18, cy - 14, width / 2 + 18, cy + 22, 8, ColorUtil.withAlpha(accent, 0x30));
            PixelIcons.menu(ctx, PixelIcons.MENU_MULTIPLAYER, width / 2 - 9, cy - 5, 2, 0xFFE9E4FF, MenuBackground.accentLight());
            boolean searching = !search.getValue().isBlank();
            Fonts.drawCentered(ctx, searching ? "No servers match \"" + search.getValue().trim() + "\"" : "No servers yet",
                    width / 2, cy + 30, 0xFFFFFFFF, false);
            Fonts.drawCentered(ctx, searching ? "Try another name or address" : "Press Add server, or pick one from Featured",
                    width / 2, cy + 42, Theme.TEXT_DIM, false);
        }
        if (max > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (double) total)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (offset / max));
            RenderUtil.roundedRect(ctx, cx2 + 4, listTop, cx2 + 6, listBottom, 1, 0x18FFFFFF);
            RenderUtil.roundedRect(ctx, cx2 + 4, thumbY, cx2 + 6, thumbY + thumbH, 1, ColorUtil.withAlpha(accent, 0xA0));
        }
        return tooltip;
    }

    private String renderCard(GuiGraphicsExtractor ctx, ServerData s, int x1, int y, int x2, int mouseX, int mouseY, int accent) {
        String tooltip = null;
        boolean hovered = RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + CARD_H);
        float h = hover.computeIfAbsent(s, k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
        boolean sel = s == selected;
        int y2 = y + CARD_H;
        if (sel) {
            Fx.glowRect(ctx, x1 - 8, y - 6, x2 + 8, y2 + 6, ColorUtil.withAlpha(accent, 0x30));
        }
        RenderUtil.roundedRect(ctx, x1, y, x2, y2, 8, sel ? Theme.tint(0xE0251843) : ColorUtil.lerp(Theme.tint(0xA0120A22), Theme.tint(0xC81E1438), h));
        if (sel || h > 0.01f) {
            RenderUtil.horizontalGradient(ctx, x1 + 1, y + 1, x1 + (x2 - x1) / 3, y2 - 1, ColorUtil.withAlpha(accent, sel ? 0x30 : Math.round(0x18 * h)), 0x00000000);
        }
        RenderUtil.roundedOutline(ctx, x1, y, x2, y2, 8, sel ? ColorUtil.withAlpha(accent, 0xE0) : ColorUtil.lerp(0x1CFFFFFF, ColorUtil.withAlpha(accent, 0x90), h));

        // icon
        int ix = x1 + 8;
        int iy = y + 7;
        FaviconTexture icon = icon(s);
        if (icon != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, icon.textureLocation(), ix, iy, 0f, 0f, 36, 36, 36, 36);
        } else {
            RenderUtil.roundedRect(ctx, ix, iy, ix + 36, iy + 36, 6, ColorUtil.withAlpha(accent, 0x45));
            PixelIcons.menu(ctx, PixelIcons.MENU_MULTIPLAYER, ix + 9, iy + 9, 2, 0xFFE9E4FF, MenuBackground.accentLight());
        }
        RenderUtil.roundedOutline(ctx, ix - 1, iy - 1, ix + 37, iy + 37, 6, 0x30FFFFFF);
        int tx = ix + 46;

        // right side: ping and players
        int rx = x2 - 10;
        String pingText;
        int pingColor;
        boolean failed = s.ping < 0 && !pinging(s);
        if (pinging(s)) {
            pingText = "...";
            pingColor = Theme.TEXT_DIM;
        } else if (failed) {
            pingText = "Offline";
            pingColor = 0xFFFF6B81;
        } else {
            pingText = s.ping + " ms";
            pingColor = s.ping < 80 ? 0xFF5BE49B : s.ping < 160 ? 0xFFB9E45B : s.ping < 300 ? 0xFFFFC857 : 0xFFFF8A3D;
        }
        int pw = Fonts.width(pingText);
        drawPing(ctx, rx - 14, y + 8, s);
        Fonts.draw(ctx, pingText, rx - 18 - pw, y + 9, pingColor, false);
        if (hovered && RenderUtil.isHovered(mouseX, mouseY, rx - 18 - pw, y + 6, rx, y + 18)) {
            tooltip = failed ? "Couldn't reach the server" : pinging(s) ? "Pinging..." : "Ping: " + s.ping + " ms";
        }
        int statsLeft = rx - 18 - pw;
        if (online(s)) {
            int on = s.players.online();
            int mx = Math.max(1, s.players.max());
            String players = String.format(Locale.ROOT, "%,d", on) + " / " + String.format(Locale.ROOT, "%,d", s.players.max());
            int plw = Fonts.width(players);
            Fonts.draw(ctx, players, rx - plw, y + 22, Theme.TEXT, false);
            int barW = Math.max(plw, 60);
            RenderUtil.roundedRect(ctx, rx - barW, y + 35, rx, y + 38, 1, 0x26FFFFFF);
            int fillW = Math.max(2, Math.round(barW * Math.min(1f, on / (float) mx)));
            RenderUtil.roundedRect(ctx, rx - barW, y + 35, rx - barW + fillW, y + 38, 1, accent);
            statsLeft = Math.min(statsLeft, rx - barW);
            if (hovered && !s.playerList.isEmpty() && RenderUtil.isHovered(mouseX, mouseY, rx - barW, y + 20, rx, y + 40)) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < Math.min(10, s.playerList.size()); i++) {
                    names.append(i == 0 ? "" : ", ").append(s.playerList.get(i).getString());
                }
                tooltip = names.toString();
            }
        }

        // Join pill on hover
        int[] joinRect = null;
        if (h > 0.05f) {
            int jw = 44;
            int jx2 = statsLeft - 10;
            int jx1 = jx2 - jw;
            int jy = y + CARD_H / 2 - 9;
            boolean jh = RenderUtil.isHovered(mouseX, mouseY, jx1, jy, jx2, jy + 18);
            RenderUtil.roundedRect(ctx, jx1, jy, jx2, jy + 18, 9, ColorUtil.withAlpha(jh ? MenuBackground.accentLight() : accent, Math.round(0xFF * h)));
            Fonts.drawCentered(ctx, "Join", (jx1 + jx2) / 2, jy + 5, ColorUtil.withAlpha(0xFFFFFF, Math.round(0xFF * h)), false);
            joinRect = new int[]{jx1, jy, jx2, jy + 18};
            statsLeft = jx1;
        }

        // name, tags and MOTD
        int textMax = statsLeft - 10 - tx;
        String name = Fonts.trim(s.name, textMax);
        Fonts.draw(ctx, name, tx, y + 8, 0xFFFFFFFF, false);
        int tagX = tx + Fonts.width(name) + 6;
        if (s.state() == ServerData.State.INCOMPATIBLE && s.version != null) {
            String v = s.version.getString();
            if (tagX + Fonts.width(v) + 8 < statsLeft - 6) {
                RenderUtil.roundedRect(ctx, tagX, y + 7, tagX + Fonts.width(v) + 8, y + 18, 3, 0x40FF8A3D);
                Fonts.draw(ctx, v, tagX + 4, y + 9, 0xFFFFB37A, false);
            }
            if (hovered) {
                tooltip = tooltip != null ? tooltip : "Runs " + v + " - you may need a different Minecraft version";
            }
        } else if (isMine(s)) {
            String linked = NightshadeClient.client().profiles().profileForServer(s.ip);
            if (linked != null && tagX + Fonts.width(linked) + 8 < statsLeft - 6) {
                RenderUtil.roundedRect(ctx, tagX, y + 7, tagX + Fonts.width(linked) + 8, y + 18, 3, 0x30FFFFFF);
                Fonts.draw(ctx, linked, tagX + 4, y + 9, Theme.TEXT, false);
            }
        }
        Component motd = s.motd == null ? Component.empty() : s.motd;
        if (failed) {
            motd = Component.literal(isMine(s) ? s.ip + "  ·  can't connect" : s.ip);
        }
        List<FormattedCharSequence> lines = font.split(motd, Math.max(20, textMax));
        int ly = y + 21;
        for (int l = 0; l < Math.min(2, lines.size()); l++) {
            ctx.text(font, lines.get(l), tx, ly, failed ? Theme.TEXT_DIM : 0xFFB9B2CF, false);
            ly += 10;
        }
        cardRects.add(new Object[]{new int[]{x1, y, x2, y2}, s, joinRect});
        return tooltip;
    }

    /** Signal bars coloured by latency, or a pulsing row of dots while pinging. */
    private void drawPing(GuiGraphicsExtractor ctx, int x, int y, ServerData s) {
        if (pinging(s)) {
            float t = Fx.time();
            for (int i = 0; i < 3; i++) {
                int alpha = 0x40 + Math.round(0xA0 * (0.5f + 0.5f * (float) Math.sin(t * 6 - i)));
                ctx.fill(x + i * 4, y + 6, x + i * 4 + 2, y + 8, ColorUtil.withAlpha(0xFFFFFF, alpha));
            }
            return;
        }
        int bars = s.ping < 0 ? 0 : s.ping < 80 ? 4 : s.ping < 160 ? 3 : s.ping < 300 ? 2 : 1;
        int color = bars >= 3 ? 0xFF5BE49B : bars == 2 ? 0xFFFFC857 : bars == 1 ? 0xFFFF8A3D : 0xFFFF6B81;
        for (int i = 0; i < 4; i++) {
            int hgt = 3 + i * 2;
            ctx.fill(x + i * 3, y + 10 - hgt, x + i * 3 + 2, y + 10, i < bars ? color : 0x30FFFFFF);
        }
    }

    private void renderMenu(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int accent) {
        int h = menu.size() * 14 + 6;
        Fx.shadow(ctx, menuX, menuY + 2, menuX + menuW, menuY + h + 2, 8, 0x90000000);
        RenderUtil.roundedRect(ctx, menuX, menuY, menuX + menuW, menuY + h, 6, Theme.tint(0xF2140C26));
        RenderUtil.roundedOutline(ctx, menuX, menuY, menuX + menuW, menuY + h, 6, ColorUtil.withAlpha(accent, 0x80));
        for (int i = 0; i < menu.size(); i++) {
            int iy = menuY + 3 + i * 14;
            boolean hov = RenderUtil.isHovered(mouseX, mouseY, menuX + 2, iy, menuX + menuW - 2, iy + 14);
            if (hov) {
                RenderUtil.roundedRect(ctx, menuX + 3, iy, menuX + menuW - 3, iy + 14, 4, ColorUtil.withAlpha(accent, 0x60));
            }
            Fonts.draw(ctx, menu.get(i).label(), menuX + 10, iy + 3, hov ? 0xFFFFFFFF : menu.get(i).color(), false);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!menu.isEmpty()) {
            int h = menu.size() * 14 + 6;
            if (RenderUtil.isHovered(mouseX, mouseY, menuX, menuY, menuX + menuW, menuY + h)) {
                int index = (int) ((mouseY - menuY - 3) / 14);
                if (index >= 0 && index < menu.size()) {
                    Runnable action = menu.get(index).action();
                    closeMenu();
                    action.run();
                }
                return true;
            }
            closeMenu();
            return true;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, searchX1, 9, searchX2, 29)) {
            setFocused(search);
            search.setFocused(true);
            return super.onMouseClicked(mouseX, mouseY, button) || true;
        }
        search.setFocused(false);
        for (int i = 0; i < 2; i++) {
            int[] r = tabRects[i];
            if (r != null && RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                switchTab(Tab.values()[i]);
                return true;
            }
        }
        for (NsButton b : new NsButton[]{join, direct, add, refresh, back}) {
            if ((b != add || tab == Tab.MINE) && b.click(mouseX, mouseY, button)) {
                return true;
            }
        }
        double my = mouseY - Math.round((1f - appear()) * 10f);
        if (my >= listTop && my < listBottom) {
            for (Object[] card : List.copyOf(cardRects)) {
                int[] r = (int[]) card[0];
                ServerData s = (ServerData) card[1];
                if (!RenderUtil.isHovered(mouseX, my, r[0], r[1], r[2], r[3])) {
                    continue;
                }
                if (button == 1) {
                    selected = s;
                    openMenu(s, (int) mouseX, (int) mouseY);
                    return true;
                }
                if (button != 0) {
                    return true;
                }
                int[] jr = (int[]) card[2];
                if (jr != null && RenderUtil.isHovered(mouseX, my, jr[0], jr[1], jr[2], jr[3])) {
                    selected = s;
                    join(s);
                    return true;
                }
                long now = System.currentTimeMillis();
                boolean again = s == selected && now - lastClick < 350;
                selected = s;
                lastClick = now;
                if (again) {
                    join(s);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        closeMenu();
        scrollTarget -= verticalAmount * 32;
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!menu.isEmpty() && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeMenu();
            return true;
        }
        if (search.isFocused()) {
            if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                search.setFocused(false);
                return true;
            }
            if (keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_RETURN && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_DOWN && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_UP) {
                return false; // typing goes to the search box
            }
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            joinSelected();
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_F5) {
            refresh();
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
            switchTab(tab == Tab.MINE ? Tab.FEATURED : Tab.MINE);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE && selected != null && isMine(selected)) {
            delete(selected);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_UP) {
            List<ServerData> list = visible();
            if (!list.isEmpty()) {
                int i = Math.max(0, list.indexOf(selected));
                i = Math.max(0, Math.min(list.size() - 1, i + (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN ? 1 : -1)));
                selected = list.get(i);
                int y = i * (CARD_H + GAP);
                int view = listBottom - listTop;
                if (y < scrollTarget) {
                    scrollTarget = y;
                } else if (y + CARD_H > scrollTarget + view) {
                    scrollTarget = y + CARD_H - view;
                }
            }
            return true;
        }
        return false;
    }

    /** Test hooks. */
    public int serverCountForTest() {
        return servers == null ? 0 : servers.size();
    }

    @Nullable
    public ServerData serverForTest(int index) {
        return servers.get(index);
    }

    public void selectForTest(int index) {
        selected = servers.get(index);
    }

    public void joinForTest() {
        joinSelected();
    }

    public void featuredForTest() {
        switchTab(Tab.FEATURED);
    }

    public void menuForTest(int index) {
        selected = servers.get(index);
        openMenu(selected, width / 2, height / 2);
    }

    public int menuSizeForTest() {
        return menu.size();
    }
}
