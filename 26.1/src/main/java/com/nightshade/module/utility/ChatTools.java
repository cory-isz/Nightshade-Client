package com.nightshade.module.utility;

import com.nightshade.util.EffectProbe;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.gui.menu.ChatSearchScreen;
import com.nightshade.mixin.accessor.ChatHudAccess;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

/** Stacks repeated chat messages ("message [x3]"). Chat search is the separate Chat Search action. */
public final class ChatTools extends Module {
    private static volatile ChatTools active;
    private final BooleanSetting stack = add(new BooleanSetting("Stack Repeats", "Merge identical consecutive messages into one line with a counter", true));
    private final BooleanSetting mentions = add(new BooleanSetting("Highlight Mentions", "Mark messages that mention your name (or your Nick Hider name)", true));
    private final BooleanSetting mentionSound = add(new BooleanSetting("Mention Sound", "Play a ping when someone mentions you", true))
            .visibleWhen(mentions::get);
    private final com.nightshade.module.setting.ColorSetting mentionColor = add(new com.nightshade.module.setting.ColorSetting("Mention Color",
            "Colour of the mention marker", 0xFFFFC857)).visibleWhen(mentions::get);
    private final com.nightshade.module.setting.TextSetting extraWords = add(new com.nightshade.module.setting.TextSetting("Extra Words",
            "More words that count as a mention, separated by commas (e.g. your clan tag)", "", 64)).visibleWhen(mentions::get);
    private final com.nightshade.module.setting.NumberSetting history = add(new com.nightshade.module.setting.NumberSetting("Chat History",
            "How many messages chat keeps (vanilla keeps 100)", 500, 100, 2000, 100, ""));
    private long lastPing;
    private String lastRaw;
    private int count;

    public ChatTools() {
        super("Chat Tools", "Stacks repeated messages, highlights mentions of your name and keeps more chat history", ModuleCategory.UTILITY);
        enabledByDefault();
        listen(WorldChangeEvent.class, event -> {
            lastRaw = null;
            count = 0;
        });
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        lastRaw = null;
    }

    /** Opens the chat search screen. */
    public static void openSearch() {
        mc.setScreen(new ChatSearchScreen(mc.screen));
    }

    /** How many messages the chat keeps (hook from ChatHudMixin). */
    public static int historyLimit() {
        ChatTools module = active;
        return module == null ? 100 : module.history.intValue();
    }

    /** Marks a message that mentions you (hook from ChatHudMixin, before timestamps). Your own messages are left alone. */
    public static Component mention(Component message) {
        ChatTools module = active;
        if (module == null || !module.mentions.get() || mc.player == null) {
            return message;
        }
        String text = message.getString();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> words = new java.util.ArrayList<>();
        words.add(mc.getUser().getName());
        String nick = NickHider.nickname();
        if (nick != null && !nick.isBlank()) {
            words.add(nick);
        }
        for (String w : module.extraWords.get().split(",")) {
            if (!w.isBlank()) {
                words.add(w.trim());
            }
        }
        // Skip messages you sent yourself ("<Name> ..." or "Name: ...").
        for (String w : words.subList(0, Math.min(2, words.size()))) {
            String wl = w.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("<" + wl + ">") || lower.startsWith(wl + ":") || lower.startsWith("[" + wl + "]")) {
                return message;
            }
        }
        boolean hit = false;
        for (String w : words) {
            String wl = w.toLowerCase(java.util.Locale.ROOT);
            int i = lower.indexOf(wl);
            while (i >= 0 && !hit) {
                boolean startOk = i == 0 || !Character.isLetterOrDigit(lower.charAt(i - 1)) && lower.charAt(i - 1) != '_';
                int end = i + wl.length();
                boolean endOk = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end)) && lower.charAt(end) != '_';
                hit = startOk && endOk;
                i = lower.indexOf(wl, i + 1);
            }
            if (hit) {
                break;
            }
        }
        if (!hit) {
            return message;
        }
        EffectProbe.hit("Chat Tools");
        long now = System.currentTimeMillis();
        if (module.mentionSound.get() && now - module.lastPing > 1000) {
            module.lastPing = now;
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, 1.6f));
        }
        int color = module.mentionColor.argb() & 0xFFFFFF;
        return Component.literal("\u258C ").withStyle(s -> s.withColor(color)).append(message);
    }

    /** Hook from ChatHudMixin, before timestamps are added. */
    public static Component stack(ChatComponent hud, Component message) {
        ChatTools module = active;
        if (module == null || !module.stack.get()) {
            return message;
        }
        String raw = message.getString();
        List<GuiMessage> messages = ((ChatHudAccess) hud).nightshade$getMessages();
        if (raw.equals(module.lastRaw) && !messages.isEmpty()) {
            module.count++;
            EffectProbe.hit("Chat Tools");
            messages.remove(0); // newest first
            ((ChatHudAccess) hud).nightshade$refresh();
            return message.copy().append(Component.literal(" [x" + module.count + "]").withStyle(ChatFormatting.GRAY));
        }
        module.lastRaw = raw;
        module.count = 1;
        return message;
    }
}
