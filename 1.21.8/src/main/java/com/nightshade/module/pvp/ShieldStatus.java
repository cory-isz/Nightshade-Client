package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Colours your shield green while it is ready and red while it is disabled (an axe hit puts shields on cooldown).
 * Applies to the shield in your hand (first person) and to shield icons in the hotbar and inventory.
 */
public final class ShieldStatus extends Module {
    private static ShieldStatus instance;
    /** Set while the first-person hands or the GUI item atlas are being submitted (see mixins). */
    public static boolean firstPersonPass;
    public static boolean guiPass;
    private static ItemStack probe;

    private final ColorSetting readyColor = add(new ColorSetting("Ready Color", "Tint while the shield can block", 0xFF4CFF6A));
    private final ColorSetting brokenColor = add(new ColorSetting("Broken Color", "Tint while the shield is disabled", 0xFFFF3B3B));
    private final NumberSetting strength = add(new NumberSetting("Strength", "How strongly the shield is tinted", 0.65, 0.1, 1.0, 0.05));
    private final BooleanSetting lowDurability = add(new BooleanSetting("Low Durability Is Broken", "Also show red when the shield is about to break", true));
    private final NumberSetting durabilityThreshold = add(new NumberSetting("Durability Threshold", "Durability that counts as broken", 10, 1, 50, 1, "%"))
            .visibleWhen(lowDurability::get);
    private final BooleanSetting inHand = add(new BooleanSetting("In Hand", "Tint the shield you are holding", true));
    private final BooleanSetting inInventory = add(new BooleanSetting("Hotbar And Inventory", "Tint shield icons", true));

    public ShieldStatus() {
        super("Shield Status", "Colours your shield green when ready and red when disabled", ModuleCategory.PVP);
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** True while the shield is disabled (axe cooldown) or, optionally, nearly broken. */
    public static boolean isBroken() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (probe == null) {
            probe = new ItemStack(Items.SHIELD); // created lazily: item components are not bound at class-load time
        }
        if (mc.player.getCooldowns().isOnCooldown(probe)) {
            return true;
        }
        if (instance.lowDurability.get()) {
            for (ItemStack held : new ItemStack[]{mc.player.getOffhandItem(), mc.player.getMainHandItem()}) {
                if (held.is(Items.SHIELD) && held.isDamageableItem()) {
                    float left = (held.getMaxDamage() - held.getDamageValue()) / (float) held.getMaxDamage();
                    if (left * 100f <= instance.durabilityThreshold.get()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether the current render pass should be tinted. */
    public static boolean shouldTint() {
        if (!active()) {
            return false;
        }
        return (firstPersonPass && instance.inHand.get()) || (guiPass && instance.inInventory.get());
    }

    /** Key added to shield item models so cached GUI icons refresh when the state changes. */
    public static Object identity() {
        return isBroken() ? "nightshade:shield_broken" : "nightshade:shield_ready";
    }

    /** Multiplies the vanilla tint (normally -1 = white) towards the status colour. */
    public static int tint(int vanilla) {
        EffectProbe.hit("Shield Status");
        int target = isBroken() ? instance.brokenColor.argb() : instance.readyColor.argb();
        float f = instance.strength.get().floatValue();
        int r = lerp(0xFF, (target >> 16) & 0xFF, f);
        int g = lerp(0xFF, (target >> 8) & 0xFF, f);
        int b = lerp(0xFF, target & 0xFF, f);
        int base = vanilla == 0 ? -1 : vanilla;
        return (base & 0xFF000000) | (mul((base >> 16) & 0xFF, r) << 16) | (mul((base >> 8) & 0xFF, g) << 8) | mul(base & 0xFF, b);
    }

    private static int lerp(int a, int b, float f) {
        return Math.round(a + (b - a) * f);
    }

    private static int mul(int a, int b) {
        return a * b / 255;
    }
}
