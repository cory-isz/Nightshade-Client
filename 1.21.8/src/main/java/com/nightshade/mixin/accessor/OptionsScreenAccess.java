package com.nightshade.mixin.accessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The screen the vanilla Options screen returns to (so the Nightshade Options screen can replace it). */
@Mixin(OptionsScreen.class)
public interface OptionsScreenAccess {
    @Accessor("lastScreen")
    Screen nightshade$parent();
}
