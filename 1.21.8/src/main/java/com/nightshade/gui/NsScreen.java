package com.nightshade.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Bridges Minecraft's screen input methods to simple
 * coordinate/key-code handlers. Each handler returns true when it consumed the input; otherwise vanilla handling
 * (child widgets such as text fields) runs.
 */
public abstract class NsScreen extends Screen {
    protected NsScreen(Component title) {
        super(title);
    }

    /**
     * Nightshade screens use GLFW button numbers (0 = left, 1 = right, 2 = middle). Minecraft 26.3 moved to SDL, which
     * numbers them from 1 (1 = left, 2 = middle, 3 = right); this converts them so every screen works the same on all
     * versions. On versions that still use GLFW numbers it changes nothing.
     */
    public static int glfwButton(int nativeButton) {
        if (com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT == 0) {
            return nativeButton;
        }
        return switch (nativeButton) {
            case 1 -> 0;
            case 3 -> 1;
            case 2 -> 2;
            default -> nativeButton - 1;
        };
    }

    /** The reverse of {@link #glfwButton}: a GLFW button number as this Minecraft version reports it. */
    public static int nativeButton(int glfwButton) {
        if (com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT == 0) {
            return glfwButton;
        }
        return switch (glfwButton) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 2;
            default -> glfwButton + 1;
        };
    }

    /** Screens drawn with their own scale (e.g. the ClickGUI's GUI Scale) map input through this factor. */
    protected float inputScale() {
        return 1f;
    }

    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    protected boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    protected boolean onCharTyped(char chr, int modifiers) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float s = inputScale();
        return onMouseClicked(mouseX / s, mouseY / s, glfwButton(button)) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float s = inputScale();
        boolean handled = onMouseReleased(mouseX / s, mouseY / s, glfwButton(button));
        return super.mouseReleased(mouseX, mouseY, button) || handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        float s = inputScale();
        return onMouseDragged(mouseX / s, mouseY / s, glfwButton(button), deltaX / s, deltaY / s) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return onKeyReleased(keyCode, scanCode, modifiers) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return onCharTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }
}
