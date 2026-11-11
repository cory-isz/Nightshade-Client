package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.NsScreen;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.waypoint.Waypoint;
import com.nightshade.waypoint.WaypointManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Waypoints of the current world: add at your position, show/hide, delete. */
public final class WaypointsScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int ROW_H = 22;

    @Nullable
    private final Screen parent;
    private EditBox nameField;
    private double scroll;

    private final NsButton add = new NsButton("Add here", NsButton.Style.PRIMARY, this::addHere);
    private final NsButton done = new NsButton("Done", NsButton.Style.SECONDARY, this::onClose);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;

    public WaypointsScreen(@Nullable Screen parent) {
        super(Component.literal("Waypoints"));
        this.parent = parent;
    }

    private WaypointManager manager() {
        return NightshadeClient.client().waypoints();
    }

    @Override
    protected void init() {
        layout();
        nameField = new EditBox(font, panelX + 20, panelY + panelH - 51, panelW - 140, 14, Component.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setBordered(false);
        nameField.setTextColor(Theme.TEXT);
        nameField.setHint(Component.literal("Waypoint name (optional)"));
        addWidget(nameField);
    }

    private void layout() {
        panelW = Math.min(width - 24, 400);
        panelH = Math.min(height - 24, 300);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 42;
        listBottom = panelY + panelH - 64;
        if (nameField != null) {
            nameField.setX(panelX + 20);
            nameField.setY(panelY + panelH - 51);
        }
    }

    private void addHere() {
        Waypoint waypoint = manager().addHere(nameField.getValue());
        if (waypoint == null) {
            NightshadeClient.client().notifications().warn("Waypoints", "Join a world to add waypoints");
            return;
        }
        nameField.setValue("");
        NightshadeClient.client().notifications().success("Waypoints", "Added " + waypoint.name());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        RenderUtil.roundedRect(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BORDER);
        Fonts.draw(ctx, "Waypoints", panelX + 16, panelY + 12, Theme.TEXT, false);
        String world = WaypointManager.currentWorldKey();
        Fonts.draw(ctx, Fonts.trim(world == null ? "Join a world to manage its waypoints" : world.substring(3) + "  -  "
                + WaypointManager.currentDimension(), panelW - 32), panelX + 16, panelY + 24, Theme.TEXT_DIM, false);
        List<Waypoint> list = manager().current();
        int maxScroll = Math.max(0, list.size() * (ROW_H + 2) - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ctx.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
        String dimension = WaypointManager.currentDimension();
        for (int i = 0; i < list.size(); i++) {
            Waypoint w = list.get(i);
            int y = listTop + i * (ROW_H + 2) - (int) scroll;
            int x1 = panelX + 12;
            int x2 = panelX + panelW - 12;
            boolean hovered = mouseY >= listTop && mouseY < listBottom && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H);
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H, 4, hovered ? Theme.PANEL_HOVER : Theme.PANEL);
            RenderUtil.roundedRect(ctx, x1 + 6, y + 7, x1 + 14, y + 15, 2, w.visible() ? w.color() : ColorUtil.fade(w.color(), 0.3f));
            String distance = minecraft.player != null && w.dimension().equals(dimension)
                    ? String.format(Locale.ROOT, "  %.0fm", Math.sqrt(minecraft.player.distanceToSqr(w.x(), w.y(), w.z()))) : "  (other dimension)";
            Fonts.draw(ctx, Fonts.trim(w.name() + "  " + w.x() + ", " + w.y() + ", " + w.z() + distance, x2 - x1 - 120), x1 + 20, y + 7,
                    w.visible() ? Theme.TEXT : Theme.TEXT_DIM, false);
            int bx = x2 - 6;
            for (String label : new String[]{"Delete", w.visible() ? "Hide" : "Show"}) {
                int bw = GuiDraw.pillWidth(label);
                bx -= bw;
                GuiDraw.pill(ctx, bx, y + 4, label, 14, hovered && RenderUtil.isHovered(mouseX, mouseY, bx, y + 4, bx + bw, y + 18),
                        label.equals("Delete") ? 0xFFFF9AAE : Theme.TEXT, Theme.SWITCH_OFF);
                bx -= 4;
            }
        }
        if (list.isEmpty()) {
            Fonts.drawCentered(ctx, "No waypoints in this world yet", width / 2, listTop + 16, Theme.TEXT_DIM, false);
        }
        ctx.disableScissor();
        int fy = panelY + panelH - 56;
        RenderUtil.roundedRect(ctx, panelX + 16, fy, panelX + panelW - 112, fy + 18, 4, nameField.isFocused() ? Theme.SELECTED : Theme.PANEL);
        RenderUtil.roundedOutline(ctx, panelX + 16, fy, panelX + panelW - 112, fy + 18, 4, nameField.isFocused() ? Theme.accent() : Theme.BORDER);
        nameField.extractRenderState(ctx, mouseX, mouseY, delta);
        add.enabled(world != null);
        add.render(ctx, panelX + panelW - 106, fy, 90, 18, mouseX, mouseY);
        done.render(ctx, panelX + panelW - 86, panelY + panelH - 26, 70, 16, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (add.click(mouseX, mouseY, button) || done.click(mouseX, mouseY, button)) {
            return true;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, panelX + 16, panelY + panelH - 56, panelX + panelW - 112, panelY + panelH - 38)) {
            setFocused(nameField);
            nameField.setFocused(true);
            return true;
        }
        if (button != 0 || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        List<Waypoint> list = manager().current();
        for (int i = 0; i < list.size(); i++) {
            Waypoint w = list.get(i);
            int y = listTop + i * (ROW_H + 2) - (int) scroll;
            if (mouseY < y || mouseY >= y + ROW_H) {
                continue;
            }
            int bx = panelX + panelW - 12 - 6;
            int deleteW = GuiDraw.pillWidth("Delete");
            if (mouseX >= bx - deleteW && mouseX < bx) {
                manager().remove(w);
                return true;
            }
            bx -= deleteW + 4;
            int toggleW = GuiDraw.pillWidth(w.visible() ? "Hide" : "Show");
            if (mouseX >= bx - toggleW && mouseX < bx) {
                w.setVisible(!w.visible());
                manager().save();
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
        if (nameField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            addHere();
            return true;
        }
        return false;
    }
}
