package com.nightshade.gui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.NsScreen;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.manager.ProfileManager;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Saved setups: save the current setup under a name, load or delete profiles, link them to servers. */
public final class ProfilesScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int ROW_H = 24;

    @Nullable
    private final Screen parent;
    private EditBox nameField;
    private String error;
    private double scroll;
    private List<String> profiles = List.of();

    private final NsButton save = new NsButton("Save current", NsButton.Style.PRIMARY, this::saveCurrent);
    private final NsButton done = new NsButton("Done", NsButton.Style.SECONDARY, this::onClose);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;

    public ProfilesScreen(@Nullable Screen parent) {
        super(Component.literal("Profiles"));
        this.parent = parent;
    }

    private ProfileManager manager() {
        return NightshadeClient.client().profiles();
    }

    @Override
    protected void init() {
        layout();
        nameField = new EditBox(font, panelX + 20, panelY + panelH - 51, panelW - 140, 14, Component.literal("Profile name"));
        nameField.setMaxLength(32);
        nameField.setBordered(false);
        nameField.setTextColor(Theme.TEXT);
        nameField.setHint(Component.literal("New profile name"));
        addWidget(nameField);
        refresh();
    }

    private void refresh() {
        profiles = manager().list();
    }

    private void layout() {
        panelW = Math.min(width - 32, 560);
        panelH = height - 4;
        panelX = (width - panelW) / 2;
        panelY = 2;
        listTop = panelY + 52;
        listBottom = panelY + panelH - 64;
        if (nameField != null) {
            nameField.setX(panelX + 20);
            nameField.setY(panelY + panelH - 51);
            nameField.setWidth(panelW - 140);
        }
    }

    private void saveCurrent() {
        String name = nameField.getValue().trim();
        String result = manager().save(name);
        if (result != null) {
            error = result;
            return;
        }
        error = null;
        nameField.setValue("");
        NightshadeClient.client().notifications().success("Profiles", "Saved '" + name + "'");
        refresh();
    }

    private void load(String name) {
        if (manager().load(name)) {
            NightshadeClient.client().notifications().success("Profiles", "Loaded '" + name + "'");
        }
    }

    private void delete(String name) {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                manager().delete(name);
            }
            minecraft.gui.setScreen(this);
            refresh();
        }, Component.literal("Delete profile '" + name + "'?"), Component.literal("Server links to this profile are removed too.")));
    }

    private void toggleLink(String name) {
        String server = ProfileManager.currentServerAddress();
        if (server == null) {
            return;
        }
        boolean linked = name.equalsIgnoreCase(manager().profileForServer(server));
        manager().linkServer(server, linked ? null : name);
        NightshadeClient.client().notifications().info("Profiles", linked ? "Unlinked from " + server : "'" + name + "' now loads on " + server);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        renderPage(ctx, panelX, panelX + panelW);
        RenderUtil.horizontalGradient(ctx, panelX + 10, panelY, panelX + panelW / 2, panelY + 1, 0x00000000, Theme.accent());
        RenderUtil.horizontalGradient(ctx, panelX + panelW / 2, panelY, panelX + panelW - 10, panelY + 1, Theme.accent(), 0x00000000);
        Fonts.draw(ctx, "Profiles", panelX + 16, panelY + 12, Theme.TEXT, false);
        String server = ProfileManager.currentServerAddress();
        String sub = server == null ? "Saved setups: modules, settings, keybinds and HUD layout"
                : "On " + server + (manager().profileForServer(server) != null ? "  -  linked: " + manager().profileForServer(server) : "");
        Fonts.draw(ctx, Fonts.trim(sub, panelW - 32), panelX + 16, panelY + 24, Theme.TEXT_DIM, false);
        Map<String, String> links = manager().serverProfiles();
        if (!links.isEmpty()) {
            Fonts.draw(ctx, Fonts.trim(links.size() + " server link" + (links.size() == 1 ? "" : "s") + ": " + String.join(", ", links.keySet()), panelW - 32),
                    panelX + 16, panelY + 36, Theme.TEXT_DIM, false);
        }

        String tooltip = renderList(ctx, mouseX, mouseY, server);

        int fy = panelY + panelH - 56;
        RenderUtil.roundedRect(ctx, panelX + 16, fy, panelX + panelW - 112, fy + 18, 4, nameField.isFocused() ? Theme.SELECTED : Theme.PANEL);
        RenderUtil.roundedOutline(ctx, panelX + 16, fy, panelX + panelW - 112, fy + 18, 4, nameField.isFocused() ? Theme.accent() : Theme.BORDER);
        nameField.extractRenderState(ctx, mouseX, mouseY, delta);
        save.render(ctx, panelX + panelW - 106, fy, 90, 18, mouseX, mouseY);
        if (error != null) {
            Fonts.draw(ctx, error, panelX + 16, fy + 21, Theme.DANGER, false);
        }
        done.render(ctx, panelX + panelW - 86, panelY + panelH - 26, 70, 16, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private String renderList(GuiGraphicsExtractor ctx, int mouseX, int mouseY, @Nullable String server) {
        int maxScroll = Math.max(0, profiles.size() * (ROW_H + 3) - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (profiles.isEmpty()) {
            Fonts.drawCentered(ctx, "No profiles yet - name your current setup below and save it", width / 2, listTop + 20, Theme.TEXT_DIM, false);
            return null;
        }
        String tooltip = null;
        ctx.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            int y = listTop + i * (ROW_H + 3) - (int) scroll;
            int x1 = panelX + 12;
            int x2 = panelX + panelW - 12;
            boolean hovered = mouseY >= listTop && mouseY < listBottom && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H);
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H, 5, hovered ? Theme.PANEL_HOVER : Theme.PANEL);
            boolean linked = server != null && name.equalsIgnoreCase(manager().profileForServer(server));
            RenderUtil.roundedOutline(ctx, x1, y, x2, y + ROW_H, 5, linked ? ColorUtil.withAlpha(Theme.accent(), 0xC0) : Theme.BORDER);
            Fonts.draw(ctx, Fonts.trim(name, x2 - x1 - 190), x1 + 8, y + 8, Theme.TEXT, false);
            int bx = x2 - 6;
            for (String label : actions(server, linked)) {
                int w = GuiDraw.pillWidth(label);
                bx -= w;
                boolean h = hovered && RenderUtil.isHovered(mouseX, mouseY, bx, y + 5, bx + w, y + 19);
                GuiDraw.pill(ctx, bx, y + 5, label, 14, h, label.equals("Delete") ? 0xFFFF9AAE : Theme.TEXT,
                        label.equals("Load") ? Theme.SELECTED : Theme.SWITCH_OFF);
                if (h && label.startsWith("Link")) {
                    tooltip = "Load this profile automatically whenever you join " + server;
                }
                bx -= 4;
            }
        }
        ctx.disableScissor();
        return tooltip;
    }

    private static List<String> actions(@Nullable String server, boolean linked) {
        if (server == null) {
            return List.of("Delete", "Load");
        }
        return List.of("Delete", linked ? "Unlink" : "Link", "Load");
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (save.click(mouseX, mouseY, button) || done.click(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || mouseY < listTop || mouseY >= listBottom) {
            boolean inField = RenderUtil.isHovered(mouseX, mouseY, panelX + 16, panelY + panelH - 56, panelX + panelW - 112, panelY + panelH - 38);
            if (inField) {
                setFocused(nameField);
                nameField.setFocused(true);
                return true;
            }
            return false;
        }
        String server = ProfileManager.currentServerAddress();
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            int y = listTop + i * (ROW_H + 3) - (int) scroll;
            int x2 = panelX + panelW - 12;
            if (mouseY < y || mouseY >= y + ROW_H) {
                continue;
            }
            boolean linked = server != null && name.equalsIgnoreCase(manager().profileForServer(server));
            int bx = x2 - 6;
            for (String label : actions(server, linked)) {
                int w = GuiDraw.pillWidth(label);
                bx -= w;
                if (RenderUtil.isHovered(mouseX, mouseY, bx, y + 5, bx + w, y + 19)) {
                    switch (label) {
                        case "Load" -> load(name);
                        case "Delete" -> delete(name);
                        default -> toggleLink(name);
                    }
                    return true;
                }
                bx -= 4;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * 18;
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.isFocused() && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            saveCurrent();
            return true;
        }
        return false;
    }
}
