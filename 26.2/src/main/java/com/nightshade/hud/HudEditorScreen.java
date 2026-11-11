package com.nightshade.hud;

import com.nightshade.gui.NsScreen;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.GuiDraw;
import com.nightshade.manager.ClientManager;
import com.nightshade.module.HudModule;
import com.nightshade.module.interfaces.HudEditorModule;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Visual HUD layout editor. Drag components with the left mouse button, scroll to scale, right click to show or
 * hide, arrow keys to nudge the selected component. Positions are clamped to the screen and persisted.
 */
public final class HudEditorScreen extends NsScreen {
    private static final int GUIDE_SNAP = 4;

    private final ClientManager manager = NightshadeClient.client();
    private final HudEditorModule settings = manager.modules().get(HudEditorModule.class);
    @Nullable
    private final Screen parent;
    @Nullable
    private HudModule dragging;
    @Nullable
    private HudModule selected;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean snappedVertical;
    private boolean snappedHorizontal;
    private int resetX;
    private int doneX;
    private int buttonsY;

    public HudEditorScreen(@Nullable Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No vanilla blur behind the editor: you need to see the game while arranging the HUD. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    }

    /** Guide lines found while dragging: x positions (vertical lines) and y positions (horizontal lines). */
    private final java.util.List<Integer> guideX = new java.util.ArrayList<>();
    private final java.util.List<Integer> guideY = new java.util.ArrayList<>();
    private final com.nightshade.render.Animation toolbarFade = new com.nightshade.render.Animation(14f, 1f);
    private int toolbarX;
    private int toolbarY;
    private int toolbarW;
    private int snapX;
    private int snapW;

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, com.nightshade.render.Theme.tint(0x50050210));
        if (settings != null && settings.snap()) {
            int step = Math.max(8, settings.gridSize() * 4);
            for (int x = step; x < width; x += step) {
                ctx.fill(x, 0, x + 1, height, 0x0CFFFFFF);
            }
            for (int y = step; y < height; y += step) {
                ctx.fill(0, y, width, y + 1, 0x0CFFFFFF);
            }
        }
        int guideColor = ColorUtil.withAlpha(Theme.accentLight(), 0xD0);
        if (dragging != null) {
            if (snappedVertical) {
                ctx.fill(width / 2, 0, width / 2 + 1, height, guideColor);
            }
            if (snappedHorizontal) {
                ctx.fill(0, height / 2, width, height / 2 + 1, guideColor);
            }
            for (int gx : guideX) {
                ctx.fill(gx, 0, gx + 1, height, ColorUtil.withAlpha(Theme.accentLight(), 0x90));
            }
            for (int gy : guideY) {
                ctx.fill(0, gy, width, gy + 1, ColorUtil.withAlpha(Theme.accentLight(), 0x90));
            }
        }

        List<HudModule> modules = manager.modules().getHudModules();
        List<HudModule> enabled = new java.util.ArrayList<>();
        for (HudModule module : modules) {
            if (module.isEnabled()) {
                enabled.add(module);
            }
        }
        HudLayout.apply(enabled, width, height);
        HudModule hovered = dragging != null ? dragging : moduleAt(mouseX, mouseY);
        for (HudModule module : modules) {
            int x = module.getScreenX(width);
            int y = module.getScreenY(height);
            int w = module.getScaledWidth();
            int h = module.getScaledHeight();
            boolean active = module == hovered || module == selected;
            if (module.isEnabled()) {
                RenderUtil.roundedRect(ctx, x - 2, y - 2, x + w + 2, y + h + 2, 3, active ? ColorUtil.withAlpha(Theme.accent(), 0x30) : 0x14FFFFFF);
                manager.hud().renderSafely(module, ctx, delta, true);
                RenderUtil.roundedOutline(ctx, x - 2, y - 2, x + w + 2, y + h + 2, 3, active ? Theme.accentLight() : 0x40FFFFFF);
            }
        }
        if (hovered != null) {
            String label = hovered.getName() + "  " + String.format(Locale.ROOT, "%.2fx", hovered.scaleSetting().get())
                    + (hovered.hasCustomPosition() ? "" : "  \u00B7 auto");
            int lw = Fonts.width(label);
            int lx = Math.max(2, Math.min(hovered.getScreenX(width), width - lw - 8));
            int ly = hovered.getScreenY(height) - 15;
            if (ly < 2) {
                ly = hovered.getScreenY(height) + hovered.getScaledHeight() + 5;
            }
            RenderUtil.roundedRect(ctx, lx - 1, ly - 1, lx + lw + 7, ly + Fonts.height() + 3, 3, com.nightshade.render.Theme.tint(0xF00E081A));
            RenderUtil.roundedOutline(ctx, lx - 1, ly - 1, lx + lw + 7, ly + Fonts.height() + 3, 3, ColorUtil.withAlpha(Theme.accent(), 0xA0));
            Fonts.draw(ctx, label, lx + 3, ly + 2, Theme.TEXT, false);
        }

        renderToolbar(ctx, mouseX, mouseY, enabled);
        Notifications.renderIfEnabled(ctx);
    }

    private static void dashed(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        for (int x = x1; x < x2; x += 4) {
            ctx.fill(x, y1, Math.min(x + 2, x2), y1 + 1, color);
            ctx.fill(x, y2 - 1, Math.min(x + 2, x2), y2, color);
        }
        for (int y = y1; y < y2; y += 4) {
            ctx.fill(x1, y, x1 + 1, Math.min(y + 2, y2), color);
            ctx.fill(x2 - 1, y, x2, Math.min(y + 2, y2), color);
        }
    }

    /** Floating toolbar, placed where it covers the fewest components and faded out while dragging. */
    private void renderToolbar(GuiGraphicsExtractor ctx, int mouseX, int mouseY, List<HudModule> enabled) {
        float alpha = toolbarFade.setTarget(dragging != null ? 0f : 1f).update();
        String help = "Drag \u00B7 Scroll to scale \u00B7 Right click to hide \u00B7 Arrows to nudge";
        List<HudModule> hidden = new java.util.ArrayList<>();
        for (HudModule m : manager.modules().getHudModules()) {
            if (!m.isEnabled()) {
                hidden.add(m);
            }
        }
        String snapLabel = "Snap: " + (settings != null && settings.snap() ? "On" : "Off");
        int resetW = GuiDraw.pillWidth("Reset Layout");
        snapW = GuiDraw.pillWidth(snapLabel);
        int doneW = GuiDraw.pillWidth("Done");
        int buttonsW = resetW + snapW + doneW + 12;
        toolbarW = Math.max(buttonsW, Math.min(Fonts.width(help), width - 20)) + 16;
        // Hidden components as chips (click to show), wrapped inside the toolbar.
        chips.clear();
        int chipsTop = 40;
        int cx = 8;
        int cy = chipsTop + 12;
        int maxW = toolbarW - 16;
        for (HudModule m : hidden) {
            int cw = Fonts.width(m.getName()) + 14;
            if (cx + cw > maxW + 8 && cx > 8) {
                cx = 8;
                cy += 15;
            }
            chips.add(new Chip(m, cx, cy, cw));
            cx += cw + 4;
        }
        int barH = hidden.isEmpty() ? 40 : cy + 18;
        int[] candidates = {height / 2 - barH / 2, Math.max(4, height - barH - 58), 46, height / 4};
        int best = candidates[0];
        int bestCover = Integer.MAX_VALUE;
        int bx = (width - toolbarW) / 2;
        for (int candY : candidates) {
            int cover = 0;
            for (HudModule m : enabled) {
                int mx = m.getScreenX(width);
                int my = m.getScreenY(height);
                int ix = Math.max(0, Math.min(bx + toolbarW, mx + m.getScaledWidth()) - Math.max(bx, mx));
                int iy = Math.max(0, Math.min(candY + barH, my + m.getScaledHeight()) - Math.max(candY, my));
                cover += ix * iy;
            }
            if (cover < bestCover) {
                bestCover = cover;
                best = candY;
            }
        }
        if (dragging == null) {
            toolbarX = bx;
            toolbarY = best;
        }
        if (alpha < 0.02f) {
            return;
        }
        com.nightshade.render.Fx.shadow(ctx, toolbarX, toolbarY + 2, toolbarX + toolbarW, toolbarY + barH + 2, 7, ColorUtil.fade(0xA0000000, alpha));
        RenderUtil.roundedRect(ctx, toolbarX, toolbarY, toolbarX + toolbarW, toolbarY + barH, 7, ColorUtil.fade(0xEE100A1E, alpha));
        RenderUtil.roundedOutline(ctx, toolbarX, toolbarY, toolbarX + toolbarW, toolbarY + barH, 7, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 0x80), alpha));
        Fonts.drawCentered(ctx, Fonts.trim(help, toolbarW - 12), width / 2, toolbarY + 6, ColorUtil.fade(Theme.TEXT_MUTED, alpha), false);
        buttonsY = toolbarY + 19;
        resetX = (width - buttonsW) / 2;
        snapX = resetX + resetW + 6;
        doneX = snapX + snapW + 6;
        GuiDraw.pill(ctx, resetX, buttonsY, "Reset Layout", 16, RenderUtil.isHovered(mouseX, mouseY, resetX, buttonsY, resetX + resetW, buttonsY + 16),
                ColorUtil.fade(Theme.WARNING, alpha), ColorUtil.fade(Theme.BACKGROUND, alpha));
        GuiDraw.pill(ctx, snapX, buttonsY, snapLabel, 16, RenderUtil.isHovered(mouseX, mouseY, snapX, buttonsY, snapX + snapW, buttonsY + 16),
                ColorUtil.fade(Theme.TEXT, alpha), ColorUtil.fade(Theme.PANEL, alpha));
        GuiDraw.pill(ctx, doneX, buttonsY, "Done", 16, RenderUtil.isHovered(mouseX, mouseY, doneX, buttonsY, doneX + doneW, buttonsY + 16),
                ColorUtil.fade(Theme.TEXT, alpha), ColorUtil.fade(Theme.accent(), alpha));
        if (!chips.isEmpty()) {
            ctx.fill(toolbarX + 8, toolbarY + 39, toolbarX + toolbarW - 8, toolbarY + 40, ColorUtil.fade(0x20FFFFFF, alpha));
            Fonts.draw(ctx, "Hidden \u00B7 click to show", toolbarX + 8, toolbarY + 42, ColorUtil.fade(Theme.TEXT_DIM, alpha), false);
            for (Chip chip : chips) {
                int x1 = toolbarX + chip.x();
                int y1 = toolbarY + chip.y();
                boolean hov = RenderUtil.isHovered(mouseX, mouseY, x1, y1, x1 + chip.w(), y1 + 13);
                RenderUtil.roundedRect(ctx, x1, y1, x1 + chip.w(), y1 + 13, 6, ColorUtil.fade(hov ? ColorUtil.withAlpha(Theme.accent(), 0x70) : 0x30FFFFFF, alpha));
                Fonts.draw(ctx, chip.module().getName(), x1 + 7, y1 + 3, ColorUtil.fade(hov ? Theme.TEXT : Theme.TEXT_MUTED, alpha), false);
            }
        }
    }

    private record Chip(HudModule module, int x, int y, int w) {
    }

    private final java.util.List<Chip> chips = new java.util.ArrayList<>();

    @Nullable
    private HudModule moduleAt(double mouseX, double mouseY) {
        List<HudModule> modules = manager.modules().getHudModules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            HudModule module = modules.get(i);
            if (!module.isEnabled()) {
                continue;
            }
            int x = module.getScreenX(width);
            int y = module.getScreenY(height);
            if (RenderUtil.isHovered(mouseX, mouseY, x, y, x + module.getScaledWidth(), y + module.getScaledHeight())) {
                return module;
            }
        }
        return null;
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, doneX, buttonsY, doneX + GuiDraw.pillWidth("Done"), buttonsY + 16)) {
            onClose();
            return true;
        }
        if (button == 0 && dragging == null) {
            for (Chip chip : chips) {
                int x1 = toolbarX + chip.x();
                int y1 = toolbarY + chip.y();
                if (RenderUtil.isHovered(mouseX, mouseY, x1, y1, x1 + chip.w(), y1 + 13)) {
                    manager.modules().setEnabled(chip.module(), true);
                    return true;
                }
            }
        }
        if (button == 0 && settings != null && RenderUtil.isHovered(mouseX, mouseY, snapX, buttonsY, snapX + snapW, buttonsY + 16)) {
            settings.toggleSnap();
            return true;
        }
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, resetX, buttonsY, resetX + GuiDraw.pillWidth("Reset Layout"), buttonsY + 16)) {
            for (HudModule module : manager.modules().getHudModules()) {
                module.resetPosition();
                module.setScale(module.scaleSetting().getDefault());
            }
            manager.notifications().info("HUD Editor", "Layout reset");
            return true;
        }
        HudModule module = moduleAt(mouseX, mouseY);
        if (module == null) {
            selected = null;
            return false;
        }
        if (button == 1) {
            manager.modules().toggle(module);
            return true;
        }
        if (button == 0) {
            dragging = module;
            selected = module;
            dragOffsetX = mouseX - module.getScreenX(width);
            dragOffsetY = mouseY - module.getScreenY(height);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        HudModule module = dragging;
        if (module == null) {
            return false;
        }
        double x = mouseX - dragOffsetX;
        double y = mouseY - dragOffsetY;
        int w = module.getScaledWidth();
        int h = module.getScaledHeight();
        if (settings != null && settings.snap()) {
            int grid = settings.gridSize();
            x = Math.round(x / grid) * grid;
            y = Math.round(y / grid) * grid;
        }
        snappedVertical = false;
        snappedHorizontal = false;
        if (settings != null && settings.guides()) {
            if (Math.abs(x + w / 2.0 - width / 2.0) <= GUIDE_SNAP) {
                x = width / 2.0 - w / 2.0;
                snappedVertical = true;
            }
            if (Math.abs(y + h / 2.0 - height / 2.0) <= GUIDE_SNAP) {
                y = height / 2.0 - h / 2.0;
                snappedHorizontal = true;
            }
        }
        guideX.clear();
        guideY.clear();
        if (settings != null && settings.smartGuides()) {
            double[] snapped = smartSnap(module, x, y, w, h);
            x = snapped[0];
            y = snapped[1];
        }
        // Edge magnet
        if (x < GUIDE_SNAP) {
            x = 0;
        }
        if (y < GUIDE_SNAP) {
            y = 0;
        }
        if (x + w > width - GUIDE_SNAP) {
            x = width - w;
        }
        if (y + h > height - GUIDE_SNAP) {
            y = height - h;
        }
        module.setScreenPosition(x, y, width, height);
        return true;
    }

    /** Aligns the dragged box's edges/centre with other visible components' edges/centres within GUIDE_SNAP px. */
    private double[] smartSnap(HudModule moving, double x, double y, int w, int h) {
        double bestDx = GUIDE_SNAP + 1;
        double bestDy = GUIDE_SNAP + 1;
        int lineX = 0;
        int lineY = 0;
        for (HudModule other : manager.modules().getHudModules()) {
            if (other == moving || !other.isEnabled()) {
                continue;
            }
            int ox = other.getScreenX(width);
            int oy = other.getScreenY(height);
            int ow = other.getScaledWidth();
            int oh = other.getScaledHeight();
            int[] targetsX = {ox, ox + ow, ox + ow / 2};
            double[] mineX = {x, x + w, x + w / 2.0};
            for (int t : targetsX) {
                for (int k = 0; k < mineX.length; k++) {
                    double d = t - mineX[k];
                    if (Math.abs(d) < Math.abs(bestDx)) {
                        bestDx = d;
                        lineX = t;
                    }
                }
            }
            int[] targetsY = {oy, oy + oh, oy + oh / 2};
            double[] mineY = {y, y + h, y + h / 2.0};
            for (int t : targetsY) {
                for (int k = 0; k < mineY.length; k++) {
                    double d = t - mineY[k];
                    if (Math.abs(d) < Math.abs(bestDy)) {
                        bestDy = d;
                        lineY = t;
                    }
                }
            }
        }
        if (Math.abs(bestDx) <= GUIDE_SNAP) {
            x += bestDx;
            guideX.add(lineX);
        }
        if (Math.abs(bestDy) <= GUIDE_SNAP) {
            y += bestDy;
            guideY.add(lineY);
        }
        return new double[]{x, y};
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        guideX.clear();
        guideY.clear();
        snappedVertical = false;
        snappedHorizontal = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudModule module = moduleAt(mouseX, mouseY);
        if (module != null && verticalAmount != 0) {
            int x = module.getScreenX(width);
            int y = module.getScreenY(height);
            module.setScale(module.scaleSetting().get() + (verticalAmount > 0 ? 0.05 : -0.05));
            if (module.hasCustomPosition()) {
                module.setScreenPosition(x, y, width, height); // keep the top-left corner in place
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected != null) {
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -step : keyCode == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int dy = keyCode == GLFW.GLFW_KEY_UP ? -step : keyCode == GLFW.GLFW_KEY_DOWN ? step : 0;
            if (dx != 0 || dy != 0) {
                selected.setScreenPosition(selected.getScreenX(width) + dx, selected.getScreenY(height) + dy, width, height);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        manager.config().saveDirty();
        super.removed();
    }
}
