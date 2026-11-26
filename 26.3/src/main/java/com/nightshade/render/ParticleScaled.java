package com.nightshade.render;

/** Added to every particle: the size multiplier chosen in the Particles module (1 = normal). */
public interface ParticleScaled {
    float nightshade$scale();

    void nightshade$setScale(float scale);
}
