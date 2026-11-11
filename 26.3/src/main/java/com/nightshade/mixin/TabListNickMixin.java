package com.nightshade.mixin;

import com.nightshade.module.utility.NickHider;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nick Hider: your nickname in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListNickMixin {
    /** Target: public Component getNameForDisplay(PlayerInfo). */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void nightshade$nick(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NickHider.tab(cir.getReturnValue()));
    }
}
