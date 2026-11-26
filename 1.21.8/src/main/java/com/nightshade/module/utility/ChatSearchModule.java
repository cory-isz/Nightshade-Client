package com.nightshade.module.utility;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;

/** Opens the chat search screen (search the whole chat history and copy any message). */
public final class ChatSearchModule extends Module {
    public ChatSearchModule() {
        super("Chat Search", "Search chat history and copy messages", ModuleCategory.UTILITY);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        ChatTools.openSearch();
    }
}
