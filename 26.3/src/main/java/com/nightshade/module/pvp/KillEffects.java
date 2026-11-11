package com.nightshade.module.pvp;

import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A client-side effect where something you killed dies: lightning, flames, hearts or a burst of particles. Only you
 * see it. A kill is an entity that dies within three seconds of a hit you landed (confirmed by the server).
 */
public final class KillEffects extends Module {
    private final ModeSetting effect = add(new ModeSetting("Effect", "What plays where your target dies", "Lightning",
            "Lightning", "Flames", "Hearts", "Souls", "Explosion"));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", "Play a sound with the effect", true));
    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", "Only for player kills", false));

    private final Map<LivingEntity, Integer> recentHits = new IdentityHashMap<>();
    private int played;
    private static int fakeId = -1_000_000;

    public KillEffects() {
        super("Kill Effects", "Lightning, flames or particles where your target dies (only you see it)", ModuleCategory.PVP);
        listen(HitLandedEvent.class, event -> {
            if (!playersOnly.get() || event.target() instanceof Player) {
                recentHits.put(event.target(), 60);
            }
        });
        listen(TickEvent.Post.class, event -> tick());
    }

    private void tick() {
        if (mc.level == null) {
            recentHits.clear();
            return;
        }
        recentHits.entrySet().removeIf(e -> {
            LivingEntity target = e.getKey();
            if (target.isDeadOrDying()) {
                play(target);
                return true;
            }
            int left = e.getValue() - 1;
            e.setValue(left);
            return left <= 0 || target.isRemoved();
        });
    }

    private void play(LivingEntity target) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        switch (effect.get()) {
            case "Lightning" -> {
                LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT, mc.level);
                bolt.setVisualOnly(true);
                bolt.setId(fakeId--); // client-only entity: give it an id no server entity uses
                if (fakeId < -2_000_000_000) {
                    fakeId = -1_000_000;
                }
                bolt.setPos(x, y, z);
                mc.level.addEntity(bolt);
                if (sound.get()) {
                    mc.level.playLocalSound(x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.6f, 1.2f, false);
                }
            }
            case "Flames" -> burst(ParticleTypes.FLAME, target, 40, 0.08);
            case "Hearts" -> burst(ParticleTypes.HEART, target, 14, 0.3);
            case "Souls" -> burst(ParticleTypes.SOUL_FIRE_FLAME, target, 40, 0.06);
            default -> burst(ParticleTypes.EXPLOSION, target, 6, 0.4);
        }
        if (sound.get() && !effect.get().equals("Lightning")) {
            mc.level.playLocalSound(x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.4f, false);
        }
        played++;
        EffectProbe.hit("Kill Effects");
    }

    private void burst(ParticleOptions particle, LivingEntity target, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double ox = (Math.random() - 0.5) * target.getBbWidth();
            double oy = Math.random() * target.getBbHeight();
            double oz = (Math.random() - 0.5) * target.getBbWidth();
            mc.level.addParticle(particle, target.getX() + ox, target.getY() + oy, target.getZ() + oz,
                    (Math.random() - 0.5) * speed, Math.random() * speed, (Math.random() - 0.5) * speed);
        }
    }

    /** Test hook. */
    public int playedForTest() {
        return played;
    }
}
