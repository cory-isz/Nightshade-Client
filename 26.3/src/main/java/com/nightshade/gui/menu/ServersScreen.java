package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.MenuBackground;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Nightshade multiplayer screen: your saved servers as cards with live status, on the menu background. */
public final class ServersScreen extends NsMenuScreen {
    private static final int ROW_H = 40;
    private static final int GAP = 4;
    private static final ExecutorService PING_POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Nightshade server ping");
        t.setDaemon(true);
        return t;
    });

    @Nullable
    private final Screen parent;
    private ServerList servers;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final Map<ServerData, FaviconTexture> icons = new HashMap<>();
    private final Map<ServerData, byte[]> iconBytes = new HashMap<>();
    private final Map<ServerData, com.nightshade.render.Animation> hover = new HashMap<>();
    private final com.nightshade.render.Animation scroll = new com.nightshade.render.Animation(18f, 0f);
    private double scrollTarget;
    private int selected = -1;
    private long lastClick;
    private long openedAt = System.nanoTime();

    private final NsButton join = new NsButton("Join", NsButton.Style.PRIMARY, this::joinSelected);
    private final NsButton direct = new NsButton("Direct", NsButton.Style.SECONDARY, this::directConnect);
    private final NsButton add = new NsButton("Add", NsButton.Style.SECONDARY, this::addServer);
    private final NsButton edit = new NsButton("Edit", NsButton.Style.SECONDARY, this::editSelected);
    private final NsButton delete = new NsButton("Delete", NsButton.Style.GHOST, this::deleteSelected);
    private final NsButton refresh = new NsButton("Refresh", NsButton.Style.GHOST, this::refresh);
    private final NsButton back = new NsButton("Back", NsButton.Style.GHOST, this::onClose);

    private int px1;
    private int py1;
    private int px2;
    private int py2;
    private int listTop;
    private int listBottom;

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
            pingAll();
            if (servers.size() > 0 && selected < 0) {
                selected = 0;
            }
        }
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
    }

    private void layout() {
        int w = Math.min(width - 24, 480);
        int h = Math.min(height - 24, 320);
        px1 = (width - w) / 2;
        px2 = px1 + w;
        py1 = (height - h) / 2;
        py2 = py1 + h;
        listTop = py1 + 40;
        listBottom = py2 - 32;
    }

    private void pingAll() {
        for (int i = 0; i < servers.size(); i++) {
            ping(servers.get(i));
        }
    }

    private void ping(ServerData data) {
        data.setState(ServerData.State.PINGING);
        data.motd = Component.literal("Pinging...");
        data.ping = -1L;
        data.playerList = List.of();
        PING_POOL.submit(() -> {
            try {
                pinger.pingServer(data, () -> minecraft.execute(() -> {}), () -> {
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

    private void refresh() {
        pinger.removeAll();
        pingAll();
    }

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
    }

    @Nullable
    private ServerData selectedServer() {
        return selected >= 0 && selected < servers.size() ? servers.get(selected) : null;
    }

    // ------------------------------------------------------------------ actions

    private void join(ServerData data) {
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    private void joinSelected() {
        ServerData data = selectedServer();
        if (data != null) {
            join(data);
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
                selected = servers.size() - 1;
                ping(data);
            }
            minecraft.gui.setScreen(this);
        }, data));
    }

    private void editSelected() {
        ServerData original = selectedServer();
        if (original == null) {
            return;
        }
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

    private void deleteSelected() {
        ServerData data = selectedServer();
        if (data == null) {
            return;
        }
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                servers.remove(data);
                servers.save();
                FaviconTexture icon = icons.remove(data);
                if (icon != null && !icon.isClosed()) {
                    icon.close();
                }
                selected = Math.min(selected, servers.size() - 1);
            }
            minecraft.gui.setScreen(this);
        }, Component.literal("Remove \"" + data.name + "\"?"), Component.literal("It will be removed from your server list."),
                Component.literal("Remove"), Component.literal("Cancel")));
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        float a = Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1_000_000_000f) / 0.35f);
        int lift = Math.round((1f - a) * 12f);
        int my = mouseY - lift;
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, lift);
        int accent = MenuBackground.accent();
        Fx.shadow(ctx, px1, py1 + 3, px2, py2 + 3, 12, 0xA0000000);
        RenderUtil.roundedRect(ctx, px1, py1, px2, py2, 8, com.nightshade.render.Theme.tint(0xC4100A1E));
        ctx.fill(px1 + 8, py1 + 1, px2 - 8, py1 + 2, 0x18FFFFFF);
        RenderUtil.roundedOutline(ctx, px1, py1, px2, py2, 8, ColorUtil.withAlpha(accent, 0x70));

        RenderUtil.roundedRect(ctx, px1 + 12, py1 + 9, px1 + 34, py1 + 31, 5, ColorUtil.withAlpha(accent, 0x60));
        PixelIcons.menu(ctx, PixelIcons.MENU_MULTIPLAYER, px1 + 14, py1 + 11, 2, 0xFFE9E4FF, MenuBackground.accentLight());
        Fonts.draw(ctx, "Multiplayer", px1 + 42, py1 + 11, Theme.TEXT, false);
        Fonts.draw(ctx, servers.size() + (servers.size() == 1 ? " server" : " servers"), px1 + 42, py1 + 22, Theme.TEXT_DIM, false);

        String tooltip = renderList(ctx, mouseX, my, accent);

        boolean has = selectedServer() != null;
        join.enabled(has);
        edit.enabled(has);
        delete.enabled(has);
        int by = py2 - 25;
        NsButton[] buttons = {join, direct, add, edit, delete, refresh, back};
        int bw = (px2 - px1 - 24 - 6 * (buttons.length - 1)) / buttons.length;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].render(ctx, px1 + 12 + i * (bw + 6), by, bw, 17, mouseX, my);
        }
        ctx.pose().popMatrix();
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null) {
            com.nightshade.gui.GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private final List<Object[]> profileChips = new ArrayList<>();

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

    private String renderList(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int accent) {
        profileChips.clear();
        int x1 = px1 + 12;
        int x2 = px2 - 12;
        int n = servers.size();
        int total = n * (ROW_H + GAP);
        double max = Math.max(0, total - (listBottom - listTop));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        int offset = Math.round(scroll.setTarget((float) scrollTarget).update());
        String tooltip = null;
        ctx.enableScissor(x1 - 2, listTop, x2 + 2, listBottom);
        for (int i = 0; i < n; i++) {
            ServerData s = servers.get(i);
            int y = listTop + i * (ROW_H + GAP) - offset;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            boolean inside = mouseY >= listTop && mouseY < listBottom;
            boolean hovered = inside && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H);
            float h = hover.computeIfAbsent(s, k -> new com.nightshade.render.Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
            boolean sel = i == selected;
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H, 6, sel ? com.nightshade.render.Theme.tint(0xE02A1A4C) : ColorUtil.lerp(0x80140B24, com.nightshade.render.Theme.tint(0xC0231740), h));
            RenderUtil.roundedOutline(ctx, x1, y, x2, y + ROW_H, 6, sel ? ColorUtil.withAlpha(accent, 0xE0) : ColorUtil.lerp(0x20FFFFFF, ColorUtil.withAlpha(accent, 0x90), h));
            if (sel) {
                RenderUtil.roundedRect(ctx, x1 + 2, y + 8, x1 + 4, y + ROW_H - 8, 1, accent);
            }
            int ix = x1 + 6;
            int iy = y + 4;
            FaviconTexture icon = icon(s);
            if (icon != null) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, icon.textureLocation(), ix, iy, 0f, 0f, 32, 32, 32, 32);
            } else {
                RenderUtil.roundedRect(ctx, ix, iy, ix + 32, iy + 32, 4, ColorUtil.withAlpha(accent, 0x40));
                PixelIcons.menu(ctx, PixelIcons.MENU_MULTIPLAYER, ix + 7, iy + 7, 2, 0xFFE9E4FF, MenuBackground.accentLight());
            }
            int tx = ix + 40;
            // Right side: players and ping bars
            String players = s.players != null ? s.players.online() + "/" + s.players.max() : "";
            int barsX = x2 - 20;
            drawPing(ctx, barsX, y + 6, s);
            if (!players.isEmpty()) {
                Fonts.draw(ctx, players, barsX - 6 - Fonts.width(players), y + 6, Theme.TEXT_MUTED, false);
            }
            if (hovered && RenderUtil.isHovered(mouseX, mouseY, barsX, y + 4, barsX + 12, y + 14)) {
                boolean pinging = s.ping < 0 && s.motd != null && s.motd.getString().equals("Pinging...");
                tooltip = s.ping >= 0 ? s.ping + " ms" : pinging ? "Pinging..." : "No connection";
            }
            int nameMax = barsX - 8 - (players.isEmpty() ? 0 : Fonts.width(players) + 6) - tx;
            Fonts.draw(ctx, Fonts.trim(s.name, nameMax), tx, y + 6, Theme.TEXT, false);
            List<FormattedCharSequence> motd = font.split(s.motd == null ? Component.empty() : s.motd, x2 - tx - 8);
            int ly = y + 17;
            for (int l = 0; l < Math.min(2, motd.size()); l++) {
                ctx.text(font, motd.get(l), tx, ly, Theme.TEXT_MUTED, false);
                ly += 10;
            }
            // Profile chip (bottom right): which saved setup loads on this server.
            if (!NightshadeClient.client().profiles().list().isEmpty() || NightshadeClient.client().profiles().profileForServer(s.ip) != null) {
                String linked = NightshadeClient.client().profiles().profileForServer(s.ip);
                String chip = linked == null ? "+ Profile" : "Profile: " + linked;
                int cw = Fonts.width(chip) + 10;
                int cx2 = x2 - 6;
                int cy = y + ROW_H - 15;
                boolean ch = inside && RenderUtil.isHovered(mouseX, mouseY, cx2 - cw, cy, cx2, cy + 11);
                RenderUtil.roundedRect(ctx, cx2 - cw, cy, cx2, cy + 11, 3, linked != null ? ColorUtil.withAlpha(accent, ch ? 0xC0 : 0x70) : (ch ? 0x40FFFFFF : 0x22FFFFFF));
                Fonts.draw(ctx, chip, cx2 - cw + 5, cy + 2, linked != null ? 0xFFFFFFFF : Theme.TEXT_MUTED, false);
                profileChips.add(new Object[]{new int[]{cx2 - cw, cy, cx2, cy + 11}, s});
                if (ch) {
                    tooltip = "Click to choose the profile that loads when you join this server";
                }
            }
            if (s.state() == ServerData.State.INCOMPATIBLE && hovered) {
                tooltip = "Different version: " + (s.version == null ? "?" : s.version.getString());
            }
        }
        ctx.disableScissor();
        if (n == 0) {
            Fonts.drawCentered(ctx, "No servers yet - press Add or Direct", (x1 + x2) / 2, listTop + 20, Theme.TEXT_DIM, false);
        }
        if (max > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (double) total)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (offset / max));
            RenderUtil.roundedRect(ctx, x2 + 4, thumbY, x2 + 6, thumbY + thumbH, 1, ColorUtil.withAlpha(accent, 0x90));
        }
        return tooltip;
    }

    /** Five signal bars coloured by latency (or a pulsing dot while pinging). */
    private void drawPing(GuiGraphicsExtractor ctx, int x, int y, ServerData s) {
        // The vanilla pinger reports a failure only through the MOTD, so treat "still pinging, but a failure MOTD" as offline.
        boolean failed = s.ping < 0 && s.motd != null && !s.motd.getString().equals("Pinging...");
        if (!failed && (s.state() == ServerData.State.PINGING || s.state() == ServerData.State.INITIAL)) {
            float t = Fx.time();
            for (int i = 0; i < 3; i++) {
                int alpha = 0x40 + Math.round(0xA0 * (0.5f + 0.5f * (float) Math.sin(t * 6 - i)));
                ctx.fill(x + i * 4, y + 4, x + i * 4 + 2, y + 6, ColorUtil.withAlpha(0xFFFFFF, alpha));
            }
            return;
        }
        int bars = s.ping < 0 ? 0 : s.ping < 150 ? 5 : s.ping < 300 ? 4 : s.ping < 600 ? 3 : s.ping < 1000 ? 2 : 1;
        int color = bars >= 4 ? 0xFF5BE49B : bars == 3 ? 0xFFFFC857 : bars > 0 ? 0xFFFF8A3D : 0xFFFF5C7A;
        for (int i = 0; i < 5; i++) {
            int hgt = 2 + i * 2;
            ctx.fill(x + i * 2 + i, y + 10 - hgt, x + i * 2 + i + 2, y + 10, i < bars ? color : 0x40FFFFFF);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        float a = Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1_000_000_000f) / 0.35f);
        mouseY -= Math.round((1f - a) * 12f);
        for (NsButton b : new NsButton[]{join, direct, add, edit, delete, refresh, back}) {
            if (b.click(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == 0 && mouseY >= listTop && mouseY < listBottom) {
            for (Object[] chip : List.copyOf(profileChips)) {
                int[] r = (int[]) chip[0];
                if (RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    cycleProfile((ServerData) chip[1]);
                    return true;
                }
            }
        }
        if (button == 0 && mouseY >= listTop && mouseY < listBottom && mouseX >= px1 + 12 && mouseX < px2 - 12) {
            int index = (int) ((mouseY - listTop + scroll.get()) / (ROW_H + GAP));
            if (index >= 0 && index < servers.size()) {
                long now = System.currentTimeMillis();
                boolean again = index == selected && now - lastClick < 350;
                selected = index;
                lastClick = now;
                if (again) {
                    joinSelected();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= verticalAmount * 30;
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            joinSelected();
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_F5) {
            refresh();
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_UP) {
            if (servers.size() > 0) {
                selected = Math.max(0, Math.min(servers.size() - 1, selected + (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN ? 1 : -1)));
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
        selected = index;
    }

    public void joinForTest() {
        joinSelected();
    }
}
