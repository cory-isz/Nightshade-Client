package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nightshade.NightshadeClient;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** First-person held-item size and position, plus a lowered shield so it covers less of the screen. */
public final class ItemView extends Module {
    private static ItemView instance;

    private final NumberSetting scale = add(new NumberSetting("Scale", "Size of items in your hands", 1.0, 0.3, 1.5, 0.05, "x"));
    private final NumberSetting offsetX = add(new NumberSetting("Offset X", "Move held items sideways", 0, -0.6, 0.6, 0.02));
    private final NumberSetting offsetY = add(new NumberSetting("Offset Y", "Move held items up or down", 0, -0.6, 0.6, 0.02));
    private final NumberSetting offsetZ = add(new NumberSetting("Offset Z", "Move held items closer or further", 0, -0.6, 0.6, 0.02));
    private final BooleanSetting lowShield = add(new BooleanSetting("Low Shield", "Lower the shield when you are not blocking", true));
    private final NumberSetting shieldDrop = add(new NumberSetting("Shield Drop", "How far the shield is lowered", 0.2, 0.05, 0.5, 0.05)).visibleWhen(lowShield::get);

    public ItemView() {
        super("Item View", "Resize and move first-person held items", ModuleCategory.PVP);
        instance = this;
    }

    private static boolean active() {
        return instance != null && instance.isEnabled() && NightshadeClient.client().isInitialized();
    }

    /** Applied inside a pushed matrix at the start of each first-person hand. */
    public static void apply(PoseStack matrices, AbstractClientPlayer player, InteractionHand hand, ItemStack item) {
        if (!active()) {
            return;
        }
        ItemView view = instance;
        EffectProbe.hit("Item View");
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        float y = view.offsetY.get().floatValue();
        if (view.lowShield.get() && item.is(Items.SHIELD) && !(player.isUsingItem() && player.getUsedItemHand() == hand)) {
            y -= view.shieldDrop.get().floatValue();
        }
        matrices.translate(side * view.offsetX.get().floatValue(), y, view.offsetZ.get().floatValue());
        float s = view.scale.get().floatValue();
        if (s != 1f) {
            // Scale around the hand's resting point so items shrink in place instead of drifting to the centre.
            matrices.translate(side * 0.56f, -0.52f, -0.72f);
            matrices.scale(s, s, s);
            matrices.translate(-side * 0.56f, 0.52f, 0.72f);
        }
    }
}
