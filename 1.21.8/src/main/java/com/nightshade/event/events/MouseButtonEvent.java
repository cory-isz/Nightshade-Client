package com.nightshade.event.events;

/** Raw mouse button input for the game window (GLFW button codes and actions). */
public record MouseButtonEvent(int button, int action, int modifiers) {
}
