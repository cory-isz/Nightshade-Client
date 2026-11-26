package com.nightshade.mixin;

import com.nightshade.cosmetic.OwnerCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ".ns ..." chat lines are handled locally and never sent to the server. */
@Mixin(ClientPacketListener.class)
public abstract class ChatOwnerCommandMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void nightshade$ownerCommands(String message, CallbackInfo ci) {
        if (OwnerCommands.handle(message)) {
            ci.cancel();
        }
    }
}
