package com.nightshade.module.utility;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Prefixes incoming chat messages with the local time (see ChatHudMixin). */
public final class ChatTimestamps extends Module {
    private static volatile ChatTimestamps active;
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H24S = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
    private static final DateTimeFormatter H12S = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ROOT);

    private final ModeSetting format = add(new ModeSetting("Format", "Clock format", "24h", "24h", "12h"));
    private final BooleanSetting seconds = add(new BooleanSetting("Seconds", "Include seconds", false));
    private final ColorSetting color = add(new ColorSetting("Color", "Timestamp colour", 0xFF8A7FB0));

    public ChatTimestamps() {
        super("Chat Timestamps", "Adds the time to chat messages", ModuleCategory.UTILITY);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static Component decorate(Component message) {
        ChatTimestamps module = active;
        if (module == null) {
            return message;
        }
        DateTimeFormatter formatter = module.format.is("24h")
                ? (module.seconds.get() ? H24S : H24)
                : (module.seconds.get() ? H12S : H12);
        MutableComponent prefix = Component.literal("[" + LocalTime.now().format(formatter) + "] ")
                .withStyle(style -> style.withColor(module.color.argb() & 0xFFFFFF));
        EffectProbe.hit("Chat Timestamps");
        return prefix.append(message);
    }
}
