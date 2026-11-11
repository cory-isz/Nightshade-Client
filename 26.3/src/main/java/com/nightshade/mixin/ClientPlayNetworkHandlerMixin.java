package com.nightshade.mixin;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.PlayerRespawnEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
    /** Target: public void onPlayerRespawn(PlayerRespawnS2CPacket). Fires after the new player entity exists. */
    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void nightshade$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (NightshadeClient.client().isInitialized()) {
            NightshadeClient.client().events().post(PlayerRespawnEvent.INSTANCE);
        }
    }

    /** Target: public void onEntityDamage(EntityDamageS2CPacket). TAIL is only reached on the render thread. */
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void nightshade$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !NightshadeClient.client().isInitialized()) {
            return;
        }
        Entity entity = mc.level.getEntity(packet.entityId());
        if (entity instanceof LivingEntity living && entity != mc.player
                && packet.getSource(mc.level).getEntity() == mc.player) {
            com.nightshade.util.EffectProbe.hit("(confirmed hit)");
            NightshadeClient.client().events().post(new HitLandedEvent(living));
        }
    }
}
