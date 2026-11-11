package com.nightshade.module;

/** How a module reacts to its keybind. */
public enum BindMode {
    /** Each press flips the enabled state. */
    TOGGLE,
    /** The module is enabled while the bind is held down. */
    HOLD
}
