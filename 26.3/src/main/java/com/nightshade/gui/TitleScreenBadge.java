package com.nightshade.gui;

import com.nightshade.render.BrandLogo;
import com.nightshade.NightshadeClient;
import com.nightshade.module.interfaces.ClickGuiModule;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.gui.menu.AccountsScreen;
import com.nightshade.gui.menu.NightshadeMenuScreen;
import com.nightshade.gui.mods.ModManagerScreen;
import com.nightshade.module.interfaces.MainMenuModule;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Draws a small "Nightshade Client" badge with the GUI key hint on the title screen. */
public final class TitleScreenBadge {
    private TitleScreenBadge() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) {
                return;
            }
            // Account switcher, mod manager and a way back to the Nightshade menu on the vanilla title screen.
            int bw = 90;
            int buttonX = scaledWidth - bw - 6;
            Screens.getWidgets(screen).add(Button.builder(Component.literal("Accounts"),
                    b -> client.gui.setScreen(new AccountsScreen(screen))).bounds(buttonX, 6, bw, 20).build());
            Screens.getWidgets(screen).add(Button.builder(Component.literal("Mods (Nightshade)"),
                    b -> client.gui.setScreen(new ModManagerScreen(screen))).bounds(buttonX, 30, bw, 20).build());
            if (MainMenuModule.isActive()) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Nightshade Menu"),
                        b -> client.gui.setScreen(new NightshadeMenuScreen())).bounds(buttonX, 54, bw, 20).build());
            }
            ScreenEvents.afterExtract(screen).register((s, ctx, mouseX, mouseY, tickDelta) -> {
                ClickGuiModule gui = NightshadeClient.client().modules().get(ClickGuiModule.class);
                String title = NightshadeClient.NAME + " v" + NightshadeClient.VERSION;
                String hint = "Press " + gui.getKeybind().displayName() + " in game to open the menu";
                int w = Math.max(Fonts.width(title) + 16, Fonts.width(hint)) + 12;
                int x = 4;
                int y = 4;
                RenderUtil.roundedRect(ctx, x, y, x + w, y + 26, 4, Theme.BACKGROUND);
                RenderUtil.roundedOutline(ctx, x, y, x + w, y + 26, 4, Theme.BORDER);
                BrandLogo.draw(ctx, x + 5, y + 3, 10);
                Fonts.draw(ctx, title, x + 18, y + 4, Theme.accentLight(), false);
                Fonts.draw(ctx, hint, x + 6, y + 15, Theme.TEXT_DIM, false);
            });
        });
    }
}
