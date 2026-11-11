package com.nightshade.gui.settings;

import com.nightshade.module.setting.ColorSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;

/** Colour row that expands into hue / saturation / brightness / alpha sliders plus an editable hex field. */
public final class ColorComponent extends SettingComponent<ColorSetting> {
    private static final int HEADER = 20;
    private static final int SLIDER_H = 8;
    private static final int SLIDER_GAP = 5;
    private static final String[] LABELS = {"H", "S", "B", "A"};

    private boolean expanded;
    private int dragging = -1;
    private boolean hexFocused;
    private String hexText = "";
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;

    public ColorComponent(ColorSetting setting) {
        super(setting);
        syncFromSetting();
    }

    private void syncFromSetting() {
        float[] hsb = setting.hsb();
        // Keep the hue when the colour is grey so the slider does not jump.
        if (hsb[1] > 0f && hsb[2] > 0f) {
            hue = hsb[0];
        }
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = setting.alpha();
        if (!hexFocused) {
            hexText = setting.toHex();
        }
    }

    @Override
    public int height() {
        return expanded ? HEADER + 4 * (SLIDER_H + SLIDER_GAP) + 18 : HEADER;
    }

    private int sliderX1() {
        return x + 18;
    }

    private int sliderX2() {
        return x + width - 8;
    }

    private int sliderY(int index) {
        return y + HEADER + index * (SLIDER_H + SLIDER_GAP);
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY) {
        if (dragging < 0) {
            syncFromSetting();
        }
        drawLabel(ctx);
        int swatchX = x + width - 26;
        RenderUtil.roundedRect(ctx, swatchX, y + 4, swatchX + 20, y + 16, 2, 0xFF000000 | setting.argb());
        RenderUtil.roundedOutline(ctx, swatchX, y + 4, swatchX + 20, y + 16, 2, Theme.BORDER_STRONG);
        String hex = setting.toHex();
        Fonts.draw(ctx, hex, swatchX - 4 - Fonts.width(hex), y + 6, Theme.TEXT_DIM, false);
        if (!expanded) {
            return;
        }
        int x1 = sliderX1();
        int x2 = sliderX2();
        for (int i = 0; i < 4; i++) {
            int sy = sliderY(i);
            Fonts.draw(ctx, LABELS[i], x + 6, sy, Theme.TEXT_MUTED, false);
            switch (i) {
                case 0 -> drawHue(ctx, x1, sy, x2);
                case 1 -> RenderUtil.horizontalGradient(ctx, x1, sy, x2, sy + SLIDER_H,
                        ColorUtil.hsbToRgb(hue, 0f, brightness), ColorUtil.hsbToRgb(hue, 1f, brightness));
                case 2 -> RenderUtil.horizontalGradient(ctx, x1, sy, x2, sy + SLIDER_H,
                        0xFF000000, ColorUtil.hsbToRgb(hue, saturation, 1f));
                default -> {
                    drawChecker(ctx, x1, sy, x2, sy + SLIDER_H);
                    int rgb = setting.argb() & 0xFFFFFF;
                    RenderUtil.horizontalGradient(ctx, x1, sy, x2, sy + SLIDER_H, rgb, 0xFF000000 | rgb);
                }
            }
            float value = switch (i) {
                case 0 -> hue;
                case 1 -> saturation;
                case 2 -> brightness;
                default -> alpha / 255f;
            };
            int knob = x1 + Math.round((x2 - x1) * value);
            RenderUtil.rect(ctx, knob - 1, sy - 1, knob + 2, sy + SLIDER_H + 1, 0xFFFFFFFF);
            RenderUtil.outline(ctx, knob - 2, sy - 2, knob + 3, sy + SLIDER_H + 2, 0xFF000000);
        }
        int fieldY = sliderY(4) - 1;
        int fieldX2 = x + 6 + 70;
        RenderUtil.roundedRect(ctx, x + 6, fieldY, fieldX2, fieldY + 13, 2, hexFocused ? Theme.SELECTED : Theme.SWITCH_OFF);
        RenderUtil.roundedOutline(ctx, x + 6, fieldY, fieldX2, fieldY + 13, 2, hexFocused ? Theme.accent() : Theme.BORDER);
        String shown = hexFocused && (System.currentTimeMillis() / 500L) % 2L == 0L ? hexText + "_" : hexText;
        Fonts.draw(ctx, shown, x + 9, fieldY + 3, Theme.TEXT, false);
        Fonts.draw(ctx, "Hex (#AARRGGBB)", fieldX2 + 6, fieldY + 3, Theme.TEXT_DIM, false);
    }

    private static void drawHue(GuiGraphics ctx, int x1, int y, int x2) {
        int w = x2 - x1;
        for (int i = 0; i < w; i++) {
            ctx.fill(x1 + i, y, x1 + i + 1, y + SLIDER_H, ColorUtil.hsbToRgb(i / (float) Math.max(1, w - 1), 1f, 1f));
        }
    }

    private static void drawChecker(GuiGraphics ctx, int x1, int y1, int x2, int y2) {
        for (int cx = x1; cx < x2; cx += 4) {
            for (int cy = y1; cy < y2; cy += 4) {
                boolean dark = ((cx - x1) / 4 + (cy - y1) / 4) % 2 == 0;
                ctx.fill(cx, cy, Math.min(cx + 4, x2), Math.min(cy + 4, y2), dark ? 0xFF555555 : 0xFF999999);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            return false;
        }
        if (mouseY < y + HEADER) {
            if (button == 0 || button == 1) {
                expanded = !expanded;
                hexFocused = false;
            }
            return true;
        }
        if (!expanded || button != 0) {
            return true;
        }
        for (int i = 0; i < 4; i++) {
            int sy = sliderY(i);
            if (mouseY >= sy - 2 && mouseY <= sy + SLIDER_H + 2) {
                dragging = i;
                hexFocused = false;
                applySlider(mouseX);
                return true;
            }
        }
        int fieldY = sliderY(4) - 1;
        hexFocused = RenderUtil.isHovered(mouseX, mouseY, x + 6, fieldY, x + 76, fieldY + 13);
        if (hexFocused) {
            hexText = setting.toHex();
        }
        return true;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY) {
        if (dragging >= 0) {
            applySlider(mouseX);
        }
    }

    @Override
    public void mouseReleased() {
        dragging = -1;
    }

    private void applySlider(double mouseX) {
        float value = (float) Math.max(0.0, Math.min(1.0, (mouseX - sliderX1()) / (double) Math.max(1, sliderX2() - sliderX1())));
        switch (dragging) {
            case 0 -> hue = Math.min(value, 0.999f);
            case 1 -> saturation = value;
            case 2 -> brightness = value;
            case 3 -> alpha = Math.round(value * 255f);
            default -> {
                return;
            }
        }
        setting.setHsba(hue, saturation, brightness, alpha);
        hexText = setting.toHex();
    }

    @Override
    public boolean keyPressed(int key) {
        if (!hexFocused) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!hexText.isEmpty()) {
                    hexText = hexText.substring(0, hexText.length() - 1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                Integer parsed = ColorSetting.parseHex(hexText);
                if (parsed != null) {
                    setting.set(parsed);
                }
                hexFocused = false;
                syncFromSetting();
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                hexFocused = false;
                hexText = setting.toHex();
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr) {
        if (!hexFocused) {
            return false;
        }
        char c = Character.toUpperCase(chr);
        boolean valid = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c == '#' && hexText.isEmpty());
        if (valid && hexText.length() < 9) {
            hexText = (hexText + c).toUpperCase(Locale.ROOT);
        }
        return true;
    }

    @Override
    public boolean hasFocus() {
        return hexFocused;
    }

    @Override
    public void unfocus() {
        hexFocused = false;
    }
}
