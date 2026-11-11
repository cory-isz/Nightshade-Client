package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.Sounds;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Warns once when a piece of armour or a held item drops below a durability threshold. */
public final class DurabilityWarning extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final NumberSetting threshold = add(new NumberSetting("Threshold", "Warn at this durability percentage", 15, 1, 50, 1, "%"));
    private final BooleanSetting held = add(new BooleanSetting("Held Items", "Also watch your main hand and off hand", true));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", "Play a sound with the warning", true));
    private final ModeSetting soundType = add(new ModeSetting("Sound Type", "Warning sound", "Bell", Sounds.NAMES)).visibleWhen(sound::get);

    private final Map<EquipmentSlot, Boolean> warned = new EnumMap<>(EquipmentSlot.class);

    public DurabilityWarning() {
        super("Durability Warning", "Warns when armour or weapons are about to break", ModuleCategory.PVP);
        listen(TickEvent.Post.class, event -> tick());
    }

    /** Remaining durability as a fraction, or -1 for items that cannot break. */
    public static float fraction(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return -1f;
        }
        return (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
    }

    private void tick() {
        if (mc.player == null) {
            return;
        }
        float limit = threshold.get().floatValue() / 100f;
        for (EquipmentSlot slot : SLOTS) {
            boolean isHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
            if (isHand && !held.get()) {
                continue;
            }
            ItemStack stack = mc.player.getItemBySlot(slot);
            float left = fraction(stack);
            boolean low = left >= 0f && left <= limit;
            if (low && !warned.getOrDefault(slot, false)) {
                warned.put(slot, true);
                EffectProbe.hit("Durability Warning");
                NightshadeClient.client().notifications().warn("Low Durability",
                        stack.getHoverName().getString() + " is at " + Math.max(0, Math.round(left * 100f)) + "%");
                if (sound.get()) {
                    Sounds.play(soundType.get(), 0.8f, 0.7f);
                }
            } else if (!low) {
                warned.put(slot, false);
            }
        }
    }

    /** Whether the item in this slot is currently below the threshold and has been reported. */
    public boolean isWarned(EquipmentSlot slot) {
        return warned.getOrDefault(slot, false);
    }

    @Override
    protected void onDisable() {
        warned.clear();
    }
}
