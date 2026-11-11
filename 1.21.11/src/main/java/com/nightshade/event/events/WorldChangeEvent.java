package com.nightshade.event.events;

import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/** Fired when the client world changes: joining a world, changing dimension, or disconnecting (world == null). */
public record WorldChangeEvent(@Nullable ClientLevel world) {
}
