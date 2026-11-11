package com.nightshade.gui;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.input.Keybind;
import com.nightshade.module.BindMode;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Every module's keybind in one list: conflicts highlighted, click a bind to change it, filter by name. */
public final class KeybindsScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int ROW_H = 18;

    @Nullable
    private final Screen parent;
    private EditBox filter;
    private boolean showUnbound;
    @Nullable
    private Module binding;
    private double scroll;
    private List<Module> rows = new ArrayList<>();

    private final NsButton unboundToggle = new NsButton("Show unbound: Off", NsButton.Style.GHOST, this::toggleUnbound);
    private final NsButton done = new NsButton("Done", NsButton.Style.PRIMARY, this::onClose);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;

    public KeybindsScreen(@Nullable Screen parent) {
        super(Component.literal("Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        filter = new EditBox(font, panelX + 26, panelY + 33, 150, 14, Component.literal("Filter"));
        filter.setBordered(false);
        filter.setTextColor(Theme.TEXT);
        filter.setHint(Component.literal("Filter modules..."));
        filter.setResponder(text -> rebuild());
        addWidget(filter);
        rebuild();
    }

    private void layout() {
        panelW = Math.min(width - 16, 460);
        panelH = Math.min(height - 16, 340);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 54;
        listBottom = panelY + panelH - 30;
        if (filter != null) {
            filter.setX(panelX + 26);
            filter.setY(panelY + 34);
        }
    }

    private void toggleUnbound() {
        showUnbound = !showUnbound;
        unboundToggle.label(showUnbound ? "Show unbound: On" : "Show unbound: Off");
        rebuild();
    }

    private void rebuild() {
        String query = filter == null ? "" : filter.getValue().trim().toLowerCase(Locale.ROOT);
        rows = new ArrayList<>();
        for (Module module : NightshadeClient.client().modules().getModules()) {
            if (!showUnbound && !module.getKeybind().isBound()) {
                continue;
            }
            if (!query.isEmpty() && !module.getName().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            rows.add(module);
        }
        rows.sort((a, b) -> a.getCategory() != b.getCategory() ? a.getCategory().compareTo(b.getCategory())
                : String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
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
        Fonts.draw(ctx, "Keybinds", panelX + 14, panelY + 12, Theme.TEXT, false);
        int conflicts = 0;
        for (Module module : NightshadeClient.client().modules().getModules()) {
            if (!NightshadeClient.client().keybinds().getConflicts(module).isEmpty()) {
                conflicts++;
            }
        }
        String summary = binding != null ? "Press a key or mouse button for " + binding.getName() + " (Esc cancel, Del clear)"
                : conflicts == 0 ? "No conflicts" : conflicts + " modules share a key with another module";
        Fonts.draw(ctx, Fonts.trim(summary, panelW - 28 - 84), panelX + 14 + 84, panelY + 12,
                binding != null ? Theme.accentLight() : conflicts == 0 ? Theme.SUCCESS : Theme.WARNING, false);

        RenderUtil.roundedRect(ctx, panelX + 12, panelY + 29, panelX + 182, panelY + 46, 4, filter.isFocused() ? Theme.SELECTED : Theme.PANEL);
        RenderUtil.roundedOutline(ctx, panelX + 12, panelY + 29, panelX + 182, panelY + 46, 4, filter.isFocused() ? Theme.accent() : Theme.BORDER);
        com.nightshade.render.PixelIcons.search(ctx, panelX + 16, panelY + 34, Theme.TEXT_DIM);
        filter.extractRenderState(ctx, mouseX, mouseY, delta);
        int tw = unboundToggle.preferredWidth();
        unboundToggle.render(ctx, panelX + panelW - 12 - tw, panelY + 29, tw, 17, mouseX, mouseY);

        int maxScroll = Math.max(0, rows.size() * ROW_H - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ctx.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
        ModuleCategory lastCategory = null;
        for (int i = 0; i < rows.size(); i++) {
            Module module = rows.get(i);
            int y = listTop + i * ROW_H - (int) scroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            int x1 = panelX + 12;
            int x2 = panelX + panelW - 12;
            boolean hovered = mouseY >= listTop && mouseY < listBottom && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H - 2);
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H - 2, 3, hovered ? Theme.PANEL_HOVER : Theme.PANEL);
            if (module.getCategory() != lastCategory) {
                lastCategory = module.getCategory();
            }
            String category = module.getCategory().displayName();
            Fonts.draw(ctx, category, x1 + 6, y + 4, Theme.TEXT_DIM, false);
            Fonts.draw(ctx, Fonts.trim(module.getName(), 150), x1 + 74, y + 4, Theme.TEXT, false);
            String mode = !module.isToggleable() ? "Action" : module.getBindMode() == BindMode.HOLD ? "Hold" : "Toggle";
            Fonts.draw(ctx, mode, x2 - 170, y + 4, Theme.TEXT_DIM, false);
            boolean conflict = !NightshadeClient.client().keybinds().getConflicts(module).isEmpty();
            String bind = binding == module ? "..." : module.getKeybind().displayName();
            int bw = Math.max(60, GuiDraw.pillWidth(bind));
            int bx = x2 - 6 - bw;
            boolean bh = hovered && RenderUtil.isHovered(mouseX, mouseY, bx, y + 1, bx + bw, y + 14);
            RenderUtil.roundedRect(ctx, bx, y + 1, bx + bw, y + 14, 3, binding == module ? Theme.SELECTED : bh ? Theme.PANEL_HOVER : Theme.SWITCH_OFF);
            Fonts.drawCentered(ctx, bind, bx + bw / 2, y + 4, conflict ? Theme.WARNING : module.getKeybind().isBound() ? Theme.TEXT : Theme.TEXT_DIM, false);
        }
        if (rows.isEmpty()) {
            Fonts.drawCentered(ctx, showUnbound ? "No modules match" : "No keybinds set - turn on 'Show unbound' to assign some", width / 2, listTop + 16, Theme.TEXT_DIM, false);
        }
        ctx.disableScissor();
        done.render(ctx, panelX + panelW - 82, panelY + panelH - 24, 70, 16, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (binding != null) {
            if (button >= GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                assign(Keybind.mouse(button));
            } else {
                binding = null;
            }
            return true;
        }
        if (done.click(mouseX, mouseY, button) || unboundToggle.click(mouseX, mouseY, button)) {
            return true;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, panelX + 12, panelY + 29, panelX + 182, panelY + 46)) {
            setFocused(filter);
            filter.setFocused(true);
            return true;
        }
        filter.setFocused(false);
        if (button == 0 && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop + scroll) / ROW_H);
            if (index >= 0 && index < rows.size()) {
                binding = rows.get(index);
                return true;
            }
        }
        return false;
    }

    private void assign(Keybind bind) {
        Module module = binding;
        binding = null;
        if (module != null) {
            NightshadeClient.client().keybinds().assign(module, bind);
            rebuild();
        }
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding != null) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> binding = null;
                case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> assign(Keybind.NONE);
                default -> assign(Keybind.key(keyCode));
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
}
