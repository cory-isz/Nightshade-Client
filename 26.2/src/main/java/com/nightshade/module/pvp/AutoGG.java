package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Says "gg" once when a minigame round ends. It recognises common end-of-game chat lines (winner announcements,
 * victory banners), waits a moment, sends a single message and then stays quiet for a cooldown. Multiplayer only.
 */
public final class AutoGG extends Module {
    private static AutoGG instance;
    private static final Pattern[] END_PATTERNS = {
            Pattern.compile("^\\s*winners?\\s*[:-]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(1st|first) killer\\s*[:-]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(won|wins) the (game|match|round)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(victory|you won|you win|game over)!?\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*winning team\\s*[:-]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*reward summary\\s*$", Pattern.CASE_INSENSITIVE),
    };

    private final ModeSetting message = add(new ModeSetting("Message", "What to say", "gg", "gg", "GG", "gg wp", "Good game"));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds to wait before sending", 1.0, 0.0, 5.0, 0.5, "s"));
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", "Minimum seconds between messages", 15, 5, 60, 5, "s"));

    private long lastSent;
    private int sendInTicks = -1;

    public AutoGG() {
        super("Auto GG", "Says gg when a minigame round ends", ModuleCategory.PVP);
        instance = this;
        listen(TickEvent.Post.class, event -> tick());
    }

    /** Whether a chat line looks like an end-of-game announcement (public for tests). */
    public static boolean isGameEnd(String line) {
        String plain = line.replaceAll("§.", "").trim();
        if (plain.isEmpty() || plain.length() > 120 || plain.startsWith("<")) {
            return false; // ignore empty, huge and player chat ("<name> ...") lines
        }
        for (Pattern pattern : END_PATTERNS) {
            if (pattern.matcher(plain).find()) {
                return true;
            }
        }
        return false;
    }

    /** Called for every incoming chat line (from the chat mixin). */
    public static void onChat(Component message) {
        AutoGG gg = instance;
        if (gg == null || !gg.isEnabled() || mc.isLocalServer() || mc.getConnection() == null) {
            return;
        }
        if (gg.sendInTicks < 0 && System.currentTimeMillis() - gg.lastSent > gg.cooldown.get() * 1000.0
                && isGameEnd(message.getString())) {
            gg.sendInTicks = (int) Math.round(gg.delay.get() * 20.0);
        }
    }

    private void tick() {
        if (sendInTicks < 0) {
            return;
        }
        if (sendInTicks-- == 0) {
            if (mc.getConnection() != null && !mc.isLocalServer()) {
                mc.getConnection().sendChat(message.get());
                EffectProbe.hit("Auto GG");
                lastSent = System.currentTimeMillis();
            }
            sendInTicks = -1;
        }
    }

    @Override
    protected void onDisable() {
        sendInTicks = -1;
    }
}
