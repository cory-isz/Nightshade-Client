package com.nightshade.module.hud;

import com.nightshade.event.events.AttackEntityEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Shows the entity you last hit: name, health bar (animated), distance and the damage your last hit dealt. */
public final class TargetHud extends HudModule {
    private final NumberSetting hideAfter = add(new NumberSetting("Hide After", "Seconds after the last hit before hiding", 5, 1, 30, 1, "s"));
    private final BooleanSetting showDamage = add(new BooleanSetting("Last Hit Damage", "Show how much health your last hit removed", true));

    private LivingEntity target;
    private long lastHitMillis;
    private float healthBefore = -1f;
    private float lastDamage;
    private final Animation bar = new Animation(10f, 1f);

    public TargetHud() {
        super("Target HUD", "Shows health and info of the entity you are fighting", ModuleCategory.HUD, 0.5, 0.7);
        listen(AttackEntityEvent.class, event -> {
            if (event.target() instanceof LivingEntity living) {
                if (living != target) {
                    bar.snap(living.getHealth() / Math.max(1f, living.getMaxHealth()));
                }
                target = living;
                healthBefore = living.getHealth();
                lastHitMillis = System.currentTimeMillis();
            }
        });
    }

    @Override
    public boolean shouldRender() {
        return mc.player != null && target != null && !target.isRemoved()
                && System.currentTimeMillis() - lastHitMillis < hideAfter.get() * 1000.0;
    }

    @Override
    public int getWidth() {
        return 130;
    }

    @Override
    public int getHeight() {
        return 36;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        int w = getWidth();
        int h = getHeight();
        if (background.get()) {
            panel(context, 0, 0, w, h, backgroundColor.argb());
        }
        if (target == null) {
            Fonts.draw(context, "Target HUD", 6, 6, Theme.TEXT_DIM, shadow());
            return;
        }
        float health = target.getHealth();
        float max = Math.max(1f, target.getMaxHealth());
        if (healthBefore >= 0f && health < healthBefore) {
            lastDamage = healthBefore - health;
            healthBefore = -1f;
        }
        int textX = 6;
        if (target instanceof Player player && mc.getConnection() != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
            if (entry != null) {
                PlayerFaceExtractor.extractRenderState(context, entry.getSkin(), 5, 5, 26);
                textX = 36;
            }
        }
        Fonts.draw(context, Fonts.trim(target.getName().getString(), w - textX - 6), textX, 5, textColor.argb(), shadow());
        String info = String.format(Locale.ROOT, "%.1f hp  |  %.1fm", health, mc.player == null ? 0.0 : mc.player.distanceTo(target));
        if (showDamage.get() && lastDamage > 0f) {
            info += String.format(Locale.ROOT, "  |  -%.1f", lastDamage);
        }
        Fonts.draw(context, Fonts.trim(info, w - textX - 6), textX, 15, Theme.TEXT_MUTED, shadow());
        float fraction = bar.setTarget(Math.max(0f, Math.min(1f, health / max))).update();
        int barX2 = w - 6;
        RenderUtil.roundedRect(context, textX, h - 9, barX2, h - 5, 2, 0x70000000);
        int color = ColorUtil.lerp(Theme.DANGER, Theme.SUCCESS, fraction);
        int fill = textX + Math.max(2, Math.round((barX2 - textX) * fraction));
        RenderUtil.roundedRect(context, textX, h - 9, fill, h - 5, 2, color);
        RenderUtil.horizontalGradient(context, textX + 1, h - 9, fill - 1, h - 8, 0x00FFFFFF, 0x50FFFFFF); // sheen
    }
}
