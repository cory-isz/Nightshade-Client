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
    private final ModeSetting cape = add(new ModeSetting("Cape", "Your cape (only you see it)", "Nightshade", CAPES));
    private final BooleanSetting elytra = add(new BooleanSetting("Elytra Skin", "Use the cape's design on your elytra too", true));
    private final BooleanSetting override = add(new BooleanSetting("Replace Real Cape", "Show the Nightshade cape even if your account has a cape", true));

    public Cosmetics() {
        super("Cosmetics", "Client-side capes and elytra skins - only you see them", ModuleCategory.VISUAL);
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static Cosmetics instance() {
        return active;
    }

    public String capeName() {
        return cape.get();
    }

    public void setCape(String name) {
        cape.set(name);
        markDirty();
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
