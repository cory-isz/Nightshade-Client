package com.nightshade.module.utility;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.TextSetting;
import com.nightshade.util.EffectProbe;

/**
 * Chat hotkeys: bind a key to send a message or a command, like "gg", "/l" or "/play bedwars_eight_one". Each hotkey
 * is its own entry so it gets its own key in the modules menu. A message starting with / is sent as a command.
 */
public final class ChatHotkeys {
    private ChatHotkeys() {
    }

    private abstract static class Hotkey extends Module {
        private final TextSetting message;
        private long lastSent;

        Hotkey(int number, String defaultMessage) {
            super("Chat Hotkey " + number, "Press its key to send a message or /command", ModuleCategory.UTILITY);
            message = add(new TextSetting("Message", "What to send; start with / for a command", defaultMessage, 128));
        }

        @Override
        public boolean isToggleable() {
            return false;
        }

        @Override
        public void onAction() {
            String text = message.get().trim();
            long now = System.currentTimeMillis();
            if (text.isEmpty() || mc.getConnection() == null || now - lastSent < 1000) {
                return; // nothing to send, not in a world, or pressed again within a second (no accidental spam)
            }
            lastSent = now;
            if (text.startsWith("/")) {
                mc.getConnection().sendCommand(text.substring(1));
            } else {
                mc.getConnection().sendChat(text);
            }
            EffectProbe.hit(getName());
        }

        /** Test hook. */
        public void setMessageForTest(String text) {
            message.set(text);
        }
    }

    public static final class One extends Hotkey {
        public One() {
            super(1, "gg");
        }
    }

    public static final class Two extends Hotkey {
        public Two() {
            super(2, "/lobby");
        }
    }

    public static final class Three extends Hotkey {
        public Three() {
            super(3, "");
        }
    }

    public static final class Four extends Hotkey {
        public Four() {
            super(4, "");
        }
    }
}
