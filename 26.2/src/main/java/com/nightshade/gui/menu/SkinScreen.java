package com.nightshade.gui.menu;

import com.nightshade.net.Http;
import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.NightshadeClient;
import com.nightshade.account.Account;
import com.nightshade.account.SkinService;
import com.nightshade.gui.NsScreen;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Preview and upload a skin for the active Microsoft (premium) account. */
public final class SkinScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private static final Identifier PREVIEW_ID = Identifier.fromNamespaceAndPath("nightshade", "skin_preview");

    @Nullable
    private final Screen parent;
    private final SkinService service = new SkinService();
    private byte[] png;
    private String fileName;
    private boolean legacyFormat;
    private boolean slim;
    private boolean previewLoaded;
    private volatile String status = "";
    private volatile int statusColor = Theme.TEXT_DIM;
    private volatile boolean busy;

    private final NsButton choose = new NsButton("Choose PNG...", NsButton.Style.SECONDARY, this::chooseFile);
    private final NsButton model = new NsButton("Model: Classic", NsButton.Style.GHOST, this::toggleModel);
    private final NsButton upload = new NsButton("Upload skin", NsButton.Style.PRIMARY, this::upload);
    private final NsButton reset = new NsButton("Reset to default", NsButton.Style.DANGER, this::reset);
    private final NsButton back = new NsButton("Back", NsButton.Style.SECONDARY, this::onClose);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public SkinScreen(@Nullable Screen parent) {
        super(Component.literal("Skin"));
        this.parent = parent;
    }

    private Account account() {
        return NightshadeClient.client().accounts().active();
    }

    private boolean canChange() {
        Account account = account();
        return NightshadeClient.client().accounts().isPremium() && account.accessToken() != null && account.accessToken().length() > 20;
    }

    private void toggleModel() {
        slim = !slim;
        model.label(slim ? "Model: Slim" : "Model: Classic");
    }

    private void chooseFile() {
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png")).flip();
            path = TinyFileDialogs.tinyfd_openFileDialog("Choose a Minecraft skin", "", filters, "PNG skin (64x64)", false);
        }
        if (path != null) {
            load(Path.of(path));
        }
    }

    /** Loads and validates a skin file (also used by the test). Returns an error message or null. */
    @Nullable
    public String load(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            NativeImage image = NativeImage.read(bytes);
            if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                image.close();
                return fail("A skin must be 64x64 (or legacy 64x32) pixels");
            }
            legacyFormat = image.getHeight() == 32;
            minecraft.getTextureManager().register(PREVIEW_ID, new DynamicTexture(() -> "nightshade skin preview", image));
            png = bytes;
            fileName = path.getFileName().toString();
            previewLoaded = true;
            status = "Ready to upload " + fileName;
            statusColor = Theme.TEXT_MUTED;
            return null;
        } catch (Exception e) {
            return fail("Not a valid PNG image");
        }
    }

    private String fail(String message) {
        status = message;
        statusColor = Theme.DANGER;
        return message;
    }

    private void upload() {
        if (png == null || busy || !canChange()) {
            return;
        }
        run("Uploading...", () -> service.upload(account().accessToken(), png, slim), "Skin uploaded - other players see it after they reload your skin");
    }

    private void reset() {
        if (busy || !canChange()) {
            return;
        }
        run("Resetting...", () -> service.reset(account().accessToken()), "Skin reset to the default");
    }

    private interface Call {
        void call() throws Exception;
    }

    private void run(String working, Call call, String done) {
        busy = true;
        status = working;
        statusColor = Theme.accentLight();
        CompletableFuture.runAsync(() -> {
            try {
                call.call();
                status = done;
                statusColor = Theme.SUCCESS;
                NightshadeClient.client().accounts().forgetSkin(account());
            } catch (Exception e) {
                status = Http.describe(e);
                statusColor = Theme.DANGER;
            } finally {
                busy = false;
            }
        }, Util.nonCriticalIoPool());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (previewLoaded) {
            minecraft.getTextureManager().release(PREVIEW_ID);
        }
        super.removed();
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        panelW = Math.min(width - 24, 360);
        panelH = Math.min(height - 24, 260);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        RenderUtil.roundedRect(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BORDER);
        Fonts.draw(ctx, "Skin", panelX + 16, panelY + 12, Theme.TEXT, false);
        Account account = account();
        Fonts.draw(ctx, Fonts.trim(account.name() + " (" + account.type().label() + ")", panelW - 32), panelX + 16, panelY + 24, Theme.TEXT_DIM, false);

        int previewX = panelX + 24;
        int previewY = panelY + 44;
        if (previewLoaded) {
            int scale = Math.max(2, Math.min(5, (panelH - 104) / 32));
            drawFront(ctx, previewX + (5 - scale) * 8, previewY, scale);
        } else {
            PlayerFaceExtractor.extractRenderState(ctx, NightshadeClient.client().accounts().skin(account), previewX + 12, previewY + 10, 56);
            Fonts.drawCentered(ctx, "current", previewX + 40, previewY + 72, Theme.TEXT_DIM, false);
        }

        int bx = panelX + 130;
        int bw = panelW - 146;
        boolean allowed = canChange();
        choose.enabled(allowed && !busy).render(ctx, bx, panelY + 50, bw, 18, mouseX, mouseY);
        model.enabled(allowed && !busy).render(ctx, bx, panelY + 74, bw, 18, mouseX, mouseY);
        upload.enabled(allowed && !busy && png != null).render(ctx, bx, panelY + 98, bw, 18, mouseX, mouseY);
        reset.enabled(allowed && !busy).render(ctx, bx, panelY + 122, bw, 18, mouseX, mouseY);
        String info = allowed ? (legacyFormat ? "Legacy 64x32 skin: arms/legs mirrored by Minecraft" : "PNG, 64x64 pixels")
                : "Skins can only be changed for Microsoft (premium) accounts";
        Fonts.draw(ctx, Fonts.trim(info, panelW - 32), panelX + 16, panelY + panelH - 48, allowed ? Theme.TEXT_DIM : Theme.WARNING, false);
        Fonts.draw(ctx, Fonts.trim(status, panelW - 110), panelX + 16, panelY + panelH - 22, statusColor, false);
        back.render(ctx, panelX + panelW - 86, panelY + panelH - 26, 70, 16, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    /** Draws a flat front view of the loaded skin (base layer, then the outer layer on top). */
    private void drawFront(GuiGraphicsExtractor ctx, int x, int y, int s) {
        int arm = slim ? 3 : 4;
        part(ctx, 8, 8, 8, 8, x + 4 * s, y, s);                       // head
        part(ctx, 20, 20, 8, 12, x + 4 * s, y + 8 * s, s);            // body
        part(ctx, 44, 20, arm, 12, x + (4 - arm) * s, y + 8 * s, s);  // right arm
        part(ctx, 4, 20, 4, 12, x + 4 * s, y + 20 * s, s);            // right leg
        if (legacyFormat) {
            part(ctx, 44, 20, arm, 12, x + 12 * s, y + 8 * s, s);
            part(ctx, 4, 20, 4, 12, x + 8 * s, y + 20 * s, s);
        } else {
            part(ctx, 36, 52, arm, 12, x + 12 * s, y + 8 * s, s);     // left arm
            part(ctx, 20, 52, 4, 12, x + 8 * s, y + 20 * s, s);       // left leg
            part(ctx, 40, 8, 8, 8, x + 4 * s, y, s);                  // hat
            part(ctx, 20, 36, 8, 12, x + 4 * s, y + 8 * s, s);        // jacket
            part(ctx, 44, 36, arm, 12, x + (4 - arm) * s, y + 8 * s, s);
            part(ctx, 52, 52, arm, 12, x + 12 * s, y + 8 * s, s);
            part(ctx, 4, 36, 4, 12, x + 4 * s, y + 20 * s, s);
            part(ctx, 4, 52, 4, 12, x + 8 * s, y + 20 * s, s);
        }
    }

    private void part(GuiGraphicsExtractor ctx, int u, int v, int w, int h, int x, int y, int s) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, x, y, u, v, w * s, h * s, w, h, 64, legacyFormat ? 32 : 64);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return choose.click(mouseX, mouseY, button) || model.click(mouseX, mouseY, button) || upload.click(mouseX, mouseY, button)
                || reset.click(mouseX, mouseY, button) || back.click(mouseX, mouseY, button);
    }

    /** Test hook. */
    public String statusForTest() {
        return status;
    }
}
