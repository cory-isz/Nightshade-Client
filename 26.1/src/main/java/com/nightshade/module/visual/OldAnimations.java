package com.nightshade.module.visual;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.world.InteractionHand;

/**
 * 1.7-style first-person animations. Visual only: nothing is sent to the server and attacks are not changed.
 * <ul>
 *   <li>No Hand Dip: your hand stays up after hitting and after switching items, instead of dropping while the attack
 *   cooldown recharges.</li>
 *   <li>Block-hit Animation: holding attack while using an item (blocking, eating, drawing a bow) plays the swing,
 *   like block-hitting in 1.7. Only your own view shows it.</li>
 * </ul>
 */
public final class OldAnimations extends Module {
    private static OldAnimations instance;

    private final BooleanSetting noDip = add(new BooleanSetting("No Hand Dip", "Keep your hand up after hitting and switching items", true));
    private final BooleanSetting blockHit = add(new BooleanSetting("Block-hit Animation", "Swing while blocking or using an item when you hold attack", true));

    public OldAnimations() {
        super("Old Animations", "1.7-style hand animations: no cooldown dip and block-hitting", ModuleCategory.VISUAL);
        instance = this;
        listen(TickEvent.Post.class, event -> {
            if (!blockHit.get() || mc.player == null || mc.screen != null) {
                return;
            }
            if (mc.player.isUsingItem() && mc.options.keyAttack.isDown() && !mc.player.swinging) {
                mc.player.swing(InteractionHand.MAIN_HAND, false); // local animation only, no packet
                EffectProbe.hit("Old Animations");
            }
        });
    }

    /** Hook from HeldItemRendererMixin: the hand's "lowered" amount (0 = fully up). */
    public static float equipProgress(float vanilla) {
        OldAnimations module = instance;
        if (module == null || !module.isEnabled() || !module.noDip.get() || !NightshadeClient.client().isInitialized()) {
            return vanilla;
        }
        if (vanilla > 0f) {
            EffectProbe.hit("Old Animations");
        }
        return 0f;
    }
}
