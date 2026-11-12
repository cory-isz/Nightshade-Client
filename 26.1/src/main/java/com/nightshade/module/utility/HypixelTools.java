package com.nightshade.module.utility;

import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;

import java.util.Locale;

/** Hypixel helpers that only send normal chat commands: a Quick Play menu and Auto Tip. */
public final class HypixelTools {
    private HypixelTools() {
    }

    /** Tests set this to exercise the Hypixel tools in an offline world. */
    public static boolean testHypixel;

    /** True when you are on a Hypixel server. */
    public static boolean onHypixel() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (testHypixel) {
            return mc.getConnection() != null;
        }
        var server = mc.getCurrentServer();
        return mc.getConnection() != null && server != null && server.ip != null
                && server.ip.toLowerCase(Locale.ROOT).contains("hypixel.net");
    }

    /** Press its key to open the Quick Play menu: join any game mode with one click. */
    public static final class QuickPlay extends Module {
        public QuickPlay() {
            super("Quick Play", "Press to pick a Hypixel game mode and join it in one click", ModuleCategory.UTILITY);
        }

        @Override
        public boolean isToggleable() {
            return false;
        }

        @Override
        public void onAction() {
            mc.setScreen(new com.nightshade.gui.menu.QuickPlayScreen(mc.screen));
            EffectProbe.hit(getName());
        }
    }

    /** Sends /tip all now and then on Hypixel, which thanks the players whose coin boosters are running. */
    public static final class AutoTip extends Module {
        private final NumberSetting every = add(new NumberSetting("Every", "How often to tip", 20, 15, 60, 5, " min"));
        private long nextTip;

        public AutoTip() {
            super("Auto Tip", "Tips active Hypixel boosters every few minutes (sends /tip all)", ModuleCategory.UTILITY);
            listen(WorldChangeEvent.class, event -> nextTip = System.currentTimeMillis() + 60_000L); // first tip a minute after joining
            listen(TickEvent.Post.class, event -> {
                if (!onHypixel() || System.currentTimeMillis() < nextTip) {
                    return;
                }
                nextTip = System.currentTimeMillis() + every.intValue() * 60_000L;
                mc.getConnection().sendCommand("tip all");
                EffectProbe.hit(getName());
            });
        }

        @Override
        protected void onEnable() {
            nextTip = System.currentTimeMillis() + 60_000L;
        }
    }
}
