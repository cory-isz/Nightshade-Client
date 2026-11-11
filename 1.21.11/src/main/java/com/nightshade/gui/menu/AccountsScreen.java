package com.nightshade.gui.menu;

import com.nightshade.net.Http;
import com.nightshade.gui.NsScreen;
import com.nightshade.NightshadeClient;
import com.nightshade.account.Account;
import com.nightshade.account.AccountManager;
import com.nightshade.account.MicrosoftAuth;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Account switcher: launcher account, Microsoft (premium) accounts signed in with a device code, and offline
 * ("cracked") usernames for singleplayer and offline-mode servers.
 */
public final class AccountsScreen extends com.nightshade.gui.menu.NsMenuScreen {
    private enum Mode {
        LIST, ADD_OFFLINE, MICROSOFT
    }

    private static final int ROW_H = 32;

    @Nullable
    private final Screen parent;
    private final Map<Account, Animation> hover = new HashMap<>();
    private Mode mode = Mode.LIST;
    private EditBox nameField;
    private String offlineError;
    private double scroll;

    // Microsoft sign-in state
    private volatile MicrosoftAuth.DeviceCode deviceCode;
    private volatile String msStatus = "";
    private volatile String msError;
    private AtomicBoolean msCancel = new AtomicBoolean();

    private final NsButton addMicrosoft = new NsButton(Lang.t("accounts.add_microsoft"), NsButton.Style.PRIMARY, this::startMicrosoft);
    private final NsButton addOffline = new NsButton(Lang.t("accounts.add_offline"), NsButton.Style.SECONDARY, () -> setMode(Mode.ADD_OFFLINE));
    private final NsButton done = new NsButton(Lang.t("common.done"), NsButton.Style.SECONDARY, this::onClose);
    private final NsButton skin = new NsButton(Lang.t("accounts.skin"), NsButton.Style.SECONDARY, () -> minecraft.setScreen(new SkinScreen(this)));
    private final NsButton useLauncher = new NsButton(Lang.t("accounts.use_launcher"), NsButton.Style.SECONDARY, this::fallbackToLauncher);
    private final NsButton confirmOffline = new NsButton(Lang.t("accounts.add"), NsButton.Style.PRIMARY, this::submitOffline);
    private final NsButton cancel = new NsButton(Lang.t("common.cancel"), NsButton.Style.GHOST, this::cancelMode);
    private final NsButton openBrowser = new NsButton(Lang.t("accounts.open_browser"), NsButton.Style.PRIMARY, this::openBrowser);

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;

    public AccountsScreen(@Nullable Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    private AccountManager accounts() {
        return NightshadeClient.client().accounts();
    }

    @Override
    protected void init() {
        layout();
        nameField = new EditBox(font, panelX + 16, panelY + panelH - 62, panelW - 32, 18, Component.literal("Username"));
        nameField.setMaxLength(16);
        nameField.setBordered(false);
        nameField.setTextColor(Theme.TEXT);
        nameField.setHint(Component.literal("Username (3-16 letters, numbers, _)"));
        // EditBox lost setFilter in 26.1: strip disallowed characters as they are typed instead.
        nameField.setResponder(s -> {
            String clean = s.replaceAll("[^A-Za-z0-9_]", "");
            if (!clean.equals(s)) {
                nameField.setValue(clean);
            }
        });
        nameField.setVisible(mode == Mode.ADD_OFFLINE);
        addWidget(nameField);
    }

    private void layout() {
        panelW = Math.min(width - 24, 340);
        panelH = Math.min(height - 24, 290);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + 40;
        listBottom = panelY + panelH - (mode == Mode.LIST ? 64 : 76);
        if (nameField != null) {
            nameField.setX(panelX + 20);
            nameField.setY(panelY + panelH - 64);
            nameField.setWidth(panelW - 40);
        }
    }

    private void setMode(Mode newMode) {
        mode = newMode;
        offlineError = null;
        nameField.setVisible(newMode == Mode.ADD_OFFLINE);
        if (newMode == Mode.ADD_OFFLINE) {
            nameField.setValue("");
            setFocused(nameField);
            nameField.setFocused(true);
        }
    }

    private void cancelMode() {
        if (mode == Mode.MICROSOFT) {
            msCancel.set(true);
        }
        setMode(Mode.LIST);
    }

    private void submitOffline() {
        String error = accounts().addOffline(nameField.getValue());
        if (error != null) {
            offlineError = error;
            return;
        }
        String name = nameField.getValue().trim();
        setMode(Mode.LIST);
        for (Account account : accounts().accounts()) {
            if (account.type() == Account.Type.OFFLINE && account.name().equalsIgnoreCase(name)) {
                use(account);
                break;
            }
        }
    }

    private void startMicrosoft() {
        if (!com.nightshade.Edition.microsoftSignIn()) {
            return;
        }
        setMode(Mode.MICROSOFT);
        deviceCode = null;
        msError = null;
        msStatus = "Contacting Microsoft...";
        msCancel = new AtomicBoolean();
        AtomicBoolean cancelled = msCancel;
        MicrosoftAuth auth = accounts().auth();
        CompletableFuture.runAsync(() -> {
            try {
                MicrosoftAuth.DeviceCode code = auth.requestDeviceCode();
                deviceCode = code;
                msStatus = "Waiting for you to sign in...";
                MicrosoftAuth.Result result = auth.pollAndLogin(code, cancelled, status -> msStatus = status);
                if (cancelled.get()) {
                    return;
                }
                minecraft.execute(() -> {
                    Account account = accounts().addMicrosoft(result);
                    setMode(Mode.LIST);
                    use(account);
                });
            } catch (Exception e) {
                if (!cancelled.get()) {
                    msError = Http.describe(e);
                    msStatus = "";
                }
            }
        }, Util.nonCriticalIoPool());
    }

    private void openBrowser() {
        MicrosoftAuth.DeviceCode code = deviceCode;
        if (code == null) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(code.userCode());
        Util.getPlatform().openUri(code.verificationUri());
        NightshadeClient.client().notifications().info("Microsoft sign-in", "Code copied - paste it in the browser");
    }

    /** When Microsoft sign-in fails, keep playing with the account the launcher signed in with. */
    private void fallbackToLauncher() {
        msCancel.set(true);
        setMode(Mode.LIST);
        use(accounts().launcherAccount());
    }

    private void use(Account account) {
        accounts().switchTo(account, success -> {
        });
    }

    @Override
    public void onClose() {
        msCancel.set(true);
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ rendering


    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        layout();
        // (the background is drawn by Screen.renderWithTooltipAndSubtitles before render(); drawing it here again doubled it)
        RenderUtil.roundedRect(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, Theme.BORDER);
        RenderUtil.horizontalGradient(ctx, panelX + 10, panelY, panelX + panelW / 2, panelY + 1, 0x00000000, Theme.accent());
        RenderUtil.horizontalGradient(ctx, panelX + panelW / 2, panelY, panelX + panelW - 10, panelY + 1, Theme.accent(), 0x00000000);

        Fonts.draw(ctx, Lang.t("accounts.title"), panelX + 16, panelY + 12, Theme.TEXT, false);
        Account active = accounts().active();
        String sub = Lang.t("accounts.playing_as", active.name()) + (accounts().isSwitching() ? "  (" + Lang.t("menu.switching") + ")" : "");
        Fonts.draw(ctx, Fonts.trim(sub, panelW - 32), panelX + 16, panelY + 24, Theme.TEXT_DIM, false);

        String tooltip = mode == Mode.MICROSOFT ? null : renderList(ctx, mouseX, mouseY);
        if (mode == Mode.MICROSOFT) {
            renderMicrosoft(ctx, mouseX, mouseY);
        } else if (mode == Mode.ADD_OFFLINE) {
            renderOfflineForm(ctx, mouseX, mouseY, delta);
        } else {
            renderButtons(ctx, mouseX, mouseY);
        }
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private String renderList(GuiGraphics ctx, int mouseX, int mouseY) {
        List<Account> list = accounts().accounts();
        int maxScroll = Math.max(0, list.size() * (ROW_H + 4) - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        String tooltip = null;
        ctx.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
        for (int i = 0; i < list.size(); i++) {
            Account account = list.get(i);
            int y = listTop + i * (ROW_H + 4) - (int) scroll;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            int x1 = panelX + 12;
            int x2 = panelX + panelW - 12;
            boolean inside = mouseY >= listTop && mouseY < listBottom;
            boolean hovered = inside && mode == Mode.LIST && RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H);
            float h = hover.computeIfAbsent(account, a -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
            boolean isActive = account == accounts().active();
            RenderUtil.roundedRect(ctx, x1, y, x2, y + ROW_H, 5, ColorUtil.lerp(Theme.PANEL, Theme.PANEL_HOVER, h));
            RenderUtil.roundedOutline(ctx, x1, y, x2, y + ROW_H, 5, isActive ? ColorUtil.withAlpha(Theme.accent(), 0xC0) : Theme.BORDER);
            PlayerFaceRenderer.draw(ctx, accounts().skin(account), x1 + 7, y + 6, 20);
            Fonts.draw(ctx, Fonts.trim(account.name(), x2 - x1 - 130), x1 + 34, y + 6, Theme.TEXT, false);
            String badge = account.type().label();
            int bw = Fonts.width(badge) + 8;
            RenderUtil.roundedRect(ctx, x1 + 34, y + 18, x1 + 34 + bw, y + 28, 3, ColorUtil.withAlpha(account.type().color(), 0x55));
            Fonts.draw(ctx, badge, x1 + 38, y + 19, Theme.TEXT, false);
            int rightEdge = x2 - (account.type() == Account.Type.LAUNCHER ? 8 : 30);
            if (isActive) {
                String label = Lang.t("accounts.active");
                Fonts.draw(ctx, label, rightEdge - Fonts.width(label), y + 12, Theme.SUCCESS, false);
            } else {
                String label = Lang.t("accounts.use");
                int lw = Fonts.width(label) + 12;
                int lx = rightEdge - lw;
                RenderUtil.roundedRect(ctx, lx, y + 9, lx + lw, y + 23, 3, ColorUtil.lerp(Theme.SELECTED, Theme.accent(), h * 0.6f));
                Fonts.draw(ctx, label, lx + 6, y + 12, Theme.TEXT, false);
            }
            if (account.type() != Account.Type.LAUNCHER) {
                int rx = x2 - 22;
                boolean removeHover = hovered && RenderUtil.isHovered(mouseX, mouseY, rx, y + 9, rx + 14, y + 23);
                RenderUtil.roundedRect(ctx, rx, y + 9, rx + 14, y + 23, 3, removeHover ? 0xFF5A1E34 : 0x00000000);
                Fonts.drawCentered(ctx, "x", rx + 7, y + 12, removeHover ? 0xFFFF9AAE : Theme.TEXT_DIM, false);
                if (removeHover) {
                    tooltip = "Remove " + account.name();
                }
            }
            if (hovered && tooltip == null) {
                tooltip = switch (account.type()) {
                    case OFFLINE -> "Offline: singleplayer and offline-mode servers only";
                    case MICROSOFT -> "Microsoft account (premium) - sign-in saved using " + account.tokenProtection().label();
                    case LAUNCHER -> "The account your launcher started the game with";
                };
            }
        }
        ctx.disableScissor();
        return tooltip;
    }

    private void renderButtons(GuiGraphics ctx, int mouseX, int mouseY) {
        int y = panelY + panelH - 54;
        Fonts.drawCentered(ctx, Fonts.trim(Lang.t("accounts.offline_note"), panelW - 24), width / 2, y, Theme.TEXT_DIM, false);
        int by = panelY + panelH - 34;
        int gap = 6;
        if (com.nightshade.Edition.microsoftSignIn()) {
            int w = (panelW - 32 - gap * 3) / 4;
            addMicrosoft.render(ctx, panelX + 16, by, w, 20, mouseX, mouseY);
            addOffline.render(ctx, panelX + 16 + w + gap, by, w, 20, mouseX, mouseY);
            skin.render(ctx, panelX + 16 + (w + gap) * 2, by, w, 20, mouseX, mouseY);
            done.render(ctx, panelX + 16 + (w + gap) * 3, by, w, 20, mouseX, mouseY);
        } else {
            // No in-game Microsoft sign-in in this edition: the launcher account and offline accounts.
            int w = (panelW - 32 - gap * 2) / 3;
            addOffline.render(ctx, panelX + 16, by, w, 20, mouseX, mouseY);
            skin.render(ctx, panelX + 16 + w + gap, by, w, 20, mouseX, mouseY);
            done.render(ctx, panelX + 16 + (w + gap) * 2, by, w, 20, mouseX, mouseY);
        }
    }

    private void renderOfflineForm(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int fy = panelY + panelH - 70;
        Fonts.draw(ctx, "New offline account", panelX + 16, fy - 2, Theme.TEXT_MUTED, false);
        RenderUtil.roundedRect(ctx, panelX + 16, fy + 8, panelX + panelW - 16, fy + 26, 4, Theme.SELECTED);
        RenderUtil.roundedOutline(ctx, panelX + 16, fy + 8, panelX + panelW - 16, fy + 26, 4, Theme.accent());
        nameField.setY(fy + 13);
        nameField.render(ctx, mouseX, mouseY, delta);
        if (offlineError != null) {
            Fonts.draw(ctx, offlineError, panelX + 16, fy + 29, Theme.DANGER, false);
        }
        int by = panelY + panelH - 30;
        int w = (panelW - 32 - 6) / 2;
        confirmOffline.render(ctx, panelX + 16, by, w, 20, mouseX, mouseY);
        cancel.render(ctx, panelX + 22 + w, by, w, 20, mouseX, mouseY);
    }

    private void renderMicrosoft(GuiGraphics ctx, int mouseX, int mouseY) {
        int cx = width / 2;
        int y = listTop + 6;
        Fonts.drawCentered(ctx, Lang.t("accounts.ms_title"), cx, y, Theme.TEXT, false);
        y += 16;
        MicrosoftAuth.DeviceCode code = deviceCode;
        if (code != null) {
            Fonts.drawCentered(ctx, "1. Open " + code.verificationUri(), cx, y, Theme.TEXT_MUTED, false);
            Fonts.drawCentered(ctx, "2. Enter this code:", cx, y + 11, Theme.TEXT_MUTED, false);
            String userCode = code.userCode();
            int cw = Fonts.width(userCode) * 2 + 24;
            RenderUtil.roundedRect(ctx, cx - cw / 2, y + 26, cx + cw / 2, y + 52, 6, Theme.SELECTED);
            RenderUtil.roundedOutline(ctx, cx - cw / 2, y + 26, cx + cw / 2, y + 52, 6, Theme.accent());
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx, y + 31);
            ctx.pose().scale(2f, 2f);
            Fonts.drawCentered(ctx, userCode, 0, 0, Theme.accentLight(), false);
            ctx.pose().popMatrix();
            long seconds = Math.max(0, (code.expiresAtMillis() - System.currentTimeMillis()) / 1000L);
            Fonts.drawCentered(ctx, "Code expires in " + seconds / 60 + ":" + String.format("%02d", seconds % 60), cx, y + 57, Theme.TEXT_DIM, false);
        }
        int statusY = code != null ? y + 72 : y + 10;
        if (msError != null) {
            Fonts.drawCentered(ctx, Fonts.trim(msError, panelW - 24), cx, statusY, Theme.DANGER, false);
        } else {
            Fonts.drawCentered(ctx, msStatus, cx, statusY, Theme.accentLight(), false);
        }
        Fonts.drawCentered(ctx, Fonts.trim(Lang.t("accounts.ms_password_note"), panelW - 24), cx, panelY + panelH - 48, Theme.TEXT_DIM, false);
        int by = panelY + panelH - 30;
        int w = (panelW - 32 - 6) / 2;
        if (msError != null) {
            useLauncher.render(ctx, panelX + 16, by, w + 30, 20, mouseX, mouseY);
        } else {
            openBrowser.enabled(code != null);
            openBrowser.render(ctx, panelX + 16, by, w + 30, 20, mouseX, mouseY);
        }
        cancel.render(ctx, panelX + 22 + w + 30, by, w - 30, 20, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.LIST) {
            if ((com.nightshade.Edition.microsoftSignIn() && addMicrosoft.click(mouseX, mouseY, button)) || addOffline.click(mouseX, mouseY, button) || done.click(mouseX, mouseY, button)
                    || skin.click(mouseX, mouseY, button)) {
                return true;
            }
            if (button == 0 && mouseY >= listTop && mouseY < listBottom) {
                List<Account> list = accounts().accounts();
                for (int i = 0; i < list.size(); i++) {
                    Account account = list.get(i);
                    int y = listTop + i * (ROW_H + 4) - (int) scroll;
                    int x1 = panelX + 12;
                    int x2 = panelX + panelW - 12;
                    if (!RenderUtil.isHovered(mouseX, mouseY, x1, y, x2, y + ROW_H)) {
                        continue;
                    }
                    if (account.type() != Account.Type.LAUNCHER && RenderUtil.isHovered(mouseX, mouseY, x2 - 22, y + 9, x2 - 8, y + 23)) {
                        accounts().remove(account);
                    } else if (account != accounts().active()) {
                        use(account);
                    }
                    return true;
                }
            }
        } else if (mode == Mode.ADD_OFFLINE) {
            if (confirmOffline.click(mouseX, mouseY, button) || cancel.click(mouseX, mouseY, button)) {
                return true;
            }
        } else if ((msError != null ? useLauncher.click(mouseX, mouseY, button) : openBrowser.click(mouseX, mouseY, button))
                || cancel.click(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * 18;
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (mode == Mode.ADD_OFFLINE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitOffline();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setMode(Mode.LIST);
                return true;
            }
        }
        if (mode == Mode.MICROSOFT && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelMode();
            return true;
        }
        return false;
    }

    /** Test hook: adds an offline account through the same path as the form. */
    public String submitOfflineForTest(String name) {
        setMode(Mode.ADD_OFFLINE);
        nameField.setValue(name);
        submitOffline();
        return offlineError;
    }

    /** Test hook: starts the Microsoft flow and exposes the device code once received. */
    public void startMicrosoftForTest() {
        startMicrosoft();
    }

    @Nullable
    public MicrosoftAuth.DeviceCode deviceCodeForTest() {
        return deviceCode;
    }

    public void cancelForTest() {
        cancelMode();
    }
}
