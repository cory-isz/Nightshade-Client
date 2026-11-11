package com.nightshade.mixin;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.AttackEntityEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerMixin {
    /** Target: public void attackEntity(PlayerEntity, Entity). HEAD runs before syncSelectedSlot(). */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void nightshade$onAttack(Player player, Entity target, CallbackInfo ci) {
        if (NightshadeClient.client().events().post(new AttackEntityEvent(target)).isCancelled()) {
            ci.cancel(); // a module replaced this attack
        }
    }

}
