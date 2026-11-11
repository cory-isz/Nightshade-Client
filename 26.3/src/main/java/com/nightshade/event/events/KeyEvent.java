package com.nightshade.event.events;

/** Raw keyboard input for the game window (GLFW key codes and actions). */
public record KeyEvent(int key, int scancode, int action, int modifiers) {
}
