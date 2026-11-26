package com.nightshade.mixin;

import com.nightshade.cosmetic.Emotes;
import com.nightshade.cosmetic.Wardrobe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: poses your own player model after vanilla's animation (client-side only). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelEmoteMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void nightshade$emote(AvatarRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!Emotes.playing() || mc.player == null || state.id != mc.player.getId() || state.id == Wardrobe.PREVIEW_ID) {
            return;
        }
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        Emotes.apply(m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
    }
}
