package com.nightshade.mixin.accessor;

import net.minecraft.client.gui.screens.FaviconTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft 1.21.8 has no public "is closed" check on server/world icons. */
@Mixin(FaviconTexture.class)
public interface FaviconTextureAccess {
    @Accessor("closed")
    boolean nightshade$closed();
}
