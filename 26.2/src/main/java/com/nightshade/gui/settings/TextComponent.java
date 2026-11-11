package com.nightshade.gui.settings;

import com.nightshade.module.setting.TextSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/** Text row: click the field, type, Enter (or click elsewhere) to save, Esc to cancel. */
public final class TextComponent extends SettingComponent<TextSetting> {
    private boolean focused;
    private String text = "";

    public TextComponent(TextSetting setting) {
        super(setting);
    }

    @Override
    public int height() {
        return 20;
    }

    private int fieldX1() {
        return x + Math.max(6 + Fonts.width(setting.getName()) + 12, width / 2);
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        drawLabel(ctx);
        int fx1 = fieldX1();
        int fx2 = x + width - 6;
        RenderUtil.roundedRect(ctx, fx1, y + 3, fx2, y + 17, 3, focused ? Theme.SELECTED : Theme.SWITCH_OFF);
        RenderUtil.roundedOutline(ctx, fx1, y + 3, fx2, y + 17, 3, focused ? Theme.accent() : Theme.BORDER);
        String shown = focused ? text : setting.get();
        if (!focused && shown.isEmpty()) {
            Fonts.draw(ctx, "Click to type...", fx1 + 4, y + 6, Theme.TEXT_DIM, false);
            return;
        }
        String caret = focused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : "";
        String visible = shown;
        while (visible.length() > 0 && Fonts.width(visible + caret) > fx2 - fx1 - 8) {
            visible = visible.substring(1); // keep the end (where you type) in view
        }
        Fonts.draw(ctx, visible + caret, fx1 + 4, y + 6, Theme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inField = RenderUtil.isHovered(mouseX, mouseY, fieldX1(), y + 3, x + width - 6, y + 17);
        if (inField && button == 0) {
            if (!focused) {
                focused = true;
                text = setting.get();
            }
            return true;
        }
        if (focused) {
            commit();
        }
        return isHovered(mouseX, mouseY);
    }

    private void commit() {
        focused = false;
        setting.set(text);
    }

    @Override
    public boolean keyPressed(int key) {
        if (!focused) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    text = text.substring(0, text.length() - 1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commit();
            case GLFW.GLFW_KEY_ESCAPE -> focused = false;
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr) {
        if (!focused || Character.isISOControl(chr) || chr == '§') {
            return false;
        }
        if (text.length() < setting.maxLength()) {
            text += chr;
        }
        return true;
    }

    @Override
    public boolean hasFocus() {
        return focused;
    }

    @Override
    public void unfocus() {
        if (focused) {
            commit();
        }
    }
}
