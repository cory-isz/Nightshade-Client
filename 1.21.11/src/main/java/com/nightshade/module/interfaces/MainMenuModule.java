package com.nightshade.module.interfaces;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;

/** Replaces the vanilla title screen with the Nightshade main menu (see MinecraftClientScreenMixin). */
public final class MainMenuModule extends Module {
    private static volatile MainMenuModule active;
    public static final String STYLE_MODERN = "Modern";
    public static final String STYLE_PREVIOUS = "Aurora (1.12)";
    public static final String STYLE_CLASSIC = "Classic (1.11)";
    private final ModeSetting style = add(new ModeSetting("Menu Style", "Modern: top bar and play panel (new). Aurora: the 1.12 tile menu. Classic: the original Nightshade menu",
            STYLE_MODERN, STYLE_MODERN, STYLE_PREVIOUS, STYLE_CLASSIC));
    private final BooleanSetting realms = add(new BooleanSetting("Show Realms", "Show the Realms entry in the menu", true));
    private final ModeSetting background = add(new ModeSetting("Background", "Aurora Peaks: aurora over mountains. Night Forest: moonlit pine forest in mist. Deep Space: planet, nebula and shooting stars. Nightshade Sky: drifting nebula. Panorama / Blurred Panorama: the Minecraft panorama. All follow your client colour",
            "Aurora Peaks", "Aurora Peaks", "Night Forest", "Deep Space", "Nightshade Sky", "Blurred Panorama", "Panorama"));
    private final BooleanSetting styledButtons = add(new BooleanSetting("Styled Buttons", "Rounded buttons and sliders in the client colour on every screen (Options, world creation, pause menu...)", true))
            .onChange(() -> com.nightshade.render.WidgetStyle.setEnabled(this.styledButtons.get()));
    private final BooleanSetting customPause = add(new BooleanSetting("Custom Pause Menu", "Nightshade pause menu instead of the vanilla one", true));
    private final BooleanSetting customOptions = add(new BooleanSetting("Custom Options", "Nightshade options screen (tiles that open Minecraft's settings pages)", true));
    private final BooleanSetting pauseStats = add(new BooleanSetting("Pause Stats Bar", "Show FPS, ping and coordinates in the pause menu", true))
            .visibleWhen(customPause::get);
    private final BooleanSetting pauseCoords = add(new BooleanSetting("Show Coordinates", "Include your coordinates in the pause menu bar (turn off when streaming)", true))
            .visibleWhen(() -> customPause.get() && pauseStats.get());
    private final BooleanSetting pausePlayer = add(new BooleanSetting("Show Player", "Show your character beside the pause menu", true))
            .visibleWhen(customPause::get);
    private final com.nightshade.module.setting.ActionSetting setup = add(new com.nightshade.module.setting.ActionSetting("Setup",
            "Run the first-launch setup again (playstyle, look and performance)", "Run Again", () -> {
                com.nightshade.NightshadeClient.client().config().clearFlag(com.nightshade.gui.menu.SetupWizardScreen.FLAG);
                if (mc.level == null) {
                    mc.setScreen(createMenu());
                } else {
                    com.nightshade.NightshadeClient.client().notifications().info("Setup", "It will open next time you are on the main menu");
                }
            }));
    private final com.nightshade.module.setting.ActionSetting tour = add(new com.nightshade.module.setting.ActionSetting("Tour",
            "Show the first-launch tour of the main menu again", "Show Again", () -> {
                com.nightshade.NightshadeClient.client().config().clearFlag("tourDone");
                com.nightshade.NightshadeClient.client().config().saveDirty();
                if (mc.level == null) {
                    mc.setScreen(createMenu());
                } else {
                    com.nightshade.NightshadeClient.client().notifications().info("Tour", "It will show next time you open the main menu");
                }
            }));
    private final BooleanSetting animate = add(new BooleanSetting("Animate Background", "Let the panorama spin and the backgrounds drift (turn off for a still background)", true));
    private final BooleanSetting updates = add(new BooleanSetting("Check For Updates", "Look on Modrinth for a newer Nightshade when the main menu opens (reads the public version list only)", true));
    private final BooleanSetting betas = add(new BooleanSetting("Include Betas", "Also tell you about beta versions", false))
            .visibleWhen(updates::get);
    private final BooleanSetting particles = add(new BooleanSetting("Floating Lights", "Soft glowing motes drifting up the menu", true));

    public MainMenuModule() {
        super("Main Menu", "Nightshade main menu with account switcher and mod manager", ModuleCategory.INTERFACE);
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** The main menu in the chosen style. */
    public static net.minecraft.client.gui.screens.Screen createMenu() {
        var client = com.nightshade.NightshadeClient.client();
        if (client.isInitialized() && !client.config().hasFlag(com.nightshade.gui.menu.SetupWizardScreen.FLAG)) {
            return new com.nightshade.gui.menu.SetupWizardScreen(); // first launch
        }
        MainMenuModule m = active;
        String s = m == null ? STYLE_MODERN : m.style.get();
        if (STYLE_PREVIOUS.equals(s)) {
            return new com.nightshade.gui.menu.PreviousMenuScreen();
        }
        if (STYLE_CLASSIC.equals(s)) {
            return new com.nightshade.gui.menu.ClassicMenuScreen();
        }
        return new com.nightshade.gui.menu.NightshadeMenuScreen();
    }

    public void setStyle(String value) {
        style.set(value);
        markDirty();
    }

    public static boolean isActive() {
        return active != null;
    }

    public static boolean customPauseMenu() {
        MainMenuModule m = active;
        return m != null && m.customPause.get();
    }

    public static boolean pauseStats() {
        MainMenuModule m = active;
        return m == null || m.pauseStats.get();
    }

    public static boolean animateBackground() {
        MainMenuModule m = active;
        return m == null || m.animate.get();
    }

    public static boolean checkUpdates() {
        MainMenuModule m = active;
        return m == null || m.updates.get();
    }

    public static boolean includeBetas() {
        MainMenuModule m = active;
        return m != null && m.betas.get();
    }

    public static boolean pausePlayer() {
        MainMenuModule m = active;
        return m == null || m.pausePlayer.get();
    }

    public static boolean pauseCoordinates() {
        MainMenuModule m = active;
        return m == null || m.pauseCoords.get();
    }

    public static boolean customOptions() {
        MainMenuModule m = active;
        return m != null && m.customOptions.get();
    }

    public boolean showRealms() {
        return realms.get();
    }

    public String background() {
        return background.get();
    }

    public boolean particles() {
        return particles.get();
    }
}
