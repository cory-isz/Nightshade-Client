package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.cosmetic.Catalog;
import com.nightshade.cosmetic.Models;
import com.nightshade.cosmetic.OwnerKey;
import com.nightshade.cosmetic.Wardrobe;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** The Wardrobe: a 3D model of you on the left (drag to spin), cosmetics on the right, bought with playtime points. */
public final class WardrobeScreen extends NsMenuScreen {
    @Nullable
    private final Screen parent;
    private Catalog.Category tab = Catalog.Category.WINGS;
    private boolean ownerTab;
    /** The shop: today's rotation and this week's picks (the only things you can buy). */
    private boolean shopTab;
    /** Shop page: clickable cards ({x1, y1, x2, y2} and the item) and the page height, for scrolling. */
    private final List<Object[]> shopHits = new java.util.ArrayList<>();
    private int shopContent;
    /** The weekly bundle card is picked (the button above then buys the bundle). */
    private boolean bundlePicked;
    private boolean confirmBundle;
    private boolean pointsPage;
    private Catalog.Item selected;
    private Catalog.Item confirm;
    private float yaw = 25f;
    private float pitch = -6f;
    private float autoSpin = 1f;
    private boolean dragging;
    private float zoom = 1f;
    /** Item grid: filter (All, Owned, Locked, Favourites), sort (Rarity, Price, Name) and scroll. */
    private static final String[] FILTERS = {"All", "Owned", "Locked", "Favourites"};
    private static final String[] SORTS = {"Rarity", "Price", "Name"};
    private int filter;
    private int sort;
    private float scroll;
    private float scrollTarget;
    private final java.util.Random rnd = new java.util.Random();
    private final NsButton wingsMode = new NsButton("", NsButton.Style.GHOST, () -> Wardrobe.previewWings = (Wardrobe.previewWings + 1) % 3);
    private final NsButton resetView = new NsButton("Reset view", NsButton.Style.GHOST, () -> {
        yaw = 25f;
        pitch = -6f;
        zoom = 1f;
        autoSpin = 1f;
    });
    private final NsButton random = new NsButton("Random", NsButton.Style.GHOST, this::randomOutfit);
    private final NsButton[] outfits = new NsButton[Wardrobe.OUTFITS];
    private final NsButton sortButton = new NsButton("", NsButton.Style.GHOST, () -> {
        sort = (sort + 1) % SORTS.length;
        scrollTarget = scroll = 0;
    });
    private final NsButton close = new NsButton("Done", NsButton.Style.PRIMARY, this::onClose);
    private final NsButton action = new NsButton("Equip", NsButton.Style.PRIMARY, this::act);
    private final NsButton confirmYes = new NsButton("Unlock and equip", NsButton.Style.PRIMARY, this::confirmUnlock);
    private final NsButton confirmNo = new NsButton("Cancel", NsButton.Style.GHOST, () -> {
        confirm = null;
        confirmBundle = false;
    });
    private final NsButton points = new NsButton("Points", NsButton.Style.GHOST, () -> pointsPage = !pointsPage);
    private final NsButton hide = new NsButton("", NsButton.Style.GHOST, () -> Wardrobe.setHidden(!Wardrobe.hidden()));
    private final NsButton[] owner = {
            new NsButton("+1,000", NsButton.Style.SECONDARY, () -> Wardrobe.ownerAdd(1000)),
            new NsButton("+10,000", NsButton.Style.SECONDARY, () -> Wardrobe.ownerAdd(10000)),
            new NsButton("+100,000", NsButton.Style.SECONDARY, () -> Wardrobe.ownerAdd(100000)),
            new NsButton("Set to 0", NsButton.Style.GHOST, () -> Wardrobe.ownerSet(0)),
            new NsButton("Unlock all", NsButton.Style.SECONDARY, Wardrobe::ownerUnlockAll)};

    public WardrobeScreen(@Nullable Screen parent) {
        super(Component.literal("Wardrobe"));
        this.parent = parent;
        Wardrobe.ensureLoaded();
        for (int i = 0; i < outfits.length; i++) {
            final int slot = i;
            outfits[i] = new NsButton("Outfit " + (i + 1), NsButton.Style.SECONDARY, () -> wearOutfit(slot));
        }
        selectFirst();
    }

    private void selectFirst() {
        if (shopTab) {
            List<Catalog.Item> shop = com.nightshade.cosmetic.Shop.listing();
            selected = shop.isEmpty() ? Catalog.in(tab).get(0) : shop.get(0);
            Wardrobe.clearPreview();
            Wardrobe.setPreview(selected.category, selected.id);
            scrollTarget = scroll = 0;
            return;
        }
        String eq = Wardrobe.equipped(tab);
        selected = eq != null ? Catalog.byId(eq) : null;
        if (selected == null || selected.category != tab) {
            selected = Catalog.in(tab).get(0);
        }
        Wardrobe.clearPreview();
        Wardrobe.setPreview(tab, selected.id);
        scrollTarget = scroll = 0;
    }

    /** The items in the current tab after the filter and sort. */
    private List<Catalog.Item> visibleItems() {
        List<Catalog.Item> out = new java.util.ArrayList<>();
        for (Catalog.Item it : shopTab ? com.nightshade.cosmetic.Shop.listing() : Catalog.in(tab)) {
            boolean owned = Wardrobe.owns(it);
            if (filter == 1 && !owned || filter == 2 && owned || filter == 3 && !Wardrobe.favourite(it)) {
                continue;
            }
            out.add(it);
        }
        if (shopTab) {
            return out; // keep the shop order: the featured deal first, the weekly picks last
        }
        if (sort == 1) {
            out.sort(java.util.Comparator.comparingInt((Catalog.Item i) -> i.price).thenComparing(i -> i.name));
        } else if (sort == 2) {
            out.sort(java.util.Comparator.comparing(i -> i.name));
        } else {
            out.sort(java.util.Comparator.comparingInt((Catalog.Item i) -> i.tier.ordinal()));
        }
        return out;
    }

    private void wearOutfit(int slot) {
        if (Wardrobe.wearOutfit(slot)) {
            Wardrobe.clearPreview();
            selectFirst();
            NightshadeClient.client().notifications().info("Outfit " + (slot + 1), Wardrobe.outfitSummary(slot));
        } else {
            NightshadeClient.client().notifications().info("Outfit " + (slot + 1) + " is empty", "Right-click it to save what you're wearing");
        }
    }

    private void randomOutfit() {
        Wardrobe.randomOutfit(rnd);
        Wardrobe.clearPreview();
        selectFirst();
    }

    @Override
    public void onClose() {
        Wardrobe.clearPreview();
        Wardrobe.save();
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ test hooks

    public void selectForTest(Catalog.Category c, String id) {
        tab = c;
        ownerTab = false;
        shopTab = false;
        pointsPage = false;
        selected = Catalog.byId(id);
        Wardrobe.setPreview(c, id);
    }

    public void setYawForTest(float y) {
        yaw = y;
        autoSpin = 0f;
    }

    public void openPointsForTest() {
        pointsPage = true;
    }

    public void openShopForTest() {
        shopTab = true;
        ownerTab = false;
        pointsPage = false;
        selectFirst();
    }

    public void pickBundleForTest() {
        bundlePicked = true;
        Wardrobe.clearPreview();
        for (Catalog.Item b : com.nightshade.cosmetic.Shop.bundle()) {
            Wardrobe.setPreview(b.category, b.id);
        }
    }

    public Catalog.Item selectedForTest() {
        return selected;
    }

    public void openOwnerForTest() {
        ownerTab = true;
        pointsPage = false;
    }

    public void askUnlockForTest() {
        confirm = selected;
    }

    public void filterForTest(int f, int sortMode) {
        filter = f;
        sort = sortMode;
        scrollTarget = scroll = 0;
    }

    public int visibleCountForTest() {
        return visibleItems().size();
    }

    public float scrollForTest(float px) {
        scrollTarget = scroll = Math.max(0, Math.min(maxScroll(), px));
        return scroll;
    }

    public int maxScrollForTest() {
        return maxScroll();
    }

    // ------------------------------------------------------------------ layout

    private int split() {
        return Math.max(220, (int) (width * 0.42f));
    }

    private int gx1() {
        return split() + 18;
    }

    private int gx2() {
        return width - 18;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int mx = mouseX;
        int my = mouseY;
        renderPage(ctx, 14, width - 14);
        drawHeader(ctx, mx, my);
        drawPreview(ctx, mx, my);
        if (pointsPage) {
            drawPoints(ctx);
        } else if (ownerTab) {
            drawOwner(ctx, mx, my);
        } else {
            drawItems(ctx, mx, my);
        }
        close.render(ctx, width - 92, height - 28, 76, 18, mx, my);
        if (confirm != null) {
            drawConfirm(ctx, mx, my);
        }
        Notifications.renderIfEnabled(ctx);
    }

    private void drawHeader(GuiGraphicsExtractor ctx, int mx, int my) {
        Fonts.draw(ctx, "Wardrobe", 18, 13, 0xFFFFFFFF, false);
        String got = Wardrobe.ownedCount() + " / " + Catalog.all().size() + " collected" + (com.nightshade.cosmetic.CloudSync.online() ? "  \u00B7  online" : "");
        Fonts.draw(ctx, got, 26 + Fonts.width("Wardrobe"), 13, Theme.TEXT_DIM, false);
        String pts = String.format("%,d", Wardrobe.points());
        int pw = Fonts.width(pts) + 52;
        int px = width - 18 - pw;
        RenderUtil.roundedRect(ctx, px, 9, px + pw, 29, 10, ColorUtil.withAlpha(Theme.PANEL, 0xE0));
        RenderUtil.roundedOutline(ctx, px, 9, px + pw, 29, 10, Theme.BORDER_STRONG);
        star(ctx, px + 8, 14, 0xFFFFC857);
        Fonts.draw(ctx, pts, px + 22, 14, 0xFFFFFFFF, false);
        Fonts.draw(ctx, "pts", px + 26 + Fonts.width(pts), 14, Theme.TEXT_DIM, false);
        points.label(pointsPage ? "Back to items" : "How to earn");
        points.render(ctx, px - 92, 10, 86, 18, mx, my);
    }

    private static void star(GuiGraphicsExtractor ctx, int x, int y, int c) {
        ctx.fill(x + 4, y, x + 6, y + 10, c);
        ctx.fill(x, y + 4, x + 10, y + 6, c);
        ctx.fill(x + 2, y + 2, x + 8, y + 8, c);
    }

    // ------------------------------------------------------------------ 3D preview

    private void drawPreview(GuiGraphicsExtractor ctx, int mx, int my) {
        int x1 = 14;
        int y1 = 40;
        int x2 = split();
        int y2 = height - 36;
        RenderUtil.roundedRect(ctx, x1, y1, x2, y2, 10, ColorUtil.withAlpha(Theme.BACKGROUND, 0x90));
        int cx = (x1 + x2) / 2;
        int floor = y2 - 54;
        ctx.fill(cx - (x2 - x1) / 4, floor - 4, cx + (x2 - x1) / 4, floor, ColorUtil.withAlpha(Theme.accent(), 0x30));
        if (!dragging) {
            yaw += autoSpin * 0.35f;
        }
        AvatarRenderState s = previewState();
        int size = (int) (Math.min((x2 - x1) / 2.9f, (floor - y1 - 26) / 2.35f) * zoom);
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        rot.mul(cam);
        ctx.entity(s, size, new Vector3f(0f, s.boundingBoxHeight / 2f + 0.0625f, 0f), rot, cam, x1 + 2, y1 + 24, x2 - 2, floor + 4);
        hide.label(Wardrobe.hidden() ? "Hidden in game (click to show)" : "Hide my cosmetics in game");
        hide.render(ctx, x1 + 6, y1 + 6, Math.min(x2 - x1 - 12, Fonts.width(hide.label()) + 16), 16, mx, my);
        String hint = "Drag to spin  -  scroll to zoom";
        Fonts.draw(ctx, hint, x1 + 10, y1 + 27, Theme.TEXT_DIM, false);
        // outfit slots: click to wear, right-click to save what you have on
        int bw = (x2 - x1 - 12 - 8) / 3;
        int oy = y2 - 46;
        for (int i = 0; i < outfits.length; i++) {
            outfits[i].label((Wardrobe.hasOutfit(i) ? "" : "+ ") + "Outfit " + (i + 1));
            outfits[i].render(ctx, x1 + 6 + i * (bw + 4), oy, bw, 18, mx, my);
            if (outfits[i].isHovered(mx, my)) {
                String sum = Wardrobe.hasOutfit(i) ? Wardrobe.outfitSummary(i) : "Right-click to save what you're wearing";
                Fonts.drawCentered(ctx, Fonts.trim(sum, x2 - x1 - 16), cx, oy - 12, Theme.TEXT, false);
            }
        }
        wingsMode.label("Wings: " + new String[]{"Moving", "Folded", "Spread"}[Wardrobe.previewWings]);
        int cw = (x2 - x1 - 12 - 8) / 3;
        wingsMode.render(ctx, x1 + 6, y2 - 24, cw, 18, mx, my);
        resetView.render(ctx, x1 + 10 + cw, y2 - 24, cw, 18, mx, my);
        random.render(ctx, x1 + 14 + cw * 2, y2 - 24, cw, 18, mx, my);
    }

    private AvatarRenderState previewState() {
        AvatarRenderState s = new AvatarRenderState();
        s.entityType = EntityType.PLAYER;
        s.id = Wardrobe.PREVIEW_ID;
        s.skin = skin();
        s.ageInTicks = (System.currentTimeMillis() % 3_600_000L) / 50f;
        s.bodyRot = 180f + yaw;
        s.yRot = 0f;
        s.xRot = 0f;
        s.scale = 1f;
        s.ageScale = 1f;
        s.boundingBoxWidth = 0.6f;
        s.boundingBoxHeight = 1.8f;
        s.eyeHeight = 1.62f;
        s.pose = Pose.STANDING;
        s.lightCoords = 0xF000F0;
        s.showHat = s.showJacket = s.showLeftPants = s.showRightPants = s.showLeftSleeve = s.showRightSleeve = true;
        String cape = Wardrobe.shown(Catalog.Category.CAPES, true);
        Catalog.Item capeItem = Catalog.byId(cape);
        if (capeItem != null && capeItem.classicCape != null) {
            net.minecraft.core.ClientAsset.Texture tex = new net.minecraft.core.ClientAsset.ResourceTexture(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("nightshade", "cape/" + capeItem.classicCape.toLowerCase(java.util.Locale.ROOT)),
                    com.nightshade.module.visual.Cosmetics.capeTexture(capeItem.classicCape));
            s.skin = new PlayerSkin(s.skin.body(), tex, s.skin.elytra(), s.skin.model(), s.skin.secure());
            s.showCape = true;
            s.capeLean = 6f;
        }
        return s;
    }

    private PlayerSkin skin() {
        if (minecraft.player != null) {
            return minecraft.player.getSkin();
        }
        try {
            return NightshadeClient.client().accounts().skin(NightshadeClient.client().accounts().active());
        } catch (RuntimeException e) {
            return net.minecraft.client.resources.DefaultPlayerSkin.get(minecraft.getUser().getProfileId());
        }
    }

    // ------------------------------------------------------------------ item grid

    private static final int GRID_TOP = 122;

    private int cols() {
        return Math.max(3, (gx2() - gx1()) / 80);
    }

    /** How far the grid can scroll, worked out from the items shown (not from the last frame drawn). */
    private int maxScroll() {
        if (shopTab) {
            return Math.max(0, shopContent - (height - 36 - (GRID_TOP - 20)));
        }
        int cols = cols();
        int rows = (visibleItems().size() + cols - 1) / cols;
        return Math.max(0, rows * (64 + 6) - 6 - (height - 36 - GRID_TOP));
    }

    /** Tab padding that keeps every category tab (and the Owner tab) inside the panel. */
    private int tabPad() {
        int n = Catalog.Category.values().length + 1 + (OwnerKey.present() ? 1 : 0);
        int text = Fonts.width("Shop") + (OwnerKey.present() ? Fonts.width("Owner") : 0);
        for (Catalog.Category c : Catalog.Category.values()) {
            text += Fonts.width(c.title);
        }
        return Math.max(6, Math.min(20, (gx2() - gx1() - text - 4 * (n - 1)) / n));
    }

    /** True when the filter chips and the sort button would overlap at full length. */
    private static final String[][] FILTER_LABELS = {{"All", "Owned", "Locked", "Favourites"}, {"All", "Owned", "Locked", "Faves"}, {"All", "Own", "Lock", "Fav"}};

    /** How short the filter and sort labels must be to fit: 0 = full, 1 = "Faves" and no "Sort:", 2 = shortest. */
    private int filterTier() {
        int room = gx2() - gx1();
        for (int tier = 0; tier < FILTER_LABELS.length; tier++) {
            int need = Fonts.width(tier == 0 ? "Sort: " + SORTS[sort] : SORTS[sort]) + 16 + 6;
            for (String f : FILTER_LABELS[tier]) {
                need += Fonts.width(f) + (tier == 0 ? 18 : 6) + 4;
            }
            if (need <= room) {
                return tier;
            }
        }
        return FILTER_LABELS.length - 1;
    }

    private boolean compactFilters() {
        return filterTier() > 0;
    }

    /** Chip padding that keeps the filter chips clear of the sort button. */
    private int chipPad() {
        int text = 0;
        for (int i = 0; i < FILTERS.length; i++) {
            text += Fonts.width(filterLabel(i));
        }
        int avail = gx2() - gx1() - (Fonts.width(sortLabel()) + 16) - 6 - 4 * (FILTERS.length - 1);
        return Math.max(4, Math.min(14, (avail - text) / FILTERS.length));
    }

    private String filterLabel(int i) {
        int tier = filterTier();
        return tier == 0 ? FILTERS[i] : FILTER_LABELS[tier][i];
    }

    private String sortLabel() {
        return compactFilters() ? SORTS[sort] : "Sort: " + SORTS[sort];
    }

    private void drawItems(GuiGraphicsExtractor ctx, int mx, int my) {
        int x1 = gx1();
        int x2 = gx2();
        int y = 40;
        int tx = x1;
        {
            int w = Fonts.width("Shop") + tabPad();
            boolean hov = mx >= tx && mx < tx + w && my >= y && my < y + 18;
            RenderUtil.roundedRect(ctx, tx, y, tx + w, y + 18, 6, shopTab ? 0xFFFFC857 : hov ? 0xFF4A3712 : 0xFF3A2A10);
            Fonts.draw(ctx, "Shop", tx + tabPad() / 2, y + 5, shopTab ? 0xFF120D22 : 0xFFFFC857, false);
            tx += w + 4;
        }
        for (Catalog.Category c : Catalog.Category.values()) {
            int w = Fonts.width(c.title) + tabPad();
            boolean on = !shopTab && c == tab;
            boolean hov = mx >= tx && mx < tx + w && my >= y && my < y + 18;
            RenderUtil.roundedRect(ctx, tx, y, tx + w, y + 18, 6, on ? 0xFFECE8F7 : hov ? Theme.PANEL_HOVER : Theme.PANEL);
            Fonts.draw(ctx, c.title, tx + tabPad() / 2, y + 5, on ? 0xFF120D22 : Theme.TEXT, false);
            tx += w + 4;
        }
        if (OwnerKey.present()) {
            int w = Fonts.width("Owner") + tabPad();
            RenderUtil.roundedRect(ctx, tx, y, tx + w, y + 18, 6, 0xFF3A2A10);
            Fonts.draw(ctx, "Owner", tx + tabPad() / 2, y + 5, 0xFFFFC857, false);
        }
        y += 28;
        Catalog.Item s = selected;
        if (shopTab && bundlePicked) {
            drawBundleDetail(ctx, mx, my, x1, x2, y);
            drawShop(ctx, mx, my, x1, x2);
            return;
        }
        Fonts.draw(ctx, s.name, x1, y, 0xFFFFFFFF, true);
        int tw = Fonts.width(s.tier.title) + 12;
        int tagX = x1 + Fonts.width(s.name) + 8;
        RenderUtil.roundedRect(ctx, tagX, y - 2, tagX + tw, y + 10, 5, ColorUtil.withAlpha(Theme.PANEL, 0xFF));
        Fonts.draw(ctx, s.tier.title, tagX + 6, y, tierColor(s.tier), false);
        Fonts.draw(ctx, Fonts.trim(s.description, x2 - x1 - 120), x1, y + 13, Theme.TEXT_DIM, false);
        boolean owned = Wardrobe.owns(s);
        boolean worn = s.id.equals(Wardrobe.equipped(s.category));
        if (worn) {
            action.label("Take off");
        } else if (owned) {
            action.label("Equip");
        } else if (!com.nightshade.cosmetic.Shop.available(s)) {
            action.label("Not in shop");
        } else {
            action.label("Unlock " + String.format("%,d", com.nightshade.cosmetic.Shop.price(s)));
        }
        action.enabled(owned || com.nightshade.cosmetic.Shop.available(s));
        action.render(ctx, x2 - 104, y - 2, 104, 20, mx, my);
        if (!owned && !com.nightshade.cosmetic.Shop.available(s)) {
            int dd = com.nightshade.cosmetic.Shop.daysUntilBack(s);
            String when = dd == 1 ? "Back tomorrow" : dd > 1 ? "Back in " + dd + " days" : "Not in the shop";
            Fonts.draw(ctx, Fonts.trim(when, x2 - x1), x2 - Math.min(Fonts.width(when), x2 - x1), y + 22, Theme.TEXT_DIM, false);
        } else if (!owned && com.nightshade.cosmetic.Shop.discount(s) > 0) {
            String deal = com.nightshade.cosmetic.Shop.discount(s) + "% off - was " + String.format("%,d", s.price);
            Fonts.draw(ctx, deal, x2 - Fonts.width(deal), y + 22, 0xFF9FD8B0, false);
        } else if (!owned && Wardrobe.points() < com.nightshade.cosmetic.Shop.price(s)) {
            String need = String.format("About %.0f more hours of play", Math.ceil(Wardrobe.hoursNeeded(s)));
            Fonts.draw(ctx, need, x2 - Fonts.width(need), y + 22, Theme.TEXT_DIM, false);
        }
        if (shopTab) {
            drawShop(ctx, mx, my, x1, x2);
            return;
        }
        // filter chips and sort
        int fy = 102;
        int fx = x1;
        for (int i = 0; i < FILTERS.length; i++) {
            int w = Fonts.width(filterLabel(i)) + chipPad();
            boolean on = i == filter;
            boolean hov = mx >= fx && mx < fx + w && my >= fy && my < fy + 14;
            RenderUtil.roundedRect(ctx, fx, fy, fx + w, fy + 14, 6, on ? ColorUtil.withAlpha(Theme.accent(), 0xC0) : hov ? Theme.PANEL_HOVER : Theme.PANEL);
            Fonts.draw(ctx, filterLabel(i), fx + chipPad() / 2, fy + 3, on ? 0xFFFFFFFF : Theme.TEXT_DIM, false);
            fx += w + 4;
        }
        if (shopTab) {
            String reset = "New in " + com.nightshade.cosmetic.Shop.format(com.nightshade.cosmetic.Shop.millisToDailyReset());
            Fonts.draw(ctx, reset, x2 - Fonts.width(reset), fy + 3, 0xFFFFC857, false);
        } else {
            sortButton.label(sortLabel());
        int sw = Fonts.width(sortButton.label()) + 16;
        sortButton.render(ctx, x2 - sw, fy - 1, sw, 16, mx, my);
        }

        List<Catalog.Item> items = visibleItems();
        int cols = cols();
        int gap = 6;
        int cw = (x2 - x1 - gap * (cols - 1)) / cols;
        int ch = 64;
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35f;
        int top = GRID_TOP;
        int bottom = height - 36;
        if (items.isEmpty()) {
            String msg = shopTab ? "You own everything in the shop today - check back tomorrow" : filter == 3 ? "No favourites yet - click the star on an item" : filter == 1 ? "You don't own anything here yet" : "You own everything here!";
            Fonts.drawCentered(ctx, msg, (x1 + x2) / 2, top + 30, Theme.TEXT_DIM, false);
            return;
        }
        ctx.enableScissor(x1 - 2, top, x2 + 2, bottom);
        long now = System.currentTimeMillis();
        for (int i = 0; i < items.size(); i++) {
            Catalog.Item it = items.get(i);
            int cx = x1 + (i % cols) * (cw + gap);
            int cy = top + (i / cols) * (ch + gap) - Math.round(scroll);
            if (cy + ch < top || cy > bottom) {
                continue;
            }
            boolean sel = it == selected;
            boolean hov = mx >= cx && mx < cx + cw && my >= Math.max(cy, top) && my < Math.min(cy + ch, bottom);
            RenderUtil.roundedRect(ctx, cx, cy, cx + cw, cy + ch, 8, sel ? Theme.SELECTED : hov ? Theme.PANEL_HOVER : Theme.PANEL);
            int outline = sel ? Theme.accentLight() : it.tier == Catalog.Tier.MYTHIC ? mythic(now, i) : Theme.BORDER;
            RenderUtil.roundedOutline(ctx, cx, cy, cx + cw, cy + ch, 8, outline);
            thumbnail(ctx, it, cx + 4, cy + 4, cw - 8, ch - 26);
            // rarity strip along the bottom
            ctx.fill(cx + 8, cy + ch - 3, cx + cw - 8, cy + ch - 1, tierColor(it.tier));
            Fonts.drawCentered(ctx, Fonts.trim(it.name, cw - 6), cx + cw / 2, cy + ch - 21, Theme.TEXT, false);
            String label;
            int lc;
            if (it.id.equals(Wardrobe.equipped(it.category))) {
                label = "Equipped";
                lc = Theme.accentLight();
            } else if (Wardrobe.owns(it)) {
                label = it.free() ? "Free" : "Owned";
                lc = 0xFF9FD8B0;
            } else if (!com.nightshade.cosmetic.Shop.available(it)) {
                label = backShort(it);
                lc = Theme.TEXT_DIM;
            } else {
                label = String.format("%,d", com.nightshade.cosmetic.Shop.price(it));
                lc = 0xFFFFC857;
            }
            int off = Wardrobe.owns(it) ? 0 : com.nightshade.cosmetic.Shop.discount(it);
            String badge = off > 0 ? "-" + off + "%" : shopTab && com.nightshade.cosmetic.Shop.weeklyPick(it) ? "Weekly" : null;
            if (badge != null) {
                int bw = Fonts.width(badge) + 6;
                RenderUtil.roundedRect(ctx, cx + 3, cy + 3, cx + 3 + bw, cy + 14, 4, off >= com.nightshade.cosmetic.Shop.FEATURED_DISCOUNT ? 0xFFFFC857 : off > 0 ? 0xFF2E9D6A : 0xFF6A4AC0);
                Fonts.draw(ctx, badge, cx + 6, cy + 5, off >= com.nightshade.cosmetic.Shop.FEATURED_DISCOUNT ? 0xFF120D22 : 0xFFFFFFFF, false);
            }
            Fonts.drawCentered(ctx, label, cx + cw / 2, cy + ch - 12, lc, false);
            boolean fav = Wardrobe.favourite(it);
            if (fav || hov) {
                star(ctx, cx + cw - 14, cy + 4, fav ? 0xFFFFC857 : 0x80FFFFFF);
            }
        }
        ctx.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int track = bottom - top;
            int bar = Math.max(20, track * track / (track + max));
            int by = top + Math.round((track - bar) * (scroll / max));
            ctx.fill(x2 + 4, by, x2 + 6, by + bar, ColorUtil.withAlpha(Theme.accentLight(), 0xA0));
        }
    }


    // ------------------------------------------------------------------ shop page

    private static final int GOLD = 0xFFFFC857;

    /** The storefront: the featured deal, today's items and this week's Legendary and Mythic picks. */
    private void drawShop(GuiGraphicsExtractor ctx, int mx, int my, int x1, int x2) {
        shopHits.clear();
        int top = GRID_TOP - 20;
        int bottom = height - 36;
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35f;
        long now = System.currentTimeMillis();
        ctx.enableScissor(x1 - 2, top, x2 + 4, bottom);
        int y = top - Math.round(scroll);
        // header: what this is and when it changes
        Fonts.draw(ctx, "Today's shop", x1, y + 3, GOLD, false);
        String when = "Daily items " + com.nightshade.cosmetic.Shop.format(com.nightshade.cosmetic.Shop.millisToDailyReset())
                + "  \u00B7  Weekly " + com.nightshade.cosmetic.Shop.format(com.nightshade.cosmetic.Shop.millisToWeeklyReset());
        int room = x2 - x1 - Fonts.width("Today's shop") - 10;
        String shown = Fonts.width(when) <= room ? when : Fonts.trim("New items in " + com.nightshade.cosmetic.Shop.format(com.nightshade.cosmetic.Shop.millisToDailyReset()), room);
        Fonts.draw(ctx, shown, x2 - Fonts.width(shown), y + 3, Theme.TEXT_DIM, false);
        y += 16;
        // featured deal
        Catalog.Item featured = com.nightshade.cosmetic.Shop.featured();
        if (featured != null) {
            shopCard(ctx, featured, x1, y, x2, y + 64, mx, my, top, bottom, "FEATURED DEAL", now);
            y += 64 + 8;
        }
        Catalog.Item flash = com.nightshade.cosmetic.Shop.flash();
        if (flash != null) {
            String ends = "FLASH DEAL  \u00B7  ends in " + com.nightshade.cosmetic.Shop.format(com.nightshade.cosmetic.Shop.millisToFlashEnd());
            shopCard(ctx, flash, x1, y, x2, y + 50, mx, my, top, bottom, ends, now);
            y += 50 + 12;
        }
        // today
        List<Catalog.Item> daily = com.nightshade.cosmetic.Shop.daily();
        if (daily.size() > 1) {
            Fonts.draw(ctx, "Today", x1, y, Theme.TEXT, false);
            y += 12;
            int n = daily.size() - 1;
            int gap = 6;
            int cols = Math.max(2, Math.min(n, (x2 - x1 + gap) / (74 + gap)));
            int cw = (x2 - x1 - gap * (cols - 1)) / cols;
            int ch = 74;
            for (int i = 0; i < n; i++) {
                int cx = x1 + (i % cols) * (cw + gap);
                int cy = y + (i / cols) * (ch + gap);
                shopCard(ctx, daily.get(i + 1), cx, cy, cx + cw, cy + ch, mx, my, top, bottom, null, now);
            }
            y += ((n + cols - 1) / cols) * (ch + gap) + 6;
        }
        // this week
        List<Catalog.Item> weekly = com.nightshade.cosmetic.Shop.weekly();
        if (!weekly.isEmpty()) {
            Fonts.draw(ctx, "This week", x1, y, Theme.TEXT, false);
            y += 12;
            boolean side = x2 - x1 >= 300;
            int gap = 6;
            int cw = side ? (x2 - x1 - gap) / 2 : x2 - x1;
            int ch = 56;
            for (int i = 0; i < weekly.size(); i++) {
                int cx = side ? x1 + (i % 2) * (cw + gap) : x1;
                int cy = side ? y + (i / 2) * (ch + gap) : y + i * (ch + gap);
                shopCard(ctx, weekly.get(i), cx, cy, cx + cw, cy + ch, mx, my, top, bottom, "WEEKLY PICK", now);
            }
            y += (side ? (weekly.size() + 1) / 2 : weekly.size()) * (ch + gap);
        }
        List<Catalog.Item> bundle = com.nightshade.cosmetic.Shop.bundle();
        if (!bundle.isEmpty()) {
            y += 6;
            Fonts.draw(ctx, "Weekly bundle", x1, y, Theme.TEXT, false);
            y += 12;
            bundleCard(ctx, bundle, x1, y, x2, y + 60, mx, my, top, bottom);
            y += 60 + 10;
        }
        Catalog.Item next = com.nightshade.cosmetic.Shop.tomorrowsFeatured();
        if (next != null) {
            String tease = "Tomorrow's deal: a " + next.tier.title + " item from " + next.category.title + " - " + com.nightshade.cosmetic.Shop.FEATURED_DISCOUNT + "% off";
            Fonts.draw(ctx, Fonts.trim(tease, x2 - x1), x1, y, Theme.TEXT_DIM, false);
            y += 14;
        }
        ctx.disableScissor();
        shopContent = y + Math.round(scroll) - top;
        int max = maxScroll();
        if (max > 0) {
            int track = bottom - top;
            int bar = Math.max(20, track * track / (track + max));
            int by = top + Math.round((track - bar) * (scroll / max));
            ctx.fill(x2 + 4, by, x2 + 6, by + bar, ColorUtil.withAlpha(Theme.accentLight(), 0xA0));
        }
    }

    /** One shop card. Wide cards put the picture on the left; small ones put it on top. */
    private void shopCard(GuiGraphicsExtractor ctx, Catalog.Item it, int x1, int y1, int x2, int y2, int mx, int my, int top, int bottom, String label, long now) {
        if (y2 < top || y1 > bottom) {
            return;
        }
        boolean owned = Wardrobe.owns(it);
        boolean sel = it == selected;
        boolean hov = mx >= x1 && mx < x2 && my >= Math.max(y1, top) && my < Math.min(y2, bottom);
        boolean featured = it == com.nightshade.cosmetic.Shop.featured();
        int bg = featured ? (hov ? 0xFF3A2E12 : 0xFF2C2310) : sel ? Theme.SELECTED : hov ? Theme.PANEL_HOVER : Theme.PANEL;
        RenderUtil.roundedRect(ctx, x1, y1, x2, y2, 9, bg);
        if (featured) { // a soft gold shine across the top
            RenderUtil.horizontalGradient(ctx, x1 + 8, y1 + 1, x2 - 8, y1 + 2, 0x00FFC857, 0xA0FFC857);
        }
        int outline = sel ? Theme.accentLight() : featured ? GOLD : it.tier == Catalog.Tier.MYTHIC ? mythic(now, 3) : it.tier == Catalog.Tier.LEGENDARY ? 0xC0FFC857 : Theme.BORDER;
        RenderUtil.roundedOutline(ctx, x1, y1, x2, y2, 9, outline);
        int w = x2 - x1;
        int h = y2 - y1;
        boolean wide = w > h * 2;
        if (wide) {
            int ts = h - 10;
            RenderUtil.roundedRect(ctx, x1 + 5, y1 + 5, x1 + 5 + ts, y1 + 5 + ts, 6, 0x40000000);
            thumbnail(ctx, it, x1 + 7, y1 + 7, ts - 4, ts - 4);
            int tx = x1 + ts + 14;
            int tw = x2 - 8 - tx;
            int ty = y1 + 7;
            if (label != null) {
                Fonts.draw(ctx, label, tx, ty, featured ? GOLD : 0xFFB794FF, false);
                ty += 11;
            }
            Fonts.draw(ctx, Fonts.trim(it.name, tw - 46), tx, ty, 0xFFFFFFFF, true);
            String tier = it.tier.title;
            Fonts.draw(ctx, tier, Math.min(tx + Fonts.width(it.name) + 6, x2 - 8 - Fonts.width(tier)), ty, tierColor(it.tier), false);
            ty += 11;
            if (featured && h >= 60) {
                Fonts.draw(ctx, Fonts.trim(it.description, tw), tx, ty, Theme.TEXT_DIM, false);
            }
            price(ctx, it, tx, y2 - 14, false);
        } else {
            thumbnail(ctx, it, x1 + 4, y1 + 4, w - 8, h - 32);
            Fonts.drawCentered(ctx, Fonts.trim(it.name, w - 6), (x1 + x2) / 2, y2 - 26, Theme.TEXT, false);
            price(ctx, it, (x1 + x2) / 2, y2 - 14, true);
            ctx.fill(x1 + 9, y2 - 3, x2 - 9, y2 - 1, tierColor(it.tier));
        }
        int off = owned ? 0 : com.nightshade.cosmetic.Shop.discount(it);
        if (off > 0) { // discount ribbon in the corner
            String r = "-" + off + "%";
            int rw = Fonts.width(r) + 8;
            RenderUtil.roundedRect(ctx, x2 - rw - 4, y1 + 4, x2 - 4, y1 + 15, 5, off >= com.nightshade.cosmetic.Shop.FEATURED_DISCOUNT ? GOLD : 0xFF2E9D6A);
            Fonts.draw(ctx, r, x2 - rw, y1 + 6, off >= com.nightshade.cosmetic.Shop.FEATURED_DISCOUNT ? 0xFF120D22 : 0xFFFFFFFF, false);
        }
        if (Wardrobe.favourite(it)) {
            star(ctx, x1 + 5, y1 + 5, GOLD);
        }
        shopHits.add(new Object[]{new int[]{x1, Math.max(y1, top), x2, Math.min(y2, bottom)}, it});
    }


    /** The detail row while the bundle is picked: what's in it and the button to buy it. */
    private void drawBundleDetail(GuiGraphicsExtractor ctx, int mx, int my, int x1, int x2, int y) {
        List<Catalog.Item> items = com.nightshade.cosmetic.Shop.bundle();
        Fonts.draw(ctx, "Weekly bundle", x1, y, 0xFFFFFFFF, true);
        int tagX = x1 + Fonts.width("Weekly bundle") + 8;
        String tag = com.nightshade.cosmetic.Shop.BUNDLE_DISCOUNT + "% off";
        RenderUtil.roundedRect(ctx, tagX, y - 2, tagX + Fonts.width(tag) + 12, y + 10, 5, 0xFF2E9D6A);
        Fonts.draw(ctx, tag, tagX + 6, y, 0xFFFFFFFF, false);
        StringBuilder names = new StringBuilder();
        for (Catalog.Item i : items) {
            names.append(names.length() == 0 ? "" : ", ").append(i.name).append(Wardrobe.owns(i) ? " (owned)" : "");
        }
        Fonts.draw(ctx, Fonts.trim(names.toString(), x2 - x1 - 120), x1, y + 13, Theme.TEXT_DIM, false);
        boolean all = items.stream().allMatch(Wardrobe::owns);
        int price = com.nightshade.cosmetic.Shop.bundlePrice(Wardrobe::owns);
        action.label(all ? "Bundle owned" : "Buy bundle " + String.format("%,d", price));
        action.enabled(!all);
        action.render(ctx, x2 - 104, y - 2, 104, 20, mx, my);
        if (!all) {
            String was = "Ends in " + com.nightshade.cosmetic.Shop.format(com.nightshade.cosmetic.Shop.millisToWeeklyReset()) + " - was " + String.format("%,d", com.nightshade.cosmetic.Shop.bundleFullPrice(Wardrobe::owns));
            Fonts.draw(ctx, was, x2 - Fonts.width(was), y + 22, 0xFF9FD8B0, false);
        }
    }

    /** The bundle card: its three items side by side and one price. */
    private void bundleCard(GuiGraphicsExtractor ctx, List<Catalog.Item> items, int x1, int y1, int x2, int y2, int mx, int my, int top, int bottom) {
        if (y2 < top || y1 > bottom) {
            return;
        }
        boolean hov = mx >= x1 && mx < x2 && my >= Math.max(y1, top) && my < Math.min(y2, bottom);
        RenderUtil.roundedRect(ctx, x1, y1, x2, y2, 9, bundlePicked ? Theme.SELECTED : hov ? Theme.PANEL_HOVER : Theme.PANEL);
        RenderUtil.roundedOutline(ctx, x1, y1, x2, y2, 9, bundlePicked ? Theme.accentLight() : 0xC02E9D6A);
        int ts = y2 - y1 - 12;
        for (int i = 0; i < items.size(); i++) {
            int tx = x1 + 6 + i * (ts + 4);
            RenderUtil.roundedRect(ctx, tx, y1 + 6, tx + ts, y1 + 6 + ts, 6, 0x40000000);
            thumbnail(ctx, items.get(i), tx + 2, y1 + 8, ts - 4, ts - 4);
            if (Wardrobe.owns(items.get(i))) {
                Fonts.draw(ctx, "\u2714", tx + ts - 9, y1 + 8, 0xFF9FD8B0, true);
            }
        }
        int tx = x1 + 6 + items.size() * (ts + 4) + 4;
        Fonts.draw(ctx, "BUNDLE  \u00B7  " + com.nightshade.cosmetic.Shop.BUNDLE_DISCOUNT + "% OFF", tx, y1 + 8, 0xFF7FE0B0, false);
        Fonts.draw(ctx, Fonts.trim(items.size() + " items: " + items.get(0).category.title + ", " + (items.size() > 1 ? items.get(1).category.title : "") + (items.size() > 2 ? ", " + items.get(2).category.title : ""), x2 - 8 - tx), tx, y1 + 20, Theme.TEXT, false);
        if (items.stream().allMatch(Wardrobe::owns)) {
            Fonts.draw(ctx, "Owned", tx, y2 - 14, 0xFF9FD8B0, false);
        } else {
            String was = String.format("%,d", com.nightshade.cosmetic.Shop.bundleFullPrice(Wardrobe::owns));
            Fonts.draw(ctx, was, tx, y2 - 14, Theme.TEXT_DIM, false);
            ctx.fill(tx - 1, y2 - 11, tx + Fonts.width(was), y2 - 10, Theme.TEXT_DIM);
            Fonts.draw(ctx, String.format("%,d", com.nightshade.cosmetic.Shop.bundlePrice(Wardrobe::owns)), tx + Fonts.width(was) + 5, y2 - 14, GOLD, false);
        }
        shopHits.add(new Object[]{new int[]{x1, Math.max(y1, top), x2, Math.min(y2, bottom)}, null});
    }

    /** "back tomorrow" / "back in 5 days" for something that isn't in the shop. */
    private static String backIn(Catalog.Item it) {
        int d = com.nightshade.cosmetic.Shop.daysUntilBack(it);
        return d == 1 ? "it's back in the shop tomorrow" : d > 1 ? "it's back in the shop in " + d + " days" : "it comes back in a later rotation";
    }

    private static String backShort(Catalog.Item it) {
        int d = com.nightshade.cosmetic.Shop.daysUntilBack(it);
        return d == 1 ? "Back tomorrow" : d > 1 ? "Back in " + d + "d" : "Not in shop";
    }

    /** "Owned", or the price - with the old price crossed out when it is on sale. */
    private static void price(GuiGraphicsExtractor ctx, Catalog.Item it, int x, int y, boolean centred) {
        if (Wardrobe.owns(it)) {
            String o = it.id.equals(Wardrobe.equipped(it.category)) ? "Equipped" : "Owned";
            int ox = centred ? x - Fonts.width(o) / 2 : x;
            Fonts.draw(ctx, o, ox, y, o.equals("Equipped") ? Theme.accentLight() : 0xFF9FD8B0, false);
            return;
        }
        String now = String.format("%,d", com.nightshade.cosmetic.Shop.price(it));
        String was = com.nightshade.cosmetic.Shop.discount(it) > 0 ? String.format("%,d", it.price) : null;
        int total = Fonts.width(now) + (was == null ? 0 : Fonts.width(was) + 5);
        int px = centred ? x - total / 2 : x;
        if (was != null) {
            Fonts.draw(ctx, was, px, y, Theme.TEXT_DIM, false);
            ctx.fill(px - 1, y + 3, px + Fonts.width(was), y + 4, Theme.TEXT_DIM); // crossed out
            px += Fonts.width(was) + 5;
        }
        Fonts.draw(ctx, now, px, y, GOLD, false);
    }

    /** Mythic items get a slowly shifting rainbow outline. */
    private static int mythic(long now, int i) {
        float h = ((now % 4000L) / 4000f + i * 0.07f) % 1f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(h, 0.55f, 1f) & 0xFFFFFF);
    }

    private static int tierColor(Catalog.Tier t) {
        return t == Catalog.Tier.MYTHIC ? mythic(System.currentTimeMillis(), 0) : t.color;
    }

    private void thumbnail(GuiGraphicsExtractor ctx, Catalog.Item it, int x, int y, int w, int h) {
        if (it.id.startsWith("cape_") && com.nightshade.cosmetic.FlatCape.has(it.id.substring(5))) {
            int cw = Math.min(w, h * 10 / 17);
            int ch2 = cw * 17 / 10;
            int cx = x + (w - cw) / 2;
            int cy = y + (h - ch2) / 2;
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, com.nightshade.cosmetic.FlatCape.texture(it.id.substring(5)), cx, cy, 0f, 0f, cw, ch2, com.nightshade.cosmetic.FlatCape.FRONT_W, com.nightshade.cosmetic.FlatCape.FRONT_H, com.nightshade.cosmetic.FlatCape.TEX_W, com.nightshade.cosmetic.FlatCape.TEX_H);
            return;
        }
        net.minecraft.resources.Identifier thumb = com.nightshade.cosmetic.Thumbnails.get(it);
        if (thumb != null) {
            int tw = com.nightshade.cosmetic.Thumbnails.W, th = com.nightshade.cosmetic.Thumbnails.H;
            float sc = Math.min(w / (float) tw, h / (float) th);
            int dw = Math.round(tw * sc), dh = Math.round(th * sc);
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, thumb, x + (w - dw) / 2, y + (h - dh) / 2, 0f, 0f, dw, dh, tw, th, tw, th);
            return;
        }
        {
            if (it.classicCape != null) {
                int cw = Math.min(w, h * 10 / 16);
                int ch2 = cw * 16 / 10;
                int cx = x + (w - cw) / 2;
                int cy = y + (h - ch2) / 2;
                ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, com.nightshade.module.visual.Cosmetics.capeTexture(it.classicCape), cx, cy, 1f, 1f, cw, ch2, 10, 16, 64, 32);
            } else {
                // aura: a sprinkle of its colours
                int[] cols = com.nightshade.cosmetic.CosmeticLayer.auraColors(it.id);
                double t = System.currentTimeMillis() / 900.0;
                for (int i = 0; i < 12; i++) {
                    double a = i * 0.52 + t * (i % 2 == 0 ? 1 : -0.7);
                    int sx = x + w / 2 + (int) (Math.cos(a) * w * 0.3);
                    int sy = y + h / 2 + (int) (Math.sin(a * 1.3) * h * 0.32);
                    int sz = 2 + (i % 3 == 0 ? 1 : 0);
                    ctx.fill(sx, sy, sx + sz, sy + sz, cols[i % cols.length]);
                }
            }
            return;
        }
    }

    // ------------------------------------------------------------------ points page and owner tools

    private void drawPoints(GuiGraphicsExtractor ctx) {
        int x1 = gx1();
        int y = 44;
        Fonts.draw(ctx, "Points", x1, y, 0xFFFFFFFF, true);
        y += 16;
        long mins = Wardrobe.activeMinutes();
        String[][] stats = {
                {"Your points", String.format("%,d", Wardrobe.points())},
                {"Played with Nightshade", (mins / 60) + "h " + (mins % 60) + "m"},
                {"Daily streak", "Day " + Wardrobe.streak() + " of 7"}};
        for (String[] st : stats) {
            Fonts.draw(ctx, st[0], x1, y, Theme.TEXT_DIM, false);
            Fonts.draw(ctx, st[1], gx2() - Fonts.width(st[1]), y, 0xFFFFFFFF, false);
            y += 12;
        }
        y += 6;
        Fonts.draw(ctx, Fonts.trim(com.nightshade.cosmetic.CloudSync.status(), gx2() - x1), x1, y, com.nightshade.cosmetic.CloudSync.online() ? 0xFF9FD8B0 : Theme.TEXT_DIM, false);
        y += 14;
        String[] rules = {
                "1 point for every minute you play (60 an hour)",
                "+" + Wardrobe.DAILY + " for your first game each day, +" + Wardrobe.STREAK + " for a 7-day streak",
                "No points in menus, while paused or after 5 minutes AFK",
                "Common 300 (5 h)  -  Rare 900 (15 h)  -  Legendary 2,500 (40 h)  -  Mythic 6,000 (100 h)",
                "Only what is in the Shop can be bought: new daily items every day (UTC), Legendary and Mythic picks every Monday",
                "Unlocks are yours for good; equipping is free"};
        for (String r : rules) {
            Fonts.draw(ctx, r, x1, y, Theme.TEXT, false);
            y += 11;
        }
        y += 8;
        Fonts.draw(ctx, "Recent", x1, y, 0xFFFFFFFF, false);
        y += 12;
        for (String[] h : Wardrobe.history()) {
            if (y > height - 50) {
                break;
            }
            Fonts.draw(ctx, h[0], x1, y, Theme.TEXT, false);
            Fonts.draw(ctx, h[2], x1 + 150, y, Theme.TEXT_DIM, false);
            Fonts.draw(ctx, h[1], gx2() - Fonts.width(h[1]), y, h[1].startsWith("+") ? 0xFF9FD8B0 : 0xFFFF8FA3, false);
            y += 11;
        }
    }

    private void drawOwner(GuiGraphicsExtractor ctx, int mx, int my) {
        int x1 = gx1();
        int y = 44;
        Fonts.draw(ctx, "Owner tools", x1, y, 0xFFFFC857, true);
        Fonts.draw(ctx, "Only shown because your owner key file is installed.", x1, y + 13, Theme.TEXT_DIM, false);
        y += 32;
        int bw = (gx2() - x1 - 12) / 3;
        for (int i = 0; i < owner.length; i++) {
            owner[i].render(ctx, x1 + (i % 3) * (bw + 6), y + (i / 3) * 26, bw, 20, mx, my);
        }
        y += 60;
        Fonts.draw(ctx, "Chat commands (never sent to the server):", x1, y, Theme.TEXT, false);
        Fonts.draw(ctx, ".ns points add <n>   .ns points set <n>   .ns unlock all", x1, y + 12, Theme.TEXT_DIM, false);
    }

    private void drawConfirm(GuiGraphicsExtractor ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0xB0050210);
        int w = 230;
        int h = 128;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, 10, Theme.BACKGROUND | 0xFF000000);
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, 10, Theme.BORDER_STRONG);
        Fonts.drawCentered(ctx, confirmBundle ? "Buy the weekly bundle?" : "Unlock " + confirm.name + "?", x + w / 2, y + 12, 0xFFFFFFFF, true);
        int cost = confirmBundle ? com.nightshade.cosmetic.Shop.bundlePrice(Wardrobe::owns) : com.nightshade.cosmetic.Shop.price(confirm);
        int left = Wardrobe.points() - cost;
        Fonts.draw(ctx, "Your points", x + 16, y + 34, Theme.TEXT_DIM, false);
        Fonts.draw(ctx, String.format("%,d", Wardrobe.points()), x + w - 16 - Fonts.width(String.format("%,d", Wardrobe.points())), y + 34, 0xFFFFFFFF, false);
        Fonts.draw(ctx, "Price", x + 16, y + 46, Theme.TEXT_DIM, false);
        String pr = "- " + String.format("%,d", cost) + (confirmBundle ? "  (" + com.nightshade.cosmetic.Shop.BUNDLE_DISCOUNT + "% off)" : cost < confirm.price ? "  (" + com.nightshade.cosmetic.Shop.discount(confirm) + "% off)" : "");
        Fonts.draw(ctx, pr, x + w - 16 - Fonts.width(pr), y + 46, 0xFFFFC857, false);
        ctx.fill(x + 16, y + 58, x + w - 16, y + 59, Theme.BORDER_STRONG);
        Fonts.draw(ctx, "Left after", x + 16, y + 64, 0xFFFFFFFF, false);
        Fonts.draw(ctx, String.format("%,d", left), x + w - 16 - Fonts.width(String.format("%,d", left)), y + 64, 0xFFFFFFFF, false);
        confirmNo.render(ctx, x + 14, y + h - 30, 80, 20, mx, my);
        confirmYes.render(ctx, x + w - 14 - 120, y + h - 30, 120, 20, mx, my);
    }

    // ------------------------------------------------------------------ input

    private void act() {
        if (shopTab && bundlePicked) {
            int price = com.nightshade.cosmetic.Shop.bundlePrice(Wardrobe::owns);
            if (com.nightshade.cosmetic.Shop.bundle().stream().allMatch(Wardrobe::owns)) {
                return;
            }
            if (Wardrobe.points() >= price) {
                confirm = com.nightshade.cosmetic.Shop.bundle().get(0);
                confirmBundle = true;
            } else {
                NightshadeClient.client().notifications().info("Not enough points", String.format("You need %,d more", price - Wardrobe.points()));
            }
            return;
        }
        Catalog.Item s = selected;
        if (s.id.equals(Wardrobe.equipped(s.category))) {
            Wardrobe.unequip(s.category);
        } else if (Wardrobe.owns(s)) {
            Wardrobe.equip(s);
        } else if (!com.nightshade.cosmetic.Shop.available(s)) {
            NightshadeClient.client().notifications().info("Not in the shop", s.name + " comes back in a later rotation");
        } else if (Wardrobe.points() >= com.nightshade.cosmetic.Shop.price(s)) {
            confirm = s;
            confirmBundle = false;
        } else {
            NightshadeClient.client().notifications().info("Not enough points", String.format("You need %,d more", com.nightshade.cosmetic.Shop.price(s) - Wardrobe.points()));
        }
    }

    private void confirmUnlock() {
        if (confirmBundle) {
            confirmBundle = false;
            confirm = null;
            if (Wardrobe.unlockBundle()) {
                NightshadeClient.client().notifications().success("Bundle unlocked", "All three items are yours for good");
            }
            return;
        }
        Catalog.Item c = confirm;
        confirm = null;
        if (c != null && Wardrobe.unlock(c)) {
            Wardrobe.equip(c);
            NightshadeClient.client().notifications().success("Unlocked " + c.name, "It's yours for good and is now equipped");
        }
    }

    @Override
    protected boolean onMouseClicked(double mx, double my, int button) {
        if (confirm != null) {
            return confirmYes.click(mx, my, button) || confirmNo.click(mx, my, button) || true;
        }
        if (close.click(mx, my, button) || points.click(mx, my, button) || hide.click(mx, my, button)
                || wingsMode.click(mx, my, button) || resetView.click(mx, my, button) || random.click(mx, my, button)) {
            return true;
        }
        for (int i = 0; i < outfits.length; i++) {
            if (button == 1 && outfits[i].isHovered(mx, my)) {
                Wardrobe.saveOutfit(i);
                NightshadeClient.client().notifications().success("Saved outfit " + (i + 1), Wardrobe.outfitSummary(i));
                return true;
            }
            if (outfits[i].click(mx, my, button)) {
                return true;
            }
        }
        if (mx < split() && my > 40 && my < height - 56) {
            dragging = true;
            autoSpin = 0f;
            return true;
        }
        int x1 = gx1();
        int x2 = gx2();
        int tx = x1;
        {
            int w = Fonts.width("Shop") + tabPad();
            if (mx >= tx && mx < tx + w && my >= 40 && my < 58) {
                shopTab = true;
                bundlePicked = false;
                ownerTab = false;
                pointsPage = false;
                selectFirst();
                return true;
            }
            tx += w + 4;
        }
        for (Catalog.Category c : Catalog.Category.values()) {
            int w = Fonts.width(c.title) + tabPad();
            if (mx >= tx && mx < tx + w && my >= 40 && my < 58) {
                tab = c;
                ownerTab = false;
                shopTab = false;
                pointsPage = false;
                selectFirst();
                return true;
            }
            tx += w + 4;
        }
        if (OwnerKey.present() && mx >= tx && mx < tx + Fonts.width("Owner") + tabPad() && my >= 40 && my < 58) {
            ownerTab = true;
            shopTab = false;
            pointsPage = false;
            return true;
        }
        if (pointsPage) {
            return false;
        }
        if (ownerTab) {
            for (NsButton b : owner) {
                if (b.click(mx, my, button)) {
                    return true;
                }
            }
            return false;
        }
        if (action.click(mx, my, button) || (!shopTab && sortButton.click(mx, my, button))) {
            return true;
        }
        if (shopTab) {
            if (my < GRID_TOP - 20 || my >= height - 36) {
                return false;
            }
            for (Object[] hit : shopHits) {
                int[] r = (int[]) hit[0];
                if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                    if (hit[1] == null) { // the bundle card
                        bundlePicked = true;
                        Wardrobe.clearPreview();
                        for (Catalog.Item b : com.nightshade.cosmetic.Shop.bundle()) {
                            Wardrobe.setPreview(b.category, b.id); // try the whole bundle on
                        }
                        return true;
                    }
                    bundlePicked = false;
                    selected = (Catalog.Item) hit[1];
                    Wardrobe.setPreview(selected.category, selected.id);
                    if (button == 0 && Wardrobe.owns(selected) && !selected.id.equals(Wardrobe.equipped(selected.category))) {
                        Wardrobe.equip(selected);
                    }
                    return true;
                }
            }
            return false;
        }
        int fx = x1;
        for (int i = 0; i < FILTERS.length; i++) {
            int w = Fonts.width(filterLabel(i)) + chipPad();
            if (mx >= fx && mx < fx + w && my >= 102 && my < 116) {
                filter = i;
                scrollTarget = scroll = 0;
                return true;
            }
            fx += w + 4;
        }
        if (my < GRID_TOP || my >= height - 36) {
            return false;
        }
        List<Catalog.Item> items = visibleItems();
        int cols = cols();
        int gap = 6;
        int cw = (x2 - x1 - gap * (cols - 1)) / cols;
        int ch = 64;
        for (int i = 0; i < items.size(); i++) {
            int cx = x1 + (i % cols) * (cw + gap);
            int cy = GRID_TOP + (i / cols) * (ch + gap) - Math.round(scroll);
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
                Catalog.Item it = items.get(i);
                if (mx >= cx + cw - 18 && my < cy + 16) {
                    Wardrobe.toggleFavourite(it);
                    return true;
                }
                selected = it;
                Wardrobe.setPreview(selected.category, selected.id);
                if (button == 0 && Wardrobe.owns(selected) && !selected.id.equals(Wardrobe.equipped(selected.category))) {
                    Wardrobe.equip(selected);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (confirm != null) {
            return true;
        }
        if (mouseX < split()) {
            zoom = Math.max(0.7f, Math.min(1.6f, zoom + (float) verticalAmount * 0.08f));
            return true;
        }
        if (!pointsPage && !ownerTab && mouseY >= GRID_TOP) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) verticalAmount * 30f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 1.6f;
            pitch = Math.max(-35f, Math.min(35f, pitch + (float) dy * 0.6f));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseReleased(double mx, double my, int button) {
        dragging = false;
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && confirm != null) { // Escape
            confirm = null;
            return true;
        }
        return false;
    }
}
