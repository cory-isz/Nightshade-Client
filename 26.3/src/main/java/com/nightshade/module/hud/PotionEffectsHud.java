package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

/** Active status effects with icon, level and remaining time; expiring effects blink. */
public final class PotionEffectsHud extends HudModule {
    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private final BooleanSetting showIcons = add(new BooleanSetting("Icons", "Show effect icons", true));
    private final BooleanSetting showLevel = add(new BooleanSetting("Level", "Show the effect level", true));
    private final NumberSetting warnSeconds = add(new NumberSetting("Expiry Warning", "Blink when an effect has this many seconds left", 10, 0, 60, 1, "s"));

    private record Row(Identifier icon, String name, String time, int color, boolean expiring) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int width = 90;

    public PotionEffectsHud() {
        super("Potion Effects", "Shows active effects and their remaining time", ModuleCategory.HUD, 0.99, 0.32);
        enabledByDefault();
    }

    @Override
    protected void refresh() {
        rows.clear();
        int max = Fonts.width("Potion Effects");
        if (mc.player != null && mc.level != null) {
            List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
            effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
            float tickRate = mc.level.tickRateManager().tickrate();
            for (MobEffectInstance effect : effects) {
                if (!effect.showIcon()) {
                    continue;
                }
                int level = effect.getAmplifier() + 1;
                String name = effect.getEffect().value().getDisplayName().getString()
                        + (showLevel.get() ? (level < ROMAN.length ? ROMAN[level] : " " + level) : "");
                String time = effect.isInfiniteDuration() ? "**:**" : MobEffectUtil.formatDuration(effect, 1f, tickRate).getString();
                boolean expiring = !effect.isInfiniteDuration() && effect.getDuration() < warnSeconds.get() * 20;
                int color = effect.getEffect().value().isBeneficial() ? textColor.argb() : Theme.DANGER;
                rows.add(new Row(net.minecraft.client.gui.Hud.getMobEffectSprite(effect.getEffect()), name, time, color, expiring));
                max = Math.max(max, Fonts.width(name) + Fonts.width(time) + 10);
            }
        }
        width = max + (showIcons.get() ? 22 : 8);
    }

    @Override
    public boolean shouldRender() {
        // Check the player directly: the cached rows are only rebuilt while the component renders.
        return mc.player != null && mc.player.getActiveEffects().stream().anyMatch(MobEffectInstance::showIcon);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return Math.max(1, rows.size()) * 18 + 4;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        if (rows.isEmpty()) {
            Fonts.draw(context, "Potion Effects", 4, 6, Theme.TEXT_DIM, shadow());
            return;
        }
        boolean blinkOff = (System.currentTimeMillis() / 300L) % 2L == 0L;
        int y = 2;
        for (Row row : rows) {
            int x = 4;
            if (showIcons.get()) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, row.icon(), x, y, 16, 16);
                x += 20;
            }
            int color = row.expiring() && blinkOff ? ColorUtil.fade(row.color(), 0.35f) : row.color();
            Fonts.draw(context, row.name(), x, y + 4, color, shadow());
            Fonts.draw(context, row.time(), getWidth() - 4 - Fonts.width(row.time()), y + 4, Theme.TEXT_MUTED, shadow());
            y += 18;
        }
    }
}
