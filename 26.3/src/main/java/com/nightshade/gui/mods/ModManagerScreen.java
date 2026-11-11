package com.nightshade.gui.mods;

import com.nightshade.net.Http;
import com.nightshade.gui.NsScreen;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.mods.ModConfigScreens;
import com.nightshade.mods.ModEntry;
import com.nightshade.mods.ModFileManager;
import com.nightshade.mods.ModIcons;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Lang;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Mod manager: every installed mod (and disabled jars in the mods folder) with icon and details. Mods can be
 * configured (their own config screen via the Mod Menu API, or their config files in the built-in editor),
 * enabled/disabled, deleted, and their pages opened.
 */
public final class ModManagerScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int ROW_H = 28;

    @Nullable
    private final Screen parent;
    private final ModFileManager files = NightshadeClient.client().mods();
    private final ModIcons icons = new ModIcons(files);
    private final ModConfigScreens configs = new ModConfigScreens();
    private final Map<String, Animation> hover = new HashMap<>();
    private List<ModEntry> all = new ArrayList<>();
    private List<ModEntry> filtered = new ArrayList<>();
    @Nullable
    private ModEntry selected;
    private boolean showLibraries;
    private double listScroll;
    private double detailScroll;
    private EditBox search;
    private List<Path> selectedConfigFiles = List.of();

    private final NsButton done = new NsButton(Lang.t("common.done"), NsButton.Style.PRIMARY, this::onClose);
    private final NsButton openFolder = new NsButton(Lang.t("mods.open_folder"), NsButton.Style.SECONDARY, () -> files.openModsFolder());
    private final NsButton libraries = new NsButton("Libraries: Off", NsButton.Style.GHOST, this::toggleLibraries);
    private final NsButton configure = new NsButton("Configure", NsButton.Style.PRIMARY, this::configureSelected);
    private final NsButton toggle = new NsButton("Disable", NsButton.Style.SECONDARY, this::toggleSelected);
    private final NsButton delete = new NsButton("Delete", NsButton.Style.DANGER, this::deleteSelected);
    private final NsButton browse = new NsButton(Lang.t("mods.browse"), NsButton.Style.PRIMARY, () -> minecraft.gui.setScreen(new ModrinthScreen(this)));
    private final NsButton checkUpdates = new NsButton("Check updates", NsButton.Style.SECONDARY, this::checkUpdates);
    private final NsButton update = new NsButton("Update", NsButton.Style.PRIMARY, this::updateSelected);
    private final com.nightshade.mods.ModrinthClient modrinth = new com.nightshade.mods.ModrinthClient();
    /** Available updates keyed by the installed jar (absolute, normalised path). */
    private final java.util.Map<Path, com.nightshade.mods.ModrinthClient.Version> updates = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Path> updating = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile String updateStatus;
    private final List<NsButton> linkButtons = new ArrayList<>();
    private final List<NsButton> fileButtons = new ArrayList<>();

    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int listX2;
    private int listTop;
    private int listBottom;
    private int detailX1;

    public ModManagerScreen(@Nullable Screen parent) {
        super(Component.literal("Mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        search = new EditBox(font, winX + 26, winY + 33, listX2 - winX - 36, 14, Component.literal("Search"));
        search.setBordered(false);
        search.setTextColor(Theme.TEXT);
        search.setHint(Component.literal("Search mods..."));
        search.setResponder(text -> applyFilter());
        addWidget(search);
        refresh();
    }

    private void layout() {
        winW = Math.min(width - 16, 680);
        winH = Math.min(height - 16, 400);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        listX2 = winX + Math.max(150, (int) (winW * 0.42));
        listTop = winY + 54;
        listBottom = winY + winH - 30;
        detailX1 = listX2 + 10;
        if (search != null) {
            search.setX(winX + 26);
            search.setY(winY + 34);
            search.setWidth(listX2 - winX - 36);
        }
    }

    private void refresh() {
        String selectedKey = selected == null ? null : key(selected);
        all = files.scan(showLibraries);
        applyFilter();
        selected = null;
        for (ModEntry entry : all) {
            if (key(entry).equals(selectedKey)) {
                select(entry);
            }
        }
        if (selected == null && !filtered.isEmpty()) {
            select(filtered.get(0));
        }
    }

    private static String key(ModEntry entry) {
        return entry.id() + "|" + entry.loaded();
    }

    private void applyFilter() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (ModEntry entry : all) {
            if (query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query) || entry.id().toLowerCase(Locale.ROOT).contains(query)
                    || String.join(" ", entry.authors()).toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(entry);
            }
        }
        listScroll = 0;
    }

    private void select(ModEntry entry) {
        selected = entry;
        detailScroll = 0;
        selectedConfigFiles = configs.configFiles(entry.id());
        linkButtons.clear();
        for (Map.Entry<String, String> contact : entry.contact().entrySet()) {
            String url = contact.getValue();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                linkButtons.add(new NsButton(contact.getKey(), NsButton.Style.GHOST, () -> ConfirmLinkScreen.confirmLinkNow(this, java.net.URI.create(url))));
            }
        }
        fileButtons.clear();
        for (Path file : selectedConfigFiles) {
            Path relative = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().relativize(file);
            fileButtons.add(new NsButton("Edit " + relative.toString().replace('\\', '/'), NsButton.Style.SECONDARY,
                    () -> minecraft.gui.setScreen(new ConfigEditorScreen(this, file))));
        }
    }

    private void toggleLibraries() {
        showLibraries = !showLibraries;
        libraries.label(showLibraries ? "Libraries: On" : "Libraries: Off");
        refresh();
    }

    private void configureSelected() {
        if (selected == null) {
            return;
        }
        Function<Screen, Screen> factory = configs.factory(selected.id());
        Screen screen = factory == null ? null : factory.apply(this);
        if (screen != null) {
            minecraft.gui.setScreen(screen);
        } else {
            NightshadeClient.client().notifications().warn(selected.name(), "Its config screen could not be opened");
        }
    }

    private void toggleSelected() {
        ModEntry entry = selected;
        if (entry == null) {
            return;
        }
        if (entry.state() != ModEntry.State.ENABLED) {
            // Enable a disabled jar, or undo a pending disable/enable.
            files.enable(entry);
            refresh();
            return;
        }
        if (!entry.dependents().isEmpty()) {
            minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    files.disable(entry);
                }
                minecraft.gui.setScreen(this);
                refresh();
            }, Component.literal("Disable " + entry.name() + "?"),
                    Component.literal("These mods depend on it and may stop the game from starting: " + String.join(", ", entry.dependents()))));
            return;
        }
        files.disable(entry);
        refresh();
    }

    private void deleteSelected() {
        ModEntry entry = selected;
        if (entry == null) {
            return;
        }
        String warning = entry.dependents().isEmpty() ? "The jar file is removed from your mods folder. This cannot be undone."
                : "These mods depend on it: " + String.join(", ", entry.dependents()) + ". This cannot be undone.";
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                files.delete(entry);
            }
            minecraft.gui.setScreen(this);
            refresh();
        }, Component.literal("Delete " + entry.name() + "?"), Component.literal(warning)));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------------ Modrinth updates

    /** First click checks; when updates are known the same button updates everything. */
    private void checkUpdates() {
        if (!updates.isEmpty()) {
            for (Path jar : new ArrayList<>(updates.keySet())) {
                startUpdate(jar);
            }
            return;
        }
        updateStatus = "Checking...";
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                java.util.Map<String, Path> hashes = com.nightshade.mods.ModrinthClient.hashModsFolder(files.modsDir());
                hashes.entrySet().removeIf(e -> e.getValue().getFileName().toString().endsWith(".disabled"));
                java.util.Map<String, com.nightshade.mods.ModrinthClient.Version> byHash = modrinth.findUpdates(hashes);
                java.util.Map<Path, com.nightshade.mods.ModrinthClient.Version> result = new java.util.HashMap<>();
                byHash.forEach((hash, version) -> result.put(hashes.get(hash).toAbsolutePath().normalize(), version));
                return result;
            } catch (Exception e) {
                throw new RuntimeException(Http.describe(e));
            }
        }, net.minecraft.util.Util.nonCriticalIoPool()).whenComplete((result, failure) -> minecraft.execute(() -> {
            if (failure != null) {
                updateStatus = null;
                NightshadeClient.client().notifications().error("Updates", "Could not check Modrinth: "
                        + (failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage()));
                return;
            }
            updates.clear();
            updates.putAll(result);
            updateStatus = result.isEmpty() ? "All up to date" : null;
            NightshadeClient.client().notifications().info("Updates", result.isEmpty() ? "Every Modrinth mod is up to date"
                    : result.size() + " update" + (result.size() == 1 ? "" : "s") + " available");
        }));
    }

    private void updateSelected() {
        if (selected != null && selected.jar() != null) {
            startUpdate(selected.jar().toAbsolutePath().normalize());
        }
    }

    /** Downloads the new version, then removes the old jar (deferred until exit on Windows if it is locked). */
    private void startUpdate(Path jar) {
        com.nightshade.mods.ModrinthClient.Version version = updates.get(jar);
        if (version == null || !updating.add(jar)) {
            return;
        }
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return modrinth.download(version.file(), files.modsDir());
            } catch (Exception e) {
                throw new RuntimeException(Http.describe(e));
            }
        }, net.minecraft.util.Util.nonCriticalIoPool()).whenComplete((path, failure) -> minecraft.execute(() -> {
            updating.remove(jar);
            if (failure != null) {
                NightshadeClient.client().notifications().error("Updates", "Update failed: "
                        + (failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage()));
                return;
            }
            for (ModEntry entry : files.scan(true)) {
                if (entry.jar() != null && entry.jar().toAbsolutePath().normalize().equals(jar) && !path.equals(jar)) {
                    files.delete(entry);
                }
            }
            updates.remove(jar);
            NightshadeClient.client().notifications().success("Updates", "Downloaded " + version.file().filename() + " - restart to apply");
            refresh();
        }));
    }

    /** Test hooks for updates. */
    public java.util.Map<Path, com.nightshade.mods.ModrinthClient.Version> updatesForTest() {
        return updates;
    }

    public void checkUpdatesForTest() {
        checkUpdates();
    }

    public String updateStatusForTest() {
        return updateStatus;
    }

    @Override
    public void removed() {
        icons.release();
        super.removed();
    }

    // ------------------------------------------------------------------ rendering


    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        RenderUtil.roundedRect(ctx, winX, winY, winX + winW, winY + winH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, winX, winY, winX + winW, winY + winH, 8, Theme.BORDER);
        RenderUtil.horizontalGradient(ctx, winX + 10, winY, winX + winW / 2, winY + 1, 0x00000000, Theme.accent());
        RenderUtil.horizontalGradient(ctx, winX + winW / 2, winY, winX + winW - 10, winY + 1, Theme.accent(), 0x00000000);

        int loaded = (int) all.stream().filter(e -> e.loaded() && !e.library()).count();
        Fonts.draw(ctx, Lang.t("mods.title"), winX + 14, winY + 12, Theme.TEXT, false);
        Fonts.draw(ctx, Lang.t("mods.loaded", loaded), winX + 14 + Fonts.width(Lang.t("mods.title")) + 8, winY + 12, Theme.TEXT_DIM, false);
        int pending = files.pendingChanges();
        if (pending > 0) {
            String banner = pending + (pending == 1 ? " change applies" : " changes apply") + " after restarting Minecraft";
            int bw = Fonts.width(banner) + 14;
            int bx = winX + winW - bw - 12;
            RenderUtil.roundedRect(ctx, bx, winY + 9, bx + bw, winY + 22, 4, 0x55FFC857);
            Fonts.draw(ctx, banner, bx + 7, winY + 12, Theme.WARNING, false);
        }

        // Search box
        RenderUtil.roundedRect(ctx, winX + 12, winY + 29, listX2, winY + 46, 4, search.isFocused() ? Theme.SELECTED : Theme.PANEL);
        RenderUtil.roundedOutline(ctx, winX + 12, winY + 29, listX2, winY + 46, 4, search.isFocused() ? Theme.accent() : Theme.BORDER);
        PixelIcons.search(ctx, winX + 16, winY + 34, Theme.TEXT_DIM);
        search.extractRenderState(ctx, mouseX, mouseY, delta);

        String tooltip = renderList(ctx, mouseX, mouseY);
        renderDetails(ctx, mouseX, mouseY);

        // Footer: left group (libraries, folder), right group (Modrinth, updates, done); short labels when narrow
        int fy = winY + winH - 24;
        checkUpdates.label(updateStatus != null ? updateStatus : updates.isEmpty() ? Lang.t("mods.check_updates") : Lang.t("mods.update_all", updates.size()));
        checkUpdates.enabled(updateStatus == null || !updateStatus.endsWith("..."));
        NsButton[] left = {libraries, openFolder};
        NsButton[] right = {browse, checkUpdates, done};
        int total = 0;
        for (NsButton b : left) {
            total += b.preferredWidth() + 6;
        }
        for (NsButton b : right) {
            total += Math.max(60, b.preferredWidth()) + 6;
        }
        boolean compactFooter = total > winW - 24;
        if (compactFooter) {
            openFolder.label("Folder");
            browse.label("Modrinth");
            libraries.label(showLibraries ? "Libs: On" : "Libs: Off");
        } else {
            openFolder.label(Lang.t("mods.open_folder"));
            browse.label(Lang.t("mods.browse"));
            libraries.label(showLibraries ? "Libraries: On" : "Libraries: Off");
        }
        int x = winX + 12;
        for (NsButton b : left) {
            int w = b.preferredWidth();
            b.render(ctx, x, fy, w, 16, mouseX, mouseY);
            x += w + 6;
        }
        int rx = winX + winW - 12;
        for (int i = right.length - 1; i >= 0; i--) {
            int w = Math.max(60, right[i].preferredWidth());
            rx -= w;
            right[i].render(ctx, rx, fy, w, 16, mouseX, mouseY);
            rx -= 6;
        }

        Notifications.renderIfEnabled(ctx);
        if (tooltip != null) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private String renderList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x1 = winX + 12;
        int x2 = listX2;
        int maxScroll = Math.max(0, filtered.size() * (ROW_H + 3) - (listBottom - listTop));
        listScroll = Math.max(0, Math.min(maxScroll, listScroll));
        String tooltip = null;
        ctx.enableScissor(x1, listTop, x2 + 1, listBottom);
        for (int i = 0; i < filtered.size(); i++) {
            ModEntry entry = filtered.get(i);
            int y = listTop + i * (ROW_H + 3) - (int) listScroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            boolean hovered = mouseY >= listTop && mouseY < listBottom && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H);
            float h = hover.computeIfAbsent(key(entry), k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
            boolean isSelected = entry == selected;
            int fill = isSelected ? Theme.SELECTED : ColorUtil.lerp(Theme.PANEL, Theme.PANEL_HOVER, h);
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H, 5, fill);
            if (isSelected) {
                RenderUtil.roundedRect(ctx, x1, y + 6, x1 + 2, y + ROW_H - 6, 1, Theme.accent());
            }
            drawIcon(ctx, entry, x1 + 6, y + 4, 20);
            boolean dim = entry.state() != ModEntry.State.ENABLED;
            int textMax = x2 - x1 - 40;
            Fonts.draw(ctx, Fonts.trim(entry.name(), textMax), x1 + 32, y + 5, dim ? Theme.TEXT_MUTED : Theme.TEXT, false);
            String sub = entry.version();
            int subColor = Theme.TEXT_DIM;
            com.nightshade.mods.ModrinthClient.Version available = entry.jar() == null ? null : updates.get(entry.jar().toAbsolutePath().normalize());
            if (available != null && entry.state() == ModEntry.State.ENABLED) {
                sub = entry.version() + "  ->  " + available.versionNumber();
                subColor = 0xFF1BD96A;
            } else if (entry.state() != ModEntry.State.ENABLED) {
                sub = entry.state().label();
                subColor = entry.state().color();
            } else if (entry.builtin()) {
                sub = entry.version() + "  -  built-in";
            } else if (entry.library()) {
                sub = entry.version() + "  -  library";
            }
            Fonts.draw(ctx, Fonts.trim(sub, textMax), x1 + 32, y + 16, subColor, false);
            if (hovered && Fonts.width(entry.name()) > textMax) {
                tooltip = entry.name();
            }
        }
        if (filtered.isEmpty()) {
            Fonts.drawCentered(ctx, "No mods match", (x1 + x2) / 2, listTop + 20, Theme.TEXT_DIM, false);
        }
        ctx.disableScissor();
        return tooltip;
    }

    private void drawIcon(GuiGraphicsExtractor ctx, ModEntry entry, int x, int y, int size) {
        ModIcons.Icon icon = icons.get(entry);
        if (icon != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, icon.id(), x, y, 0f, 0f, size, size, icon.width(), icon.height(), icon.width(), icon.height());
            return;
        }
        RenderUtil.roundedRect(ctx, x, y, x + size, y + size, 4, ColorUtil.withAlpha(Theme.accent(), 0x40));
        String letter = entry.name().isEmpty() ? "?" : entry.name().substring(0, 1).toUpperCase(Locale.ROOT);
        Fonts.drawCentered(ctx, letter, x + size / 2, y + (size - Fonts.height()) / 2 + 1, Theme.accentLight(), false);
    }

    private void renderDetails(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x1 = detailX1;
        int x2 = winX + winW - 12;
        RenderUtil.roundedRect(ctx, x1, winY + 29, x2, listBottom, 6, com.nightshade.render.Theme.tint(0x60140B24));
        ModEntry entry = selected;
        if (entry == null) {
            Fonts.drawCentered(ctx, "Select a mod", (x1 + x2) / 2, winY + 80, Theme.TEXT_DIM, false);
            return;
        }
        int top = winY + 29;
        ctx.enableScissor(x1, top, x2, listBottom);
        int y = top + 10 - (int) detailScroll;
        int pad = 10;
        drawIcon(ctx, entry, x1 + pad, y, 36);
        int tx = x1 + pad + 44;
        Fonts.draw(ctx, Fonts.trim(entry.name(), x2 - tx - 8), tx, y + 2, Theme.TEXT, false);
        Fonts.draw(ctx, Fonts.trim(entry.version() + "   " + entry.id(), x2 - tx - 8), tx, y + 13, Theme.TEXT_DIM, false);
        String state = entry.state().label();
        int sw = Fonts.width(state) + 8;
        RenderUtil.roundedRect(ctx, tx, y + 24, tx + sw, y + 35, 3, ColorUtil.withAlpha(entry.state().color(), 0x45));
        Fonts.draw(ctx, state, tx + 4, y + 26, entry.state().color(), false);
        y += 46;

        // Actions
        int ax = x1 + pad;
        int aw = x2 - x1 - pad * 2;
        Function<Screen, Screen> factory = entry.loaded() ? configs.factory(entry.id()) : null;
        List<NsButton> actions = new ArrayList<>();
        if (factory != null) {
            actions.add(configure);
        }
        Path jarKey = entry.jar() == null ? null : entry.jar().toAbsolutePath().normalize();
        if (jarKey != null && updates.containsKey(jarKey) && entry.state() == ModEntry.State.ENABLED) {
            update.label(updating.contains(jarKey) ? "Updating..." : "Update").enabled(!updating.contains(jarKey));
            actions.add(update);
        }
        if (entry.editable() || (entry.jar() != null && !entry.loaded())) {
            toggle.label(switch (entry.state()) {
                case ENABLED -> "Disable";
                case DISABLED -> "Enable";
                case PENDING_DISABLE -> "Keep enabled";
                case PENDING_ENABLE -> "Keep disabled";
                case PENDING_DELETE -> "Deleted";
            }).enabled(entry.state() != ModEntry.State.PENDING_DELETE);
            delete.enabled(entry.state() != ModEntry.State.PENDING_DELETE);
            actions.add(toggle);
            actions.add(delete);
        }
        if (!actions.isEmpty()) {
            int gap = 6;
            int bw = (aw - gap * (actions.size() - 1)) / actions.size();
            for (int i = 0; i < actions.size(); i++) {
                actions.get(i).render(ctx, ax + i * (bw + gap), y, bw, 18, mouseX, mouseY);
            }
            y += 26;
        } else {
            String why = entry.builtin() ? "Built-in component - cannot be changed here."
                    : entry.library() ? "Bundled inside another mod - manage the parent mod instead."
                    : "nightshade".equals(entry.id()) ? "This is Nightshade Client itself."
                    : "Not loaded from the mods folder (e.g. a development environment).";
            Fonts.draw(ctx, Fonts.trim(why, aw), ax, y + 2, Theme.TEXT_DIM, false);
            y += 16;
        }

        // Description
        y = section(ctx, "Description", ax, y);
        String description = entry.description().isBlank() ? "No description provided." : entry.description();
        for (FormattedCharSequence line : font.split(Fonts.text(description), aw)) {
            ctx.text(font, line, ax, y, Theme.TEXT_MUTED, false);
            y += Fonts.height() + 2;
        }
        y += 6;

        // Info
        y = section(ctx, "Information", ax, y);
        y = infoRow(ctx, "Authors", entry.authors().isEmpty() ? "Unknown" : String.join(", ", entry.authors()), ax, aw, y);
        if (!entry.licenses().isEmpty()) {
            y = infoRow(ctx, "License", String.join(", ", entry.licenses()), ax, aw, y);
        }
        y = infoRow(ctx, "Environment", switch (entry.environment()) {
            case "client" -> "Client only";
            case "server" -> "Server only";
            default -> "Client and server";
        }, ax, aw, y);
        if (entry.jar() != null) {
            y = infoRow(ctx, "File", entry.jar().getFileName().toString(), ax, aw, y);
        }
        if (!entry.dependents().isEmpty()) {
            y = infoRow(ctx, "Required by", String.join(", ", entry.dependents()), ax, aw, y);
        }
        y += 6;

        // Config files
        if (!fileButtons.isEmpty()) {
            y = section(ctx, "Config files", ax, y);
            for (NsButton button : fileButtons) {
                button.render(ctx, ax, y, aw, 16, mouseX, mouseY);
                y += 20;
            }
            y += 4;
        }

        // Links
        if (!linkButtons.isEmpty()) {
            y = section(ctx, "Links", ax, y);
            int lx = ax;
            for (NsButton button : linkButtons) {
                int lw = button.preferredWidth();
                button.render(ctx, lx, y, lw, 16, mouseX, mouseY);
                lx += lw + 6;
            }
            y += 22;
        }
        ctx.disableScissor();
        int contentHeight = y + (int) detailScroll - top;
        maxDetailScroll = Math.max(0, contentHeight - (listBottom - top) + 8);
    }

    private int maxDetailScroll;

    private int section(GuiGraphicsExtractor ctx, String title, int x, int y) {
        Fonts.draw(ctx, title, x, y, Theme.accentLight(), false);
        return y + Fonts.height() + 4;
    }

    private int infoRow(GuiGraphicsExtractor ctx, String label, String value, int x, int width, int y) {
        Fonts.draw(ctx, label, x, y, Theme.TEXT_DIM, false);
        int vx = x + 70;
        Fonts.draw(ctx, Fonts.trim(value, width - 70), vx, y, Theme.TEXT, false);
        return y + Fonts.height() + 3;
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (done.click(mouseX, mouseY, button) || openFolder.click(mouseX, mouseY, button) || libraries.click(mouseX, mouseY, button)
                || browse.click(mouseX, mouseY, button) || checkUpdates.click(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseY >= winY + 29 && mouseY < listBottom && mouseX >= detailX1 && update.click(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseY >= winY + 29 && mouseY < listBottom && mouseX >= detailX1) {
            if (configure.click(mouseX, mouseY, button) || toggle.click(mouseX, mouseY, button) || delete.click(mouseX, mouseY, button)) {
                return true;
            }
            for (NsButton b : fileButtons) {
                if (b.click(mouseX, mouseY, button)) {
                    return true;
                }
            }
            for (NsButton b : linkButtons) {
                if (b.click(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        if (button == 0 && mouseY >= listTop && mouseY < listBottom && mouseX >= winX + 12 && mouseX < listX2) {
            int index = (int) ((mouseY - listTop + listScroll) / (ROW_H + 3));
            if (index >= 0 && index < filtered.size()) {
                select(filtered.get(index));
                search.setFocused(false);
                return true;
            }
        }
        boolean handled = false;
        if (!RenderUtil.isHovered(mouseX, mouseY, winX + 12, winY + 29, listX2, winY + 46)) {
            search.setFocused(false);
        } else {
            setFocused(search);
            search.setFocused(true);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < listX2) {
            listScroll -= verticalAmount * 20;
        } else {
            detailScroll = Math.max(0, Math.min(maxDetailScroll, detailScroll - verticalAmount * 20));
        }
        return true;
    }

    @Override
    protected boolean onCharTyped(char chr, int modifiers) {
        if (!search.isFocused() && Character.isLetterOrDigit(chr)) {
            setFocused(search);
            search.setFocused(true);
        }
        return false;
    }

    // ------------------------------------------------------------------ test hooks

    public List<ModEntry> entriesForTest() {
        return filtered;
    }

    /** Test hook: selects the mod with this id and presses Configure. Returns whether it was found. */
    public boolean configureForTest(String id) {
        for (ModEntry entry : entriesForTest()) {
            if (id.equals(entry.id())) {
                selectForTest(entry);
                configureSelected();
                return true;
            }
        }
        return false;
    }

    public void selectForTest(ModEntry entry) {
        select(entry);
    }

    @Nullable
    public ModEntry selectedForTest() {
        return selected;
    }

    public void refreshForTest() {
        refresh();
    }

    public void setSearchForTest(String text) {
        search.setValue(text);
    }
}
