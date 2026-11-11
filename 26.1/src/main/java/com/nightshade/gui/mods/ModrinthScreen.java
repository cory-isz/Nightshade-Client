package com.nightshade.gui.mods;

import com.nightshade.net.Http;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.NsScreen;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.mods.ModIcons;
import com.nightshade.mods.ModrinthClient;
import com.nightshade.mods.RemoteIcons;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Browse and install from Modrinth: Fabric mods (into the mods folder, restart to load) and resource packs (into the
 * resourcepacks folder, can be enabled immediately). Both are filtered to this Minecraft version.
 */
public final class ModrinthScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int ROW_H = 30;

    @Nullable
    private final Screen parent;
    private final ModrinthClient modrinth = new ModrinthClient();
    private final RemoteIcons icons = new RemoteIcons();
    private EditBox query;
    private volatile List<ModrinthClient.SearchHit> hits = new ArrayList<>();
    private volatile boolean loading;
    private volatile String error;
    private ModrinthClient.SearchHit selected;
    private final Map<String, String> installState = new HashMap<>();
    private double scroll;
    private int requestId;

    private final NsButton search = new NsButton("Search", NsButton.Style.SECONDARY, this::runSearch);
    private final NsButton install = new NsButton("Install", NsButton.Style.PRIMARY, this::installSelected);
    private final NsButton page = new NsButton("Open page", NsButton.Style.GHOST, this::openPage);
    private final NsButton back = new NsButton("Back", NsButton.Style.SECONDARY, this::onClose);
    private final NsButton enablePack = new NsButton("Enable", NsButton.Style.SECONDARY, this::enableSelectedPack);

    /** What the browser lists. */
    public enum Kind { MODS, RESOURCE_PACKS }

    private Kind kind = Kind.MODS;
    /** projectId -> downloaded resource-pack file name (this session). */
    private final Map<String, String> packFiles = new HashMap<>();
    private int tabsX;
    private int tabModsW;
    private int tabPacksW;

    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int listX2;
    private int listTop;
    private int listBottom;

    public ModrinthScreen(@Nullable Screen parent) {
        this(parent, Kind.MODS);
    }

    public ModrinthScreen(@Nullable Screen parent, Kind kind) {
        super(Component.literal("Browse Modrinth"));
        this.parent = parent;
        this.kind = kind;
    }

    private void switchKind(Kind newKind) {
        if (newKind == kind) {
            return;
        }
        kind = newKind;
        hits = new ArrayList<>();
        selected = null;
        scroll = 0;
        query.setHint(Component.literal(kind == Kind.MODS ? "Search mods..." : "Search resource packs..."));
        runSearch();
    }

    @Override
    protected void init() {
        layout();
        query = new EditBox(font, winX + 26, winY + 34, listX2 - winX - 104, 14, Component.literal("Search"));
        query.setBordered(false);
        query.setTextColor(Theme.TEXT);
        query.setHint(Component.literal(kind == Kind.MODS ? "Search mods..." : "Search resource packs..."));
        addWidget(query);
        if (hits.isEmpty()) {
            runSearch();
        }
    }

    private void layout() {
        winW = Math.min(width - 16, 700);
        winH = Math.min(height - 16, 400);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        listX2 = winX + Math.max(170, (int) (winW * 0.46));
        listTop = winY + 54;
        listBottom = winY + winH - 30;
        if (query != null) {
            query.setX(winX + 26);
            query.setY(winY + 34);
            query.setWidth(listX2 - winX - 104);
        }
    }

    private void runSearch() {
        String text = query == null ? "" : query.getValue().trim();
        int id = ++requestId;
        loading = true;
        error = null;
        Kind searching = kind;
        CompletableFuture.supplyAsync(() -> {
            try {
                return searching == Kind.MODS ? modrinth.search(text, 0) : modrinth.searchResourcePacks(text, 0);
            } catch (Exception e) {
                throw new RuntimeException(Http.describe(e));
            }
        }, Util.nonCriticalIoPool()).whenComplete((result, failure) -> minecraft.execute(() -> {
            if (id != requestId) {
                return;
            }
            loading = false;
            if (failure != null) {
                error = "Could not reach Modrinth: " + (failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage());
                return;
            }
            hits = result;
            scroll = 0;
            selected = result.isEmpty() ? null : result.get(0);
        }));
    }

    private void installSelected() {
        if (kind == Kind.RESOURCE_PACKS) {
            installSelectedPack();
            return;
        }
        ModrinthClient.SearchHit hit = selected;
        if (hit == null || installState.containsKey(hit.projectId())) {
            return;
        }
        installState.put(hit.projectId(), "Installing...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return modrinth.install(hit.projectId(), NightshadeClient.client().mods().modsDir(),
                        message -> minecraft.execute(() -> installState.put(hit.projectId(), message)));
            } catch (Exception e) {
                throw new RuntimeException(Http.describe(e));
            }
        }, Util.nonCriticalIoPool()).whenComplete((files, failure) -> minecraft.execute(() -> {
            if (failure != null) {
                String message = failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage();
                installState.put(hit.projectId(), "Failed: " + message);
                NightshadeClient.client().notifications().error("Modrinth", hit.title() + ": " + message);
                return;
            }
            String summary = files.isEmpty() ? "Already installed" : "Installed - restart to load";
            installState.put(hit.projectId(), summary);
            NightshadeClient.client().notifications().success("Modrinth", files.isEmpty() ? hit.title() + " is already installed"
                    : "Installed " + String.join(", ", files) + ". Restart Minecraft to load " + (files.size() == 1 ? "it" : "them") + ".");
        }));
    }

    private void installSelectedPack() {
        ModrinthClient.SearchHit hit = selected;
        if (hit == null || (installState.containsKey(hit.projectId()) && !installState.get(hit.projectId()).startsWith("Failed"))) {
            return;
        }
        installState.put(hit.projectId(), "Installing...");
        java.nio.file.Path packsDir = minecraft.getResourcePackDirectory();
        CompletableFuture.supplyAsync(() -> {
            try {
                String name = modrinth.installResourcePack(hit.projectId(), packsDir,
                        message -> minecraft.execute(() -> installState.put(hit.projectId(), message)));
                if (name == null) {
                    // Same file already present: find its name so Enable still works.
                    var version = modrinth.latestResourcePackVersion(hit.projectId());
                    return version == null ? "" : "=" + ModrinthClient.safeFileName(version.file().filename());
                }
                return name;
            } catch (Exception e) {
                throw new RuntimeException(Http.describe(e));
            }
        }, Util.nonCriticalIoPool()).whenComplete((name, failure) -> minecraft.execute(() -> {
            if (failure != null) {
                String message = failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage();
                installState.put(hit.projectId(), "Failed: " + message);
                NightshadeClient.client().notifications().error("Modrinth", hit.title() + ": " + message);
                return;
            }
            boolean already = name.startsWith("=");
            String file = already ? name.substring(1) : name;
            if (!file.isEmpty()) {
                packFiles.put(hit.projectId(), file);
            }
            installState.put(hit.projectId(), already ? "Already installed" : "Installed");
            NightshadeClient.client().notifications().success("Modrinth", already ? hit.title() + " is already installed"
                    : "Installed " + file + ". Press Enable to use it now.");
        }));
    }

    /** Turns the downloaded pack on (top of the list) and reloads resources - no restart needed. */
    private void enableSelectedPack() {
        ModrinthClient.SearchHit hit = selected;
        String file = hit == null ? null : packFiles.get(hit.projectId());
        if (file == null) {
            return;
        }
        var repository = minecraft.getResourcePackRepository();
        repository.reload();
        String id = "file/" + file;
        if (!repository.getAvailableIds().contains(id)) {
            NightshadeClient.client().notifications().error("Modrinth", "Minecraft did not recognise " + file + " as a resource pack");
            return;
        }
        java.util.List<String> selectedIds = new ArrayList<>(repository.getSelectedIds());
        if (selectedIds.contains(id)) {
            installState.put(hit.projectId(), "Enabled");
            return;
        }
        selectedIds.add(id); // last = highest priority
        repository.setSelected(selectedIds);
        minecraft.options.updateResourcePacks(repository);
        installState.put(hit.projectId(), "Enabled");
        NightshadeClient.client().notifications().success("Modrinth", hit.title() + " enabled");
    }

    private void openPage() {
        if (selected != null) {
            ConfirmLinkScreen.confirmLinkNow(this, "https://modrinth.com/" + (kind == Kind.MODS ? "mod/" : "resourcepack/") + selected.slug());
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        icons.release();
        super.removed();
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        RenderUtil.roundedRect(ctx, winX, winY, winX + winW, winY + winH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, winX, winY, winX + winW, winY + winH, 8, Theme.BORDER);
        RenderUtil.horizontalGradient(ctx, winX + 10, winY, winX + winW / 2, winY + 1, 0x00000000, 0xFF1BD96A);
        RenderUtil.horizontalGradient(ctx, winX + winW / 2, winY, winX + winW - 10, winY + 1, 0xFF1BD96A, 0x00000000);
        Fonts.draw(ctx, "Browse Modrinth", winX + 14, winY + 12, Theme.TEXT, false);
        tabsX = winX + 14 + Fonts.width("Browse Modrinth") + 12;
        tabModsW = GuiDraw.pillWidth("Mods");
        tabPacksW = GuiDraw.pillWidth("Resource Packs");
        int tabY = winY + 8;
        GuiDraw.pill(ctx, tabsX, tabY, "Mods", 15, RenderUtil.isHovered(mouseX, mouseY, tabsX, tabY, tabsX + tabModsW, tabY + 15),
                kind == Kind.MODS ? Theme.TEXT : Theme.TEXT_MUTED, kind == Kind.MODS ? 0xFF1F8F52 : Theme.PANEL);
        int px = tabsX + tabModsW + 4;
        GuiDraw.pill(ctx, px, tabY, "Resource Packs", 15, RenderUtil.isHovered(mouseX, mouseY, px, tabY, px + tabPacksW, tabY + 15),
                kind == Kind.RESOURCE_PACKS ? Theme.TEXT : Theme.TEXT_MUTED, kind == Kind.RESOURCE_PACKS ? 0xFF1F8F52 : Theme.PANEL);
        String sub = (kind == Kind.MODS ? "Fabric" : "Resource packs") + "  |  Minecraft " + SharedConstants.getCurrentVersion().name();
        int subX = px + tabPacksW + 10;
        if (subX + Fonts.width(sub) < winX + winW - 12) {
            Fonts.draw(ctx, sub, subX, winY + 12, Theme.TEXT_DIM, false);
        }

        RenderUtil.roundedRect(ctx, winX + 12, winY + 29, listX2 - 70, winY + 46, 4, query.isFocused() ? Theme.SELECTED : Theme.PANEL);
        RenderUtil.roundedOutline(ctx, winX + 12, winY + 29, listX2 - 70, winY + 46, 4, query.isFocused() ? Theme.accent() : Theme.BORDER);
        PixelIcons.search(ctx, winX + 16, winY + 34, Theme.TEXT_DIM);
        query.extractRenderState(ctx, mouseX, mouseY, delta);
        search.render(ctx, listX2 - 64, winY + 29, 64, 17, mouseX, mouseY);

        renderList(ctx, mouseX, mouseY);
        renderDetails(ctx, mouseX, mouseY);
        back.render(ctx, winX + winW - 82, winY + winH - 24, 70, 16, mouseX, mouseY);
        Fonts.draw(ctx, Fonts.trim("Data from modrinth.com  -  downloads are checked against Modrinth's SHA-512", winW - 110), winX + 14, winY + winH - 20, Theme.TEXT_DIM, false);
        Notifications.renderIfEnabled(ctx);
    }

    private void renderList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x1 = winX + 12;
        int x2 = listX2;
        List<ModrinthClient.SearchHit> list = hits;
        int maxScroll = Math.max(0, list.size() * (ROW_H + 3) - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ctx.enableScissor(x1, listTop, x2 + 1, listBottom);
        for (int i = 0; i < list.size(); i++) {
            ModrinthClient.SearchHit hit = list.get(i);
            int y = listTop + i * (ROW_H + 3) - (int) scroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            boolean hovered = mouseY >= listTop && mouseY < listBottom && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H);
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H, 5, hit == selected ? Theme.SELECTED : hovered ? Theme.PANEL_HOVER : Theme.PANEL);
            drawIcon(ctx, hit, x1 + 5, y + 4, 22);
            Fonts.draw(ctx, Fonts.trim(hit.title(), x2 - x1 - 40), x1 + 32, y + 5, Theme.TEXT, false);
            String sub = installState.getOrDefault(hit.projectId(), "by " + hit.author() + "  |  " + compact(hit.downloads()) + " downloads");
            Fonts.draw(ctx, Fonts.trim(sub, x2 - x1 - 40), x1 + 32, y + 17, installState.containsKey(hit.projectId()) ? Theme.accentLight() : Theme.TEXT_DIM, false);
        }
        ctx.disableScissor();
        if (loading) {
            Fonts.drawCentered(ctx, "Searching Modrinth...", (x1 + x2) / 2, listTop + 16, Theme.TEXT_DIM, false);
        } else if (error != null) {
            int ly = listTop + 10;
            for (FormattedCharSequence line : font.split(Fonts.text(error), x2 - x1 - 10)) {
                ctx.text(font, line, x1 + 5, ly, Theme.DANGER, false);
                ly += font.lineHeight + 2;
            }
        } else if (list.isEmpty()) {
            Fonts.drawCentered(ctx, kind == Kind.MODS ? "No mods found" : "No resource packs found", (x1 + x2) / 2, listTop + 16, Theme.TEXT_DIM, false);
        }
    }

    private void renderDetails(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x1 = listX2 + 10;
        int x2 = winX + winW - 12;
        RenderUtil.roundedRect(ctx, x1, winY + 29, x2, listBottom, 6, com.nightshade.render.Theme.tint(0x60140B24));
        ModrinthClient.SearchHit hit = selected;
        if (hit == null) {
            return;
        }
        int y = winY + 39;
        drawIcon(ctx, hit, x1 + 10, y, 36);
        int tx = x1 + 54;
        Fonts.draw(ctx, Fonts.trim(hit.title(), x2 - tx - 8), tx, y + 2, Theme.TEXT, false);
        Fonts.draw(ctx, Fonts.trim("by " + hit.author(), x2 - tx - 8), tx, y + 13, Theme.TEXT_DIM, false);
        Fonts.draw(ctx, compact(hit.downloads()) + " downloads", tx, y + 24, 0xFF1BD96A, false);
        y += 46;
        String state = installState.get(hit.projectId());
        int bw = (x2 - x1 - 26) / 2;
        if (kind == Kind.RESOURCE_PACKS) {
            boolean failed = state != null && state.startsWith("Failed");
            boolean downloaded = packFiles.containsKey(hit.projectId());
            if (downloaded) {
                boolean enabled = "Enabled".equals(state);
                enablePack.label(enabled ? "Enabled" : "Enable");
                enablePack.enabled(!enabled);
                enablePack.render(ctx, x1 + 10, y, bw, 18, mouseX, mouseY);
            } else {
                install.label(state == null ? "Install" : failed ? "Retry" : state);
                install.enabled(state == null || failed);
                install.render(ctx, x1 + 10, y, bw, 18, mouseX, mouseY);
            }
            page.render(ctx, x1 + 16 + bw, y, bw, 18, mouseX, mouseY);
        } else {
            boolean alreadyLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(hit.slug())
                    || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(hit.slug().replace('-', '_'));
            install.label(state != null ? (state.startsWith("Failed") ? "Retry" : state) : alreadyLoaded ? "Installed" : "Install");
            install.enabled(state == null && !alreadyLoaded || (state != null && state.startsWith("Failed")));
            install.render(ctx, x1 + 10, y, bw, 18, mouseX, mouseY);
            page.render(ctx, x1 + 16 + bw, y, bw, 18, mouseX, mouseY);
        }
        y += 28;
        Fonts.draw(ctx, "Description", x1 + 10, y, Theme.accentLight(), false);
        y += Fonts.height() + 4;
        for (FormattedCharSequence line : font.split(Fonts.text(hit.description().isBlank() ? "No description." : hit.description()), x2 - x1 - 20)) {
            if (y > listBottom - 12) {
                break;
            }
            ctx.text(font, line, x1 + 10, y, Theme.TEXT_MUTED, false);
            y += Fonts.height() + 2;
        }
        if (state != null && state.startsWith("Failed")) {
            Fonts.draw(ctx, Fonts.trim(state, x2 - x1 - 20), x1 + 10, listBottom - 14, Theme.DANGER, false);
        } else if (kind == Kind.MODS && state != null && state.startsWith("Installed")) {
            Fonts.draw(ctx, "Restart Minecraft to load it", x1 + 10, listBottom - 14, Theme.WARNING, false);
        } else if (kind == Kind.RESOURCE_PACKS && packFiles.containsKey(hit.projectId()) && !"Enabled".equals(state)) {
            Fonts.draw(ctx, "Downloaded to resourcepacks - Enable turns it on now", x1 + 10, listBottom - 14, Theme.accentLight(), false);
        }
    }

    private void drawIcon(GuiGraphicsExtractor ctx, ModrinthClient.SearchHit hit, int x, int y, int size) {
        ModIcons.Icon icon = icons.get(hit.iconUrl());
        if (icon != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, icon.id(), x, y, 0f, 0f, size, size, icon.width(), icon.height(), icon.width(), icon.height());
            return;
        }
        RenderUtil.roundedRect(ctx, x, y, x + size, y + size, 4, ColorUtil.withAlpha(0xFF1BD96A, 0x40));
        Fonts.drawCentered(ctx, hit.title().isEmpty() ? "?" : hit.title().substring(0, 1).toUpperCase(Locale.ROOT), x + size / 2,
                y + (size - Fonts.height()) / 2 + 1, 0xFF1BD96A, false);
    }

    private static String compact(long value) {
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, tabsX, winY + 8, tabsX + tabModsW, winY + 23)) {
            switchKind(Kind.MODS);
            return true;
        }
        int packsTabX = tabsX + tabModsW + 4;
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, packsTabX, winY + 8, packsTabX + tabPacksW, winY + 23)) {
            switchKind(Kind.RESOURCE_PACKS);
            return true;
        }
        boolean packReady = kind == Kind.RESOURCE_PACKS && selected != null && packFiles.containsKey(selected.projectId());
        if (search.click(mouseX, mouseY, button) || (packReady ? enablePack.click(mouseX, mouseY, button) : install.click(mouseX, mouseY, button))
                || page.click(mouseX, mouseY, button) || back.click(mouseX, mouseY, button)) {
            return true;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, winX + 12, winY + 29, listX2 - 70, winY + 46)) {
            setFocused(query);
            query.setFocused(true);
            return true;
        }
        query.setFocused(false);
        if (button == 0 && mouseY >= listTop && mouseY < listBottom && mouseX >= winX + 12 && mouseX < listX2) {
            int index = (int) ((mouseY - listTop + scroll) / (ROW_H + 3));
            if (index >= 0 && index < hits.size()) {
                selected = hits.get(index);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < listX2) {
            scroll -= verticalAmount * 24;
        }
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (query.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            runSearch();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ test hooks

    public boolean isLoadingForTest() {
        return loading;
    }

    public List<ModrinthClient.SearchHit> hitsForTest() {
        return hits;
    }

    @Nullable
    public String errorForTest() {
        return error;
    }

    public void searchForTest(String text) {
        query.setValue(text);
        runSearch();
    }

    public void selectForTest(ModrinthClient.SearchHit hit) {
        selected = hit;
    }

    public void installForTest() {
        installSelected();
    }

    public Map<String, String> installStateForTest() {
        return installState;
    }

    public void switchKindForTest(Kind newKind) {
        switchKind(newKind);
    }

    public void enablePackForTest() {
        enableSelectedPack();
    }

    public Map<String, String> packFilesForTest() {
        return packFiles;
    }
}
