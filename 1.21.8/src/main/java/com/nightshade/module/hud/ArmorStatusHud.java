package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Equipped armour with icons, durability and low-durability warnings. */
public final class ArmorStatusHud extends HudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int ROW = 18;

    private final ModeSetting layout = add(new ModeSetting("Layout", "Arrangement of armour pieces", "Vertical", "Vertical", "Horizontal"));
    private final ModeSetting durability = add(new ModeSetting("Durability", "How durability is shown", "Percent",
            "Percent", "Value", "Both", "None"));
    private final NumberSetting warnThreshold = add(new NumberSetting("Warning Threshold", "Warn when durability falls to this percentage", 20, 1, 75, 1, "%"));
    private final BooleanSetting flashWarning = add(new BooleanSetting("Flash Warning", "Pulse low-durability pieces", true));
    private final BooleanSetting showEmpty = add(new BooleanSetting("Show Empty Slots", "Show placeholders for empty armour slots", true));
    private final BooleanSetting durabilityBar = add(new BooleanSetting("Durability Bar", "Draw a bar under each icon", true));
    private final BooleanSetting showHeld = add(new BooleanSetting("Show Held Item", "Also show the main-hand item", false));
    private final ColorSetting warnColor = add(new ColorSetting("Warning Color", "Colour used for low durability", 0xFFFF5C7A));

    private final ItemStack[] stacks = new ItemStack[5];
    private final String[] texts = new String[5];
    private final float[] fractions = new float[5];
    private int textWidth;
    private int count = 4;

    public ArmorStatusHud() {
        super("Armor Status", "Shows equipped armour and durability", ModuleCategory.HUD, 0.99, 0.6, false);
        enabledByDefault();
    }

    @Override
    protected void refresh() {
        count = showHeld.get() ? 5 : 4;
        int maxWidth = 0;
        for (int i = 0; i < count; i++) {
            ItemStack stack = mc.player == null ? ItemStack.EMPTY
                    : i < 4 ? mc.player.getItemBySlot(SLOTS[i]) : mc.player.getMainHandItem();
            stacks[i] = stack;
            if (stack.isEmpty()) {
                fractions[i] = -1f;
                texts[i] = showEmpty.get() && i < 4 ? "-" : "";
            } else if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int remaining = max - stack.getDamageValue();
                fractions[i] = remaining / (float) max;
                int percent = Math.round(fractions[i] * 100f);
                texts[i] = switch (durability.get()) {
                    case "Percent" -> percent + "%";
                    case "Value" -> Integer.toString(remaining);
                    case "Both" -> remaining + " (" + percent + "%)";
                    default -> "";
                };
            } else {
                fractions[i] = -1f;
                texts[i] = stack.getCount() > 1 ? "x" + stack.getCount() : "";
            }
            maxWidth = Math.max(maxWidth, Fonts.width(texts[i]));
        }
        textWidth = maxWidth;
    }

    private boolean vertical() {
        return layout.is("Vertical");
    }

    private int cellWidth() {
        return 16 + (textWidth > 0 ? textWidth + 4 : 0);
    }

    @Override
    public int getWidth() {
        return vertical() ? cellWidth() + 4 : count * (cellWidth() + 4) + 2;
    }

    @Override
    public int getHeight() {
        return vertical() ? count * ROW + 2 : ROW + 4;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        boolean pulseOn = (System.currentTimeMillis() / 400L) % 2L == 0L;
        int threshold = warnThreshold.intValue();
        for (int i = 0; i < count; i++) {
            int x = vertical() ? 2 : 2 + i * (cellWidth() + 4);
            int y = vertical() ? 1 + i * ROW : 2;
            ItemStack stack = stacks[i] == null ? ItemStack.EMPTY : stacks[i];
            if (stack.isEmpty()) {
                if (showEmpty.get() && i < 4) {
                    RenderUtil.roundedOutline(context, x, y, x + 16, y + 16, 3, Theme.BORDER_STRONG);
                    drawText(context, texts[i], x, y, Theme.TEXT_DIM);
                }
                continue;
            }
            context.renderItem(stack, x, y);
            float fraction = fractions[i];
            boolean low = fraction >= 0f && fraction * 100f <= threshold;
            if (durabilityBar.get() && fraction >= 0f) {
                int barColor = low ? warnColor.argb() : durabilityColor(fraction);
                RenderUtil.rect(context, x + 1, y + 15, x + 15, y + 17, 0xC0000000);
                RenderUtil.rect(context, x + 1, y + 15, x + 1 + Math.max(1, Math.round(14 * fraction)), y + 16, barColor);
            }
            int color = fraction < 0f ? textColor.argb() : low ? warnColor.argb() : durabilityColor(fraction);
            if (low && flashWarning.get() && !pulseOn) {
                color = ColorUtil.fade(color, 0.35f);
            }
            drawText(context, texts[i], x, y, color);
            if (low) {
                context.pose().pushMatrix();
                Fonts.draw(context, "!", x + 12, y - 1, warnColor.argb(), true);
                context.pose().popMatrix();
            }
        }
    }

    private void drawText(GuiGraphics context, String text, int x, int y, int color) {
        if (text.isEmpty()) {
            return;
        }
        Fonts.draw(context, text, x + 20, y + (16 - Fonts.height()) / 2 + 1, color, shadow());
    }

    private static int durabilityColor(float fraction) {
        return fraction > 0.5f
                ? ColorUtil.lerp(Theme.WARNING, Theme.SUCCESS, (fraction - 0.5f) * 2f)
                : ColorUtil.lerp(Theme.DANGER, Theme.WARNING, fraction * 2f);
    }
}
