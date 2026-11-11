package com.nightshade.mixin;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.WorldChangeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    /** Target: public void joinWorld(ClientWorld) */
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void nightshade$onJoinWorld(ClientLevel world, CallbackInfo ci) {
        if (NightshadeClient.client().isInitialized()) {
            NightshadeClient.client().cps().reset();
            NightshadeClient.client().events().post(new WorldChangeEvent(world));
        }
    }
}
