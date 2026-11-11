package com.nightshade.mixin;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    /** Target (1.21.9+): private void onKey(long window, int action, KeyInput input) */
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void nightshade$onKey(long window, int action, net.minecraft.client.input.KeyEvent input, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle() && NightshadeClient.client().isInitialized()) {
            NightshadeClient.client().events().post(new KeyEvent(input.key(), input.keycode(), action, input.modifiers()));
        }
    }
}
