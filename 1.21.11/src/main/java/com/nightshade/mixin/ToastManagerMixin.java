package com.nightshade.mixin;

import com.nightshade.module.utility.ToastControl;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    /** Target: public void add(Toast) */
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void nightshade$filter(Toast toast, CallbackInfo ci) {
        if (ToastControl.shouldHide(toast)) {
            ci.cancel();
        }
    }
}
