package com.nightshade.module.hud;

import java.util.Locale;

/** Your yaw and pitch (handy for bridging and aiming at exact angles). */
public final class RotationHud extends SimpleTextHud {
    public RotationHud() {
        super("Rotation", "Shows your yaw and pitch", "Yaw ", 0.0, 0.54);
    }

    @Override
    protected void refresh() {
        if (mc.player == null) {
            return;
        }
        float yaw = net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot());
        value = String.format(Locale.ROOT, "%.1f  Pitch %.1f", yaw, mc.player.getXRot());
    }
}
