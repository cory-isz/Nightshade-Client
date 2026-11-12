package com.nightshade.gui.menu;

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
import com.nightshade.util.Log;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Nightshade singleplayer screen: your worlds as cards on the menu background, with play / create / edit / delete. */
public final class WorldsScreen extends NsMenuScreen {
    private static final int ROW_H = 40;
    private static final int GAP = 4;

    @Nullable
    private final Screen parent;
    private final List<LevelSummary> worlds = new ArrayList<>();
    private final Map<String, FaviconTexture> icons = new HashMap<>();
    private final Map<String, com.nightshade.render.Animation> hover = new HashMap<>();
    private final com.nightshade.render.Animation scroll = new com.nightshade.render.Animation(18f, 0f);
    private double scrollTarget;
    private LevelSummary selected;
    private boolean loading = true;
    private String error;
    private EditBox search;
    private long lastClick;
    private long openedAt = System.nanoTime();

    private final NsButton play = new NsButton("Play", NsButton.Style.PRIMARY, this::playSelected);
    private final NsButton create = new NsButton("Create New", NsButton.Style.SECONDARY, this::createNew);
    private final NsButton edit = new NsButton("Edit", NsButton.Style.SECONDARY, this::editSelected);
    private final NsButton delete = new NsButton("Delete", NsButton.Style.GHOST, this::deleteSelected);
    private final NsButton back = new NsButton("Back", NsButton.Style.GHOST, this::onClose);

    private int px1;
    private int py1;
    private int px2;
    private int py2;
    private int listTop;
    private int listBottom;

    public WorldsScreen(@Nullable Screen parent) {
        super(Component.literal("Singleplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        search = new EditBox(font, px2 - 150, py1 + 13, 132, 14, Component.literal("Search"));
        search.setBordered(false);
        search.setTextColor(Theme.TEXT);
        search.setHint(Component.literal("Search worlds..."));
        search.setResponder(text -> {
            scrollTarget = 0;
            pickFirstVisible();
        });
        addWidget(search);
        reload();
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
    }

    private void layout() {
        int w = Math.min(width - 24, 460);
        int h = Math.min(height - 24, 320);
        px1 = (width - w) / 2;
        px2 = px1 + w;
        py1 = (height - h) / 2;
        py2 = py1 + h;
        listTop = py1 + 40;
        listBottom = py2 - 32;
        if (search != null) {
            search.setX(px2 - 150);
            search.setY(py1 + 13);
        }
    }

    private void reload() {
        loading = true;
        error = null;
        LevelStorageSource source = minecraft.getLevelSource();
        try {
            source.loadLevelSummaries(source.findLevelCandidates()).whenComplete((list, failure) -> minecraft.execute(() -> {
                loading = false;
                if (failure != null) {
                    error = "Could not read your worlds: " + failure.getMessage();
                    return;
                }
                worlds.clear();
                worlds.addAll(list);
                worlds.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
                if (selected == null || worlds.stream().noneMatch(w -> w.getLevelId().equals(selected.getLevelId()))) {
                    selected = null;
                    pickFirstVisible();
                }
            }));
        } catch (Exception e) {
            loading = false;
            error = "Could not read your worlds: " + e.getMessage();
        }
    }

    private List<LevelSummary> visible() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return worlds;
        }
        List<LevelSummary> out = new ArrayList<>();
        for (LevelSummary w : worlds) {
            if (w.getLevelName().toLowerCase(Locale.ROOT).contains(q) || w.getLevelId().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(w);
            }
        }
        return out;
    }

    private void pickFirstVisible() {
        List<LevelSummary> v = visible();
        selected = v.isEmpty() ? null : v.get(0);
    }

    // ------------------------------------------------------------------ actions

    private void playSelected() {
        if (selected != null && !selected.isDisabled()) {
            minecraft.createWorldOpenFlows().openWorld(selected.getLevelId(), () -> minecraft.setScreen(this));
        }
    }

    private void createNew() {
        CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(this));
    }

    private void editSelected() {
        if (selected == null) {
            return;
        }
        String id = selected.getLevelId();
        try {
            LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().createAccess(id);
            minecraft.setScreen(EditWorldScreen.create(minecraft, access, changed -> {
                try {
                    access.close();
                } catch (Exception ignored) {
                    // already closed
                }
                reload();
                minecraft.setScreen(this);
            }));
        } catch (Exception e) {
            Log.warn("Cannot edit world {}: {}", id, e.toString());
            com.nightshade.NightshadeClient.client().notifications().error("Worlds", "Cannot edit " + selected.getLevelName() + ": " + e.getMessage());
        }
    }

    private void deleteSelected() {
        if (selected == null) {
            return;
        }
        LevelSummary world = selected;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().createAccess(world.getLevelId())) {
                    access.deleteLevel();
                    com.nightshade.NightshadeClient.client().notifications().info("Worlds", "Deleted " + world.getLevelName());
                } catch (Exception e) {
                    com.nightshade.NightshadeClient.client().notifications().error("Worlds", "Could not delete: " + e.getMessage());
                }
                selected = null;
                reload();
            }
            minecraft.setScreen(this);
        }, Component.literal("Delete \"" + world.getLevelName() + "\"?"),
                Component.literal("This world will be lost forever (a long time!)"),
                Component.literal("Delete"), Component.literal("Cancel")));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        icons.values().forEach(icon -> {
            if (!icon.isClosed()) {
                icon.close(); // 26.3 throws when an icon is closed twice
            }
        });
        icons.clear();
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Nullable
    private FaviconTexture icon(LevelSummary world) {
        FaviconTexture icon = icons.get(world.getLevelId());
        if (icon != null) {
            return icon.isClosed() ? null : icon;
        }
        icon = FaviconTexture.forWorld(minecraft.getTextureManager(), world.getLevelId());
        icons.put(world.getLevelId(), icon);
        try {
            if (world.getIcon() != null && Files.isRegularFile(world.getIcon())) {
                try (InputStream in = Files.newInputStream(world.getIcon())) {
                    icon.upload(NativeImage.read(in));
                }
                return icon;
            }
        } catch (Exception e) {
            Log.warn("World icon of {} unreadable: {}", world.getLevelId(), e.toString());
        }
        icon.close();
        return null;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        layout();
        float a = Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1_000_000_000f) / 0.35f);
        int lift = Math.round((1f - a) * 12f);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, lift);
        int accent = MenuBackground.accent();
        Fx.shadow(ctx, px1, py1 + 3, px2, py2 + 3, 12, 0xA0000000);
        RenderUtil.roundedRect(ctx, px1, py1, px2, py2, 8, com.nightshade.render.Theme.tint(0xC4100A1E));
        ctx.fill(px1 + 8, py1 + 1, px2 - 8, py1 + 2, 0x18FFFFFF);
        RenderUtil.roundedOutline(ctx, px1, py1, px2, py2, 8, ColorUtil.withAlpha(accent, 0x70));

        // Header
        RenderUtil.roundedRect(ctx, px1 + 12, py1 + 9, px1 + 34, py1 + 31, 5, ColorUtil.withAlpha(accent, 0x60));
        PixelIcons.menu(ctx, PixelIcons.MENU_SINGLEPLAYER, px1 + 14, py1 + 11, 2, 0xFFE9E4FF, MenuBackground.accentLight());
        Fonts.draw(ctx, "Singleplayer", px1 + 42, py1 + 11, Theme.TEXT, false);
        Fonts.draw(ctx, loading ? "Loading worlds..." : worlds.size() + (worlds.size() == 1 ? " world" : " worlds"), px1 + 42, py1 + 22, Theme.TEXT_DIM, false);
        RenderUtil.roundedRect(ctx, px2 - 160, py1 + 9, px2 - 12, py1 + 27, 5, search.isFocused() ? com.nightshade.render.Theme.tint(0xE0261848) : com.nightshade.render.Theme.tint(0x80140B24));
        RenderUtil.roundedOutline(ctx, px2 - 160, py1 + 9, px2 - 12, py1 + 27, 5, search.isFocused() ? ColorUtil.withAlpha(accent, 0xD0) : 0x30FFFFFF);
        PixelIcons.search(ctx, px2 - 156, py1 + 14, Theme.TEXT_DIM);
        search.render(ctx, mouseX, mouseY - lift, delta);

        String tooltip = renderList(ctx, mouseX, mouseY - lift, accent);

        // Actions
        boolean has = selected != null;
        play.enabled(has && !selected.isDisabled());
        edit.enabled(has);
        delete.enabled(has);
        int by = py2 - 25;
        int bw = (px2 - px1 - 24 - 4 * 6) / 5;
        play.render(ctx, px1 + 12, by, bw, 17, mouseX, mouseY - lift);
        create.render(ctx, px1 + 12 + (bw + 6), by, bw, 17, mouseX, mouseY - lift);
        edit.render(ctx, px1 + 12 + 2 * (bw + 6), by, bw, 17, mouseX, mouseY - lift);
        delete.render(ctx, px1 + 12 + 3 * (bw + 6), by, bw, 17, mouseX, mouseY - lift);
        back.render(ctx, px2 - 12 - bw, by, bw, 17, mouseX, mouseY - lift);
        ctx.pose().popMatrix();
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null) {
            com.nightshade.gui.GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private String renderList(GuiGraphics ctx, int mouseX, int mouseY, int accent) {
        List<LevelSummary> list = visible();
        int x1 = px1 + 12;
        int x2 = px2 - 12;
        int total = list.size() * (ROW_H + GAP);
        double max = Math.max(0, total - (listBottom - listTop));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        int offset = Math.round(scroll.setTarget((float) scrollTarget).update());
        String tooltip = null;
        ctx.enableScissor(x1 - 2, listTop, x2 + 2, listBottom);
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (int i = 0; i < list.size(); i++) {
            LevelSummary w = list.get(i);
            int y = listTop + i * (ROW_H + GAP) - offset;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            boolean inside = mouseY >= listTop && mouseY < listBottom;
            boolean hovered = inside && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H);
            float h = hover.computeIfAbsent(w.getLevelId(), k -> new com.nightshade.render.Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
            boolean sel = selected != null && selected.getLevelId().equals(w.getLevelId());
            int fill = sel ? com.nightshade.render.Theme.tint(0xE02A1A4C) : ColorUtil.lerp(0x80140B24, com.nightshade.render.Theme.tint(0xC0231740), h);
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H, 6, fill);
            RenderUtil.roundedOutline(ctx, x1, y, x2, y + ROW_H, 6, sel ? ColorUtil.withAlpha(accent, 0xE0) : ColorUtil.lerp(0x20FFFFFF, ColorUtil.withAlpha(accent, 0x90), h));
            if (sel) {
                RenderUtil.roundedRect(ctx, x1 + 2, y + 8, x1 + 4, y + ROW_H - 8, 1, accent);
            }
            FaviconTexture icon = icon(w);
            int ix = x1 + 6;
            int iy = y + 4;
            if (icon != null) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, icon.textureLocation(), ix, iy, 0f, 0f, 32, 32, 32, 32);
            } else {
                RenderUtil.roundedRect(ctx, ix, iy, ix + 32, iy + 32, 4, ColorUtil.withAlpha(accent, 0x40));
                PixelIcons.menu(ctx, PixelIcons.MENU_SINGLEPLAYER, ix + 7, iy + 7, 2, 0xFFE9E4FF, MenuBackground.accentLight());
            }
            int tx = ix + 40;
            Fonts.draw(ctx, Fonts.trim(w.getLevelName(), x2 - tx - 80), tx, y + 6, Theme.TEXT, false);
            String when = w.getLastPlayed() > 0 ? format.format(new Date(w.getLastPlayed())) : "never played";
            Fonts.draw(ctx, Fonts.trim(w.getLevelId() + "  ·  " + when, x2 - tx - 8), tx, y + 17, Theme.TEXT_DIM, false);
            String mode = w.isHardcore() ? "Hardcore" : capitalise(w.getGameMode().getName());
            int bx = tx;
            bx = badge(ctx, bx, y + 27, mode, w.isHardcore() ? 0xFFFF5C7A : accent);
            if (w.hasCommands()) {
                bx = badge(ctx, bx, y + 27, "Cheats", 0xFFFFC857);
            }
            badge(ctx, bx, y + 27, w.getWorldVersionName().getString(), com.nightshade.render.Theme.tint(0xFF8A8FA8));
            if (w.isDisabled() || !w.isCompatible() || w.requiresManualConversion()) {
                String info = w.getInfo().getString();
                if (hovered) {
                    tooltip = info;
                }
                Fonts.draw(ctx, "!", x2 - 12, y + 6, Theme.WARNING, false);
            }
            if (hovered || sel) {
                PixelIcons.menu(ctx, PixelIcons.MENU_PLAY, x2 - 16, y + (ROW_H - 9) / 2, 1, ColorUtil.withAlpha(MenuBackground.accentLight(), Math.round(255 * Math.max(h, sel ? 1f : 0f))), 0);
            }
        }
        ctx.disableScissor();
        if (loading) {
            Fonts.drawCentered(ctx, "Loading worlds...", (x1 + x2) / 2, listTop + 20, Theme.TEXT_DIM, false);
        } else if (error != null) {
            Fonts.drawCentered(ctx, Fonts.trim(error, x2 - x1 - 10), (x1 + x2) / 2, listTop + 20, Theme.DANGER, false);
        } else if (list.isEmpty()) {
            Fonts.drawCentered(ctx, worlds.isEmpty() ? "No worlds yet - press Create New" : "No worlds match", (x1 + x2) / 2, listTop + 20, Theme.TEXT_DIM, false);
        }
        if (max > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (double) total)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (offset / max));
            RenderUtil.roundedRect(ctx, x2 + 4, thumbY, x2 + 6, thumbY + thumbH, 1, ColorUtil.withAlpha(accent, 0x90));
        }
        return tooltip;
    }

    private int badge(GuiGraphics ctx, int x, int y, String text, int color) {
        int w = Fonts.width(text) + 6;
        RenderUtil.roundedRect(ctx, x, y - 1, x + w, y + 9, 3, ColorUtil.withAlpha(color, 0x45));
        Fonts.draw(ctx, text, x + 3, y, ColorUtil.lerp(color, 0xFFFFFFFF, 0.55f), false);
        return x + w + 4;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        float a = Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1_000_000_000f) / 0.35f);
        mouseY -= Math.round((1f - a) * 12f);
        if (play.click(mouseX, mouseY, button) || create.click(mouseX, mouseY, button) || edit.click(mouseX, mouseY, button)
                || delete.click(mouseX, mouseY, button) || back.click(mouseX, mouseY, button)) {
            return true;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, px2 - 160, py1 + 9, px2 - 12, py1 + 27)) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        search.setFocused(false);
        if (button == 0 && mouseY >= listTop && mouseY < listBottom && mouseX >= px1 + 12 && mouseX < px2 - 12) {
            int index = (int) ((mouseY - listTop + scroll.get()) / (ROW_H + GAP));
            List<LevelSummary> list = visible();
            if (index >= 0 && index < list.size()) {
                LevelSummary clicked = list.get(index);
                long now = System.currentTimeMillis();
                boolean again = selected != null && selected.getLevelId().equals(clicked.getLevelId()) && now - lastClick < 350;
                selected = clicked;
                lastClick = now;
                if (again) {
                    playSelected();
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            playSelected();
            return true;
        }
        if (!search.isFocused() && (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP)) {
            List<LevelSummary> list = visible();
            int i = selected == null ? -1 : list.indexOf(selected);
            i = Math.max(0, Math.min(list.size() - 1, i + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            if (!list.isEmpty()) {
                selected = list.get(i);
            }
            return true;
        }
        return false;
    }

    /** Test hooks. */
    public boolean loadingForTest() {
        return loading;
    }

    public List<LevelSummary> worldsForTest() {
        return worlds;
    }

    public void selectForTest(String levelId) {
        for (LevelSummary w : worlds) {
            if (w.getLevelId().equals(levelId)) {
                selected = w;
            }
        }
    }

    public void playForTest() {
        playSelected();
    }
}
