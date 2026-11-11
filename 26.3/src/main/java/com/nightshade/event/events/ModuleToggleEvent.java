package com.nightshade.event.events;

import com.nightshade.module.Module;

/** Fired after a module has been enabled or disabled. */
public record ModuleToggleEvent(Module module, boolean enabled) {
}
