package com.nightshade.mixin;

import com.nightshade.util.EffectProbe;
import com.nightshade.gui.menu.NightshadeMenuScreen;
import com.nightshade.module.interfaces.MainMenuModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class MinecraftClientScreenMixin {
    /** Target (26.2: moved from Minecraft to Gui): public void setScreen(Screen) - swaps the vanilla title screen for the Nightshade menu. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen nightshade$replaceTitleScreen(Screen screen) {
        if (screen != null && screen.getClass() == TitleScreen.class && MainMenuModule.isActive()) {
            if (NightshadeMenuScreen.vanillaRequested) {
                NightshadeMenuScreen.vanillaRequested = false;
                return screen;
            }
            EffectProbe.hit("Main Menu");
            return MainMenuModule.createMenu();
        }
        // The pause menu (Esc). F3+Esc pauses without a menu and stays vanilla.
        if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.PauseScreen.class
                && ((net.minecraft.client.gui.screens.PauseScreen) screen).showsPauseMenu() && MainMenuModule.customPauseMenu()) {
            EffectProbe.hit("Main Menu");
            return new com.nightshade.gui.menu.NightshadePauseScreen();
        }
        // Vanilla server/world lists (e.g. leaving a server returns to JoinMultiplayerScreen) -> the Nightshade screens.
        // Left alone when coming from the vanilla title screen ("Vanilla menu"), so that path stays fully vanilla.
        if (screen != null && MainMenuModule.isActive()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            boolean fromVanillaTitle = mc.gui.screen() instanceof TitleScreen;
            if (!fromVanillaTitle && screen.getClass() == net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen.class) {
                return new com.nightshade.gui.menu.ServersScreen(MainMenuModule.createMenu());
            }
            if (!fromVanillaTitle && screen.getClass() == net.minecraft.client.gui.screens.worldselection.SelectWorldScreen.class) {
                return new com.nightshade.gui.menu.WorldsScreen(MainMenuModule.createMenu());
            }
        }
        // The vanilla Options screen (e.g. opened by other mods' menus) -> Nightshade Options.
        if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.OptionsScreen.class && MainMenuModule.customOptions()) {
            return new com.nightshade.gui.menu.NightshadeOptionsScreen(((com.nightshade.mixin.accessor.OptionsScreenAccess) screen).nightshade$parent());
        }
        return screen;
    }
}
