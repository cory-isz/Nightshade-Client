package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.TotemPopEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Counts Totems of Undying popped by each player, using the totem animation the server already broadcasts to
 * everyone nearby. Messages are shown locally only; nothing is sent to the server.
 */
public final class TotemPopCounter extends Module {
    private final BooleanSetting chat = add(new BooleanSetting("Chat Message", "Print pops in your chat (client-side only)", true));
    private final BooleanSetting toast = add(new BooleanSetting("Notification", "Show a notification for each pop", false));
    private final BooleanSetting self = add(new BooleanSetting("Include Yourself", "Also count your own pops", true));
    private final BooleanSetting deaths = add(new BooleanSetting("Announce Deaths", "Report the total when a counted player dies", true));

    private final Map<UUID, Integer> pops = new HashMap<>();
    private final Map<UUID, Player> tracked = new HashMap<>();

    public TotemPopCounter() {
        super("Totem Pop Counter", "Counts totems popped by each player", ModuleCategory.PVP);
        listen(TotemPopEvent.class, event -> onPop(event.player()));
        listen(WorldChangeEvent.class, event -> reset());
        listen(TickEvent.Post.class, event -> checkDeaths());
    }

    private void onPop(Player player) {
        if (player == mc.player && !self.get()) {
            return;
        }
        int count = pops.merge(player.getUUID(), 1, Integer::sum);
        EffectProbe.hit("Totem Pop Counter");
        tracked.put(player.getUUID(), player);
        String name = player == mc.player ? "You" : player.getName().getString();
        String message = name + (player == mc.player ? " popped " : " popped ") + count + (count == 1 ? " totem" : " totems");
        if (chat.get() && mc.gui != null) {
            mc.gui.getChat().addMessage(Component.literal("[Totems] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(message).withStyle(ChatFormatting.GRAY)));
        }
        if (toast.get()) {
            NightshadeClient.client().notifications().info("Totem Pop", message);
        }
    }

    private void checkDeaths() {
        Iterator<Map.Entry<UUID, Player>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Player> entry = it.next();
            Player player = entry.getValue();
            if (player.isDeadOrDying() || player.getHealth() <= 0f) {
                int count = pops.getOrDefault(entry.getKey(), 0);
                if (deaths.get() && chat.get() && mc.gui != null) {
                    String name = player == mc.player ? "You" : player.getName().getString();
                    mc.gui.getChat().addMessage(Component.literal("[Totems] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.literal(name + " died after popping " + count + (count == 1 ? " totem" : " totems"))
                                    .withStyle(ChatFormatting.RED)));
                }
                pops.remove(entry.getKey());
                it.remove();
            } else if (player.isRemoved() && mc.level != null && mc.level.getPlayerByUUID(entry.getKey()) == null) {
                it.remove(); // out of range: keep the count, stop watching this instance
            }
        }
    }

    public int pops(UUID player) {
        return pops.getOrDefault(player, 0);
    }

    private void reset() {
        pops.clear();
        tracked.clear();
    }

    @Override
    protected void onDisable() {
        reset();
    }
}
