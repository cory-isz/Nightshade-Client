package com.nightshade.mixin;

import com.nightshade.module.visual.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cosmetics: swaps in the Nightshade cape (and elytra skin) for the local player only. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarCosmeticsMixin {
    /** Target: public void extractRenderState(Avatar, AvatarRenderState, float). */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void nightshade$cosmetics(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state.id != mc.player.getId() || state.skin == null) {
            return;
        }
        if (com.nightshade.cosmetic.CosmeticLayer.applies(state)) {
            String worn = com.nightshade.cosmetic.Wardrobe.equipped(com.nightshade.cosmetic.Catalog.Category.CAPES);
            if (worn != null && worn.startsWith("cape_")) {
                state.showCape = false; // the 3D cape replaces the flat one
                return;
            }
        }
        ClientAsset.Texture cape = Cosmetics.cape(state.skin.cape());
        if (cape == null) {
            return;
        }
        PlayerSkin skin = state.skin;
        state.skin = new PlayerSkin(skin.body(), cape, Cosmetics.elytraToo() ? cape : skin.elytra(), skin.model(), skin.secure());
        state.showCape = true;
    }
}
