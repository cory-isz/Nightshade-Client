package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.mixin.accessor.OverlayTextureAccessor;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ColorSetting;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Changes the tint entities flash when they take damage. Minecraft stores that tint in the top half (rows 0-7)
 * of the 16x16 entity overlay texture; this rewrites those pixels and uploads the texture again.
 */
public final class HitColor extends Module {
    /** Vanilla hurt tint (ARGB). */
    public static final int VANILLA = 0xB2FF0000;
    private final ColorSetting color = add(new ColorSetting("Color", "Hurt tint colour (alpha = strength)", 0xA07C6CFF))
            .onChange(this::applyIfEnabled);

    public HitColor() {
        super("Hit Color", "Changes the colour entities flash when hit", ModuleCategory.VISUAL);
    }

    private void applyIfEnabled() {
        if (isEnabled()) {
            write(color.argb());
        }
    }

    @Override
    protected void onEnable() {
        write(color.argb());
    }

    @Override
    protected void onDisable() {
        write(VANILLA);
    }

    private static void write(int argb) {
        if (mc.gameRenderer == null) {
            return;
        }
        mc.execute(() -> {
            DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).nightshade$getTexture();
            NativeImage image = texture.getPixels();
            if (image == null) {
                return;
            }
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 16; x++) {
                    image.setPixel(x, y, argb);
                }
            }
            texture.upload();
            if (argb != VANILLA) {
                EffectProbe.hit("Hit Color");
            }
        });
    }

    /** The tint this module writes (an untouched default follows the client colour). */
    public int targetTint() {
        return color.argb();
    }

    /** Current tint as stored in the overlay texture (used by the self-test). */
    public static int currentTint() {
        NativeImage image = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).nightshade$getTexture().getPixels();
        return image == null ? 0 : image.getPixel(0, 0);
    }
}
