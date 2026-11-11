package com.nightshade.event.events;

import com.nightshade.event.Cancellable;
import net.minecraft.world.entity.Entity;

/** Fired immediately before the local player attacks an entity. Cancelling it skips that attack. */
public final class AttackEntityEvent extends Cancellable {
    private final Entity target;

    public AttackEntityEvent(Entity target) {
        this.target = target;
    }

    public Entity target() {
        return target;
    }
}
