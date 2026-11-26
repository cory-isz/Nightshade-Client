package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.render.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;

/** Shows the server you are connected to (or "Singleplayer") and the online player count. */
public final class ServerAddressHud extends HudModule {
    private final BooleanSetting playerCount = add(new BooleanSetting("Player Count", "Show how many players are online", true));
    private final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of the label", 0xFFB794FF));
    private String label = "";
    private String value = "";

    public ServerAddressHud() {
        super("Server Address", "Shows the current server address", ModuleCategory.HUD, 1.0, 0.0);
    }

    @Override
    protected void refresh() {
        ServerData info = mc.getCurrentServer();
        if (mc.isLocalServer() || info == null) {
            label = "World ";
            value = "Singleplayer";
        } else {
            label = "IP ";
            value = info.ip;
        }
        if (playerCount.get() && mc.getConnection() != null) {
            value += "  (" + mc.getConnection().getOnlinePlayers().size() + ")";
        }
    }

    @Override
    public int getWidth() {
        return Fonts.width(label) + Fonts.width(value) + 8;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        int x = Fonts.draw(context, label, 4, 4, labelColor.argb(), shadow()) - 1;
        Fonts.draw(context, value, x, 4, textColor.argb(), shadow());
    }
}
