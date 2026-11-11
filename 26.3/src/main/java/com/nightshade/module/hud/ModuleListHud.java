package com.nightshade.module.hud;

import com.nightshade.NightshadeClient;
import com.nightshade.module.HudModule;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Animated list of enabled modules. */
public final class ModuleListHud extends HudModule {
    private final BooleanSetting hideHud = add(new BooleanSetting("Hide HUD Modules", "Do not list HUD components", true));
    private final ModeSetting sort = add(new ModeSetting("Sort", "Ordering of entries", "Length", "Length", "Alphabetical"));
    private final ModeSetting alignment = add(new ModeSetting("Alignment", "Text alignment", "Right", "Right", "Left"));
    private final BooleanSetting accentBar = add(new BooleanSetting("Accent Bar", "Draw an accent bar beside entries", true));

    private final List<Module> entries = new ArrayList<>();
    private final Map<Module, Animation> animations = new IdentityHashMap<>();
    private int width = 60;

    public ModuleListHud() {
        super("Module List", "Lists currently enabled modules", ModuleCategory.HUD, 0.99, 0.07, false);
    }

    @Override
    protected void refresh() {
        entries.clear();
        for (Module module : NightshadeClient.client().modules().getModules()) {
            boolean visible = module.isEnabled() && module != this && module.isToggleable()
                    && !(hideHud.get() && module.getCategory() == ModuleCategory.HUD);
            Animation animation = animations.get(module);
            if (visible && animation == null) {
                animation = new Animation(12f, 0f);
                animations.put(module, animation);
            }
            if (animation != null) {
                animation.setTarget(visible ? 1f : 0f);
                if (visible || animation.get() > 0.01f) {
                    entries.add(module);
                } else {
                    animations.remove(module);
                }
            }
        }
        if (sort.is("Length")) {
            entries.sort(Comparator.comparingInt((Module m) -> Fonts.width(m.getName())).reversed());
        } else {
            entries.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        }
        int max = Fonts.width("Module List");
        for (Module module : entries) {
            max = Math.max(max, Fonts.width(module.getName()));
        }
        width = max + 8;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return Math.max(1, entries.size()) * (Fonts.height() + 3) + 2;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        int lineHeight = Fonts.height() + 3;
        if (entries.isEmpty()) {
            if (preview) {
                Fonts.draw(context, "Module List", 4, 2, Theme.TEXT_DIM, shadow());
            }
            return;
        }
        boolean right = alignment.is("Right");
        float y = 1f;
        for (Module module : entries) {
            Animation animation = animations.get(module);
            float progress = animation == null ? 1f : animation.update();
            String name = module.getName();
            int textWidth = Fonts.width(name);
            int slide = Math.round((1f - progress) * (textWidth + 8));
            int x = right ? width - textWidth - 4 + slide : 4 - slide;
            int top = Math.round(y);
            int color = textColor.argb();
            if (background.get()) {
                String style = com.nightshade.render.Theme.hudStyle();
                if ("Classic".equals(style)) {
                    RenderUtil.rect(context, x - 3, top, x + textWidth + 3, top + lineHeight, ColorUtil.fade(backgroundColor.argb(), progress));
                } else if (!"Minimal".equals(style)) {
                    RenderUtil.roundedRect(context, x - 3, top, x + textWidth + 3, top + lineHeight - 1, 3,
                            ColorUtil.fade(ColorUtil.withAlpha(backgroundColor.argb(), Math.min(255, ColorUtil.alpha(backgroundColor.argb()) + 0x18)), progress));
                }
            }
            if (accentBar.get()) {
                int barX = right ? x + textWidth + 3 : x - 4;
                RenderUtil.rect(context, barX, top, barX + 1, top + lineHeight, ColorUtil.fade(Theme.accent(), progress));
            }
            Fonts.draw(context, name, x, top + 2, ColorUtil.fade(color, progress), shadow());
            y += lineHeight * progress;
        }
    }
}
