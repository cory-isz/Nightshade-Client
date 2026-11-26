package com.nightshade.event.events;

import net.minecraft.world.entity.LivingEntity;

/**
 * The server reported that an entity took damage caused by you (EntityDamageS2CPacket with you as the attacker).
 * Unlike watching the red hurt flash, this is exact even when the target is burning, poisoned or hit by others.
 */
public record HitLandedEvent(LivingEntity target) {
}
