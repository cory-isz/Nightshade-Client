package com.nightshade.gui;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** A Minecraft item as each module's icon (a spyglass for Zoom, a map for the Minimap...), like the big clients' mod grids. */
public final class ModuleIcons {
    private static final Map<String, Item> ICONS = new HashMap<>();

    private static void put(Item item, String... names) {
        for (String name : names) {
            ICONS.put(name, item);
        }
    }

    static {
        put(Items.LAVA_BUCKET, "Animation Optimizer");
        put(Items.DIAMOND_CHESTPLATE, "Armor Status");
        put(Items.CAKE, "Auto GG");
        put(Items.MACE, "Auto Mace");
        put(Items.DIAMOND_AXE, "Auto Shield Breaker");
        put(Items.OAK_SAPLING, "Biome");
        put(Items.CHEST, "Block Entity Culling");
        put(Items.GLASS, "Block Outline");
        put(Items.ENCHANTED_BOOK, "Breach Swap");
        put(Items.STONE_BUTTON, "CPS Counter");
        put(Items.WRITABLE_BOOK, "Chat Search");
        put(Items.PAPER, "Chat Timestamps");
        put(Items.BOOK, "Chat Tools");
        put(Items.NETHER_STAR, "ClickGUI");
        put(Items.CLOCK, "Clock", "Time Changer");
        put(Items.BLAZE_ROD, "Combo Counter");
        put(Items.COMPASS, "Compass");
        put(Items.ENDER_PEARL, "Cooldown HUD");
        put(Items.CARTOGRAPHY_TABLE, "Coordinates");
        put(Items.ELYTRA, "Cosmetics");
        put(Items.TARGET, "Custom Crosshair");
        put(Items.REDSTONE, "Damage Indicators");
        put(Items.SUNFLOWER, "Day Counter");
        put(Items.AMETHYST_SHARD, "Discord Rich Presence");
        put(Items.ANVIL, "Durability Warning");
        put(Items.REPEATER, "Dynamic FPS");
        put(Items.FERMENTED_SPIDER_EYE, "Effect Reducer");
        put(Items.TINTED_GLASS, "Entity Culling");
        put(Items.ARMOR_STAND, "Entity Render Limiter");
        put(Items.GLOWSTONE_DUST, "FPS & Ping");
        put(Items.RECOVERY_COMPASS, "Fight Replay");
        put(Items.ENDER_EYE, "Freelook");
        put(Items.TORCH, "Fullbright");
        put(Items.ITEM_FRAME, "HUD Editor");
        put(Items.GLISTERING_MELON_SLICE, "Health Tags");
        put(Items.NETHER_WART, "Hit Color");
        put(Items.FIREWORK_STAR, "Hit Particles");
        put(Items.NOTE_BLOCK, "Hit Sounds");
        put(Items.GLASS_PANE, "Hitboxes");
        put(Items.HOPPER, "Item Counter");
        put(Items.APPLE, "Item Physics");
        put(Items.IRON_SWORD, "Item View");
        put(Items.OAK_BUTTON, "Keystrokes");
        put(Items.FIRE_CHARGE, "Kill Effects");
        put(Items.LANTERN, "Light Level");
        put(Items.CAMPFIRE, "Low Fire");
        put(Items.REDSTONE_TORCH, "Low Health Warning");
        put(Items.GRASS_BLOCK, "Main Menu", "Vanilla HUD");
        put(Items.MUSIC_DISC_CAT, "Media Play/Pause");
        put(Items.MUSIC_DISC_BLOCKS, "Media Next");
        put(Items.MUSIC_DISC_13, "Media Previous");
        put(Items.REDSTONE_BLOCK, "Memory Usage");
        put(Items.OBSERVER, "Menu FPS Cap");
        put(Items.KNOWLEDGE_BOOK, "Menu Optimizer");
        put(Items.FILLED_MAP, "Minimap");
        put(Items.BOOKSHELF, "Module List");
        put(Items.NAME_TAG, "Name Tag Culling");
        put(Items.CARVED_PUMPKIN, "Nick Hider");
        put(Items.GHAST_TEAR, "No Fog");
        put(Items.GOLDEN_HELMET, "No Hurt Cam");
        put(Items.BELL, "Notifications");
        put(Items.PLAYER_HEAD, "Own Nametag");
        put(Items.PAINTING, "Pack Display");
        put(Items.BLAZE_POWDER, "Particle Optimizer");
        put(Items.WIND_CHARGE, "Pearl Catch");
        put(Items.POTION, "Potion Effects");
        put(Items.LECTERN, "Profiles");
        put(Items.ARROW, "Reach Display");
        put(Items.SPECTRAL_ARROW, "Rotation");
        put(Items.COOKED_BEEF, "Saturation");
        put(Items.ECHO_SHARD, "Save Replay");
        put(Items.OAK_SIGN, "Scoreboard");
        put(Items.BEACON, "Server Address");
        put(Items.EXPERIENCE_BOTTLE, "Session Stats");
        put(Items.INK_SAC, "Shadow Culling");
        put(Items.SHIELD, "Shield Status");
        put(Items.SPIDER_EYE, "Snaplook");
        put(Items.FEATHER, "Speed");
        put(Items.JUKEBOX, "Spotify");
        put(Items.COMPARATOR, "Stopwatch", "Stopwatch Key");
        put(Items.TNT, "TNT Countdown");
        put(Items.SKELETON_SKULL, "Target HUD");
        put(Items.BREAD, "Toast Control");
        put(Items.SUGAR, "Toggle Sprint");
        put(Items.TOTEM_OF_UNDYING, "Totem Pop Counter");
        put(Items.LILAC, "Watermark");
        put(Items.LODESTONE, "Waypoint Manager", "Waypoints");
        put(Items.SNOWBALL, "Weather & Clouds");
        put(Items.WATER_BUCKET, "Weather Changer");
        put(Items.MAP, "World Map");
        put(Items.SPYGLASS, "Zoom");
    }

    private ModuleIcons() {
    }

    private static Item fallback(ModuleCategory category) {
        return switch (category) {
            case PVP -> Items.IRON_SWORD;
            case HUD -> Items.ITEM_FRAME;
            case VISUAL -> Items.ENDER_EYE;
            case PERFORMANCE -> Items.REDSTONE;
            default -> Items.BOOK;
        };
    }

    private static final Map<Item, ItemStack> ITEM_STACKS = new HashMap<>();

    public static Item item(Module module) {
        return ICONS.getOrDefault(module.getName(), fallback(module.getCategory()));
    }

    /**
     * Draws an item 16x16 at (x, y). Returns false (and draws nothing) when items cannot be drawn yet: on newer
     * Minecraft versions item data only exists once the game has fully loaded its registries.
     */
    public static boolean drawItem(GuiGraphicsExtractor ctx, Item item, int x, int y) {
        if (!componentsReady(item)) {
            return false;
        }
        ctx.item(ITEM_STACKS.computeIfAbsent(item, ItemStack::new), x, y);
        return true;
    }

    /** Always true on 1.21.11; on 26.x the port checks that the item's data is loaded. */
    private static boolean componentsReady(Item item) {
        return item.builtInRegistryHolder().areComponentsBound();
    }

    /** Draws the module's icon, 16x16 at (x, y); its first letter until items can be drawn. */
    public static void draw(GuiGraphicsExtractor ctx, Module module, int x, int y) {
        if (!drawItem(ctx, item(module), x, y)) {
            com.nightshade.render.Fonts.drawCentered(ctx, module.getName().substring(0, 1), x + 8, y + 4,
                    com.nightshade.render.Theme.accentLight(), false);
        }
    }
}
