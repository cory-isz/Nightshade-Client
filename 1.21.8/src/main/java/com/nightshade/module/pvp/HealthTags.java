package com.nightshade.module.pvp;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/** Adds the player's health to their name tag (e.g. "Steve 18.5 ❤"), coloured green to red (see EntityRendererNameTagMixin). */
public final class HealthTags extends Module {
    private static volatile HealthTags active;
    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", "Only players (not mobs with name tags)", true));
    private final ModeSetting format = add(new ModeSetting("Format", "Health as a number or in hearts", "Health", "Health", "Hearts", "Percent"));

    public HealthTags() {
        super("Health Tags", "Shows players' health next to their name tag", ModuleCategory.PVP);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** The name tag with health appended, or the original when inactive or not applicable. */
    public static Component decorate(LivingEntity entity, Component nameTag) {
        HealthTags m = active;
        if (m == null || nameTag == null || (m.playersOnly.get() && !(entity instanceof Player))) {
            return nameTag;
        }
        float hp = entity.getHealth() + entity.getAbsorptionAmount();
        float max = Math.max(1f, entity.getMaxHealth());
        float frac = Math.min(1f, entity.getHealth() / max);
        ChatFormatting color = frac > 0.66f ? ChatFormatting.GREEN : frac > 0.33f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        String text = switch (m.format.get()) {
            case "Hearts" -> String.format(Locale.ROOT, "%.1f ❤", hp / 2f);
            case "Percent" -> Math.round(frac * 100f) + "%";
            default -> String.format(Locale.ROOT, "%.1f ❤", hp);
        };
        EffectProbe.hit("Health Tags");
        MutableComponent out = nameTag.copy().append(Component.literal(" " + text).withStyle(color));
        return out;
    }
}
