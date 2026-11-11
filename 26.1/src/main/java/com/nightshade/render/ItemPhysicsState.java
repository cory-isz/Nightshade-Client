package com.nightshade.render;

/** Extra per-frame data carried on ItemEntityRenderState for Item Physics (added by ItemEntityRenderStateMixin). */
public interface ItemPhysicsState {
    boolean nightshade$onGround();

    void nightshade$setOnGround(boolean onGround);
}
