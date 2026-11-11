package com.nightshade.mixin;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TotemPopEvent;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusMixin {
    /** Target: public void handleStatus(byte) - entity status packets from the server. */
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void nightshade$onStatus(byte status, CallbackInfo ci) {
        if (status == EntityEvent.PROTECTED_FROM_DEATH && (Object) this instanceof Player player
                && player.level().isClientSide() && NightshadeClient.client().isInitialized()) {
            NightshadeClient.client().events().post(new TotemPopEvent(player));
        }
    }
}
