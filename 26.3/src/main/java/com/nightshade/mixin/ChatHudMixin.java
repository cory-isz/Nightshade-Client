package com.nightshade.mixin;

import com.nightshade.module.utility.ChatTimestamps;
import com.nightshade.module.utility.ChatTools;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    /** Target: public void addMessage(Text, MessageSignatureData, MessageIndicator) - the Text parameter. */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component nightshade$timestamp(Component message) {
        com.nightshade.module.pvp.AutoGG.onChat(message);
        // Stack repeats on the raw message first, then add the timestamp.
        return ChatTimestamps.decorate(ChatTools.stack((ChatComponent) (Object) this, com.nightshade.module.utility.NickHider.chat(message)));
    }
}
