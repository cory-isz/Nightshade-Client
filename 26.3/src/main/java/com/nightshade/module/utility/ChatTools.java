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
    private String lastRaw;
    private int count;

    public ChatTools() {
        super("Chat Tools", "Stacks repeated chat messages into one line with a counter", ModuleCategory.UTILITY);
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
        mc.gui.setScreen(new ChatSearchScreen(mc.gui.screen()));
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
