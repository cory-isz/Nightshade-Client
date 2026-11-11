package com.nightshade.gui.settings;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.GuiDraw;
import com.nightshade.module.BindMode;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.ModuleSetting;
import com.nightshade.render.Animation;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Individual configuration panel for one module: toggle, keybind, bind mode, reset and every setting. */
public final class SettingsPanel {
    public enum Action {
        NONE, BACK, TOGGLE, BIND, CLEAR_BIND, RESET, CONSUMED
    }

    private static final int ROW_GAP = 3;
    private final Module module;
    private final List<SettingComponent<?>> components = new ArrayList<>();
    private final Animation toggle;
    private int x;
    private int y;
    private int w;
    private int contentTop;

    public SettingsPanel(Module module) {
        this.module = module;
        for (ModuleSetting<?> setting : module.getSettings()) {
            components.add(SettingComponents.create(setting));
        }
        toggle = new Animation(16f, module.isEnabled() ? 1f : 0f);
    }

    public Module module() {
        return module;
    }

    public static int headerHeight() {
        return 40;
    }

    /** Height of the scrollable content (rows). */
    public int contentHeight() {
        int height = 2 * (20 + ROW_GAP);
        for (SettingComponent<?> component : components) {
            if (component.setting().isVisible()) {
                height += component.height() + ROW_GAP;
            }
        }
        return height;
    }

    /** Fixed header (not scrolled). */
    public void renderHeader(GuiGraphics ctx, int x, int y, int w, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.w = w;
        int backW = GuiDraw.pill(ctx, x, y, "< Back", 14, RenderUtil.isHovered(mouseX, mouseY, x, y, x + GuiDraw.pillWidth("< Back"), y + 14),
                Theme.TEXT, Theme.SWITCH_OFF);
        Fonts.draw(ctx, module.getName(), x + backW + 8, y + 3, Theme.TEXT, false);

        int resetW = GuiDraw.pillWidth("Reset");
        int resetX = x + w - resetW;
        GuiDraw.pill(ctx, resetX, y, "Reset", 14, RenderUtil.isHovered(mouseX, mouseY, resetX, y, resetX + resetW, y + 14),
                Theme.WARNING, Theme.SWITCH_OFF);
        if (module.isToggleable()) {
            int sx = resetX - GuiDraw.SWITCH_W - 8;
            GuiDraw.toggle(ctx, sx, y + 2, toggle.setTarget(module.isEnabled() ? 1f : 0f).update(),
                    RenderUtil.isHovered(mouseX, mouseY, sx, y + 2, sx + GuiDraw.SWITCH_W, y + 2 + GuiDraw.SWITCH_H));
        }
        Fonts.draw(ctx, Fonts.trim(module.getDescription(), w), x, y + 20, Theme.TEXT_MUTED, false);
        String issue = module.getCompatibilityIssue();
        if (issue != null) {
            Fonts.draw(ctx, Fonts.trim(issue, w), x, y + 30, Theme.WARNING, false);
        }
    }

    /** Scrollable rows; {@code top} already includes the scroll offset. */
    public void renderContent(GuiGraphics ctx, int top, int mouseX, int mouseY, boolean binding) {
        contentTop = top;
        int rowY = top;
        // Keybind row
        RenderUtil.roundedRect(ctx, x, rowY, x + w, rowY + 20, 3, Theme.PANEL);
        Fonts.draw(ctx, "Keybind", x + 6, rowY + 6, Theme.TEXT, false);
        String bind = binding ? "Press a key... (Esc cancel, Del clear)" : module.getKeybind().displayName();
        int clearW = GuiDraw.pillWidth("Clear");
        int clearX = x + w - clearW - 5;
        GuiDraw.pill(ctx, clearX, rowY + 3, "Clear", 14, RenderUtil.isHovered(mouseX, mouseY, clearX, rowY + 3, clearX + clearW, rowY + 17),
                Theme.TEXT_MUTED, Theme.SWITCH_OFF);
        int bindW = GuiDraw.pillWidth(bind);
        int bindX = clearX - bindW - 4;
        boolean conflict = !binding && !NightshadeClient.client().keybinds().getConflicts(module).isEmpty();
        GuiDraw.pill(ctx, bindX, rowY + 3, bind, 14, RenderUtil.isHovered(mouseX, mouseY, bindX, rowY + 3, bindX + bindW, rowY + 17),
                binding ? Theme.accentLight() : conflict ? Theme.WARNING : Theme.TEXT, binding ? Theme.SELECTED : Theme.SWITCH_OFF);
        if (conflict) {
            String note = "Shared with " + NightshadeClient.client().keybinds().getConflicts(module).get(0).getName();
            Fonts.draw(ctx, Fonts.trim(note, Math.max(10, bindX - x - 60)), x + 52, rowY + 6, Theme.WARNING, false);
        }
        rowY += 20 + ROW_GAP;

        // Bind mode row
        RenderUtil.roundedRect(ctx, x, rowY, x + w, rowY + 20, 3, Theme.PANEL);
        Fonts.draw(ctx, "Bind Mode", x + 6, rowY + 6, module.isToggleable() ? Theme.TEXT : Theme.TEXT_DIM, false);
        String mode = module.isToggleable() ? "< " + (module.getBindMode() == BindMode.TOGGLE ? "Toggle" : "Hold") + " >" : "Action";
        int modeW = GuiDraw.pillWidth(mode);
        int modeX = x + w - modeW - 5;
        GuiDraw.pill(ctx, modeX, rowY + 3, mode, 14, module.isToggleable() && RenderUtil.isHovered(mouseX, mouseY, modeX, rowY + 3, modeX + modeW, rowY + 17),
                Theme.TEXT, Theme.SELECTED);
        rowY += 20 + ROW_GAP;

        for (SettingComponent<?> component : components) {
            if (!component.setting().isVisible()) {
                continue;
            }
            component.render(ctx, x, rowY, w, mouseX, mouseY);
            rowY += component.height() + ROW_GAP;
        }
    }

    public String tooltip(double mouseX, double mouseY) {
        for (SettingComponent<?> component : components) {
            if (component.setting().isVisible() && component.isLabelHovered(mouseX, mouseY)) {
                return component.setting().getDescription();
            }
        }
        return null;
    }

    public Action clickHeader(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return Action.NONE;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, x, y, x + GuiDraw.pillWidth("< Back"), y + 14)) {
            return Action.BACK;
        }
        int resetW = GuiDraw.pillWidth("Reset");
        int resetX = x + w - resetW;
        if (RenderUtil.isHovered(mouseX, mouseY, resetX, y, resetX + resetW, y + 14)) {
            return Action.RESET;
        }
        int sx = resetX - GuiDraw.SWITCH_W - 8;
        if (module.isToggleable() && RenderUtil.isHovered(mouseX, mouseY, sx, y + 2, sx + GuiDraw.SWITCH_W, y + 2 + GuiDraw.SWITCH_H)) {
            return Action.TOGGLE;
        }
        return Action.NONE;
    }

    public Action clickContent(double mouseX, double mouseY, int button) {
        int rowY = contentTop;
        if (RenderUtil.isHovered(mouseX, mouseY, x, rowY, x + w, rowY + 20)) {
            int clearW = GuiDraw.pillWidth("Clear");
            int clearX = x + w - clearW - 5;
            if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, clearX, rowY + 3, clearX + clearW, rowY + 17)) {
                return Action.CLEAR_BIND;
            }
            return button == 0 ? Action.BIND : Action.CONSUMED;
        }
        rowY += 20 + ROW_GAP;
        if (RenderUtil.isHovered(mouseX, mouseY, x, rowY, x + w, rowY + 20)) {
            if (module.isToggleable() && (button == 0 || button == 1)) {
                module.setBindMode(module.getBindMode() == BindMode.TOGGLE ? BindMode.HOLD : BindMode.TOGGLE);
            }
            return Action.CONSUMED;
        }
        for (SettingComponent<?> component : components) {
            if (component.setting().isVisible() && component.mouseClicked(mouseX, mouseY, button)) {
                for (SettingComponent<?> other : components) {
                    if (other != component) {
                        other.unfocus();
                    }
                }
                return Action.CONSUMED;
            }
        }
        unfocusAll();
        return Action.NONE;
    }

    public void mouseDragged(double mouseX, double mouseY) {
        for (SettingComponent<?> component : components) {
            component.mouseDragged(mouseX, mouseY);
        }
    }

    public void mouseReleased() {
        for (SettingComponent<?> component : components) {
            component.mouseReleased();
        }
    }

    public boolean keyPressed(int key) {
        for (SettingComponent<?> component : components) {
            if (component.hasFocus() && component.keyPressed(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char chr) {
        for (SettingComponent<?> component : components) {
            if (component.hasFocus() && component.charTyped(chr)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasFocus() {
        for (SettingComponent<?> component : components) {
            if (component.hasFocus()) {
                return true;
            }
        }
        return false;
    }

    public void unfocusAll() {
        for (SettingComponent<?> component : components) {
            component.unfocus();
        }
    }
}
