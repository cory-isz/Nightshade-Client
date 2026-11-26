package com.nightshade.module.utility;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;

/** Hides the vanilla pop-ups in the top-right corner that cover the screen mid-fight. */
public final class ToastControl extends Module {
    private static ToastControl instance;

    private final BooleanSetting recipes = add(new BooleanSetting("Hide Recipe Toasts", "\"New recipes unlocked\"", true));
    private final BooleanSetting tutorial = add(new BooleanSetting("Hide Tutorial Toasts", "Movement and crafting hints", true));
    private final BooleanSetting advancements = add(new BooleanSetting("Hide Advancement Toasts", "Advancement and challenge pop-ups", false));
    private final BooleanSetting system = add(new BooleanSetting("Hide System Toasts", "Warnings such as \"world backup\" or narrator (not recommended)", false));

    public ToastControl() {
        super("Toast Control", "Hides recipe, tutorial and advancement pop-ups", ModuleCategory.UTILITY);
        enabledByDefault();
        instance = this;
    }

    public static boolean shouldHide(Toast toast) {
        ToastControl control = instance;
        if (control == null || !control.isEnabled()) {
            return false;
        }
        boolean hide = (toast instanceof RecipeToast && control.recipes.get())
                || (toast instanceof TutorialToast && control.tutorial.get())
                || (toast instanceof AdvancementToast && control.advancements.get())
                || (toast instanceof SystemToast && control.system.get());
        if (hide) {
            EffectProbe.hit("Toast Control");
        }
        return hide;
    }
}
