package com.nightshade.module.pvp;

import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.core.particles.ParticleTypes;

/** More hit particles: extra crit (and sharpness) sparks on every hit you land, so hits feel and read better. */
public final class HitParticles extends Module {
    private final NumberSetting multiplier = add(new NumberSetting("Multiplier", "How many extra particle bursts per hit", 2, 1, 6, 1, "x"));
    private final BooleanSetting crits = add(new BooleanSetting("Always Crits", "Show crit sparks on every hit, not only real crits", true));
    private final BooleanSetting sharpness = add(new BooleanSetting("Always Sharpness", "Show the blue enchant sparks on every hit", true));

    public HitParticles() {
        super("Hit Particles", "More crit and sharpness particles on every hit you land", ModuleCategory.PVP);
        listen(HitLandedEvent.class, event -> {
            if (mc.particleEngine == null) {
                return;
            }
            for (int i = 0; i < multiplier.intValue(); i++) {
                if (crits.get()) {
                    mc.particleEngine.createTrackingEmitter(event.target(), ParticleTypes.CRIT);
                }
                if (sharpness.get()) {
                    mc.particleEngine.createTrackingEmitter(event.target(), ParticleTypes.ENCHANTED_HIT);
                }
            }
            EffectProbe.hit("Hit Particles");
        });
    }
}
