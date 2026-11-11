package com.nightshade.mixin.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor("rightClickDelay")
    int nightshade$getItemUseCooldown();

    @Accessor("rightClickDelay")
    void nightshade$setItemUseCooldown(int cooldown);
}
