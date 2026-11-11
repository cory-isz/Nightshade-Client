package com.nightshade.mixin;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.MouseButtonEvent;
import com.nightshade.event.events.MouseScrollEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    /** Target (1.21.9+): private void onMouseButton(long window, MouseInput input, int action) */
    @Inject(method = "onButton", at = @At("HEAD"))
    private void nightshade$onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        int button = input.button();
        int mods = input.modifiers();
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle() || !NightshadeClient.client().isInitialized()) {
            return;
        }
        if (action == GLFW.GLFW_PRESS && mc.gui.screen() == null && mc.gui.overlay() == null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                NightshadeClient.client().cps().recordLeft();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                NightshadeClient.client().cps().recordRight();
            }
        }
        NightshadeClient.client().events().post(new MouseButtonEvent(button, action, mods));
    }

    /** Target: private void onMouseScroll(long window, double horizontal, double vertical) */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nightshade$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle() || mc.gui.screen() != null || !NightshadeClient.client().isInitialized()) {
            return;
        }
        if (NightshadeClient.client().events().post(new MouseScrollEvent(horizontal, vertical)).isCancelled()) {
            ci.cancel();
        }
    }
}
