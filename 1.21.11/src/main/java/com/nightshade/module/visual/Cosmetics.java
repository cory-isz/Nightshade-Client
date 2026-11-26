package com.nightshade.module.visual;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side cosmetics: a Nightshade cape (also used as your elytra's texture). Only you see them - nothing is sent
 * anywhere, so they work on every server and need no account.
 */
public final class Cosmetics extends Module {
    public static final String[] CAPES = {"None", "Nightshade", "Aurora", "Galaxy", "Crimson", "Ocean", "Mono"};
    private static volatile Cosmetics active;
    private final ModeSetting cape = add(new ModeSetting("Cape", "Your cape (only you see it)", "Nightshade", CAPES))
            .onChange(this::capeChanged);
    private String lastCape;
    private boolean fromWardrobe;
    private final BooleanSetting elytra = add(new BooleanSetting("Elytra Skin", "Use the cape's design on your elytra too", true));
    private final BooleanSetting share = add(new BooleanSetting("Show Mine To Others", "Let other Nightshade players see your Wardrobe cosmetics (Microsoft accounts)", true));
    private final BooleanSetting others = add(new BooleanSetting("See Others' Cosmetics", "Show the Wardrobe cosmetics other Nightshade players wear", true));
    private final BooleanSetting badges = add(new BooleanSetting("Show Nightshade Badges", "A small Nightshade logo next to the names of other Nightshade players", true));
    private static volatile Cosmetics any;
    private final BooleanSetting override = add(new BooleanSetting("Replace Real Cape", "Show the Nightshade cape even if your account has a cape", true));

    public Cosmetics() {
        super("Cosmetics", "Client-side capes and elytra skins - only you see them", ModuleCategory.VISUAL);
        any = this;
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        if (lastCape == null) {
            lastCape = cape.get();
        }
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Sharing settings (they apply even while the module is off). */
    public static boolean shareMine() {
        return any == null || any.share.get();
    }

    public static boolean badges() {
        return any == null || any.badges.get();
    }

    public static boolean seeOthers() {
        return any == null || any.others.get();
    }

    public static Cosmetics instance() {
        return active;
    }

    public String capeName() {
        return cape.get();
    }

    /** Called by the Wardrobe when you wear a classic cape there. */
    public void setCape(String name) {
        fromWardrobe = true;
        try {
            cape.set(name);
        } finally {
            fromWardrobe = false;
        }
        lastCape = cape.get();
        markDirty();
    }

    /**
     * You picked a cape in this module's settings: wear the matching classic cape in the Wardrobe too, so it shows
     * (a 3D Wardrobe cape would otherwise hide it). Loading settings at startup or the same value again changes nothing.
     */
    private void capeChanged() {
        String now = cape.get();
        String before = lastCape;
        lastCape = now;
        if (fromWardrobe || before == null || now.equals(before) || !com.nightshade.NightshadeClient.client().isInitialized()) {
            return;
        }
        if (now.equals("None")) {
            String worn = com.nightshade.cosmetic.Wardrobe.equipped(com.nightshade.cosmetic.Catalog.Category.CAPES);
            if (worn != null && worn.startsWith("classic_")) {
                com.nightshade.cosmetic.Wardrobe.unequip(com.nightshade.cosmetic.Catalog.Category.CAPES);
            }
            return;
        }
        com.nightshade.cosmetic.Catalog.Item classic = com.nightshade.cosmetic.Catalog.byId("classic_" + now.toLowerCase(java.util.Locale.ROOT));
        if (classic != null) {
            com.nightshade.cosmetic.Wardrobe.equip(classic);
        }
    }

    public static Identifier capeTexture(String name) {
        return Identifier.fromNamespaceAndPath("nightshade", "textures/cape/" + name.toLowerCase(java.util.Locale.ROOT) + ".png");
    }

    /** The cape texture to use for the local player, or null to keep the real one. */
    @Nullable
    public static ClientAsset.Texture cape(@Nullable ClientAsset.Texture real) {
        Cosmetics m = active;
        if (m == null || m.cape.is("None") || (real != null && !m.override.get())) {
            return null;
        }
        EffectProbe.hit("Cosmetics");
        String name = m.cape.get().toLowerCase(java.util.Locale.ROOT);
        return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("nightshade", "cape/" + name), capeTexture(name));
    }

    public static boolean elytraToo() {
        Cosmetics m = active;
        return m != null && m.elytra.get();
    }
}
