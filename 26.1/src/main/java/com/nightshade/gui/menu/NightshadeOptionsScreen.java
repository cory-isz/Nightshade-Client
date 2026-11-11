package com.nightshade.gui.menu;

import com.nightshade.gui.ClickGuiScreen;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Nightshade Options: Minecraft's settings as a grid of tiles (each opens the vanilla settings page, which gets the
 * Nightshade buttons), with the field-of-view slider on top and a shortcut to the client settings.
 */
public final class NightshadeOptionsScreen extends NsMenuScreen {
    @Nullable
    private final Screen parent;
    private final MenuTiles tiles = new MenuTiles();
    private final List<MenuTiles.Tile> grid = new ArrayList<>();
    private long openedAt = System.nanoTime();
    private AbstractWidget fov;
    private AbstractWidget difficultyButton;
    private int px1;
    private int py1;
    private int px2;
    private int py2;

    public NightshadeOptionsScreen(@Nullable Screen parent) {
        super(Component.literal("Options"));
        this.parent = parent;
    }

    @Override
    public void added() {
        super.added();
        openedAt = System.nanoTime();
    }

    private void open(Screen screen) {
        minecraft.setScreen(screen);
    }

    @Override
    protected void init() {
        layout();
        var o = minecraft.options;
        grid.clear();
        grid.add(new MenuTiles.Tile("video", com.nightshade.compat.SodiumCompat.loaded() ? "Video (Sodium)" : "Video",
                com.nightshade.compat.SodiumCompat.loaded() ? "Sodium's video settings: graphics, performance, quality..." : "Render distance, graphics, brightness, GUI scale...",
                PixelIcons.ICON_VIDEO, () -> open(com.nightshade.compat.SodiumCompat.videoSettings(this, () -> new VideoSettingsScreen(this, minecraft, o))), true, false));
        grid.add(new MenuTiles.Tile("controls", "Controls", "Key binds and mouse settings", PixelIcons.ICON_CONTROLS, () -> open(new ControlsScreen(this, o)), true, false));
        grid.add(new MenuTiles.Tile("sound", "Music & Sounds", "Volume for each sound category", PixelIcons.ICON_SOUND, () -> open(new SoundOptionsScreen(this, o)), true, false));
        grid.add(new MenuTiles.Tile("skin", "Skin", "Skin layers and main hand", PixelIcons.ICON_SKIN, () -> open(new SkinCustomizationScreen(this, o)), true, false));
        grid.add(new MenuTiles.Tile("language", "Language", "Game language", PixelIcons.ICON_LANGUAGE, () -> open(new LanguageSelectScreen(this, o, minecraft.getLanguageManager())), true, false));
        grid.add(new MenuTiles.Tile("packs", "Resource Packs", "Turn resource packs on and off", PixelIcons.MENU_MODS, () -> open(new PackSelectionScreen(minecraft.getResourcePackRepository(), repo -> {
            o.updateResourcePacks(repo);
            minecraft.setScreen(this);
        }, minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title"))), true, false));
        grid.add(new MenuTiles.Tile("chat", "Chat", "Chat visibility, size and colours", PixelIcons.ICON_CHAT, () -> open(new ChatOptionsScreen(this, o)), true, false));
        grid.add(new MenuTiles.Tile("access", "Accessibility", "Narrator, subtitles, motion and contrast", PixelIcons.ICON_EYE, () -> open(new AccessibilityOptionsScreen(this, o)), true, false));
        grid.add(new MenuTiles.Tile("online", "Online", "Realms notifications, server lists, skins", PixelIcons.ICON_ONLINE, () -> open(new OnlineOptionsScreen(this, o)), true, false));
        grid.add(new MenuTiles.Tile("telemetry", "Telemetry", "Data Minecraft sends to Mojang", PixelIcons.ICON_STATS, () -> open(new TelemetryInfoScreen(this, o)), true, false));
        grid.add(new MenuTiles.Tile("credits", "Credits", "Credits and attribution", PixelIcons.ICON_STAR, () -> open(new CreditsAndAttributionScreen(this)), true, false));
        grid.add(new MenuTiles.Tile("client", "Client Settings", "Nightshade modules, HUD and theme", PixelIcons.MENU_SETTINGS, () -> open(new ClickGuiScreen(this)), true, false));
        grid.add(new MenuTiles.Tile("mods", "Mods", "Configure your installed mods (their own settings, or their config files)", PixelIcons.MENU_MODS,
                () -> open(new com.nightshade.gui.mods.ModManagerScreen(this)), true, false));
        boolean difficulty = minecraft.level != null && minecraft.hasSingleplayerServer();
        int fovW = difficulty ? (px2 - px1 - 38) / 2 : px2 - px1 - 32;
        fov = o.fov().createButton(o, px1 + 16, py1 + 40, fovW);
        addRenderableWidget(fov);
        if (difficulty) {
            difficultyButton = net.minecraft.client.gui.screens.options.DifficultyButtons.create(minecraft, minecraft.level, this).difficultyButton();
            difficultyButton.setX(px1 + 22 + fovW);
            difficultyButton.setY(py1 + 40);
            difficultyButton.setWidth(fovW);
            addRenderableWidget(difficultyButton);
        }
    }

    private void layout() {
        int w = Math.min(width - 24, 460);
        int h = Math.min(height - 24, 300);
        px1 = (width - w) / 2;
        px2 = px1 + w;
        py1 = (height - h) / 2;
        py2 = py1 + h;
        if (fov != null) {
            int fovW = difficultyButton != null ? (px2 - px1 - 38) / 2 : px2 - px1 - 32;
            fov.setX(px1 + 16);
            fov.setY(py1 + 40);
            fov.setWidth(fovW);
            if (difficultyButton != null) {
                difficultyButton.setX(px1 + 22 + fovW);
                difficultyButton.setY(py1 + 40);
                difficultyButton.setWidth(fovW);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        float a = Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1e9f) / 0.35f);
        tiles.beginFrame();
        Fx.shadow(ctx, px1, py1 + 3, px2, py2 + 3, 12, 0xA0000000);
        RenderUtil.roundedRect(ctx, px1, py1, px2, py2, 8, ColorUtil.withAlpha(Theme.BACKGROUND, 0xC8));
        ctx.fill(px1 + 8, py1 + 1, px2 - 8, py1 + 2, 0x18FFFFFF);
        RenderUtil.roundedOutline(ctx, px1, py1, px2, py2, 8, ColorUtil.withAlpha(Theme.accent(), 0x70));
        RenderUtil.roundedRect(ctx, px1 + 12, py1 + 9, px1 + 34, py1 + 31, 5, ColorUtil.withAlpha(Theme.accent(), 0x60));
        PixelIcons.menu(ctx, PixelIcons.MENU_OPTIONS, px1 + 14, py1 + 11, 2, 0xFFE9E4FF, Theme.accentLight());
        Fonts.draw(ctx, "Options", px1 + 42, py1 + 11, Theme.TEXT, false);
        Fonts.draw(ctx, "Minecraft settings", px1 + 42, py1 + 22, Theme.TEXT_DIM, false);

        int cols = 4;
        int gap = 6;
        int gx1 = px1 + 16;
        int gw = (px2 - px1 - 32 - gap * (cols - 1)) / cols;
        int top = py1 + 68;
        int rows = (grid.size() + cols - 1) / cols;
        int th = Math.max(30, Math.min(44, (py2 - 34 - top - gap * (rows - 1)) / rows));
        for (int i = 0; i < grid.size(); i++) {
            int r = i / cols;
            int c = i % cols;
            float appear = Fx.easeOutCubic(((System.nanoTime() - openedAt) / 1e9f - 0.03f * i) / 0.35f);
            tiles.draw(ctx, grid.get(i), gx1 + c * (gw + gap), top + r * (th + gap), gw, th, mouseX, mouseY, appear, false);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta); // vanilla widgets (the FOV slider)
        String done = "Done";
        int dw = 80;
        int dx = (width - dw) / 2;
        int dy = py2 - 24;
        boolean dh = RenderUtil.isHovered(mouseX, mouseY, dx, dy, dx + dw, dy + 16);
        com.nightshade.render.WidgetStyle.button(ctx, dx, dy, dw, 16, true, dh, a);
        Fonts.drawCentered(ctx, done, width / 2, dy + 4, Theme.TEXT, false);
        Notifications.renderIfEnabled(ctx);
        if (tiles.tooltip() != null) {
            com.nightshade.gui.GuiDraw.tooltip(ctx, tiles.tooltip(), mouseX, mouseY, width, height);
        }
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, (width - 80) / 2, py2 - 24, (width + 80) / 2, py2 - 8)) {
            onClose();
            return true;
        }
        return button == 0 && tiles.click(mouseX, mouseY);
    }

    @Override
    public void onClose() {
        minecraft.options.save();
        minecraft.setScreen(parent);
    }

    /** Test hook: number of settings tiles. */
    public int tileCountForTest() {
        return grid.size();
    }
}
