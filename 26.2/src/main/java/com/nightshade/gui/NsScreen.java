package com.nightshade.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/**
 * Bridges Minecraft 1.21.9+'s record-based input API ({@link MouseButtonEvent}, {@link KeyEvent}, {@link CharacterEvent}) to simple
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
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        float s = inputScale();
        return onMouseClicked(click.x() / s, click.y() / s, glfwButton(click.button())) || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        float s = inputScale();
        boolean handled = onMouseReleased(click.x() / s, click.y() / s, glfwButton(click.button()));
        return super.mouseReleased(click) || handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        float s = inputScale();
        return onMouseDragged(click.x() / s, click.y() / s, glfwButton(click.button()), deltaX / s, deltaY / s) || super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return onKeyPressed(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        return onKeyReleased(input.key(), input.scancode(), input.modifiers()) || super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (Character.charCount(input.codepoint()) == 1 && onCharTyped((char) input.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(input);
    }

    // ------------------------------------------------------------------ helpers for programmatic input (tests)

    public static MouseButtonEvent click(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(nativeButton(button), 0));
    }

    public static KeyEvent key(int keyCode) {
        return new KeyEvent(keyCode, 0, 0);
    }

    public static CharacterEvent chr(char c) {
        return new CharacterEvent(c);
    }
}
