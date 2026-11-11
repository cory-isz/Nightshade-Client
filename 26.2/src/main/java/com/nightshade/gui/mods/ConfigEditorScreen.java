package com.nightshade.gui.mods;

import com.nightshade.gui.NsScreen;
import com.google.gson.stream.JsonReader;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * In-game text editor for a mod's config file. JSON files are validated before saving and a {@code .bak} copy
 * of the previous version is kept next to the file.
 */
public final class ConfigEditorScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final int MAX_BYTES = 512 * 1024;

    private final Screen parent;
    private final Path file;
    private final String displayName;
    private MultiLineEditBox editor;
    private String original = "";
    private String loadError;
    private String status = "";
    private int statusColor = Theme.TEXT_DIM;

    private final NsButton save = new NsButton("Save", NsButton.Style.PRIMARY, this::save);
    private final NsButton revert = new NsButton("Revert", NsButton.Style.SECONDARY, this::revert);
    private final NsButton openExternal = new NsButton("Open externally", NsButton.Style.GHOST, this::openExternally);
    private final NsButton back = new NsButton("Back", NsButton.Style.SECONDARY, this::onClose);

    private int winX;
    private int winY;
    private int winW;
    private int winH;

    public ConfigEditorScreen(Screen parent, Path file) {
        super(Component.literal("Edit config"));
        this.parent = parent;
        this.file = file;
        this.displayName = FabricLoader.getInstance().getConfigDir().relativize(file).toString().replace('\\', '/');
        try {
            if (Files.size(file) > MAX_BYTES) {
                loadError = "File is larger than 512 KB - use 'Open externally'.";
            } else {
                original = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\t", "    ");
            }
        } catch (IOException e) {
            loadError = "Could not read file: " + e.getMessage();
        }
    }

    @Override
    protected void init() {
        layout();
        editor = MultiLineEditBox.builder().setX(winX + 12).setY(winY + 34).setPlaceholder(Component.literal("")).setTextColor(Theme.TEXT).build(font, winW - 24, winH - 72, Component.literal(displayName));
        editor.setCharacterLimit(MAX_BYTES);
        editor.setValue(original);
        editor.setValueListener(text -> {
            status = isModified() ? "Unsaved changes" : "";
            statusColor = Theme.WARNING;
        });
        if (loadError == null) {
            addRenderableWidget(editor);
            setInitialFocus(editor);
        }
    }

    private void layout() {
        winW = Math.min(width - 16, 720);
        winH = Math.min(height - 16, 420);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
    }

    private boolean isModified() {
        return editor != null && !editor.getValue().equals(original);
    }

    private boolean isJson() {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json");
    }

    private void save() {
        if (editor == null || loadError != null) {
            return;
        }
        String text = editor.getValue();
        if (isJson()) {
            try {
                JsonReader reader = new JsonReader(new StringReader(text));
                reader.setStrictness(com.google.gson.Strictness.STRICT);
                new com.google.gson.Gson().getAdapter(com.google.gson.JsonElement.class).read(reader);
                if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                    throw new IllegalStateException("unexpected content after the end of the document");
                }
            } catch (Exception e) {
                status = "Invalid JSON: " + e.getMessage();
                statusColor = Theme.DANGER;
                return;
            }
        }
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            if (Files.exists(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, text, StandardCharsets.UTF_8);
            original = text;
            status = "Saved (backup: " + backup.getFileName() + "). Some mods only reload config on restart.";
            statusColor = Theme.SUCCESS;
        } catch (IOException e) {
            Log.error("Could not save " + file, e);
            status = "Save failed: " + e.getMessage();
            statusColor = Theme.DANGER;
        }
    }

    private void openExternally() {
        Util.getPlatform().openFile(file.toFile());
    }

    private void revert() {
        if (editor != null) {
            editor.setValue(original);
            status = "Reverted";
            statusColor = Theme.TEXT_DIM;
        }
    }

    @Override
    public void onClose() {
        if (isModified()) {
            minecraft.gui.setScreen(new ConfirmScreen(discard -> minecraft.gui.setScreen(discard ? parent : this),
                    Component.literal("Discard unsaved changes?"), Component.literal(displayName)));
            return;
        }
        minecraft.gui.setScreen(parent);
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        RenderUtil.roundedRect(ctx, winX, winY, winX + winW, winY + winH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, winX, winY, winX + winW, winY + winH, 8, Theme.BORDER);
        Fonts.draw(ctx, "Edit config", winX + 14, winY + 10, Theme.TEXT, false);
        Fonts.draw(ctx, Fonts.trim(displayName, winW - 120), winX + 14, winY + 21, Theme.TEXT_DIM, false);
        String hint = isJson() ? "JSON is validated on save" : "Ctrl+S to save";
        Fonts.draw(ctx, hint, winX + winW - 14 - Fonts.width(hint), winY + 14, Theme.TEXT_DIM, false);
        if (loadError != null) {
            Fonts.drawCentered(ctx, loadError, width / 2, winY + winH / 2, Theme.DANGER, false);
        } else {
            super.extractRenderState(ctx, mouseX, mouseY, delta);
        }
        int fy = winY + winH - 28;
        Fonts.draw(ctx, Fonts.trim(status, winW - 330), winX + 14, fy + 5, statusColor, false);
        int x = winX + winW - 12;
        for (NsButton button : new NsButton[]{back, openExternal, revert, save}) {
            int w = Math.max(56, button.preferredWidth());
            x -= w;
            button.enabled(loadError == null || button == back || button == openExternal);
            button.render(ctx, x, fy, w, 18, mouseX, mouseY);
            x -= 6;
        }
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (save.click(mouseX, mouseY, button) || revert.click(mouseX, mouseY, button)
                || openExternal.click(mouseX, mouseY, button) || back.click(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return false;
    }

    /** Test hooks. */
    public void setTextForTest(String text) {
        editor.setValue(text);
    }

    public void saveForTest() {
        save();
    }

    public String statusForTest() {
        return status;
    }
}
