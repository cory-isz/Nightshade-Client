package com.nightshade.module.pvp;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item cooldowns (ender pearls, chorus fruit, wind charges, shields disabled by axes...) with seconds remaining. */
public final class CooldownHud extends HudModule {
    private static final Map<Identifier, long[]> TIMES = new HashMap<>(); // group -> {startMillis, endMillis}

    private final ModeSetting layout = add(new ModeSetting("Layout", "Arrangement", "Vertical", "Vertical", "Horizontal"));
    private final BooleanSetting showBar = add(new BooleanSetting("Progress Bar", "Draw a bar under each item", true));

    private final List<ItemStack> items = new ArrayList<>();
    private final List<Float> progress = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    public CooldownHud() {
        super("Cooldown HUD", "Shows item cooldowns like ender pearls and shields", ModuleCategory.PVP, 0.6, 0.86);
    }

    /** Called from the cooldown mixin for the local player's cooldown manager. */
    public static synchronized void record(Identifier group, int durationTicks) {
        long now = System.currentTimeMillis();
        TIMES.put(group, new long[]{now, now + durationTicks * 50L});
    }

    /** Milliseconds left on a recorded cooldown group, or 0. */
    public static synchronized long remainingMillis(Identifier group) {
        long[] time = TIMES.get(group);
        return time == null ? 0L : Math.max(0L, time[1] - System.currentTimeMillis());
    }

    private static synchronized long[] times(Identifier group) {
        return TIMES.get(group);
    }

    @Override
    protected void refresh() {
        items.clear();
        progress.clear();
        labels.clear();
        if (mc.player == null) {
            return;
        }
        ItemCooldowns cooldowns = mc.player.getCooldowns();
        Inventory inventory = mc.player.getInventory();
        Set<Identifier> seen = new HashSet<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !cooldowns.isOnCooldown(stack)) {
                continue;
            }
            Identifier group = cooldowns.getCooldownGroup(stack);
            if (!seen.add(group)) {
                continue;
            }
            float left = cooldowns.getCooldownPercent(stack, 0f);
            long[] time = times(group);
            String label;
            if (time != null && time[1] > System.currentTimeMillis()) {
                label = String.format(Locale.ROOT, "%.1fs", (time[1] - System.currentTimeMillis()) / 1000.0);
            } else {
                label = Math.round(left * 100f) + "%";
            }
            items.add(stack);
            progress.add(left);
            labels.add(label);
        }
    }

    @Override
    public boolean shouldRender() {
        // Checked directly (not via the cached list, which only refreshes while the element is drawn).
        if (mc.player == null) {
            return false;
        }
        ItemCooldowns cooldowns = mc.player.getCooldowns();
        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && cooldowns.isOnCooldown(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean vertical() {
        return layout.is("Vertical");
    }

    private int cellWidth() {
        return 16 + 4 + Fonts.width("00.0s");
    }

    private int count(boolean preview) {
        return preview && items.isEmpty() ? 2 : Math.max(1, items.size());
    }

    @Override
    public int getWidth() {
        int cells = count(true);
        return 4 + (vertical() ? cellWidth() : cells * (cellWidth() + 4) - 4) + 4;
    }

    @Override
    public int getHeight() {
        int cells = count(true);
        return 4 + (vertical() ? cells * 20 - 2 : 18) + 4;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        boolean sample = preview && items.isEmpty();
        int n = sample ? 2 : items.size();
        for (int i = 0; i < n; i++) {
            ItemStack stack = sample ? new ItemStack(i == 0 ? Items.ENDER_PEARL : Items.SHIELD) : items.get(i);
            float left = sample ? (i == 0 ? 0.6f : 0.3f) : progress.get(i);
            String label = sample ? (i == 0 ? "0.6s" : "1.5s") : labels.get(i);
            int x = 4 + (vertical() ? 0 : i * (cellWidth() + 4));
            int y = 4 + (vertical() ? i * 20 : 0);
            context.item(stack, x, y);
            Fonts.draw(context, label, x + 20, y + 4, textColor.argb(), textShadow.get());
            if (showBar.get()) {
                RenderUtil.rect(context, x, y + 16, x + 16, y + 17, 0x80000000);
                RenderUtil.rect(context, x, y + 16, x + Math.round(16 * left), y + 17, Theme.accent());
            }
        }
    }
}
