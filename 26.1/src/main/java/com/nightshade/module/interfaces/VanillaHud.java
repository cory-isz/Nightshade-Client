package com.nightshade.module.interfaces;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.util.EffectProbe;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/**
 * Hide individual parts of the vanilla HUD - for example the potion icons in the top right when the Nightshade
 * Potion Effects component replaces them. Uses Fabric's HUD element registry, so each part is skipped cleanly.
 */
public final class VanillaHud extends Module {
    private static volatile VanillaHud active;
    private static boolean registered;

    private final BooleanSetting potions = add(new BooleanSetting("Hide Potion Icons", "The status effect icons in the top right", true));
    private final BooleanSetting bossBars = add(new BooleanSetting("Hide Boss Bars", "Boss health bars at the top", false));
    private final BooleanSetting armor = add(new BooleanSetting("Hide Armor Bar", "Armour points above the hotbar", false));
    private final BooleanSetting health = add(new BooleanSetting("Hide Health", "Hearts above the hotbar", false));
    private final BooleanSetting food = add(new BooleanSetting("Hide Food", "Hunger above the hotbar", false));
    private final BooleanSetting air = add(new BooleanSetting("Hide Air", "Bubbles while underwater", false));
    private final BooleanSetting experience = add(new BooleanSetting("Hide Experience", "XP bar and level number", false));
    private final BooleanSetting heldItem = add(new BooleanSetting("Hide Held Item Name", "The item name shown when you switch slots", false));
    private final BooleanSetting hotbar = add(new BooleanSetting("Hide Hotbar", "The hotbar itself", false));
    private final BooleanSetting crosshair = add(new BooleanSetting("Hide Vanilla Crosshair", "The vanilla crosshair (Custom Crosshair draws its own)", false));
    private final BooleanSetting actionBar = add(new BooleanSetting("Hide Action Bar", "Messages above the hotbar", false));
    private final BooleanSetting titles = add(new BooleanSetting("Hide Titles", "Big title and subtitle text", false));
    private final BooleanSetting scoreboard = add(new BooleanSetting("Hide Scoreboard", "The sidebar scoreboard", false));
    private final BooleanSetting subtitles = add(new BooleanSetting("Hide Subtitles", "Sound subtitles in the corner", false));

    public VanillaHud() {
        super("Vanilla HUD", "Hide parts of the vanilla HUD, like the potion icons", ModuleCategory.INTERFACE);
        registerOnce();
    }

    private static synchronized void registerOnce() {
        if (registered) {
            return;
        }
        registered = true;
        hide(VanillaHudElements.MOB_EFFECTS, () -> active != null && active.potions.get());
        hide(VanillaHudElements.BOSS_BAR, () -> active != null && active.bossBars.get());
        hide(VanillaHudElements.ARMOR_BAR, () -> active != null && active.armor.get());
        hide(VanillaHudElements.HEALTH_BAR, () -> active != null && active.health.get());
        hide(VanillaHudElements.FOOD_BAR, () -> active != null && active.food.get());
        hide(VanillaHudElements.AIR_BAR, () -> active != null && active.air.get());
        hide(VanillaHudElements.INFO_BAR, () -> active != null && active.experience.get());
        hide(VanillaHudElements.EXPERIENCE_LEVEL, () -> active != null && active.experience.get());
        hide(VanillaHudElements.HELD_ITEM_TOOLTIP, () -> active != null && active.heldItem.get());
        hide(VanillaHudElements.HOTBAR, () -> active != null && active.hotbar.get());
        hide(VanillaHudElements.CROSSHAIR, () -> active != null && active.crosshair.get());
        hide(VanillaHudElements.OVERLAY_MESSAGE, () -> active != null && active.actionBar.get());
        hide(VanillaHudElements.TITLE_AND_SUBTITLE, () -> active != null && active.titles.get());
        hide(VanillaHudElements.SCOREBOARD, () -> active != null && active.scoreboard.get());
        hide(VanillaHudElements.SUBTITLES, () -> active != null && active.subtitles.get());
    }

    private static void hide(Identifier element, BooleanSupplier hidden) {
        HudElementRegistry.replaceElement(element, original -> (context, tickCounter) -> {
            if (hidden.getAsBoolean()) {
                EffectProbe.hit("Vanilla HUD");
                return;
            }
            original.extractRenderState(context, tickCounter);
        });
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }
}
