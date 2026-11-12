package com.nightshade.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.settings.SettingsPanel;
import com.nightshade.hud.HudEditorScreen;
import com.nightshade.input.Keybind;
import com.nightshade.manager.ClientManager;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.interfaces.ClickGuiModule;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The client interface (default key: Right Shift). Six category tabs, module cards, search, per-module settings panels and keybind editing. Every control operates directly
 * on the ModuleManager / KeybindManager / ConfigManager. The game is not paused.
 */
public final class ClickGuiScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int PAD = 8;
    private static final int GAP = 6;

    private final ClientManager manager = NightshadeClient.client();
    private final ClickGuiModule guiModule = manager.modules().get(ClickGuiModule.class);
    private final CategoryPanel sidebar;
    private final Map<Module, ModuleCard> cards = new HashMap<>();
    private final Animation open = new Animation(14f, 0f);
    private final Animation scroll = new Animation(18f, 0f);
    /** Entrance of the content area after a tab or settings change (cards slide in, staggered). */
    private long contentShownAt = System.nanoTime();
    private final Animation categorySwitch;

    private ModuleCategory category;
    private String query = "";
    private boolean searchFocused;
    @Nullable
    private Module binding;
    @Nullable
    private SettingsPanel settings;
    private double scrollTarget;
    private int maxScroll;
    /**
     * True while the key that opened the GUI is still held. Vanilla forwards that same key event to the newly
     * opened screen, which must not be interpreted as "close".
     */
    private boolean openKeyHeld;

    // Layout (recomputed every frame)
    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int mainX1;
    private int mainX2;
    private int contentTop;
    private int contentBottom;
    private int searchX;
    private int searchY;
    private int switchX;
    private int switchY;
    private int footerY;
    private int saveX;
    private int searchX2;
    private int closeX;
    private int hudX;
    private int keysX;

    @Nullable
    private final Screen parent;

    public ClickGuiScreen() {
        this(null);
    }

    /** Opens the client GUI with a screen to return to (e.g. from the main menu or mod manager). */
    public ClickGuiScreen(@Nullable Screen parent) {
        super(Component.literal("Nightshade Client"));
        this.parent = parent;
        this.category = manager.config().getLastCategory();
        this.sidebar = new CategoryPanel(manager.modules(), category);
        this.categorySwitch = new Animation(16f, manager.modules().isCategoryEnabled(category) ? 1f : 0f);
        for (Module module : manager.modules().getModules()) {
            cards.put(module, new ModuleCard(module));
        }
        open.setTarget(1f);
        if (guiModule != null && guiModule.getKeybind().type() == Keybind.Type.KEY) {
            openKeyHeld = com.nightshade.manager.KeybindManager.wasJustPressed(guiModule.getKeybind().code())
                    || InputConstants.isKeyDown(guiModule.getKeybind().code());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout & rendering

    private static final int BAR = CategoryPanel.BAR_H;

    private void layout() {
        float o = open.update();
        winW = Math.min(width - 24, 660);
        winX = (width - winW) / 2;
        winY = 0;
        winH = height;
        mainX1 = winX;
        mainX2 = winX + winW;
        closeX = width - 24;
        int searchW = Math.max(80, Math.min(130, width / 6));
        searchX2 = closeX - 6;
        searchX = searchX2 - searchW;
        searchY = 5;
        int headerY = BAR + 12 + Math.round((1f - o) * 10f);
        switchX = mainX2 - GuiDraw.SWITCH_W;
        switchY = headerY + 7;
        contentTop = headerY + 34;
        footerY = height - 19;
        contentBottom = footerY - 8;
    }

    private float uiScale = 1f;

    @Override
    protected float inputScale() {
        return uiScale;
    }

    /** Applies the ClickGUI's own GUI Scale: the screen lays out in virtual pixels inside a scaled matrix. */
    private void updateScale() {
        uiScale = guiModule == null ? 1f : guiModule.guiScale();
        if (this.minecraft != null) {
            width = Math.round(this.minecraft.getWindow().getGuiScaledWidth() / uiScale);
            height = Math.round(this.minecraft.getWindow().getGuiScaledHeight() / uiScale);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        updateScale();
        ctx.pose().pushMatrix();
        ctx.pose().scale(uiScale, uiScale);
        renderScaled(ctx, Math.round(mouseX / uiScale), Math.round(mouseY / uiScale), delta);
        ctx.pose().popMatrix();
        Notifications.renderIfEnabled(ctx);
    }

    private void renderScaled(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        float o = open.get();
        boolean interactive = binding == null;
        // Top bar: brand, category tabs, search and close.
        com.nightshade.render.Fx.verticalGradient(ctx, 0, 0, width, BAR + 14, ColorUtil.fade(ColorUtil.withAlpha(Theme.BACKGROUND, 0xC8), o), 0x00000000);
        sidebar.renderBar(ctx, 0, 0, width, width - searchX + 6, category, interactive ? mouseX : -1, interactive ? mouseY : -1);
        renderSearch(ctx, mouseX, mouseY);

        renderHeader(ctx, mouseX, mouseY);
        String tooltip = settings != null ? renderSettings(ctx, mouseX, mouseY) : renderCards(ctx, mouseX, mouseY);
        renderFooter(ctx, mouseX, mouseY);

        if (sidebar.barIconsOnly() && tooltip == null && binding == null) {
            ModuleCategory hoveredTab = sidebar.categoryAt(mouseX, mouseY);
            if (hoveredTab != null) {
                tooltip = hoveredTab.displayName();
            }
        }
        if (tooltip != null && binding == null) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // The background is drawn unscaled, so lay it out in real GUI pixels.
        int w = width;
        int h = height;
        if (minecraft != null) {
            width = minecraft.getWindow().getGuiScaledWidth();
            height = minecraft.getWindow().getGuiScaledHeight();
        }
        super.extractBackground(ctx, mouseX, mouseY, delta);
        width = w;
        height = h;
    }

    private void renderSearch(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        boolean hovered = RenderUtil.isHovered(mouseX, mouseY, searchX, searchY, searchX2, searchY + 16);
        RenderUtil.roundedRect(ctx, searchX, searchY, searchX2, searchY + 16, 8,
                searchFocused ? ColorUtil.withAlpha(Theme.accent(), 0x40) : hovered ? 0x40FFFFFF & 0x38FFFFFF : 0x50000000);
        RenderUtil.roundedOutline(ctx, searchX, searchY, searchX2, searchY + 16, 8, searchFocused ? Theme.accent() : Theme.BORDER);
        PixelIcons.search(ctx, searchX + 6, searchY + 5, searchFocused ? Theme.accentLight() : Theme.TEXT_DIM);
        String text;
        int color;
        if (query.isEmpty() && !searchFocused) {
            text = "Search";
            color = Theme.TEXT_DIM;
        } else {
            boolean caret = searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L;
            text = Fonts.trim(query, searchX2 - searchX - 26) + (caret ? "_" : "");
            color = Theme.TEXT;
        }
        Fonts.draw(ctx, text, searchX + 17, searchY + 4, color, false);
        // Close
        boolean ch = RenderUtil.isHovered(mouseX, mouseY, closeX, 5, closeX + 16, 21);
        if (ch) {
            RenderUtil.roundedRect(ctx, closeX, 5, closeX + 16, 21, 4, ColorUtil.withAlpha(Theme.DANGER, 0x50));
        }
        Fonts.drawCentered(ctx, "\u00D7", closeX + 8, 9, ch ? 0xFFFFFFFF : Theme.TEXT_MUTED, false);
    }

    /** 0..1 entrance progress of content item {@code index} since the last tab/settings change. */
    private float contentAppear(int index) {
        float t = (System.nanoTime() - contentShownAt) / 1_000_000_000f;
        return com.nightshade.render.Fx.easeOutCubic((t - 0.025f * Math.min(index, 12)) / 0.28f);
    }

    private void restartContentAnimation() {
        contentShownAt = System.nanoTime();
    }

    private void renderHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int hy = switchY - 7;
        String title = isSearching() ? "Search" : category.displayName();
        String desc = isSearching() ? "Results from all categories" : category.description();
        ctx.pose().pushMatrix();
        ctx.pose().translate(mainX1, hy);
        ctx.pose().scale(2f, 2f);
        Fonts.draw(ctx, title, 0, 0, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
        int titleW = Fonts.width(title) * 2;
        String count = isSearching() ? visibleModules().size() + " found"
                : manager.modules().countEnabled(category) + " of " + manager.modules().search("", category).size() + " on";
        int maxDesc = switchX - mainX1 - Fonts.width(count) - 24;
        Fonts.draw(ctx, Fonts.trim(desc, Math.max(40, maxDesc)), mainX1, hy + 20, Theme.TEXT_DIM, false);
        Fonts.draw(ctx, count, mainX1 + titleW + 8, hy + 6, Theme.accentLight(), false);

        // Category master switch with label
        boolean categoryOn = manager.modules().isCategoryEnabled(category);
        String label = categoryOn ? "Category on" : "Category off";
        Fonts.draw(ctx, label, switchX - Fonts.width(label) - 6, switchY + 2, categoryOn ? Theme.TEXT_MUTED : Theme.WARNING, false);
        GuiDraw.toggle(ctx, switchX, switchY, categorySwitch.setTarget(categoryOn ? 1f : 0f).update(),
                RenderUtil.isHovered(mouseX, mouseY, switchX, switchY, switchX + GuiDraw.SWITCH_W, switchY + GuiDraw.SWITCH_H));
    }

    /** A non-empty query searches every category; otherwise the current tab is shown. */
    private List<Module> visibleModules() {
        List<Module> list;
        if (isSearching()) {
            list = new java.util.ArrayList<>(manager.modules().search(query, null));
            String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
            for (Module module : manager.modules().getModules()) {
                String hint = null;
                for (com.nightshade.module.ModuleSetting<?> setting : module.getSettings()) {
                    if (setting.getName().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                        hint = setting.getName();
                        break;
                    }
                }
                ModuleCard card = cards.get(module);
                boolean nameMatch = list.contains(module);
                if (card != null) {
                    card.setMatchHint(nameMatch ? null : hint);
                }
                if (hint != null && !nameMatch) {
                    list.add(module); // found through one of its settings
                }
            }
        } else {
            list = new java.util.ArrayList<>(manager.modules().search("", category));
            for (Module module : list) {
                ModuleCard card = cards.get(module);
                if (card != null) {
                    card.setMatchHint(null);
                }
            }
        }
        // favourites first, otherwise the usual order
        list.sort(java.util.Comparator.comparing(m -> !ModuleCard.isFavourite(m)));
        return list;
    }

    private boolean isSearching() {
        return !query.isBlank();
    }

    /** Whether cards show their category tag line (search results only). */
    private boolean showTags() {
        return isSearching();
    }

    private int columns() {
        int width = mainX2 - mainX1;
        return width >= 520 ? 3 : width >= 330 ? 2 : 1;
    }

    private int cardHeight() {
        boolean showDescription = guiModule == null || guiModule.showDescriptions();
        return showDescription ? 58 : 46;
    }

    private String renderCards(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Module> modules = visibleModules();
        int cols = columns();
        int cardW = (mainX2 - mainX1 - GAP * (cols - 1)) / cols;
        int cardH = cardHeight();
        int rows = (modules.size() + cols - 1) / cols;
        int totalHeight = rows * (cardH + GAP);
        maxScroll = Math.max(0, totalHeight - (contentBottom - contentTop));
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        int offset = Math.round(scroll.setTarget((float) scrollTarget).update());

        if (modules.isEmpty()) {
            String message = query.isEmpty() ? "No modules in this category" : "No modules match \"" + query + "\"";
            Fonts.drawCentered(ctx, message, (mainX1 + mainX2) / 2, contentTop + 30, Theme.TEXT_DIM, false);
            return null;
        }
        boolean insideContent = mouseY >= contentTop && mouseY < contentBottom;
        boolean interactive = binding == null && insideContent;
        boolean showDescription = guiModule == null || guiModule.showDescriptions();
        boolean showSub = showTags();
        String tooltip = null;
        ctx.enableScissor(mainX1 - 2, contentTop, mainX2 + 2, contentBottom);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = mainX1 + col * (cardW + GAP);
            int y = contentTop + row * (cardH + GAP) - offset;
            if (y + cardH < contentTop || y > contentBottom) {
                continue;
            }
            ModuleCard card = cards.get(module);
            card.setSearchMode(isSearching());
            boolean isBinding = binding == module;
            float appear = contentAppear(i);
            int slide = Math.round((1f - appear) * 10f);
            if (slide != 0) {
                ctx.pose().pushMatrix();
                ctx.pose().translate(0, slide);
            }
            card.render(ctx, x, y, cardW, cardH, mouseX, mouseY - slide, isBinding, showDescription, showSub, interactive || isBinding);
            if (slide != 0) {
                ctx.pose().popMatrix();
            }
            if (interactive && tooltip == null) {
                tooltip = card.tooltip(mouseX, mouseY);
            }
        }
        ctx.disableScissor();
        drawScrollbar(ctx, totalHeight, offset);
        return tooltip;
    }

    private String renderSettings(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        SettingsPanel panel = settings;
        int width = mainX2 - mainX1;
        RenderUtil.roundedRect(ctx, mainX1 - 8, contentTop - 6, mainX2 + 8, contentBottom + 4, 8, ColorUtil.withAlpha(Theme.BACKGROUND, 0xB0));
        RenderUtil.roundedOutline(ctx, mainX1 - 8, contentTop - 6, mainX2 + 8, contentBottom + 4, 8, 0x1CFFFFFF);
        int slideX = Math.round((1f - contentAppear(0)) * 14f);
        if (slideX != 0) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(slideX, 0);
            mouseX -= slideX;
        }
        try {
            return renderSettingsInner(ctx, panel, width, mouseX, mouseY);
        } finally {
            if (slideX != 0) {
                ctx.pose().popMatrix();
            }
        }
    }

    private String renderSettingsInner(GuiGraphicsExtractor ctx, SettingsPanel panel, int width, int mouseX, int mouseY) {
        panel.renderHeader(ctx, mainX1, contentTop, width, binding == null ? mouseX : -1, mouseY);
        int rowsTop = contentTop + SettingsPanel.headerHeight();
        int totalHeight = panel.contentHeight();
        maxScroll = Math.max(0, totalHeight - (contentBottom - rowsTop));
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        int offset = Math.round(scroll.setTarget((float) scrollTarget).update());
        boolean inside = mouseY >= rowsTop && mouseY < contentBottom && binding == null;
        ctx.enableScissor(mainX1 - 2, rowsTop, mainX2 + 2, contentBottom);
        panel.renderContent(ctx, rowsTop - offset, inside ? mouseX : -1, inside ? mouseY : -1, binding == panel.module());
        ctx.disableScissor();
        drawScrollbar(ctx, totalHeight, offset);
        return inside ? panel.tooltip(mouseX, mouseY) : null;
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx, int totalHeight, int offset) {
        if (maxScroll <= 0) {
            return;
        }
        int top = settings != null ? contentTop + SettingsPanel.headerHeight() : contentTop;
        int trackH = contentBottom - top;
        int thumbH = Math.max(16, trackH * trackH / Math.max(1, totalHeight));
        int thumbY = top + Math.round((trackH - thumbH) * (offset / (float) maxScroll));
        RenderUtil.roundedRect(ctx, mainX2 + 3, thumbY, mainX2 + 5, thumbY + thumbH, 1, ColorUtil.withAlpha(Theme.accent(), 0x99));
    }

    private static int buttonWidth(String label) {
        return Fonts.width(label) + 14;
    }

    private void button(GuiGraphicsExtractor ctx, int x, int y, String label, boolean primary, boolean hovered) {
        int w = buttonWidth(label);
        if (primary) {
            RenderUtil.roundedRect(ctx, x, y, x + w, y + 15, 5, hovered ? Theme.accentLight() : Theme.accent());
        } else {
            RenderUtil.roundedRect(ctx, x, y, x + w, y + 15, 5, hovered ? ColorUtil.withAlpha(Theme.accent(), 0x60) : 0x40000000);
            RenderUtil.roundedOutline(ctx, x, y, x + w, y + 15, 5, hovered ? ColorUtil.withAlpha(Theme.accent(), 0xC0) : Theme.BORDER);
        }
        Fonts.draw(ctx, label, x + 7, y + 4, primary || hovered ? 0xFFFFFFFF : Theme.TEXT, false);
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        com.nightshade.render.Fx.verticalGradient(ctx, 0, height - 34, width, height, 0x00000000, ColorUtil.withAlpha(Theme.BACKGROUND, 0xC8));
        int enabled = manager.modules().countEnabled(null);
        String status = enabled + " modules on  \u00B7  " + (manager.config().hasPendingChanges() ? "saving..." : "all changes saved");
        if (binding != null) {
            status = "Binding " + binding.getName() + ": press a key or mouse button (Esc cancel, Del clear)";
        }
        int saveW = buttonWidth("Save");
        saveX = mainX2 - saveW;
        hudX = saveX - buttonWidth("Edit HUD") - 5;
        keysX = hudX - buttonWidth("Keybinds") - 5;
        boolean free = binding == null;
        button(ctx, keysX, footerY, "Keybinds", false, free && RenderUtil.isHovered(mouseX, mouseY, keysX, footerY, keysX + buttonWidth("Keybinds"), footerY + 15));
        button(ctx, hudX, footerY, "Edit HUD", false, free && RenderUtil.isHovered(mouseX, mouseY, hudX, footerY, hudX + buttonWidth("Edit HUD"), footerY + 15));
        button(ctx, saveX, footerY, "Save", true, free && RenderUtil.isHovered(mouseX, mouseY, saveX, footerY, saveX + saveW, footerY + 15));
        int statusMax = keysX - mainX1 - 10;
        Fonts.draw(ctx, Fonts.trim(status, statusMax), mainX1, footerY + 4, binding != null ? Theme.accentLight() : Theme.TEXT_DIM, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (binding != null) {
            if (button >= InputConstants.MOUSE_BUTTON_MIDDLE) {
                assignBind(Keybind.mouse(button));
            } else {
                binding = null; // left/right click cancels binding
            }
            return true;
        }
        layout();
        ModuleCategory clicked = sidebar.categoryAt(mouseX, mouseY);
        if (clicked != null && button == 0) {
            selectCategory(clicked);
            return true;
        }
        boolean wasSearch = searchFocused;
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, closeX, 5, closeX + 16, 21)) {
            onClose();
            return true;
        }
        searchFocused = RenderUtil.isHovered(mouseX, mouseY, searchX, searchY, searchX2, searchY + 16);
        if (searchFocused) {
            if (button == 1) {
                query = "";
            }
            if (settings != null) {
                settings.unfocusAll();
            }
            return true;
        }
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, switchX, switchY, switchX + GuiDraw.SWITCH_W, switchY + GuiDraw.SWITCH_H)) {
            manager.modules().setCategoryEnabled(category, !manager.modules().isCategoryEnabled(category));
            manager.notifications().info(category.displayName(), manager.modules().isCategoryEnabled(category) ? "Category enabled" : "Category disabled");
            return true;
        }
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, saveX, footerY, saveX + buttonWidth("Save"), footerY + 15)) {
            manager.config().saveAll();
            manager.notifications().success("Configuration", "Saved to " + manager.config().getRoot().getFileName());
            return true;
        }
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, keysX, footerY, keysX + buttonWidth("Keybinds"), footerY + 15)) {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new KeybindsScreen(this));
            }
            return true;
        }
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, hudX, footerY, hudX + buttonWidth("Edit HUD"), footerY + 15)) {
            manager.config().setGuiState(category);
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new HudEditorScreen(this));
            }
            return true;
        }
        if (settings != null) {
            return clickSettings(mouseX, mouseY, button) || wasSearch;
        }
        if (mouseY < contentTop || mouseY >= contentBottom) {
            return wasSearch;
        }
        for (Module module : visibleModules()) {
            ModuleCard card = cards.get(module);
            switch (card.click(mouseX, mouseY, button)) {
                case TOGGLE -> {
                    if (module.isToggleable()) {
                        manager.modules().toggle(module);
                    } else {
                        runAction(module);
                    }
                    return true;
                }
                case OPEN_SETTINGS -> {
                    openSettings(module);
                    return true;
                }
                case BIND -> {
                    binding = module;
                    return true;
                }
                case FAVOURITE -> {
                    ModuleCard.toggleFavourite(module);
                    return true;
                }
                default -> {
                }
            }
        }
        return wasSearch;
    }

    private void runAction(Module module) {
        try {
            if (module instanceof ClickGuiModule) {
                openSettings(module); // already open: show its settings instead
            } else {
                module.onAction();
            }
        } catch (Throwable t) {
            manager.modules().handleModuleFailure(module, t);
        }
    }

    private boolean clickSettings(double mouseX, double mouseY, int button) {
        SettingsPanel panel = settings;
        SettingsPanel.Action action = panel.clickHeader(mouseX, mouseY, button);
        if (action == SettingsPanel.Action.NONE && mouseY >= contentTop + SettingsPanel.headerHeight() && mouseY < contentBottom) {
            action = panel.clickContent(mouseX, mouseY, button);
        }
        switch (action) {
            case BACK -> closeSettings();
            case TOGGLE -> manager.modules().toggle(panel.module());
            case BIND -> binding = panel.module();
            case CLEAR_BIND -> manager.keybinds().clear(panel.module());
            case RESET -> {
                panel.module().resetSettings();
                panel.module().setBindMode(panel.module().getDefaultBindMode());
                manager.keybinds().assign(panel.module(), panel.module().getDefaultKeybind());
                manager.notifications().info(panel.module().getName(), "Settings reset to defaults");
                settings = new SettingsPanel(panel.module());
            }
            case NONE -> {
                return false;
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (settings != null && binding == null) {
            settings.mouseDragged(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (settings != null) {
            settings.mouseReleased();
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (binding == null) {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - verticalAmount * 24.0));
        }
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding != null) {
            switch (keyCode) {
                case InputConstants.KEY_ESCAPE -> binding = null;
                case InputConstants.KEY_BACKSPACE, InputConstants.KEY_DELETE -> assignBind(Keybind.NONE);
                default -> assignBind(Keybind.key(keyCode));
            }
            return true;
        }
        if (settings != null && settings.keyPressed(keyCode)) {
            return true;
        }
        if (searchFocused) {
            switch (keyCode) {
                case InputConstants.KEY_BACKSPACE -> {
                    if (!query.isEmpty()) {
                        query = (modifiers & InputConstants.MOD_CONTROL) != 0 ? "" : query.substring(0, query.length() - 1);
                        scrollTarget = 0;
                    }
                }
                case InputConstants.KEY_ESCAPE -> {
                    if (query.isEmpty()) {
                        searchFocused = false;
                    } else {
                        query = "";
                    }
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_TAB -> searchFocused = false;
                default -> {
                }
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_F && (modifiers & InputConstants.MOD_CONTROL) != 0) {
            searchFocused = true;
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (settings != null) {
                closeSettings();
            } else {
                onClose();
            }
            return true;
        }
        if (guiModule != null && guiModule.getKeybind().matchesKey(keyCode)) {
            if (openKeyHeld) {
                openKeyHeld = false; // the press that opened the GUI
            } else {
                onClose();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        if (guiModule != null && guiModule.getKeybind().matchesKey(keyCode)) {
            openKeyHeld = false;
        }
        return false;
    }

    @Override
    protected boolean onCharTyped(char chr, int modifiers) {
        if (binding != null) {
            return true;
        }
        if (settings != null && settings.charTyped(chr)) {
            return true;
        }
        if (!searchFocused && (settings == null || !settings.hasFocus()) && Character.isLetterOrDigit(chr)) {
            searchFocused = true; // start typing anywhere to search
        }
        if (searchFocused && chr >= 32 && chr != 127 && query.length() < 32) {
            query += chr;
            scrollTarget = 0;
            if (settings != null) {
                closeSettings();
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ state helpers

    private void assignBind(Keybind bind) {
        Module module = binding;
        binding = null;
        if (module == null) {
            return;
        }
        if (bind.type() == Keybind.Type.MOUSE && bind.code() <= InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }
        manager.keybinds().assign(module, bind);
    }

    private void selectCategory(ModuleCategory newCategory) {
        restartContentAnimation();
        if (newCategory != category) {
            category = newCategory;
            categorySwitch.snap(manager.modules().isCategoryEnabled(category) ? 1f : 0f);
        }
        settings = null;
        scrollTarget = 0;
        scroll.snap(0f);
        manager.config().setGuiState(category);
    }

    private void openSettings(Module module) {
        restartContentAnimation();
        settings = new SettingsPanel(module, isSearching() ? query : null);
        searchFocused = false;
        // from a search: start at the first matching setting
        scrollTarget = Math.max(0, settings.firstHighlightY() - 4);
        scroll.snap((float) scrollTarget);
    }

    private void closeSettings() {
        restartContentAnimation();
        settings = null;
        scrollTarget = 0;
        scroll.snap(0f);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        manager.config().setGuiState(category);
        manager.config().saveDirty();
        super.removed();
    }

    /** Test hooks: screen-space points of a category tab and of the first module card (after one rendered frame). */
    public void searchForTest(String text) {
        query = text;
        settings = null;
    }

    public List<Module> visibleForTest() {
        return visibleModules();
    }

    public void openSettingsForTest(Module module) {
        openSettings(module);
    }

    public double[] tabPointForTest(ModuleCategory c) {
        int[] r = sidebar.tabRect(c);
        return new double[]{(r[0] + r[2]) / 2.0 * uiScale, (r[1] + r[3]) / 2.0 * uiScale};
    }

    public double[] firstCardPointForTest() {
        return new double[]{(mainX1 + 40) * uiScale, (contentTop + 10) * uiScale};
    }
}
