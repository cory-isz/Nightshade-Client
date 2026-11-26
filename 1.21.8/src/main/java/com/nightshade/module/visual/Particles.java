package com.nightshade.module.visual;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.module.setting.TextSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Choose which particles you see and how big they are: every particle in the game belongs to a group (Combat, Totem
 * Pops, Explosions...), and each group can be hidden or resized. Single particles can also be hidden by name.
 * Only changes what you see - nothing is sent to the server.
 */
public final class Particles extends Module {
    /** The particle groups, each with the vanilla particle ids that belong to it. */
    public enum Group {
        COMBAT("Combat", "Crits, sharpness sparks, sweeps and damage hearts",
                "crit", "enchanted_hit", "sweep_attack", "damage_indicator"),
        TOTEM("Totem Pops", "The green and yellow burst when a totem saves someone",
                "totem_of_undying"),
        EXPLOSIONS("Explosions", "Explosions, wind charge gusts and sonic booms",
                "explosion", "explosion_emitter", "flash", "gust", "small_gust", "gust_emitter_large", "gust_emitter_small", "sonic_boom"),
        POTIONS("Potion Effects", "Swirls from potion effects, splash potions and omens",
                "effect", "entity_effect", "instant_effect", "witch", "dragon_breath", "raid_omen", "trial_omen", "ominous_spawning"),
        FIREWORKS("Fireworks", "Firework sparks and rocket trails",
                "firework"),
        BLOCKS("Blocks & Items", "Block breaking, falling dust and item crumbs (eating, snowballs...)",
                "block", "block_marker", "falling_dust", "dust_pillar", "dust_plume", "block_crumble", "item", "item_slime", "item_cobweb", "item_snowball"),
        FIRE("Fire & Smoke", "Flames, smoke, campfire smoke, lava pops, ash and noxious gas",
                "flame", "small_flame", "soul_fire_flame", "copper_fire_flame", "smoke", "large_smoke", "white_smoke",
                "campfire_cosy_smoke", "campfire_signal_smoke", "lava", "ash", "white_ash", "noxious_gas", "noxious_gas_cloud"),
        WATER("Water", "Bubbles, splashes, rain, fishing, geysers and squid ink",
                "bubble", "bubble_pop", "bubble_column_up", "current_down", "splash", "rain", "fishing", "underwater", "nautilus",
                "dolphin", "squid_ink", "glow_squid_ink", "glow", "sulfur_bubbles", "geyser", "geyser_base", "geyser_poof", "geyser_plume"),
        DRIPS("Drips", "Dripping water, lava, honey and obsidian tears",
                "dripping_water", "falling_water", "dripping_lava", "falling_lava", "landing_lava", "dripping_dripstone_water",
                "falling_dripstone_water", "dripping_dripstone_lava", "falling_dripstone_lava", "dripping_honey", "falling_honey",
                "landing_honey", "falling_nectar", "dripping_obsidian_tear", "falling_obsidian_tear", "landing_obsidian_tear"),
        MAGIC("Magic", "Enchanting table letters, portals, end rods and sculk",
                "enchant", "portal", "reverse_portal", "end_rod", "soul", "sculk_soul", "sculk_charge", "sculk_charge_pop", "shriek",
                "vibration", "trail", "electric_spark", "wax_on", "wax_off", "scrape"),
        NATURE("Nature", "Falling leaves, spores, snowflakes, fireflies and composting",
                "cherry_leaves", "pale_oak_leaves", "tinted_leaves", "red_poplar_leaves", "orange_poplar_leaves", "yellow_poplar_leaves",
                "falling_spore_blossom", "spore_blossom_air", "crimson_spore", "warped_spore", "mycelium", "snowflake", "firefly",
                "composter", "egg_crack"),
        MOBS("Mobs", "Hearts, villager reactions, notes, mob death puffs and spawners",
                "heart", "angry_villager", "happy_villager", "note", "poof", "cloud", "sneeze", "spit", "elder_guardian", "infested",
                "trial_spawner_detection", "trial_spawner_detection_ominous", "vault_connection", "pause_mob_growth", "reset_mob_growth", "sulfur_cube_goo"),
        DUST("Redstone & Dust", "Redstone dust and coloured dust particles",
                "dust", "dust_color_transition"),
        OTHER("Other", "Every particle not in another group (including ones added by other mods)");

        public final String label;
        public final String description;
        final String[] ids;

        Group(String label, String description, String... ids) {
            this.label = label;
            this.description = description;
            this.ids = ids;
        }
    }

    private static volatile Particles active;
    private static final Map<String, Group> GROUP_OF = new HashMap<>();

    static {
        for (Group g : Group.values()) {
            for (String id : g.ids) {
                GROUP_OF.put(id, g);
            }
        }
    }

    private final Map<Group, BooleanSetting> show = new EnumMap<>(Group.class);
    private final Map<Group, NumberSetting> size = new EnumMap<>(Group.class);
    private final NumberSetting allSizes;
    private final TextSetting hideList;
    private Set<String> hidden = Set.of();
    private String hiddenSource = "";

    public Particles() {
        super("Particles", "Pick which particles you see and how big they are", ModuleCategory.VISUAL);
        allSizes = add(new NumberSetting("All Sizes", "Makes every particle bigger or smaller (multiplies the group sizes)", 1.0, 0.25, 3.0, 0.05, "x"));
        for (Group g : Group.values()) {
            BooleanSetting s = add(new BooleanSetting("Show " + g.label, g.description, true));
            show.put(g, s);
            size.put(g, add(new NumberSetting(g.label + " Size", "How big " + g.label.toLowerCase(Locale.ROOT) + " particles are", 1.0, 0.25, 3.0, 0.05, "x"))
                    .visibleWhen(s::get));
        }
        hideList = add(new TextSetting("Hide These", "Hide single particles by name, separated by commas (e.g. heart, note, flame)", "", 400));
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static Particles instance() {
        return active;
    }

    /** Which group a particle type belongs to. */
    public static Group groupOf(ParticleType<?> type) {
        ResourceLocation id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
        if (id == null || !id.getNamespace().equals("minecraft")) {
            return Group.OTHER;
        }
        return GROUP_OF.getOrDefault(id.getPath(), Group.OTHER);
    }

    private Set<String> hiddenNames() {
        String src = hideList.get();
        if (!src.equals(hiddenSource)) {
            Set<String> out = new HashSet<>();
            for (String part : src.split("[,;]")) {
                String name = part.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (name.startsWith("minecraft:")) {
                    name = name.substring("minecraft:".length());
                }
                if (!name.isEmpty()) {
                    out.add(name);
                }
            }
            hidden = out;
            hiddenSource = src;
        }
        return hidden;
    }

    /** Hook from ParticleManagerMixin: true when this particle type should not be spawned. */
    public static boolean hides(ParticleType<?> type) {
        Particles m = active;
        if (m == null) {
            return false;
        }
        boolean hide = !m.show.get(groupOf(type)).get();
        if (!hide && !m.hideList.get().isBlank()) {
            ResourceLocation id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            hide = id != null && (m.hiddenNames().contains(id.getPath()) || m.hiddenNames().contains(id.toString()));
        }
        if (hide) {
            EffectProbe.hit("Particles");
        }
        return hide;
    }

    /** Hook for block-breaking particles, which skip the normal spawn path. */
    public static boolean hidesGroup(Group group) {
        Particles m = active;
        boolean hide = m != null && !m.show.get(group).get();
        if (hide) {
            EffectProbe.hit("Particles");
        }
        return hide;
    }

    /** Size multiplier for particles of this group (1 = normal). */
    public static float scale(Group group) {
        Particles m = active;
        if (m == null) {
            return 1f;
        }
        float s = (float) (m.allSizes.get() * m.size.get(group).get());
        if (s != 1f) {
            EffectProbe.hit("Particles");
        }
        return s;
    }

    /** Hook from SingleQuadParticleMixin: the size a particle is drawn at. */
    public static float drawSize(Object particle, float size) {
        float s = ((com.nightshade.render.ParticleScaled) particle).nightshade$scale();
        if (s == 1f) {
            return size;
        }
        drawnResized++;
        return size * s;
    }

    /** How many resized particles have been drawn (for the self-test). */
    public static long drawnResized;

    // ------------------------------------------------------------------ for the self-test

    public BooleanSetting showSettingForTest(Group g) {
        return show.get(g);
    }

    public NumberSetting sizeSettingForTest(Group g) {
        return size.get(g);
    }

    public TextSetting hideListForTest() {
        return hideList;
    }
}
