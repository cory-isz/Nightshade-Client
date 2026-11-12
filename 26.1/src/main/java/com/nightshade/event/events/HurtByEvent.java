package com.nightshade.event.events;

import net.minecraft.world.entity.Entity;

/** The server reported that you took damage, and from whom (null when nothing caused it, like fall damage). */
public record HurtByEvent(Entity attacker) {
}
