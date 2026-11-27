package com.nightshade.cosmetic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The cosmetics shop. Only a rotating selection is for sale at any time: a daily rotation of Common and Rare items
 * (one of them the featured deal) and a weekly rotation of Legendary and Mythic items. Everything else has to wait
 * until it comes back. The rotation follows the UTC date, so everybody sees the same shop, and every item comes
 * round once per cycle. Items you already own can always be equipped.
 */
public final class Shop {
    public static final int DAILY_SLOTS = 6;
    public static final int WEEKLY_SLOTS = 2;
    public static final int FEATURED_DISCOUNT = 40;
    public static final int FLASH_DISCOUNT = 50;
    public static final int BUNDLE_DISCOUNT = 30;
    /** A flash deal lasts this many hours. */
    public static final int FLASH_HOURS = 4;
    private static final int[] DAILY_DISCOUNTS = {0, 0, 10, 15, 20, 25};
    private static final int[] WEEKLY_DISCOUNTS = {0, 10, 20};

    /** Test hook: pretend it is this day (days since 1970-01-01, UTC). */
    public static Long dayOverride;
    /** Test hook: pretend it is this flash-deal slot of the day (0-5). */
    public static Integer flashSlotOverride;

    private static long cachedDay = Long.MIN_VALUE;
    private static List<Catalog.Item> daily = List.of();
    private static List<Catalog.Item> weekly = List.of();

    private Shop() {
    }

    // ------------------------------------------------------------------ time

    public static long today() {
        return dayOverride != null ? dayOverride : LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** Weeks start on Monday (day 0 of the epoch was a Thursday). */
    private static long week(long day) {
        return Math.floorDiv(day - 4, 7);
    }

    /** Milliseconds until the daily items change. */
    public static long millisToDailyReset() {
        long next = (today() + 1) * 86_400_000L;
        return Math.max(0, next - Instant.now().toEpochMilli());
    }

    /** Milliseconds until the weekly items change. */
    public static long millisToWeeklyReset() {
        long nextMonday = (week(today()) + 1) * 7 + 4;
        return Math.max(0, nextMonday * 86_400_000L - Instant.now().toEpochMilli());
    }

    /** "5h 12m" or "3d 4h". */
    public static String format(long millis) {
        long mins = millis / 60_000;
        if (mins >= 24 * 60) {
            return (mins / 1440) + "d " + (mins % 1440 / 60) + "h";
        }
        return (mins / 60) + "h " + (mins % 60) + "m";
    }

    // ------------------------------------------------------------------ rotation

    private static void refresh() {
        long day = today();
        if (day == cachedDay) {
            return;
        }
        cachedDay = day;
        daily = dailyFor(day);
        weekly = weeklyFor(day);
    }

    private static List<Catalog.Item> dailyFor(long day) {
        return slice(pool(false), DAILY_SLOTS, day, 0x5EEDL);
    }

    private static List<Catalog.Item> weeklyFor(long day) {
        return slice(pool(true), WEEKLY_SLOTS, week(day), 0xC0FFEEL);
    }

    private static List<Catalog.Item> pool(boolean rare) {
        List<Catalog.Item> out = new ArrayList<>();
        for (Catalog.Item i : Catalog.all()) {
            if (i.free()) {
                continue;
            }
            boolean high = i.tier == Catalog.Tier.LEGENDARY || i.tier == Catalog.Tier.MYTHIC;
            if (high == rare) {
                out.add(i);
            }
        }
        out.sort(Comparator.comparing(i -> i.id)); // stable order, so the shuffle is the same for everyone
        return out;
    }

    /**
     * The items for period {@code n}: the pool is shuffled once per cycle and cut into consecutive slices, so every
     * item appears exactly once per cycle and nothing repeats until the cycle is over.
     */
    private static List<Catalog.Item> slice(List<Catalog.Item> pool, int slots, long n, long salt) {
        if (pool.isEmpty()) {
            return List.of();
        }
        int perCycle = Math.max(1, (pool.size() + slots - 1) / slots);
        long cycle = Math.floorDiv(n, perCycle);
        int part = (int) Math.floorMod(n, perCycle);
        List<Catalog.Item> order = new ArrayList<>(pool);
        Collections.shuffle(order, new Random(cycle * 0x9E3779B97F4A7C15L ^ salt));
        List<Catalog.Item> out = new ArrayList<>();
        for (int k = 0; k < slots && k < order.size(); k++) {
            out.add(order.get((part * slots + k) % order.size()));
        }
        return out;
    }

    /** Today's daily items; the first one is the featured deal. */
    public static List<Catalog.Item> daily() {
        refresh();
        return daily;
    }

    /** This week's Legendary and Mythic picks. */
    public static List<Catalog.Item> weekly() {
        refresh();
        return weekly;
    }

    /** Everything for sale right now: the featured deal first, then the daily items, then the weekly picks. */
    public static List<Catalog.Item> listing() {
        List<Catalog.Item> out = new ArrayList<>(limitedAndNew());
        out.addAll(daily());
        for (Catalog.Item i : weekly()) {
            if (!out.contains(i)) {
                out.add(i);
            }
        }
        return out;
    }

    public static Catalog.Item featured() {
        List<Catalog.Item> d = daily();
        return d.isEmpty() ? null : d.get(0);
    }

    public static boolean available(Catalog.Item i) {
        if (i != null && i.remote) {
            return i.free() || !i.limited() || i.limitedOn(now());
        }
        return i != null && (i.free() || daily().contains(i) || weekly().contains(i) || i == flash());
    }

    // ------------------------------------------------------------------ limited and new (downloaded items)

    /** Test hook: pretend it is this time (ms since 1970). */
    public static Long nowOverride;

    public static long now() {
        return nowOverride != null ? nowOverride : System.currentTimeMillis();
    }

    /** Downloaded items for sale right now: limited ones still in their window, then the rest. Never part of the rotation. */
    public static List<Catalog.Item> limitedAndNew() {
        long now = now();
        List<Catalog.Item> out = new ArrayList<>();
        for (Catalog.Item i : Catalog.remote()) {
            if (i.limitedOn(now)) {
                out.add(i);
            }
        }
        out.sort(Comparator.comparingLong(i -> i.limitedUntil)); // ending soonest first
        for (Catalog.Item i : Catalog.remote()) {
            if (!i.limited() && !i.free()) {
                out.add(i);
            }
        }
        return out;
    }

    /** How long a limited item is still for sale (0 when it has ended or is not limited). */
    public static long millisLeft(Catalog.Item i) {
        return i == null || !i.limited() ? 0 : Math.max(0, i.limitedUntil - now());
    }

    // ------------------------------------------------------------------ flash deal

    private static int flashSlot() {
        if (flashSlotOverride != null) {
            return flashSlotOverride;
        }
        if (dayOverride != null) {
            return 0;
        }
        return java.time.LocalTime.now(ZoneOffset.UTC).getHour() / FLASH_HOURS;
    }

    /** An extra item at {@value #FLASH_DISCOUNT}% off for a few hours; never one of today's or this week's items. */
    public static Catalog.Item flash() {
        List<Catalog.Item> all = new ArrayList<>();
        for (Catalog.Item i : Catalog.all()) {
            if (!i.free() && i.tier != Catalog.Tier.MYTHIC && !daily().contains(i) && !weekly().contains(i)) {
                all.add(i);
            }
        }
        if (all.isEmpty()) {
            return null;
        }
        all.sort(Comparator.comparing(i -> i.id));
        long slot = today() * (24 / FLASH_HOURS) + flashSlot();
        return all.get((int) Math.floorMod(mix(slot, "flash"), all.size()));
    }

    public static long millisToFlashEnd() {
        long period = FLASH_HOURS * 3_600_000L;
        long now = Instant.now().toEpochMilli();
        return period - Math.floorMod(now, period);
    }

    // ------------------------------------------------------------------ weekly bundle

    /** Three items from different categories, sold together at {@value #BUNDLE_DISCOUNT}% off. Changes every Monday. */
    public static List<Catalog.Item> bundle() {
        long w = week(today());
        Random r = new Random(w * 0x2545F4914F6CDD1DL ^ 0xB0D1EL);
        List<Catalog.Item> out = new ArrayList<>();
        List<Catalog.Category> cats = new ArrayList<>(List.of(Catalog.Category.values()));
        cats.sort(Comparator.comparing(Enum::name));
        Collections.shuffle(cats, r);
        for (Catalog.Category c : cats) {
            if (out.size() == 3) {
                break;
            }
            List<Catalog.Item> pick = new ArrayList<>();
            for (Catalog.Item i : Catalog.in(c)) {
                if (!i.free() && i.tier != Catalog.Tier.MYTHIC && i.classicCape == null) {
                    pick.add(i);
                }
            }
            if (!pick.isEmpty()) {
                pick.sort(Comparator.comparing(i -> i.id));
                out.add(pick.get(r.nextInt(pick.size())));
            }
        }
        return out;
    }

    /** What the bundle costs you: the items you don't own yet, {@value #BUNDLE_DISCOUNT}% off, rounded to 10. */
    public static int bundlePrice(java.util.function.Predicate<Catalog.Item> owned) {
        int sum = 0;
        for (Catalog.Item i : bundle()) {
            if (!owned.test(i)) {
                sum += i.price;
            }
        }
        return Math.round(sum * (100 - BUNDLE_DISCOUNT) / 1000f) * 10;
    }

    /** The bundle's full price (no discount) for the items you don't own yet. */
    public static int bundleFullPrice(java.util.function.Predicate<Catalog.Item> owned) {
        int sum = 0;
        for (Catalog.Item i : bundle()) {
            if (!owned.test(i)) {
                sum += i.price;
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------ looking ahead

    /** Days until an item is next in the daily or weekly shop (0 = today), or -1 if not within a year. */
    public static int daysUntilBack(Catalog.Item i) {
        if (i.remote) {
            return -1;
        }
        long day = today();
        if (day != backDay) { // work the whole year out once a day, not for every card every frame
            backDay = day;
            BACK.clear();
            for (int d = 366; d >= 0; d--) {
                for (Catalog.Item x : dailyFor(day + d)) {
                    BACK.put(x.id, d);
                }
                for (Catalog.Item x : weeklyFor(day + d)) {
                    BACK.put(x.id, d);
                }
            }
        }
        return BACK.getOrDefault(i.id, -1);
    }

    private static final java.util.Map<String, Integer> BACK = new java.util.HashMap<>();
    private static long backDay = Long.MIN_VALUE;

    /** Tomorrow's featured deal (shown as a teaser: just its rarity and kind). */
    public static Catalog.Item tomorrowsFeatured() {
        List<Catalog.Item> d = dailyFor(today() + 1);
        return d.isEmpty() ? null : d.get(0);
    }

    public static boolean weeklyPick(Catalog.Item i) {
        return weekly().contains(i);
    }

    /** Percent off right now (0 when not discounted or not in the shop). */
    public static int discount(Catalog.Item i) {
        if (i == null || i.free() || i.remote) {
            return 0; // downloaded items always cost their own (special) price
        }
        if (i == featured()) {
            return FEATURED_DISCOUNT;
        }
        if (i == flash()) {
            return FLASH_DISCOUNT;
        }
        long day = today();
        if (daily().contains(i)) {
            return DAILY_DISCOUNTS[(int) Math.floorMod(mix(day, i.id), DAILY_DISCOUNTS.length)];
        }
        if (weekly().contains(i)) {
            return WEEKLY_DISCOUNTS[(int) Math.floorMod(mix(week(day), i.id), WEEKLY_DISCOUNTS.length)];
        }
        return 0;
    }

    /** What the item costs today, rounded to 10 points. */
    public static int price(Catalog.Item i) {
        int d = discount(i);
        return d == 0 ? i.price : Math.round(i.price * (100 - d) / 1000f) * 10;
    }

    private static long mix(long n, String id) {
        long h = n * 0x9E3779B97F4A7C15L + id.hashCode();
        h ^= h >>> 29;
        return h * 0xBF58476D1CE4E5B9L >>> 33;
    }
}
