package com.nightshade.mixin;

import com.nightshade.render.ItemPhysicsState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {
    @Unique
    private boolean nightshade$onGround;

    @Override
    public boolean nightshade$onGround() {
        return nightshade$onGround;
    }

    @Override
    public void nightshade$setOnGround(boolean onGround) {
        nightshade$onGround = onGround;
    }
}
