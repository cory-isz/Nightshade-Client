package com.nightshade.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

@Mixin(ChatComponent.class)
public interface ChatHudAccess {
    @Accessor("allMessages")
    List<GuiMessage> nightshade$getMessages();

    @Invoker("refreshTrimmedMessages")
    void nightshade$refresh();
}
