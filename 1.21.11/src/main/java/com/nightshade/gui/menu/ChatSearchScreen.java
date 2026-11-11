package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.NsScreen;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.mixin.accessor.ChatHudAccess;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searches the chat history (newest first); click a message to copy it to the clipboard. */
public final class ChatSearchScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int ROW_H = 13;

    @Nullable
    private final Screen parent;
    private EditBox query;
    private List<String> results = new ArrayList<>();
    private double scroll;
    private final NsButton done = new NsButton("Done", NsButton.Style.SECONDARY, this::onClose);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;

    public ChatSearchScreen(@Nullable Screen parent) {
        super(Component.literal("Chat Search"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        query = new EditBox(font, panelX + 26, panelY + 33, panelW - 52, 14, Component.literal("Search"));
        query.setBordered(false);
        query.setTextColor(Theme.TEXT);
        query.setHint(Component.literal("Search chat..."));
        query.setResponder(text -> search());
        addWidget(query);
        setInitialFocus(query);
        search();
    }

    private void layout() {
        panelW = Math.min(width - 16, 520);
        panelH = Math.min(height - 16, 340);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 54;
        listBottom = panelY + panelH - 30;
    }

    private void search() {
        String needle = query == null ? "" : query.getValue().toLowerCase(Locale.ROOT).trim();
        results = new ArrayList<>();
        for (GuiMessage line : ((ChatHudAccess) minecraft.gui.getChat()).nightshade$getMessages()) {
            String text = line.content().getString();
            if (needle.isEmpty() || text.toLowerCase(Locale.ROOT).contains(needle)) {
                results.add(text);
            }
        }
        scroll = 0;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }


    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        layout();
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        RenderUtil.roundedRect(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BORDER);
        Fonts.draw(ctx, "Chat Search", panelX + 14, panelY + 12, Theme.TEXT, false);
        Fonts.draw(ctx, results.size() + " messages  -  click one to copy it", panelX + 14 + Fonts.width("Chat Search") + 10, panelY + 12, Theme.TEXT_DIM, false);
        RenderUtil.roundedRect(ctx, panelX + 12, panelY + 29, panelX + panelW - 12, panelY + 46, 4, query.isFocused() ? Theme.SELECTED : Theme.PANEL);
        RenderUtil.roundedOutline(ctx, panelX + 12, panelY + 29, panelX + panelW - 12, panelY + 46, 4, query.isFocused() ? Theme.accent() : Theme.BORDER);
        PixelIcons.search(ctx, panelX + 16, panelY + 34, Theme.TEXT_DIM);
        query.render(ctx, mouseX, mouseY, delta);
        int maxScroll = Math.max(0, results.size() * ROW_H - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ctx.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
        for (int i = 0; i < results.size(); i++) {
            int y = listTop + i * ROW_H - (int) scroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            boolean hovered = mouseY >= listTop && mouseY < listBottom && RenderUtil.isHovered(mouseX, mouseY, panelX + 12, y, panelX + panelW - 12, y + ROW_H);
            if (hovered) {
                RenderUtil.roundedRect(ctx, panelX + 12, y, panelX + panelW - 12, y + ROW_H, 2, Theme.PANEL_HOVER);
            }
            Fonts.draw(ctx, Fonts.trim(results.get(i), panelW - 32), panelX + 16, y + 2, hovered ? Theme.TEXT : Theme.TEXT_MUTED, false);
        }
        if (results.isEmpty()) {
            Fonts.drawCentered(ctx, "No messages match", width / 2, listTop + 16, Theme.TEXT_DIM, false);
        }
        ctx.disableScissor();
        done.render(ctx, panelX + panelW - 82, panelY + panelH - 24, 70, 16, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (done.click(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop + scroll) / ROW_H);
            if (index >= 0 && index < results.size()) {
                minecraft.keyboardHandler.setClipboard(results.get(index));
                NightshadeClient.client().notifications().success("Chat Search", "Message copied");
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * ROW_H * 3;
        return true;
    }

    /** Test hooks. */
    public void setQueryForTest(String text) {
        query.setValue(text);
    }

    public List<String> resultsForTest() {
        return results;
    }
}
