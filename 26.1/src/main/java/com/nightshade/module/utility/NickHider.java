package com.nightshade.module.utility;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.TextSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * Nick Hider: shows a nickname instead of your real name in chat, the tab list and your name tag - handy for
 * streaming and recording. Only changes what you see; the server and other players still see your real name.
 */
public final class NickHider extends Module {
    private static volatile NickHider active;
    private final TextSetting nick = add(new TextSetting("Nick", "The name shown instead of yours", "You", 16));
    private final BooleanSetting chat = add(new BooleanSetting("In Chat", "Replace your name in chat messages", true));
    private final BooleanSetting tab = add(new BooleanSetting("In Tab List", "Replace your name in the player list", true));
    private final BooleanSetting nametag = add(new BooleanSetting("On Name Tag", "Replace your name above your head (F5)", true));

    public NickHider() {
        super("Nick Hider", "Shows a nickname instead of your name in chat, tab and your name tag (only you see it)", ModuleCategory.UTILITY);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** The nickname while Nick Hider is on, otherwise null. */
    public static String nickname() {
        NickHider m = active;
        return m == null ? null : m.nick.get();
    }

    private static String realName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() == null ? null : mc.getUser().getName();
    }

    public static Component chat(Component message) {
        NickHider m = active;
        return m == null || !m.chat.get() ? message : replace(message, m);
    }

    public static Component tab(Component name) {
        NickHider m = active;
        return m == null || !m.tab.get() ? name : replace(name, m);
    }

    public static Component nametag(Component name) {
        NickHider m = active;
        return m == null || !m.nametag.get() ? name : replace(name, m);
    }

    /** Rebuilds the component with every occurrence of your name replaced, keeping each part's colour and style. */
    private static Component replace(Component in, NickHider m) {
        String real = realName();
        String nick = m.nick.get();
        if (in == null || real == null || real.isEmpty() || nick.isEmpty() || !in.getString().contains(real)) {
            return in;
        }
        MutableComponent out = Component.empty();
        in.visit((Style style, String text) -> {
            out.append(Component.literal(text.replace(real, nick)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        EffectProbe.hit("Nick Hider");
        return out;
    }
}
