package com.nightshade.gui.menu;

import com.nightshade.render.BrandLogo;
import com.nightshade.gui.NsScreen;
import com.mojang.realmsclient.RealmsMainScreen;
import com.nightshade.NightshadeClient;
import com.nightshade.account.Account;
import com.nightshade.account.AccountManager;
import com.nightshade.gui.ClickGuiScreen;
import com.nightshade.gui.GuiDraw;
import com.nightshade.gui.mods.ModManagerScreen;
import com.nightshade.module.interfaces.MainMenuModule;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Lang;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Nightshade main menu: panorama background, navigation rail, account card with quick switching, and access to
 * the mod manager and client settings. Replaces the vanilla title screen while the Main Menu module is enabled;
 * the vanilla screen stays one click away for compatibility with mods that extend it.
 */
public final class ClassicMenuScreen extends NsScreen {
    private static final int RAIL_W = 168;
    private static final int MAX_ITEM_H = 24;
    private static final int MIN_ITEM_H = 17;

    /** Set when the user explicitly asks for the vanilla title screen, so the replacement mixin lets it through once. */

    private final List<NavItem> items = new ArrayList<>();
    private final Animation fade = new Animation(6f, 0f);
    private final Animation accountHover = new Animation(16f, 0f);
    private final Animation vanillaHover = new Animation(16f, 0f);
    private final int modCount;
    private int accountX1;
    private int accountY1;
    private int accountX2;
    private int accountY2;
    private int vanillaX;
    private int vanillaY;
    private int vanillaW;

    private record NavItem(String label, Runnable action, java.util.function.BooleanSupplier enabled, String disabledReason, Animation hover) {
    }

    public ClassicMenuScreen() {
        super(Component.literal("Nightshade Client"));
        modCount = (int) FabricLoader.getInstance().getAllMods().stream().filter(m -> m.getContainingMod().isEmpty()).count();
        fade.setTarget(1f);
    }

    private AccountManager accounts() {
        return NightshadeClient.client().accounts();
    }

    @Override
    protected void init() {
        items.clear();
        add("Singleplayer", () -> minecraft.gui.setScreen(new SelectWorldScreen(this)));
        add("Multiplayer", () -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this)));
        MainMenuModule module = NightshadeClient.client().modules().get(MainMenuModule.class);
        if (module == null || module.showRealms()) {
            items.add(new NavItem("Realms", () -> minecraft.gui.setScreen(new RealmsMainScreen(this)), () -> accounts().isPremium(),
                    Lang.t("menu.realms_needs_ms"), new Animation(18f, 0f)));
        }
        add("Mods", () -> minecraft.gui.setScreen(new ModManagerScreen(this)));
        add("Accounts", () -> minecraft.gui.setScreen(new AccountsScreen(this)));
        add("Client Settings", () -> minecraft.gui.setScreen(new ClickGuiScreen(this)));
        add("Options", () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, false)));
        add("Quit Game", () -> minecraft.stop());
    }

    /** Translated label for a navigation entry id. */
    private static String display(String id) {
        return Lang.t("menu." + id.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
    }

    private void add(String label, Runnable action) {
        items.add(new NavItem(label, action, () -> true, null, new Animation(18f, 0f)));
    }

    private int brandScaleTop() {
        return height < 300 ? 14 : 26;
    }

    private int navTop() {
        int brandBottom = brandScaleTop() + 34;
        int itemH = itemHeight();
        int total = items.size() * itemH;
        int bottomLimit = height - 30;
        return Math.max(brandBottom + 8, Math.min(height / 2 - total / 2 + 10, bottomLimit - total));
    }

    private int itemHeight() {
        int available = height - 30 - (brandScaleTop() + 42);
        return Math.max(MIN_ITEM_H, Math.min(MAX_ITEM_H, available / Math.max(1, items.size())));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractPanorama(ctx, delta);
        // Violet wash: dark on the navigation side, fading toward the panorama.
        int split = Math.min(width, RAIL_W + 160);
        for (int x = 0; x < split; x += 2) {
            float t = x / (float) split;
            int alpha = (int) (0xE8 - t * 0xC0);
            ctx.fill(x, 0, Math.min(x + 2, split), height, ColorUtil.withAlpha(0x0E081A, Math.max(0x28, alpha)));
        }
        ctx.fill(split, 0, width, height, 0x280E081A);
        ctx.fillGradient(0, height - 70, width, height, 0x00000000, 0x9A0E081A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractBackground(ctx, mouseX, mouseY, delta);
        float f = fade.update();
        int offset = Math.round((1f - f) * 12f);

        // Brand
        int brandX = 22 - offset;
        int brandY = brandScaleTop();
        BrandLogo.draw(ctx, brandX - 3, brandY - 4, 24, ColorUtil.fade(0xFFFFFFFF, f));
        ctx.pose().pushMatrix();
        ctx.pose().translate(brandX + 26, brandY - 1);
        ctx.pose().scale(1.6f, 1.6f);
        Fonts.draw(ctx, "Nightshade", 0, 0, ColorUtil.fade(Theme.accentLight(), f), true);
        ctx.pose().popMatrix();
        Fonts.draw(ctx, "C L I E N T", brandX + 27, brandY + 15, ColorUtil.fade(Theme.TEXT_DIM, f), false);

        // Navigation
        int top = navTop();
        int itemH = itemHeight();
        String tooltip = null;
        for (int i = 0; i < items.size(); i++) {
            NavItem item = items.get(i);
            int y = top + i * itemH;
            boolean enabled = item.enabled().getAsBoolean();
            boolean hovered = RenderUtil.isHovered(mouseX, mouseY, 14, y, RAIL_W, y + itemH - 2);
            float h = item.hover().setTarget(hovered && enabled ? 1f : 0f).update();
            if (hovered && !enabled) {
                tooltip = item.disabledReason();
            }
            if (h > 0.01f) {
                RenderUtil.roundedRect(ctx, 14, y, RAIL_W, y + itemH - 2, 5, ColorUtil.fade(0xB0362260, h));
                RenderUtil.roundedRect(ctx, 14, y + 5, 16, y + itemH - 7, 1, ColorUtil.fade(Theme.accent(), h));
            }
            boolean quit = item.label().equals("Quit Game");
            int color = !enabled ? Theme.TEXT_DIM : quit ? ColorUtil.lerp(0xFFD9667E, 0xFFFF8FA3, h) : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, h);
            Fonts.draw(ctx, display(item.label()), 24 + Math.round(h * 4f) - offset, y + (itemH - 2 - Fonts.height()) / 2 + 1, ColorUtil.fade(color, f), false);
            if (item.label().equals("Mods")) {
                String count = Integer.toString(modCount);
                int cw = Fonts.width(count) + 6;
                int by = y + (itemH - 2 - 11) / 2;
                RenderUtil.roundedRect(ctx, RAIL_W - 8 - cw, by, RAIL_W - 8, by + 11, 3, ColorUtil.withAlpha(Theme.accent(), 0x55));
                Fonts.draw(ctx, count, RAIL_W - 5 - cw, by + 2, Theme.TEXT, false);
            }
        }

        renderAccountCard(ctx, mouseX, mouseY);

        // Footer (right)
        String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  |  Fabric  |  " + modCount + " mods";
        Fonts.draw(ctx, version, width - Fonts.width(version) - 8, height - 22, Theme.TEXT_MUTED, true);
        String copyright = "Copyright Mojang AB. Do not distribute!";
        Fonts.draw(ctx, copyright, width - Fonts.width(copyright) - 8, height - 11, Theme.TEXT_DIM, true);

        // Vanilla menu shortcut (bottom left, under the navigation)
        String vanilla = Lang.t("menu.vanilla");
        vanillaW = Fonts.width(vanilla) + 12;
        vanillaX = 14;
        vanillaY = height - 21;
        boolean vh = RenderUtil.isHovered(mouseX, mouseY, vanillaX, vanillaY, vanillaX + vanillaW, vanillaY + 15);
        float vp = vanillaHover.setTarget(vh ? 1f : 0f).update();
        RenderUtil.roundedRect(ctx, vanillaX, vanillaY, vanillaX + vanillaW, vanillaY + 15, 4, ColorUtil.lerp(0x900E081A, 0xD0231740, vp));
        RenderUtil.roundedOutline(ctx, vanillaX, vanillaY, vanillaX + vanillaW, vanillaY + 15, 4, Theme.BORDER);
        Fonts.draw(ctx, vanilla, vanillaX + 6, vanillaY + 4, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, vp), false);

        if (tourActive()) {
            renderTour(ctx, mouseX, mouseY);
        }
        Notifications.renderIfEnabled(ctx);
        if (tooltip != null && !tourActive()) {
            GuiDraw.tooltip(ctx, tooltip, mouseX, mouseY, width, height);
        }
    }

    private void renderAccountCard(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Account account = accounts().active();
        accountX2 = width - 8;
        accountX1 = Math.max(RAIL_W + 20, accountX2 - 160);
        accountY1 = 8;
        accountY2 = accountY1 + 38;
        boolean hovered = RenderUtil.isHovered(mouseX, mouseY, accountX1, accountY1, accountX2, accountY2);
        float h = accountHover.setTarget(hovered ? 1f : 0f).update();
        RenderUtil.roundedRect(ctx, accountX1, accountY1, accountX2, accountY2, 6, ColorUtil.lerp(0xC0140B24, 0xE0231740, h));
        RenderUtil.roundedOutline(ctx, accountX1, accountY1, accountX2, accountY2, 6, ColorUtil.lerp(Theme.BORDER, ColorUtil.withAlpha(Theme.accent(), 0xC0), h));
        PlayerFaceExtractor.extractRenderState(ctx, accounts().skin(account), accountX1 + 8, accountY1 + 7, 24);
        int textX = accountX1 + 40;
        Fonts.draw(ctx, Fonts.trim(account.name(), accountX2 - textX - 8), textX, accountY1 + 8, Theme.TEXT, false);
        String badge = account.type().label();
        int bw = Fonts.width(badge) + 8;
        RenderUtil.roundedRect(ctx, textX, accountY1 + 20, textX + bw, accountY1 + 31, 3, ColorUtil.withAlpha(account.type().color(), 0x55));
        Fonts.draw(ctx, badge, textX + 4, accountY1 + 22, Theme.TEXT, false);
        String hint = accounts().isSwitching() ? Lang.t("menu.switching") : Lang.t("menu.switch");
        Fonts.draw(ctx, hint, accountX2 - Fonts.width(hint) - 8, accountY1 + 22, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accentLight(), h), false);
    }

    // ------------------------------------------------------------------ first-launch tour

    private static final String TOUR_FLAG = "tourDone";
    private static final int TOUR_STEPS = 5;
    private int tourStep = -1;
    private final com.nightshade.gui.widget.NsButton tourNext = new com.nightshade.gui.widget.NsButton("", com.nightshade.gui.widget.NsButton.Style.PRIMARY, this::nextTourStep);
    private final com.nightshade.gui.widget.NsButton tourSkip = new com.nightshade.gui.widget.NsButton("", com.nightshade.gui.widget.NsButton.Style.GHOST, this::endTour);

    private boolean tourActive() {
        if (tourStep < 0 && !NightshadeClient.client().config().hasFlag(TOUR_FLAG)) {
            tourStep = 0;
        }
        return tourStep >= 0 && tourStep < TOUR_STEPS;
    }

    private void nextTourStep() {
        tourStep++;
        if (tourStep >= TOUR_STEPS) {
            endTour();
        }
    }

    private void endTour() {
        tourStep = TOUR_STEPS;
        NightshadeClient.client().config().setFlag(TOUR_FLAG);
        NightshadeClient.client().config().saveDirty();
    }

    /** Test hooks. */
    public int tourStepForTest() {
        return tourActive() ? tourStep : -1;
    }

    public void tourNextForTest() {
        nextTourStep();
    }

    private void renderTour(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, width, height, 0x88050210);
        int top = navTop();
        int itemH = itemHeight();
        switch (tourStep) {
            case 1 -> RenderUtil.roundedOutline(ctx, 10, top - 4, RAIL_W + 4, top + items.size() * itemH + 2, 6, Theme.accentLight());
            case 2 -> RenderUtil.roundedOutline(ctx, accountX1 - 3, accountY1 - 3, accountX2 + 3, accountY2 + 3, 7, Theme.accentLight());
            case 3 -> {
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).label().equals("Mods")) {
                        int y = top + i * itemH;
                        RenderUtil.roundedOutline(ctx, 10, y - 2, RAIL_W + 4, y + itemH, 6, Theme.accentLight());
                    }
                }
            }
            default -> {
            }
        }
        int cardW = Math.min(width - 40, 260);
        int cardX = Math.max(RAIL_W + 24, (width - cardW) / 2 + 60);
        if (cardX + cardW > width - 10) {
            cardX = Math.max(10, (width - cardW) / 2);
        }
        String title = Lang.t("tour.title." + tourStep);
        java.util.List<net.minecraft.util.FormattedCharSequence> body = font.split(Fonts.text(Lang.t("tour.body." + tourStep)), cardW - 24);
        int cardH = 44 + body.size() * (Fonts.height() + 2) + 22;
        int cardY = Math.max(56, height / 2 - cardH / 2);
        RenderUtil.roundedRect(ctx, cardX, cardY, cardX + cardW, cardY + cardH, 8, Theme.BACKGROUND);
        RenderUtil.roundedOutline(ctx, cardX, cardY, cardX + cardW, cardY + cardH, 8, Theme.accent());
        Fonts.draw(ctx, title, cardX + 12, cardY + 12, Theme.TEXT, false);
        int y = cardY + 28;
        for (net.minecraft.util.FormattedCharSequence line : body) {
            ctx.text(font, line, cardX + 12, y, Theme.TEXT_MUTED, false);
            y += Fonts.height() + 2;
        }
        for (int i = 0; i < TOUR_STEPS; i++) {
            int dx = cardX + 12 + i * 9;
            RenderUtil.roundedRect(ctx, dx, cardY + cardH - 16, dx + 6, cardY + cardH - 10, 2, i == tourStep ? Theme.accent() : Theme.SWITCH_OFF);
        }
        tourNext.label(tourStep == TOUR_STEPS - 1 ? Lang.t("tour.done") : Lang.t("tour.next"));
        tourSkip.label(Lang.t("tour.skip"));
        int nw = Math.max(60, tourNext.preferredWidth());
        tourNext.render(ctx, cardX + cardW - 12 - nw, cardY + cardH - 22, nw, 16, mouseX, mouseY);
        if (tourStep < TOUR_STEPS - 1) {
            int sw = Math.max(50, tourSkip.preferredWidth());
            tourSkip.render(ctx, cardX + cardW - 18 - nw - sw, cardY + cardH - 22, sw, 16, mouseX, mouseY);
        }
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (tourActive()) {
            if (!tourNext.click(mouseX, mouseY, button)) {
                tourSkip.click(mouseX, mouseY, button);
            }
            return true; // the tour is modal
        }
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, accountX1, accountY1, accountX2, accountY2)) {
            minecraft.gui.setScreen(new AccountsScreen(this));
            return true;
        }
        if (button == 0 && RenderUtil.isHovered(mouseX, mouseY, vanillaX, vanillaY, vanillaX + vanillaW, vanillaY + 15)) {
            NightshadeMenuScreen.vanillaRequested = true;
            minecraft.gui.setScreen(new TitleScreen());
            return true;
        }
        if (button == 0) {
            int top = navTop();
            int itemH = itemHeight();
            for (int i = 0; i < items.size(); i++) {
                int y = top + i * itemH;
                NavItem item = items.get(i);
                if (RenderUtil.isHovered(mouseX, mouseY, 14, y, RAIL_W, y + itemH - 2) && item.enabled().getAsBoolean()) {
                    item.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    /** Clicks a navigation entry by label (used by the automated test). */
    public boolean activate(String label) {
        for (NavItem item : items) {
            if (item.label().equals(label) && item.enabled().getAsBoolean()) {
                item.action().run();
                return true;
            }
        }
        return false;
    }
}
