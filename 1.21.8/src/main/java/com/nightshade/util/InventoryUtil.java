package com.nightshade.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Inventory helpers for the player's own inventory screen handler.
 * <p>
 * PlayerScreenHandler slot layout: 0 crafting output, 1-4 crafting grid, 5-8 armour (head..feet),
 * 9-35 main inventory (inventory indices 9-35), 36-44 hotbar (inventory indices 0-8), 45 offhand.
 */
public final class InventoryUtil {
    public static final int OFFHAND_BUTTON = 40;

    private InventoryUtil() {
    }

    /** Converts a PlayerInventory index (0-35) to a PlayerScreenHandler slot id. */
    public static int toHandlerSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    /** PlayerScreenHandler slot id of an armour slot. */
    public static int armorHandlerSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> throw new IllegalArgumentException("Not an armour slot: " + slot);
        };
    }

    /**
     * Inventory clicks on the player handler are only valid when no other container is open: either no screen or
     * the player's own inventory screen.
     */
    public static boolean canUsePlayerHandler() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.gameMode != null
                && (mc.screen == null || mc.screen instanceof InventoryScreen)
                && mc.player.containerMenu == mc.player.inventoryMenu
                && mc.player.inventoryMenu.getCarried().isEmpty();
    }

    public static void click(int handlerSlot, int button, ClickType action) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, handlerSlot, button, action, mc.player);
    }

    /** Sum of additive main-hand attack damage modifiers of an item (0 for non-weapons). */
    public static double attackDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double[] total = {0.0};
        modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    /** Remaining durability fraction, or 1 for undamageable items. */
    public static float durabilityFraction(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1f;
        }
        return (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
    }
}
