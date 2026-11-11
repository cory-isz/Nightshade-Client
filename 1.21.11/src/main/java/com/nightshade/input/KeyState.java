package com.nightshade.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.nightshade.mixin.accessor.KeyBindingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Reads the physical state of vanilla key bindings, used to restore bindings that a module held down. */
public final class KeyState {
    private KeyState() {
    }

    public static InputConstants.Key boundKey(KeyMapping binding) {
        return ((KeyBindingAccessor) binding).nightshade$getBoundKey();
    }

    public static boolean isPhysicallyDown(KeyMapping binding) {
        InputConstants.Key key = boundKey(binding);
        long handle = Minecraft.getInstance().getWindow().handle();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
    }

    /** Restores a binding's pressed flag to match the physical input. */
    public static void restore(KeyMapping binding) {
        binding.setDown(isPhysicallyDown(binding));
    }

    public static boolean isBoundTo(KeyMapping binding, InputConstants.Type type, int code) {
        InputConstants.Key key = boundKey(binding);
        return key.getType() == type && key.getValue() == code;
    }
}
