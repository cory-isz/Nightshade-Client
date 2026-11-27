package com.nightshade.cosmetic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every cosmetic, its tier and price. Models are built lazily the first time they are drawn. */
public final class Catalog {
    public enum Category {
        WINGS("Wings"), CAPES("Capes"), HATS("Hats"), AURAS("Auras"), COMPANIONS("Pets");

        public final String title;

        Category(String title) {
            this.title = title;
        }
    }

    public enum Tier {
        FREE("Free", 0xFFB7ACD6), COMMON("Common", 0xFF9FD8B0), RARE("Rare", 0xFF8EC5FF), LEGENDARY("Legendary", 0xFFFFC857), MYTHIC("Mythic", 0xFFFF5FA2);

        public final String title;
        public final int color;

        Tier(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    public static final class Item {
        public final String id;
        public final String name;
        public final Category category;
        public final Tier tier;
        public final int price;
        public final String description;
        /** For classic capes: the texture name used by the Cosmetics module. */
        public final String classicCape;
        /** Downloaded from the cosmetics service instead of built into this version. */
        public boolean remote;
        /** Limited items can only be bought between these times (ms since 1970, UTC); 0 = no limit. */
        public long limitedFrom;
        public long limitedUntil;
        /** Shown on limited items, e.g. "Winter 2026". */
        public String limitedTag;
        /** When a downloaded item was added (ms), for the "New" badge; 0 = unknown. */
        public long added;

        Item(String id, String name, Category category, Tier tier, int price, String description, String classicCape) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.tier = tier;
            this.price = price;
            this.description = description;
            this.classicCape = classicCape;
        }

        public boolean free() {
            return price == 0;
        }

        public boolean limited() {
            return limitedUntil > 0;
        }

        /** A limited item that can still be bought at {@code now}. */
        public boolean limitedOn(long now) {
            return limited() && now >= limitedFrom && now < limitedUntil;
        }
    }

    /** Makes a downloaded item (see {@link RemoteContent}). */
    public static Item remoteItem(String id, String name, Category category, Tier tier, int price, String description) {
        Item i = new Item(id, name, category, tier, price, description, null);
        i.remote = true;
        return i;
    }

    /** Downloaded items. They are kept apart from the built-in list so the shop rotation never changes when new ones arrive. */
    private static volatile List<Item> remoteItems = List.of();

    static void setRemote(List<Item> items) {
        remoteItems = List.copyOf(items);
    }

    public static List<Item> remote() {
        return remoteItems;
    }

    /** Every item: built-in, then downloaded (not for the shop rotation, which only uses {@link #all()}). */
    public static List<Item> everything() {
        List<Item> out = new ArrayList<>(ITEMS);
        out.addAll(remoteItems);
        return out;
    }

    /** Everything the Wardrobe lists in a category: built-in items, then downloaded ones. */
    public static List<Item> listed(Category c) {
        List<Item> out = in(c);
        for (Item i : remoteItems) {
            if (i.category == c) {
                out.add(i);
            }
        }
        return out;
    }

    private static final List<Item> ITEMS = new ArrayList<>();

    static {
        wing("moonlit", "Moonlit Wings", Tier.COMMON, 300, "Violet feathers with glowing specks");
        wing("frost", "Frost Wings", Tier.COMMON, 300, "Ice-blue feathers tipped with snow");
        wing("raven", "Raven Wings", Tier.COMMON, 300, "Sleek black feathers with a blue sheen");
        wing("verdant", "Verdant Wings", Tier.COMMON, 300, "A fan of leaves with raised veins");
        wing("dusk", "Dusk Wings", Tier.COMMON, 300, "Feathers fading from violet to sunset");
        wing("owl", "Owl Wings", Tier.COMMON, 300, "Soft brown feathers with pale tips");
        wing("angel", "Angel Wings", Tier.RARE, 900, "Soft white feathers with a gold edge");
        wing("phoenix", "Phoenix Wings", Tier.RARE, 900, "Fiery feathers with glowing tips");
        wing("bee", "Bee Wings", Tier.RARE, 900, "Clear wings with bold dark veins");
        wing("fairy", "Fairy Wings", Tier.RARE, 900, "Long glassy wings with a glowing rim");
        wing("monarch", "Monarch Wings", Tier.RARE, 900, "Orange butterfly wings with white spots");
        wing("morpho", "Morpho Wings", Tier.RARE, 900, "Bright blue butterfly wings that shimmer");
        wing("luna", "Luna Wings", Tier.RARE, 900, "Pale green moth wings with long tails");
        wing("sakura", "Sakura Wings", Tier.RARE, 900, "Wings made of cherry blossom petals");
        wing("crystal", "Crystal Wings", Tier.RARE, 900, "Faceted shards that catch the light");
        wing("circuit", "Circuit Wings", Tier.RARE, 900, "Armoured plates with glowing lines");
        wing("void", "Void Wings", Tier.LEGENDARY, 2500, "Dark membrane wings with a violet glow");
        wing("dragon", "Dragon Wings", Tier.LEGENDARY, 2500, "Scaled membrane wings with horn spikes");
        wing("demon", "Demon Wings", Tier.LEGENDARY, 2500, "Blood-red membrane wings with burning edges");
        wing("fallen", "Fallen Wings", Tier.LEGENDARY, 2500, "Black feathers with crimson tips");
        wing("skeletal", "Skeletal Wings", Tier.LEGENDARY, 2500, "Bare bones with glowing joints");
        wing("storm", "Storm Wings", Tier.LEGENDARY, 2500, "Thundercloud membranes with crackling edges");
        wing("seraph", "Seraph Wings", Tier.MYTHIC, 6000, "Two pairs of gold-tipped white wings");
        wing("starlight", "Starlight Wings", Tier.MYTHIC, 6000, "Two pairs of night-sky feathers full of stars");
        wing("nebula", "Nebula Wings", Tier.MYTHIC, 6000, "Butterfly wings swirling with a galaxy");

        cape("cape_moon", "Nightshade Cape", Tier.FREE, 0, "The Nightshade moon, raised in 3D");
        cape("cape_stars", "Starfall Cape", Tier.COMMON, 300, "Glowing stars on a midnight cape");
        cape("cape_dusk", "Dusk Cape", Tier.COMMON, 300, "Violet fading into sunset orange");
        cape("cape_tattered", "Tattered Cape", Tier.RARE, 900, "A worn cape with a ragged edge");
        cape("cape_royal", "Royal Cape", Tier.RARE, 900, "Crimson and gold with a fur collar");
        cape("cape_checker", "Checker Cape", Tier.COMMON, 300, "A bold checkerboard with a violet trim");
        cape("cape_aurora", "Aurora Cape", Tier.RARE, 900, "Glowing northern lights over a dark sky");
        cape("cape_ember", "Ember Cape", Tier.LEGENDARY, 2500, "Glowing flames licking up from the hem");
        cape("cape_galaxy", "Galaxy Cape", Tier.LEGENDARY, 2500, "A swirling galaxy with glowing stars");
        cape("cape_sakura", "Sakura Cape", Tier.RARE, 900, "Cherry blossoms drifting over pale pink");
        cape("cape_storm", "Storm Cape", Tier.RARE, 900, "Dark clouds split by a glowing bolt");
        for (String c : new String[]{"Nightshade", "Aurora", "Galaxy", "Crimson", "Ocean", "Mono"}) {
            ITEMS.add(new Item("classic_" + c.toLowerCase(java.util.Locale.ROOT), c + " Classic", Category.CAPES, Tier.FREE, 0, "A flat classic cape (also skins your elytra)", c));
        }

        hat("cat_ears", "Cat Ears", Tier.COMMON, 300, "Soft ears with pink insides");
        hat("fox_ears", "Fox Ears", Tier.COMMON, 300, "Orange ears with pale tips");
        hat("bunny_ears", "Bunny Ears", Tier.COMMON, 300, "One up, one flopped over");
        hat("beanie", "Beanie", Tier.COMMON, 300, "Striped knit beanie with a pom-pom");
        hat("party_hat", "Party Hat", Tier.COMMON, 300, "Striped cone with a glowing pom");
        hat("headphones", "Headphones", Tier.COMMON, 300, "Over-ear headphones with lit cups");
        hat("flower_crown", "Flower Crown", Tier.COMMON, 300, "A vine ring of little flowers");
        hat("top_hat", "Top Hat", Tier.COMMON, 300, "Tall black hat with a red band");
        hat("cowboy", "Cowboy Hat", Tier.COMMON, 300, "Leather hat with a curled brim");
        hat("chef", "Chef Hat", Tier.COMMON, 300, "A tall, puffy toque");
        hat("halo", "Halo", Tier.RARE, 900, "A glowing ring floating above you");
        hat("wizard", "Wizard Hat", Tier.RARE, 900, "Bent pointed hat with glowing stars");
        hat("witch", "Witch Hat", Tier.RARE, 900, "Wide brim, crooked tip, gold buckle");
        hat("devil_horns", "Devil Horns", Tier.RARE, 900, "Glossy red horns that curl forward");
        hat("ram_horns", "Ram Horns", Tier.RARE, 900, "Ridged horns that spiral round your ears");
        hat("antlers", "Antlers", Tier.RARE, 900, "Branching antlers");
        hat("mushroom", "Mushroom Cap", Tier.RARE, 900, "A spotted red toadstool cap");
        hat("santa", "Santa Hat", Tier.RARE, 900, "Floppy red hat with a fur trim");
        hat("propeller", "Propeller Cap", Tier.RARE, 900, "Its propeller really spins");
        hat("viking", "Viking Helmet", Tier.RARE, 900, "Steel helmet with horns and rivets");
        hat("grad_cap", "Graduation Cap", Tier.RARE, 900, "Square cap with a swinging gold tassel");
        hat("frog", "Frog Hat", Tier.RARE, 900, "A cheerful frog with big eyes");
        hat("crown", "Crown", Tier.LEGENDARY, 2500, "Gold crown with glowing gems");
        hat("planet", "Little Planet", Tier.MYTHIC, 6000, "A ringed planet orbiting your head");
        hat("star_crown", "Crown of Stars", Tier.MYTHIC, 6000, "A glowing circlet with stars circling it");
        hat("pirate", "Pirate Hat", Tier.RARE, 900, "A black tricorn with a skull badge");
        hat("bow", "Ribbon Bow", Tier.COMMON, 300, "A big pink bow on the side of your head");
        hat("headband", "Ninja Headband", Tier.COMMON, 300, "A red band with long trailing ties");

        aura("fireflies", "Fireflies", Tier.COMMON, 300, "Little lights blinking around you");
        aura("stardust", "Stardust", Tier.RARE, 900, "Twinkling stars circling you");
        aura("hearts", "Hearts", Tier.RARE, 900, "Pink hearts floating up around you");
        aura("petals", "Petals", Tier.RARE, 900, "Pink petals drifting down around you");
        aura("frost_motes", "Frost Motes", Tier.RARE, 900, "Slow, glowing flakes of ice");
        aura("embers", "Embers", Tier.LEGENDARY, 2500, "Sparks rising from your feet");
        aura("rainbow", "Rainbow Spiral", Tier.LEGENDARY, 2500, "A spiral of colours climbing round you");
        aura("void_rift", "Void Rift", Tier.MYTHIC, 6000, "Violet sparks pulled into you from all around");
        aura("bubbles", "Bubbles", Tier.COMMON, 300, "Soap bubbles bobbing up around you");
        aura("notes", "Music Notes", Tier.RARE, 900, "Colourful notes bouncing round your head");

        companion("pet_wisp", "Wisp", Tier.COMMON, 300, "A glowing orb with circling sparks");
        companion("pet_slime", "Slime", Tier.COMMON, 300, "A happy little cube that bounces beside you");
        companion("pet_ghost", "Ghost", Tier.RARE, 900, "A friendly ghost that floats along");
        companion("pet_bee", "Bee", Tier.RARE, 900, "A fuzzy bee with buzzing wings");
        companion("pet_bat", "Bat", Tier.RARE, 900, "A bat with flapping wings and glowing eyes");
        companion("pet_book", "Spell Book", Tier.LEGENDARY, 2500, "An open book with runes circling it");
        companion("pet_crystal", "Floating Crystal", Tier.LEGENDARY, 2500, "A spinning crystal with orbiting shards");
        companion("pet_dragon", "Baby Dragon", Tier.MYTHIC, 6000, "A little dragon that flaps along beside you");
    }

    private static void wing(String id, String name, Tier tier, int price, String desc) {
        ITEMS.add(new Item(id, name, Category.WINGS, tier, price, desc, null));
    }

    private static void cape(String id, String name, Tier tier, int price, String desc) {
        ITEMS.add(new Item(id, name, Category.CAPES, tier, price, desc, null));
    }

    private static void hat(String id, String name, Tier tier, int price, String desc) {
        ITEMS.add(new Item(id, name, Category.HATS, tier, price, desc, null));
    }

    private static void aura(String id, String name, Tier tier, int price, String desc) {
        ITEMS.add(new Item(id, name, Category.AURAS, tier, price, desc, null));
    }

    private static void companion(String id, String name, Tier tier, int price, String desc) {
        ITEMS.add(new Item(id, name, Category.COMPANIONS, tier, price, desc, null));
    }

    private Catalog() {
    }

    public static List<Item> all() {
        return Collections.unmodifiableList(ITEMS);
    }

    public static List<Item> in(Category c) {
        List<Item> out = new ArrayList<>();
        for (Item i : ITEMS) {
            if (i.category == c) {
                out.add(i);
            }
        }
        return out;
    }

    /** A built-in item (never a downloaded one). */
    public static Item byIdBuiltIn(String id) {
        for (Item i : ITEMS) {
            if (i.id.equals(id)) {
                return i;
            }
        }
        return null;
    }

    public static Item byId(String id) {
        if (id == null) {
            return null;
        }
        for (Item i : ITEMS) {
            if (i.id.equals(id)) {
                return i;
            }
        }
        for (Item i : remoteItems) {
            if (i.id.equals(id)) {
                return i;
            }
        }
        return null;
    }
}
