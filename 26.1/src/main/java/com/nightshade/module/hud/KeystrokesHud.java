package com.nightshade.module.hud;

import com.nightshade.NightshadeClient;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** WASD / Space / mouse button overlay with animated press highlighting and optional CPS. */
public final class KeystrokesHud extends HudModule {
    private final NumberSetting keySize = add(new NumberSetting("Key Size", "Size of a single key", 22, 16, 32, 1, "px"));
    private final NumberSetting gap = add(new NumberSetting("Gap", "Spacing between keys", 2, 0, 6, 1, "px"));
    private final NumberSetting fadeSpeed = add(new NumberSetting("Fade Speed", "Speed of the press fade transition", 18, 4, 40, 1));
    private final BooleanSetting showSpace = add(new BooleanSetting("Show Space", "Show the jump key", true));
    private final BooleanSetting showMouse = add(new BooleanSetting("Show Mouse", "Show left and right mouse buttons", true));
    private final BooleanSetting showCps = add(new BooleanSetting("Show CPS", "Show clicks per second on mouse buttons", true))
            .visibleWhen(showMouse::get);
    private final BooleanSetting rounded = add(new BooleanSetting("Rounded", "Rounded key corners", true));
    private final ColorSetting pressedColor = add(new ColorSetting("Pressed Color", "Key colour while pressed", 0xE07C6CFF));
    private final ColorSetting pressedTextColor = add(new ColorSetting("Pressed Text", "Label colour while pressed", 0xFFFFFFFF));

    private static final int W = 0;
    private static final int A = 1;
    private static final int S = 2;
    private static final int D = 3;
    private static final int SPACE = 4;
    private static final int LMB = 5;
    private static final int RMB = 6;
    private final Animation[] animations = new Animation[7];
    private float currentFadeSpeed = -1f;
    private final String[] labels = new String[4];
    private String leftCpsText = "0 CPS";
    private String rightCpsText = "0 CPS";

    public KeystrokesHud() {
        super("Keystrokes", "Shows WASD, Space and mouse buttons with press highlighting", ModuleCategory.HUD, 0.0, 0.42);
        enabledByDefault();
        rebuildAnimations();
    }

    private void rebuildAnimations() {
        currentFadeSpeed = fadeSpeed.floatValue();
        for (int i = 0; i < animations.length; i++) {
            float value = animations[i] == null ? 0f : animations[i].get();
            animations[i] = new Animation(currentFadeSpeed, value);
        }
    }

    private int size() {
        return keySize.intValue();
    }

    private int spaceHeight() {
        return Math.max(8, Math.round(size() * 0.5f));
    }

    @Override
    public int getWidth() {
        return size() * 3 + gap.intValue() * 2;
    }

    @Override
    public int getHeight() {
        int g = gap.intValue();
        int height = size() * 2 + g;
        if (showMouse.get()) {
            height += g + size();
        }
        if (showSpace.get()) {
            height += g + spaceHeight();
        }
        return height;
    }

    @Override
    protected void refresh() {
        labels[W] = shortName(mc.options.keyUp);
        labels[A] = shortName(mc.options.keyLeft);
        labels[S] = shortName(mc.options.keyDown);
        labels[D] = shortName(mc.options.keyRight);
        if (showCps.get()) {
            leftCpsText = NightshadeClient.client().cps().leftCps() + " CPS";
            rightCpsText = NightshadeClient.client().cps().rightCps() + " CPS";
        }
    }

    private static String shortName(KeyMapping binding) {
        String name = binding.getTranslatedKeyMessage().getString();
        return name.length() > 3 ? name.substring(0, 3) : name;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        if (currentFadeSpeed != fadeSpeed.floatValue()) {
            rebuildAnimations();
        }
        int size = size();
        int g = gap.intValue();
        animations[W].setTarget(mc.options.keyUp.isDown() ? 1f : 0f);
        animations[A].setTarget(mc.options.keyLeft.isDown() ? 1f : 0f);
        animations[S].setTarget(mc.options.keyDown.isDown() ? 1f : 0f);
        animations[D].setTarget(mc.options.keyRight.isDown() ? 1f : 0f);
        animations[SPACE].setTarget(mc.options.keyJump.isDown() ? 1f : 0f);
        animations[LMB].setTarget(mc.options.keyAttack.isDown() ? 1f : 0f);
        animations[RMB].setTarget(mc.options.keyUse.isDown() ? 1f : 0f);

        drawKey(context, size + g, 0, size, size, labels[W], null, animations[W].update());
        int row = size + g;
        drawKey(context, 0, row, size, size, labels[A], null, animations[A].update());
        drawKey(context, size + g, row, size, size, labels[S], null, animations[S].update());
        drawKey(context, (size + g) * 2, row, size, size, labels[D], null, animations[D].update());
        int y = row + size + g;
        if (showMouse.get()) {
            int half = (getWidth() - g) / 2;
            drawKey(context, 0, y, half, size, "LMB", showCps.get() ? leftCpsText : null, animations[LMB].update());
            drawKey(context, half + g, y, getWidth() - half - g, size, "RMB", showCps.get() ? rightCpsText : null, animations[RMB].update());
            y += size + g;
        }
        if (showSpace.get()) {
            float progress = animations[SPACE].update();
            int h = spaceHeight();
            drawKeyBackground(context, 0, y, getWidth(), h, progress);
            int barColor = ColorUtil.lerp(textColor.argb(), pressedTextColor.argb(), progress);
            int barWidth = getWidth() / 3;
            RenderUtil.rect(context, (getWidth() - barWidth) / 2, y + h / 2 - 1, (getWidth() + barWidth) / 2, y + h / 2, barColor);
        }
    }

    private void drawKeyBackground(GuiGraphicsExtractor context, int x, int y, int w, int h, float progress) {
        String style = com.nightshade.render.Theme.hudStyle();
        if (background.get() && !"Classic".equals(style) && !"Minimal".equals(style)) {
            // Glass key; pressing fills it with the pressed colour and lifts it slightly.
            int lift = Math.round(progress);
            com.nightshade.render.PanelCache.shape(context, x, y + 1, x + w, y + h + 1, rounded.get() ? 4 : 0, 0x38000000, false);
            int body = ColorUtil.lerp(ColorUtil.withAlpha(backgroundColor.argb(), Math.min(255, ColorUtil.alpha(backgroundColor.argb()) + 0x18)), pressedColor.argb(), progress);
            com.nightshade.render.PanelCache.shape(context, x, y - lift, x + w, y + h - lift, rounded.get() ? 4 : 0, body, false);
            context.fillGradient(x + 2, y + 1 - lift, x + w - 2, y + h / 2 - lift, 0x12FFFFFF, 0x00FFFFFF);
            com.nightshade.render.PanelCache.shape(context, x, y - lift, x + w, y + h - lift, rounded.get() ? 4 : 0,
                    ColorUtil.lerp(0x24FFFFFF, ColorUtil.withAlpha(com.nightshade.render.Theme.accentLight(), 0xC0), progress), true);
            return;
        }
        int base = background.get() ? backgroundColor.argb() : 0x00000000;
        int color = ColorUtil.lerp(base, pressedColor.argb(), progress);
        RenderUtil.roundedRect(context, x, y, x + w, y + h, rounded.get() ? 3 : 0, color);
    }

    private void drawKey(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, String sub, float progress) {
        drawKeyBackground(context, x, y, w, h, progress);
        int color = ColorUtil.lerp(textColor.argb(), pressedTextColor.argb(), progress);
        int fontHeight = Fonts.height();
        if (sub == null) {
            Fonts.drawCentered(context, label, x + w / 2, y + (h - fontHeight) / 2 + 1, color, shadow());
            return;
        }
        Fonts.drawCentered(context, label, x + w / 2, y + 2, color, shadow());
        context.pose().pushMatrix();
        float subScale = 0.7f;
        context.pose().translate(x + w / 2f, y + h - fontHeight * subScale - 1);
        context.pose().scale(subScale, subScale);
        Fonts.drawCentered(context, sub, 0, 0, ColorUtil.fade(color, 0.85f), shadow());
        context.pose().popMatrix();
    }
}
