package com.nightshade.module.pvp;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.AttackEntityEvent;
import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.HurtByEvent;
import com.nightshade.event.events.MouseButtonEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.gui.menu.NsMenuScreen;
import com.nightshade.gui.menu.SessionRecapScreen;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ActionSetting;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.util.Screens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Session Recap: how your fights went. It counts your kills, deaths, accuracy (hits landed for every swing), critical
 * hits, best combo and hit trade, keeps a list of your recent fights, shows a short summary when a fight ends and
 * opens the full recap when you leave a server or world. It only reads what happens; it never does anything for you.
 */
public final class SessionRecap extends Module {
    /** A fight ends after this long without either of you landing a hit. */
    private static final long FIGHT_GAP_MS = 6000;
    private static final int MAX_FIGHTS = 20;

    public enum Result { WON, LOST, ENDED }

    /** One fight: who against, how it went and the numbers. */
    public static final class Fight {
        public String opponent = "?";
        public Result result = Result.ENDED;
        public int landed;
        public int taken;
        public int swings;
        public int crits;
        public int bestCombo;
        public long start;
        public long end;
        private final Map<String, Integer> hitsOn = new HashMap<>();

        public int accuracy() {
            return swings == 0 ? 0 : Math.min(100, Math.round(landed * 100f / swings));
        }

        public long seconds() {
            return Math.max(1, (end - start) / 1000);
        }
    }

    private final BooleanSetting afterFights = add(new BooleanSetting("After Fights", "Show a short summary when a fight ends", true));
    private final BooleanSetting whenLeaving = add(new BooleanSetting("When Leaving", "Open the recap when you leave a server or world (if you fought)", true));
    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", "Only count fights against players", true));
    private final ActionSetting open = add(new ActionSetting("Recap", "Open your session recap now", "Open", this::openNow));

    // session totals
    private long sessionStart = System.currentTimeMillis();
    private String sessionKey;
    private String sessionName = "";
    private int kills;
    private int deaths;
    private int landed;
    private int taken;
    private int swings;
    private int crits;
    private int bestCombo;
    private final List<Fight> fights = new ArrayList<>();

    // live state
    private Fight current;
    private long lastCombat;
    private int combo;
    private boolean wasDead;
    private long lastSwing;
    private final Deque<Long> recentSwings = new ArrayDeque<>();
    private final Map<LivingEntity, Integer> recentHits = new java.util.IdentityHashMap<>();
    private boolean pendingOpen;

    public SessionRecap() {
        super("Session Recap", "Kills, deaths, accuracy, crits and your recent fights, after a fight and when you leave", ModuleCategory.PVP);
        listen(MouseButtonEvent.class, e -> {
            if (e.button() == 0 && e.action() == 1 && Screens.current() == null && mc.player != null) {
                swing();
            }
        });
        listen(AttackEntityEvent.class, e -> {
            if (System.currentTimeMillis() - lastSwing > 100) {
                swing(); // attacks that did not come from a mouse click (other bindings) still count as a swing
            }
            boolean counts = e.target() instanceof LivingEntity && (!playersOnly.get() || e.target() instanceof Player);
            if (counts && critNow()) {
                if (current != null) {
                    current.crits++;
                    crits++;
                } else {
                    pendingCrits++; // the hit that starts a fight is confirmed a moment later
                }
            }
        });
        listen(HitLandedEvent.class, e -> {
            LivingEntity target = e.target();
            if (playersOnly.get() && !(target instanceof Player)) {
                return;
            }
            Fight f = fight();
            f.landed++;
            landed++;
            f.hitsOn.merge(target.getName().getString(), 1, Integer::sum);
            recentHits.put(target, 60);
            combo++;
            f.bestCombo = Math.max(f.bestCombo, combo);
            bestCombo = Math.max(bestCombo, combo);
        });
        listen(HurtByEvent.class, e -> {
            Entity attacker = e.attacker();
            if (!(attacker instanceof LivingEntity) || (playersOnly.get() && !(attacker instanceof Player))) {
                return;
            }
            Fight f = fight();
            f.taken++;
            taken++;
            f.hitsOn.merge(attacker.getName().getString(), 1, Integer::sum);
            combo = 0;
        });
        listen(TickEvent.Post.class, e -> tick());
        listen(WorldChangeEvent.class, e -> {
            if (e.world() == null) {
                onLeave();
                return;
            }
            pendingOpen = false; // joined somewhere else straight away
            String key = mc.getCurrentServer() != null ? "server:" + mc.getCurrentServer().ip
                    : mc.getSingleplayerServer() != null ? "world:" + mc.getSingleplayerServer().getWorldData().getLevelName() : "";
            if (!key.equals(sessionKey)) {
                sessionKey = key;
                sessionName = mc.getCurrentServer() != null ? mc.getCurrentServer().name
                        : mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldData().getLevelName() : "";
                reset();
            }
        });
    }

    private int pendingCrits;

    private void onLeave() {
        endFight(Result.ENDED, false);
        if (whenLeaving.get() && !fights.isEmpty()) {
            pendingOpen = true;
        }
    }

    private void swing() {
        long now = System.currentTimeMillis();
        lastSwing = now;
        swings++;
        if (current != null) {
            current.swings++;
        } else {
            recentSwings.addLast(now);
            while (recentSwings.size() > 40) {
                recentSwings.removeFirst();
            }
        }
    }

    /** The same checks the game uses for a critical hit, read at the moment you attack. */
    private boolean critNow() {
        Player p = mc.player;
        return p != null && p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger()
                && !p.isSprinting() && !p.hasEffect(MobEffects.BLINDNESS) && p.getAttackStrengthScale(0.5f) > 0.9f;
    }

    private Fight fight() {
        long now = System.currentTimeMillis();
        lastCombat = now;
        if (current == null) {
            current = new Fight();
            current.start = now;
            // swings and crits from the second before the first hit belong to this fight
            while (!recentSwings.isEmpty() && now - recentSwings.peekFirst() > 1500) {
                recentSwings.removeFirst();
            }
            current.swings = recentSwings.size();
            recentSwings.clear();
            current.crits = pendingCrits;
            crits += pendingCrits;
            combo = 0;
        }
        pendingCrits = 0;
        return current;
    }

    private void tick() {
        if (pendingOpen && mc.level == null) {
            Screen s = Screens.current();
            if (s instanceof TitleScreen || s instanceof NsMenuScreen || s instanceof JoinMultiplayerScreen || s instanceof SelectWorldScreen) {
                pendingOpen = false;
                if (!(s instanceof SessionRecapScreen)) {
                    Screens.open(new SessionRecapScreen(s, this));
                }
            }
            return;
        }
        if (mc.player == null) {
            return;
        }
        recentHits.entrySet().removeIf(en -> {
            LivingEntity entity = en.getKey();
            if (entity.isDeadOrDying()) {
                kills++;
                if (current != null) {
                    current.opponent = entity.getName().getString();
                    endFight(Result.WON, true);
                }
                return true;
            }
            int left = en.getValue() - 1;
            if (left <= 0 || entity.isRemoved()) {
                return true;
            }
            en.setValue(left);
            return false;
        });
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) {
            deaths++;
            endFight(Result.LOST, true);
        }
        wasDead = dead;
        if (current != null && System.currentTimeMillis() - lastCombat > FIGHT_GAP_MS) {
            endFight(Result.ENDED, true);
        }
        if (current == null && System.currentTimeMillis() - lastSwing > 1500) {
            pendingCrits = 0; // swung but never landed: not a fight
        }
    }

    private void endFight(Result result, boolean announce) {
        Fight f = current;
        if (f == null) {
            return;
        }
        current = null;
        combo = 0;
        f.end = Math.max(lastCombat, f.start);
        if (result != Result.WON || "?".equals(f.opponent)) {
            // the player you traded the most hits with
            f.opponent = f.hitsOn.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(f.opponent);
        }
        f.result = result;
        fights.add(0, f);
        while (fights.size() > MAX_FIGHTS) {
            fights.remove(fights.size() - 1);
        }
        if (announce && afterFights.get()) {
            String head = switch (result) {
                case WON -> "Fight won";
                case LOST -> "Fight lost";
                case ENDED -> "Fight over";
            };
            String msg = "vs " + f.opponent + " - " + f.landed + " hits, " + f.crits + " crits, " + f.accuracy() + "% accuracy";
            if (result == Result.WON) {
                NightshadeClient.client().notifications().success(head, msg);
            } else {
                NightshadeClient.client().notifications().info(head, msg);
            }
        }
    }

    private void openNow() {
        Screens.open(new SessionRecapScreen(Screens.current(), this));
    }

    public void reset() {
        sessionStart = System.currentTimeMillis();
        kills = deaths = landed = taken = swings = crits = bestCombo = combo = pendingCrits = 0;
        fights.clear();
        current = null;
        recentHits.clear();
        recentSwings.clear();
    }

    // ------------------------------------------------------------------ read by the recap screen

    public int kills() { return kills; }
    public int deaths() { return deaths; }
    public int landed() { return landed; }
    public int taken() { return taken; }
    public int swings() { return swings; }
    public int crits() { return crits; }
    public int bestCombo() { return bestCombo; }
    public String sessionName() { return sessionName; }
    public long sessionMillis() { return System.currentTimeMillis() - sessionStart; }

    public int accuracy() {
        return swings == 0 ? 0 : Math.min(100, Math.round(landed * 100f / swings));
    }

    public String kd() {
        return String.format(Locale.ROOT, "%.2f", deaths == 0 ? (double) kills : kills / (double) deaths);
    }

    public int fightsWon() {
        int n = 0;
        for (Fight f : fights) {
            if (f.result == Result.WON) {
                n++;
            }
        }
        return n;
    }

    /** Newest first. */
    public List<Fight> fights() {
        return Collections.unmodifiableList(fights);
    }

    // ------------------------------------------------------------------ test hooks

    public void endFightForTest() {
        endFight(Result.ENDED, true);
    }

    public Fight currentForTest() {
        return current;
    }

    public boolean pendingOpenForTest() {
        return pendingOpen;
    }

    /** Runs what happens when you leave a server, without leaving (the recap opens once you are back in a menu). */
    public void leaveForTest() {
        onLeave();
    }

    public void cancelPendingForTest() {
        pendingOpen = false;
    }

    /** Adds a finished fight with these numbers (for screenshots of a busy recap). */
    public void addFightForTest(String opponent, Result result, int landed, int taken, int swings, int crits, int combo, int seconds) {
        Fight f = new Fight();
        f.opponent = opponent;
        f.result = result;
        f.landed = landed;
        f.taken = taken;
        f.swings = swings;
        f.crits = crits;
        f.bestCombo = combo;
        f.end = System.currentTimeMillis();
        f.start = f.end - seconds * 1000L;
        fights.add(0, f);
        this.landed += landed;
        this.taken += taken;
        this.swings += swings;
        this.crits += crits;
        this.bestCombo = Math.max(this.bestCombo, combo);
        if (result == Result.WON) {
            kills++;
        } else if (result == Result.LOST) {
            deaths++;
        }
    }
}
