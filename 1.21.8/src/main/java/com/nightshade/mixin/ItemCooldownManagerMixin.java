package com.nightshade.mixin;

import com.nightshade.module.pvp.CooldownHud;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownManagerMixin {
    /** Target: public void set(ResourceLocation, int) - records the duration so the HUD can show seconds left. */
    @Inject(method = "addCooldown(Lnet/minecraft/resources/ResourceLocation;I)V", at = @At("HEAD"))
    private void nightshade$onSet(ResourceLocation group, int duration, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getCooldowns() == (Object) this) {
            CooldownHud.record(group, duration);
        }
    }
}
