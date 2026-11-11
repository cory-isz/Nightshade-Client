package com.nightshade.gui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Screenshot manager: every screenshot as a thumbnail grid (newest first). Click to select, double-click (or Enter)
 * for a large view with arrow-key browsing; open the file, delete it or open the folder. Thumbnails are decoded and
 * downscaled in the background and only the ones on screen are kept as textures.
 */
public final class ScreenshotsScreen extends NsMenuScreen {
    private static final int THUMB_W = 192;
    private static final int THUMB_H = 108;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    @Nullable
    private final Screen parent;
    private final List<Path> files = new ArrayList<>();
    private final Map<Path, Identifier> thumbs = new HashMap<>();
    private final Set<Path> loading = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Object[]> ready = new ConcurrentLinkedQueue<>();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nightshade screenshot thumbnails");
        t.setDaemon(true);
        return t;
    });
    private final Map<Path, Animation> hover = new HashMap<>();
    private final Animation scroll = new Animation(18f, 0f);
    private double scrollTarget;
    private int selected = -1;
    private long lastClick;
    private boolean viewing;
    @Nullable
    private Identifier full;
    @Nullable
    private Path fullPath;
    private long openedAt = System.nanoTime();
    private int px1, py1, px2, py2, gridTop, gridBottom, cols, cellW, cellH;

    private final NsButton view = new NsButton("View", NsButton.Style.PRIMARY, this::openViewer);
    private final NsButton open = new NsButton("Open", NsButton.Style.SECONDARY, this::openFile);
    private final NsButton delete = new NsButton("Delete", NsButton.Style.DANGER, this::deleteSelected);
    private final NsButton folder = new NsButton("Folder", NsButton.Style.SECONDARY, this::openFolder);
    private final NsButton done = new NsButton("Done", NsButton.Style.GHOST, this::onClose);

    public ScreenshotsScreen(@Nullable Screen parent) {
        super(Component.literal("Screenshots"));
        this.parent = parent;
    }

    public static Path folderPath() {
        return FabricLoader.getInstance().getGameDir().resolve("screenshots");
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
    }

    @Override
    protected void init() {
        reload();
    }

    private void reload() {
        files.clear();
        Path dir = folderPath();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                        .sorted(Comparator.comparingLong(ScreenshotsScreen::modified).reversed())
                        .forEach(files::add);
            } catch (Exception e) {
                Log.warn("Could not list screenshots: {}", e.toString());
            }
        }
        selected = files.isEmpty() ? -1 : Math.min(Math.max(selected, 0), files.size() - 1);
    }

    private static long modified(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    // ------------------------------------------------------------------ textures

    private void requestThumb(Path file) {
        if (thumbs.containsKey(file) || !loading.add(file)) {
            return;
        }
        decoder.execute(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                NativeImage thumb = new NativeImage(THUMB_W, THUMB_H, false);
                // crop to 16:9 from the centre, then scale down
                int w = image.getWidth();
                int h = image.getHeight();
                int cw = Math.min(w, h * 16 / 9);
                int ch = Math.min(h, w * 9 / 16);
                image.resizeSubRectTo((w - cw) / 2, (h - ch) / 2, cw, ch, thumb);
                image.close();
                ready.add(new Object[]{file, thumb, Boolean.FALSE});
            } catch (Exception e) {
                ready.add(new Object[]{file, null, Boolean.FALSE});
            }
        });
    }

    private void requestFull(Path file) {
        decoder.execute(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                ready.add(new Object[]{file, NativeImage.read(in), Boolean.TRUE});
            } catch (Exception e) {
                ready.add(new Object[]{file, null, Boolean.TRUE});
            }
        });
    }

    /** Uploads decoded images on the render thread. */
    private void uploadReady() {
        Object[] item;
        while ((item = ready.poll()) != null) {
            Path file = (Path) item[0];
            NativeImage image = (NativeImage) item[1];
            boolean isFull = (Boolean) item[2];
            loading.remove(file);
            if (image == null) {
                if (!isFull) {
                    thumbs.put(file, null);
                }
                continue;
            }
            String key = Integer.toHexString(file.toString().hashCode()) + (isFull ? "_full" : "_thumb");
            Identifier id = Identifier.fromNamespaceAndPath("nightshade", "screenshot/" + key);
            minecraft.getTextureManager().register(id, new DynamicTexture(() -> "Nightshade screenshot " + file.getFileName(), image));
            if (isFull) {
                if (viewing && file.equals(fullPath)) {
                    releaseFull();
                    full = id;
                } else {
                    minecraft.getTextureManager().release(id);
                }
            } else {
                thumbs.put(file, id);
            }
        }
    }

    private void releaseFull() {
        if (full != null) {
            minecraft.getTextureManager().release(full);
            full = null;
        }
    }

    @Override
    public void removed() {
        decoder.shutdownNow();
        thumbs.values().forEach(id -> {
            if (id != null) {
                minecraft.getTextureManager().release(id);
            }
        });
        thumbs.clear();
        releaseFull();
        super.removed();
    }

    // ------------------------------------------------------------------ layout & rendering

    private void layout() {
        int w = Math.min(width - 24, 620);
        int h = Math.min(height - 24, 360);
        px1 = (width - w) / 2;
        px2 = px1 + w;
        py1 = (height - h) / 2;
        py2 = py1 + h;
        gridTop = py1 + 40;
        gridBottom = py2 - 32;
        int inner = w - 24;
        cols = Math.max(2, Math.min(5, inner / 120));
        int gap = 6;
        cellW = (inner - gap * (cols - 1)) / cols;
        cellH = cellW * 9 / 16 + 14;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        uploadReady();
        layout();
        float a = Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1e9f) / 0.35f);
        Fx.shadow(ctx, px1, py1 + 3, px2, py2 + 3, 12, 0xA0000000);
        RenderUtil.roundedRect(ctx, px1, py1, px2, py2, 8, ColorUtil.withAlpha(Theme.BACKGROUND, 0xD0));
        RenderUtil.roundedOutline(ctx, px1, py1, px2, py2, 8, Theme.BORDER);
        int mid = (px1 + px2) / 2;
        RenderUtil.horizontalGradient(ctx, px1 + 8, py1, mid, py1 + 1, 0x00000000, ColorUtil.withAlpha(Theme.accentLight(), 0xB0));
        RenderUtil.horizontalGradient(ctx, mid, py1, px2 - 8, py1 + 1, ColorUtil.withAlpha(Theme.accentLight(), 0xB0), 0x00000000);
        Fonts.draw(ctx, "Screenshots", px1 + 14, py1 + 11, Theme.TEXT, false);
        String sub = files.isEmpty() ? "None yet - press F2 in game to take one" : files.size() + (files.size() == 1 ? " screenshot" : " screenshots");
        Fonts.draw(ctx, sub, px1 + 14, py1 + 22, Theme.TEXT_DIM, false);
        if (selected >= 0 && selected < files.size()) {
            Path f = files.get(selected);
            String info = DATE.format(Instant.ofEpochMilli(modified(f)));
            Fonts.draw(ctx, info, px2 - 14 - Fonts.width(info), py1 + 22, Theme.TEXT_MUTED, false);
        }

        renderGrid(ctx, mouseX, mouseY);

        boolean has = selected >= 0 && selected < files.size();
        view.enabled(has);
        open.enabled(has);
        delete.enabled(has);
        NsButton[] buttons = {view, open, delete, folder, done};
        int bw = (px2 - px1 - 24 - 6 * (buttons.length - 1)) / buttons.length;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].render(ctx, px1 + 12 + i * (bw + 6), py2 - 25, bw, 17, viewing ? -1 : mouseX, viewing ? -1 : mouseY);
        }
        if (viewing) {
            renderViewer(ctx);
        }
        Notifications.renderIfEnabled(ctx);
    }

    private void renderGrid(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x0 = px1 + 12;
        int rows = (files.size() + cols - 1) / cols;
        int total = rows * (cellH + 6);
        double max = Math.max(0, total - (gridBottom - gridTop));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        int offset = Math.round(scroll.setTarget((float) scrollTarget).update());
        ctx.enableScissor(px1 + 8, gridTop, px2 - 8, gridBottom);
        for (int i = 0; i < files.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = x0 + col * (cellW + 6);
            int y = gridTop + row * (cellH + 6) - offset;
            if (y + cellH < gridTop || y > gridBottom) {
                continue;
            }
            Path f = files.get(i);
            requestThumb(f);
            boolean hovered = !viewing && mouseY >= gridTop && mouseY < gridBottom && RenderUtil.isHovered(mouseX, mouseY, x, y, x + cellW, y + cellH);
            float h = hover.computeIfAbsent(f, k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
            boolean sel = i == selected;
            int imgH = cellH - 14;
            RenderUtil.roundedRect(ctx, x, y, x + cellW, y + cellH, 5, sel ? ColorUtil.withAlpha(Theme.accent(), 0x50) : ColorUtil.lerp(0x40000000, 0x60FFFFFF & 0x30FFFFFF, h));
            Identifier tex = thumbs.get(f);
            if (tex != null) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, tex, x + 2, y + 2, 0f, 0f, cellW - 4, imgH - 2, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
            } else {
                RenderUtil.rect(ctx, x + 2, y + 2, x + cellW - 2, y + imgH, 0x30FFFFFF);
                Fonts.drawCentered(ctx, thumbs.containsKey(f) ? "unreadable" : "...", x + cellW / 2, y + imgH / 2 - 4, Theme.TEXT_DIM, false);
            }
            String name = f.getFileName().toString().replace(".png", "");
            Fonts.draw(ctx, Fonts.trim(name, cellW - 8), x + 4, y + imgH + 3, sel ? 0xFFFFFFFF : Theme.TEXT_MUTED, false);
            RenderUtil.roundedOutline(ctx, x, y, x + cellW, y + cellH, 5, sel ? ColorUtil.withAlpha(Theme.accent(), 0xE0) : ColorUtil.lerp(0x18FFFFFF, ColorUtil.withAlpha(Theme.accent(), 0x90), h));
        }
        ctx.disableScissor();
        if (max > 0) {
            int trackH = gridBottom - gridTop;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (double) total)));
            int thumbY = gridTop + (int) ((trackH - thumbH) * (offset / max));
            RenderUtil.roundedRect(ctx, px2 - 7, thumbY, px2 - 5, thumbY + thumbH, 1, ColorUtil.withAlpha(Theme.accent(), 0x90));
        }
    }

    private void renderViewer(GuiGraphicsExtractor ctx) {
        ctx.fill(0, 0, width, height, 0xE0050208);
        int maxW = width - 40;
        int maxH = height - 50;
        int w = maxW;
        int h = w * 9 / 16;
        if (h > maxH) {
            h = maxH;
            w = h * 16 / 9;
        }
        int x = (width - w) / 2;
        int y = (height - h) / 2 - 6;
        if (full != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, full, x, y, 0f, 0f, w, h, w, h, w, h);
        } else {
            Identifier thumb = fullPath == null ? null : thumbs.get(fullPath);
            if (thumb != null) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, thumb, x, y, 0f, 0f, w, h, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
            }
            Fonts.drawCentered(ctx, "Loading...", width / 2, y + h / 2, Theme.TEXT, true);
        }
        RenderUtil.roundedOutline(ctx, x - 1, y - 1, x + w + 1, y + h + 1, 2, 0x40FFFFFF);
        String caption = (fullPath == null ? "" : fullPath.getFileName().toString()) + "   ·   " + (selected + 1) + " / " + files.size()
                + "   ·   ← → to browse, Esc or click to close";
        Fonts.drawCentered(ctx, caption, width / 2, y + h + 8, Theme.TEXT_MUTED, false);
    }

    // ------------------------------------------------------------------ actions

    private void openViewer() {
        if (selected < 0 || selected >= files.size()) {
            return;
        }
        viewing = true;
        releaseFull();
        fullPath = files.get(selected);
        requestFull(fullPath);
    }

    private void step(int direction) {
        if (files.isEmpty()) {
            return;
        }
        selected = Math.floorMod(selected + direction, files.size());
        if (viewing) {
            openViewer();
        }
    }

    private void openFile() {
        if (selected >= 0 && selected < files.size()) {
            Path file = files.get(selected);
            net.minecraft.util.Util.getPlatform().openFile(file.toFile());
        }
    }

    private void openFolder() {
        try {
            Files.createDirectories(folderPath());
        } catch (Exception ignored) {
            // opening shows the error, if any
        }
        Path dir = folderPath();
        net.minecraft.util.Util.getPlatform().openFile(dir.toFile());
    }

    private void deleteSelected() {
        if (selected < 0 || selected >= files.size()) {
            return;
        }
        Path file = files.get(selected);
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                try {
                    Files.deleteIfExists(file);
                    Identifier id = thumbs.remove(file);
                    if (id != null) {
                        minecraft.getTextureManager().release(id);
                    }
                    NightshadeClient.client().notifications().info("Screenshots", "Deleted " + file.getFileName());
                } catch (Exception e) {
                    NightshadeClient.client().notifications().warn("Screenshots", "Could not delete: " + e.getMessage());
                }
                reload();
            }
            minecraft.setScreen(this);
        }, Component.literal("Delete this screenshot?"), Component.literal(file.getFileName().toString())));
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (viewing) {
            viewing = false;
            releaseFull();
            return true;
        }
        for (NsButton b : new NsButton[]{view, open, delete, folder, done}) {
            if (b.click(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == 0 && mouseY >= gridTop && mouseY < gridBottom) {
            int x0 = px1 + 12;
            int col = (int) ((mouseX - x0) / (cellW + 6));
            int row = (int) ((mouseY - gridTop + scroll.get()) / (cellH + 6));
            int index = row * cols + col;
            if (col >= 0 && col < cols && index >= 0 && index < files.size()) {
                long now = System.currentTimeMillis();
                boolean again = index == selected && now - lastClick < 350;
                selected = index;
                lastClick = now;
                if (again) {
                    openViewer();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (viewing) {
            step(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        scrollTarget -= verticalAmount * 30;
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && viewing) {
            viewing = false;
            releaseFull();
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
            step(1);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) {
            step(-1);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            openViewer();
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE) {
            deleteSelected();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Test hooks. */
    public int countForTest() {
        return files.size();
    }

    public int thumbsLoadedForTest() {
        return (int) thumbs.values().stream().filter(java.util.Objects::nonNull).count();
    }
}
