package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.module.utility.FightReplay;
import com.nightshade.replay.ReplayClip;
import com.nightshade.replay.ReplayPlayer;
import com.nightshade.replay.ReplayRecorder;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Saved fight replays: watch, delete, save the last seconds now, open the folder. */
public final class ReplaysScreen extends NsMenuScreen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss").withZone(ZoneId.systemDefault());
    @Nullable
    private final Screen parent;
    private final List<Path> files = new ArrayList<>();
    private final Map<Path, ReplayClip> headers = new HashMap<>();
    private final Map<Path, Animation> hover = new HashMap<>();
    private int selected = -1;
    private long lastClick;
    private int px1, py1, px2, py2, listTop, listBottom;
    private double scroll;
    private final NsButton watch = new NsButton("Watch", NsButton.Style.PRIMARY, this::watchSelected);
    private final NsButton saveNow = new NsButton("Save last 30s", NsButton.Style.SECONDARY, this::saveNow);
    private final NsButton delete = new NsButton("Delete", NsButton.Style.DANGER, this::deleteSelected);
    private final NsButton folder = new NsButton("Folder", NsButton.Style.SECONDARY, this::openFolder);
    private final NsButton done = new NsButton("Done", NsButton.Style.GHOST, this::onClose);

    public ReplaysScreen(@Nullable Screen parent) {
        super(Component.literal("Replays"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reload();
    }

    private void reload() {
        files.clear();
        Path dir = ReplayRecorder.folder();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(f -> f.getFileName().toString().endsWith(".nsr"))
                        .sorted(Comparator.comparing((Path f) -> f.getFileName().toString()).reversed())
                        .forEach(files::add);
            } catch (Exception ignored) {
                // empty list
            }
        }
        selected = files.isEmpty() ? -1 : Math.max(0, Math.min(selected, files.size() - 1));
    }

    @Nullable
    private ReplayClip header(Path f) {
        return headers.computeIfAbsent(f, k -> {
            try {
                return ReplayClip.read(k);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private void layout() {
        int w = Math.min(width - 32, 600);
        int h = height - 4;
        px1 = (width - w) / 2;
        px2 = px1 + w;
        py1 = 2;
        py2 = py1 + h;
        listTop = py1 + 40;
        listBottom = py2 - 32;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        renderPage(ctx, px1, px2);
        Fonts.draw(ctx, "Replays", px1 + 14, py1 + 11, Theme.TEXT, false);
        boolean recording = NightshadeClient.client().modules().get(FightReplay.class) != null
                && NightshadeClient.client().modules().get(FightReplay.class).isEnabled();
        String sub = recording ? "Fight Replay is recording - " + ReplayRecorder.bufferedTicks() / 20 + "s buffered"
                : "Turn on Fight Replay (Utility) to record fights";
        Fonts.draw(ctx, sub, px1 + 14, py1 + 22, recording ? Theme.SUCCESS : Theme.TEXT_DIM, false);

        int x1 = px1 + 12;
        int x2 = px2 - 12;
        int rowH = 30;
        ctx.enableScissor(x1 - 2, listTop, x2 + 2, listBottom);
        for (int i = 0; i < files.size(); i++) {
            int y = listTop + i * (rowH + 4) - (int) scroll;
            if (y + rowH < listTop || y > listBottom) {
                continue;
            }
            Path f = files.get(i);
            ReplayClip c = header(f);
            boolean hovered = mouseY >= listTop && mouseY < listBottom && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + rowH);
            float h = hover.computeIfAbsent(f, k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
            boolean sel = i == selected;
            RenderUtil.roundedRect(ctx, x1, y, x2, y + rowH, 5, sel ? ColorUtil.withAlpha(Theme.accent(), 0x50) : ColorUtil.lerp(0x40000000, 0x30FFFFFF, h));
            if (sel) {
                RenderUtil.roundedOutline(ctx, x1, y, x2, y + rowH, 5, ColorUtil.withAlpha(Theme.accent(), 0xC0));
            }
            PixelIcons.menu(ctx, PixelIcons.MENU_PLAY, x1 + 8, y + (rowH - 9) / 2, 1, sel ? 0xFFFFFFFF : Theme.TEXT_MUTED, 0);
            String title = c == null ? f.getFileName().toString() + " (unreadable)"
                    : DATE.format(Instant.ofEpochMilli(c.recordedAt)) + "   " + c.server;
            String info = c == null ? "" : String.format(java.util.Locale.ROOT, "%.0fs  ·  %d players  ·  %s", c.seconds(), c.names.size(),
                    String.join(", ", c.names.values()));
            Fonts.draw(ctx, Fonts.trim(title, x2 - x1 - 30), x1 + 22, y + 5, Theme.TEXT, false);
            Fonts.draw(ctx, Fonts.trim(info, x2 - x1 - 30), x1 + 22, y + 16, Theme.TEXT_DIM, false);
        }
        ctx.disableScissor();
        if (files.isEmpty()) {
            Fonts.drawCentered(ctx, "No replays yet - turn on Fight Replay and press Save Replay after a fight", (px1 + px2) / 2, listTop + 20, Theme.TEXT_DIM, false);
        }
        boolean has = selected >= 0 && selected < files.size();
        watch.enabled(has && minecraft.level != null);
        delete.enabled(has);
        saveNow.enabled(minecraft.level != null && ReplayRecorder.bufferedTicks() > 0);
        NsButton[] buttons = {watch, saveNow, delete, folder, done};
        int bw = (px2 - px1 - 24 - 6 * (buttons.length - 1)) / buttons.length;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].render(ctx, px1 + 12 + i * (bw + 6), py2 - 25, bw, 17, mouseX, mouseY);
        }
        if (has && minecraft.level == null && RenderUtil.isHovered(mouseX, mouseY, px1 + 12, py2 - 25, px1 + 12 + bw, py2 - 8)) {
            com.nightshade.gui.GuiDraw.tooltip(ctx, "Join a world first - replays play back in the world you are in", mouseX, mouseY, width, height);
        }
        Notifications.renderIfEnabled(ctx);
    }

    private void watchSelected() {
        if (selected < 0 || selected >= files.size() || minecraft.level == null) {
            return;
        }
        ReplayClip clip = header(files.get(selected));
        if (clip == null) {
            NightshadeClient.client().notifications().warn("Replays", "That file could not be read");
            return;
        }
        String server = minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "singleplayer";
        boolean samePlace = server.equals(clip.server) && minecraft.level.dimension().toString().equals(clip.dimension);
        ReplayPlayer.start(clip, !samePlace);
        if (ReplayPlayer.active()) {
            if (!samePlace) {
                NightshadeClient.client().notifications().info("Replays", "Recorded elsewhere - playing it around you");
            }
            minecraft.gui.setScreen(new ReplayControlsScreen());
        }
    }

    private void saveNow() {
        FightReplay.saveClip();
        reload();
    }

    private void deleteSelected() {
        if (selected < 0 || selected >= files.size()) {
            return;
        }
        Path f = files.get(selected);
        minecraft.gui.setScreen(new ConfirmScreen(ok -> {
            if (ok) {
                try {
                    Files.deleteIfExists(f);
                } catch (Exception ignored) {
                    // stays in the list
                }
                headers.remove(f);
                reload();
            }
            minecraft.gui.setScreen(this);
        }, Component.literal("Delete this replay?"), Component.literal(f.getFileName().toString())));
    }

    private void openFolder() {
        try {
            Files.createDirectories(ReplayRecorder.folder());
        } catch (Exception ignored) {
            // opening reports it
        }
        Path dir = ReplayRecorder.folder();
        net.minecraft.util.Util.getPlatform().openFile(dir.toFile());
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        for (NsButton b : new NsButton[]{watch, saveNow, delete, folder, done}) {
            if (b.click(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == 0 && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop + scroll) / 34);
            if (index >= 0 && index < files.size()) {
                long now = System.currentTimeMillis();
                boolean again = index == selected && now - lastClick < 350;
                selected = index;
                lastClick = now;
                if (again) {
                    watchSelected();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(Math.max(0, files.size() * 34 - (listBottom - listTop)), scroll - verticalAmount * 24));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    /** Test hooks. */
    public int countForTest() {
        return files.size();
    }

    public void watchFirstForTest() {
        selected = files.isEmpty() ? -1 : 0;
        watchSelected();
    }
}
