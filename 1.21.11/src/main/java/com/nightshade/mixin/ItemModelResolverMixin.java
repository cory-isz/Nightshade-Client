package com.nightshade.mixin;

import com.nightshade.module.pvp.ShieldStatus;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    /** Target: public void appendItemLayers(...) - keys shield models by Shield Status so cached GUI icons refresh. */
    @Inject(method = "appendItemLayers", at = @At("TAIL"))
    private void nightshade$shieldIdentity(ItemStackRenderState output, ItemStack stack, ItemDisplayContext context, Level level,
                                           ItemOwner owner, int seed, CallbackInfo ci) {
        if (ShieldStatus.active() && stack.is(Items.SHIELD)) {
            output.appendModelIdentityElement(ShieldStatus.identity());
            output.setAnimated(); // GUI icons of animated items are redrawn every frame, so the colour follows the state
        }
    }
}
