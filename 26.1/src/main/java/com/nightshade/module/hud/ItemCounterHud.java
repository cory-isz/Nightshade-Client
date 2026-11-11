package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts important items across the whole inventory (arrows, potions, pearls, totems, golden apples...). */
public final class ItemCounterHud extends HudModule {
    /** The icon stack is created lazily: 26.x forbids building ItemStacks before item components are bound. */
    private static final class Counter {
        private final BooleanSetting enabled;
        private final Item item;
        private final Predicate<ItemStack> matches;
        private ItemStack icon;

        Counter(BooleanSetting enabled, Item item, Predicate<ItemStack> matches) {
            this.enabled = enabled;
            this.item = item;
            this.matches = matches;
        }

        BooleanSetting enabled() {
            return enabled;
        }

        Predicate<ItemStack> matches() {
            return matches;
        }

        ItemStack icon() {
            if (icon == null) {
                icon = new ItemStack(item);
            }
            return icon;
        }
    }

    private final List<Counter> counters = new ArrayList<>();
    private final List<ItemStack> icons = new ArrayList<>();
    private final List<String> counts = new ArrayList<>();
    private final BooleanSetting hideZero = add(new BooleanSetting("Hide Empty", "Hide items you have none of", false));

    public ItemCounterHud() {
        super("Item Counter", "Counts arrows, potions, pearls and more in your inventory", ModuleCategory.HUD, 0.99, 0.82);
        counter("Arrows", Items.ARROW, s -> s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW), true);
        counter("Potions", Items.SPLASH_POTION, s -> s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION), true);
        counter("Ender Pearls", Items.ENDER_PEARL, s -> s.is(Items.ENDER_PEARL), true);
        counter("Totems", Items.TOTEM_OF_UNDYING, s -> s.is(Items.TOTEM_OF_UNDYING), true);
        counter("Golden Apples", Items.GOLDEN_APPLE, s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE), true);
        counter("Food", Items.COOKED_BEEF, s -> s.get(DataComponents.FOOD) != null, false);
        counter("Blocks", Items.COBBLESTONE, s -> s.getItem() instanceof net.minecraft.world.item.BlockItem, false);
    }

    private void counter(String name, Item icon, Predicate<ItemStack> predicate, boolean defaultOn) {
        BooleanSetting setting = add(new BooleanSetting(name, "Count " + name.toLowerCase(), defaultOn));
        counters.add(new Counter(setting, icon, predicate));
    }

    @Override
    protected void refresh() {
        icons.clear();
        counts.clear();
        if (mc.player == null) {
            return;
        }
        var inventory = mc.player.getInventory();
        for (Counter counter : counters) {
            if (!counter.enabled().get()) {
                continue;
            }
            int total = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && counter.matches().test(stack)) {
                    total += stack.getCount();
                }
            }
            if (total == 0 && hideZero.get()) {
                continue;
            }
            icons.add(counter.icon());
            counts.add(Integer.toString(total));
        }
    }

    @Override
    public int getWidth() {
        int max = 0;
        for (String count : counts) {
            max = Math.max(max, Fonts.width(count));
        }
        return 24 + Math.max(10, max) + 4;
    }

    @Override
    public int getHeight() {
        return Math.max(1, icons.size()) * 18 + 2;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        if (icons.isEmpty()) {
            Fonts.draw(context, "-", 6, 6, Theme.TEXT_DIM, shadow());
            return;
        }
        for (int i = 0; i < icons.size(); i++) {
            int y = 1 + i * 18;
            context.item(icons.get(i), 3, y);
            int color = counts.get(i).equals("0") ? Theme.DANGER : textColor.argb();
            Fonts.draw(context, counts.get(i), 23, y + 5, color, shadow());
        }
    }
}
