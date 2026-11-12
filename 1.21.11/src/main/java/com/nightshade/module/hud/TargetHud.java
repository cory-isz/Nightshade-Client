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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Shows the entity you last hit: name, health bar (animated), distance and the damage your last hit dealt. */
public final class TargetHud extends HudModule {
    private final NumberSetting hideAfter = add(new NumberSetting("Hide After", "Seconds after the last hit before hiding", 5, 1, 30, 1, "s"));
    private final BooleanSetting showDamage = add(new BooleanSetting("Last Hit Damage", "Show how much health your last hit removed", true));
    private final BooleanSetting showGear = add(new BooleanSetting("Show Gear", "Show what your target is holding and wearing", true));
    private final BooleanSetting armourWarning = add(new BooleanSetting("Armour Warning", "Mark your target's armour pieces that are about to break", true));
    private final NumberSetting breakAt = add(new NumberSetting("Warn Below", "Warn when a piece has less durability left than this", 15, 5, 50, 5, "%"));
    private final BooleanSetting showTrade = add(new BooleanSetting("Hit Trade", "Hits you landed on them : hits they landed on you", true));

    private LivingEntity target;
    private long lastHitMillis;
    private float healthBefore = -1f;
    private float lastDamage;
    private final Animation bar = new Animation(10f, 1f);
    private int hitsGiven;
    private int hitsTaken;
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR = {
            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};

    public TargetHud() {
        super("Target HUD", "Shows health and info of the entity you are fighting", ModuleCategory.HUD, 0.5, 0.7);
        listen(AttackEntityEvent.class, event -> {
            if (event.target() instanceof LivingEntity living) {
                if (living != target) {
                    bar.snap(living.getHealth() / Math.max(1f, living.getMaxHealth()));
                    hitsGiven = 0;
                    hitsTaken = 0;
                }
                target = living;
                healthBefore = living.getHealth();
                lastHitMillis = System.currentTimeMillis();
            }
        });
        listen(com.nightshade.event.events.HitLandedEvent.class, event -> {
            if (event.target() == target) {
                hitsGiven++;
            }
        });
        listen(com.nightshade.event.events.HurtByEvent.class, event -> {
            if (event.attacker() != null && event.attacker() == target) {
                hitsTaken++;
                lastHitMillis = System.currentTimeMillis(); // keep showing while they are hitting you
            }
        });
    }

    /** Test hooks. */
    public int hitsGivenForTest() {
        return hitsGiven;
    }

    @Override
    public boolean shouldRender() {
        return mc.player != null && target != null && !target.isRemoved()
                && System.currentTimeMillis() - lastHitMillis < hideAfter.get() * 1000.0;
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return showGear.get() ? 52 : 36;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
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
                PlayerFaceRenderer.draw(context, entry.getSkin(), 5, 5, 26);
                textX = 36;
            }
        }
        String trade = showTrade.get() && hitsGiven + hitsTaken > 0 ? hitsGiven + " : " + hitsTaken : "";
        int tradeW = trade.isEmpty() ? 0 : Fonts.width(trade) + 6;
        Fonts.draw(context, Fonts.trim(target.getName().getString(), w - textX - 6 - tradeW), textX, 5, textColor.argb(), shadow());
        if (!trade.isEmpty()) {
            int tc = hitsGiven > hitsTaken ? Theme.SUCCESS : hitsGiven < hitsTaken ? Theme.DANGER : Theme.TEXT_MUTED;
            Fonts.draw(context, trade, w - 6 - Fonts.width(trade), 5, tc, shadow());
        }
        String info = String.format(Locale.ROOT, "%.1f hp  |  %.1fm", health, mc.player == null ? 0.0 : mc.player.distanceTo(target));
        if (showDamage.get() && lastDamage > 0f) {
            info += String.format(Locale.ROOT, "  |  -%.1f", lastDamage);
        }
        Fonts.draw(context, Fonts.trim(info, w - textX - 6), textX, 15, Theme.TEXT_MUTED, shadow());
        float fraction = bar.setTarget(Math.max(0f, Math.min(1f, health / max))).update();
        int barX2 = w - 6;
        RenderUtil.roundedRect(context, textX, 27, barX2, 31, 2, 0x70000000);
        int color = ColorUtil.lerp(Theme.DANGER, Theme.SUCCESS, fraction);
        int fill = textX + Math.max(2, Math.round((barX2 - textX) * fraction));
        RenderUtil.roundedRect(context, textX, 27, fill, 31, 2, color);
        RenderUtil.horizontalGradient(context, textX + 1, 27, fill - 1, 28, 0x00FFFFFF, 0x50FFFFFF); // sheen
        if (showGear.get()) {
            // held item, then armour, at 3/4 size
            int gx = textX;
            context.pose().pushMatrix();
            context.pose().translate(gx, 35);
            context.pose().scale(0.75f, 0.75f);
            int ix = 0;
            net.minecraft.world.item.ItemStack held = target.getMainHandItem();
            if (!held.isEmpty()) {
                context.renderItem(held, ix, 0);
                ix += 22;
            }
            boolean breaking = false;
            for (net.minecraft.world.entity.EquipmentSlot slot : ARMOR) {
                net.minecraft.world.item.ItemStack piece = target.getItemBySlot(slot);
                if (!piece.isEmpty()) {
                    if (piece.isDamageableItem() && piece.getMaxDamage() > 0
                            && 1f - piece.getDamageValue() / (float) piece.getMaxDamage() < breakAt.floatValue() / 100f) {
                        // nearly broken: red tile behind it
                        RenderUtil.roundedRect(context, ix - 1, -1, ix + 17, 17, 3, 0xA0FF3B5C);
                        breaking = true;
                    }
                    context.renderItem(piece, ix, 0);
                    ix += 17;
                }
            }
            context.pose().popMatrix();
            if (breaking && armourWarning.get()) {
                int tx = gx + Math.round(ix * 0.75f) + 4;
                Fonts.draw(context, "Armour breaking", tx, 38, Theme.DANGER, shadow());
            }
        }
    }
}
