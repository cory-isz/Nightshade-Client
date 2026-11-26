package com.nightshade.mixin;

import com.nightshade.module.pvp.ShieldStatus;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    /** Target: private void prepareItemElements() - GUI item icons (hotbar, inventory) are submitted inside it. */
    @Inject(method = "prepareItemElements", at = @At("HEAD"))
    private void nightshade$beginItems(CallbackInfo ci) {
        ShieldStatus.guiPass = true;
    }

    @Inject(method = "prepareItemElements", at = @At("RETURN"))
    private void nightshade$endItems(CallbackInfo ci) {
        ShieldStatus.guiPass = false;
    }
}
