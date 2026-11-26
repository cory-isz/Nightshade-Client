package com.nightshade.mixin.accessor;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

/** Access to the identity-related final fields of MinecraftClient, used by the account switcher. */
@Mixin(Minecraft.class)
public interface MinecraftClientSessionAccessor {
    @Mutable
    @Accessor("user")
    void nightshade$setSession(User session);

    @Mutable
    @Accessor("userApiService")
    void nightshade$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("profileKeyPairManager")
    void nightshade$setProfileKeys(ProfileKeyPairManager keys);

    @Mutable
    @Accessor("profileFuture")
    void nightshade$setGameProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("userPropertiesFuture")
    void nightshade$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);
}
