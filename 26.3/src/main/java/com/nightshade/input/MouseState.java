package com.nightshade.input;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Physical mouse button state. Minecraft 26.3 replaced GLFW with SDL and no longer exposes a "is this button held"
 * query, so the client records presses and releases as they arrive (see MouseMixin).
 */
public final class MouseState {
    private static final boolean[] DOWN = new boolean[16];

    private MouseState() {
    }

    public static void update(int button, int action) {
        if (button >= 0 && button < DOWN.length) {
            if (action == InputConstants.PRESS) {
                DOWN[button] = true;
            } else if (action == InputConstants.RELEASE) {
                DOWN[button] = false;
            }
        }
    }

    public static boolean isDown(int button) {
        return button >= 0 && button < DOWN.length && DOWN[button];
    }

    /** Called when the window loses focus: SDL will not send releases for buttons let go elsewhere. */
    public static void releaseAll() {
        java.util.Arrays.fill(DOWN, false);
    }
}
