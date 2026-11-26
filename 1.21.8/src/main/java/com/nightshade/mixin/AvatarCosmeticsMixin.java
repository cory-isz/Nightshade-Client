package com.nightshade.mixin;

import com.nightshade.module.visual.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cosmetics: swaps in the Nightshade cape (and elytra skin) for the local player only. */
@Mixin(PlayerRenderer.class)
public abstract class AvatarCosmeticsMixin {
    /** Target: public void extractRenderState(AbstractClientPlayer, PlayerRenderState, float). */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL"))
    private void nightshade$cosmetics(AbstractClientPlayer avatar, PlayerRenderState state, float partialTick, CallbackInfo ci) {
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
        ResourceLocation cape = Cosmetics.cape(state.skin.capeTexture());
        if (cape == null) {
            return;
        }
        PlayerSkin skin = state.skin;
        state.skin = new PlayerSkin(skin.texture(), skin.textureUrl(), cape, Cosmetics.elytraToo() ? cape : skin.elytraTexture(), skin.model(), skin.secure());
        state.showCape = true;
    }
}
