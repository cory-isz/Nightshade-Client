package com.nightshade.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Owner-only chat commands. Everyone else just gets "unknown command"; the text never reaches the server. */
public final class OwnerCommands {
    public static String lastReplyForTest;

    private OwnerCommands() {
    }

    /** Returns true when the message was a Nightshade command (and must not be sent). */
    public static boolean handle(String message) {
        String m = message.trim();
        if (!m.equals(".ns") && !m.startsWith(".ns ")) {
            return false;
        }
        String[] a = m.split("\\s+");
        if (!OwnerKey.present()) {
            reply("Unknown command");
            return true;
        }
        try {
            if (a.length >= 4 && a[1].equals("points") && a[2].equals("add")) {
                int n = Integer.parseInt(a[3].replace(",", ""));
                Wardrobe.ownerAdd(n);
                reply("Added " + String.format("%,d", n) + " points (" + String.format("%,d", Wardrobe.points()) + " total)");
            } else if (a.length >= 4 && a[1].equals("points") && a[2].equals("set")) {
                int n = Integer.parseInt(a[3].replace(",", ""));
                Wardrobe.ownerSet(n);
                reply("Points set to " + String.format("%,d", n));
            } else if (a.length >= 3 && a[1].equals("unlock") && a[2].equals("all")) {
                reply("Unlocked " + Wardrobe.ownerUnlockAll() + " cosmetics");
            } else {
                reply("Owner commands: .ns points add <n>, .ns points set <n>, .ns unlock all");
            }
        } catch (NumberFormatException e) {
            reply("That isn't a number");
        }
        return true;
    }

    private static void reply(String text) {
        lastReplyForTest = text;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d[Nightshade]§r " + text));
        }
    }
}
