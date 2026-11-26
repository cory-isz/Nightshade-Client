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
    /** Target: public void keyPress(long window, int key, int scancode, int action, int modifiers) */
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void nightshade$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && NightshadeClient.client().isInitialized()) {
            NightshadeClient.client().events().post(new KeyEvent(key, scancode, action, modifiers));
        }
    }
}
