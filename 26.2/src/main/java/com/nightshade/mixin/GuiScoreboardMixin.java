package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nightshade.module.visual.ScoreboardTweaks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scoreboard module: hide, scale, background and red-number removal for the sidebar. */
@Mixin(Hud.class)
public abstract class GuiScoreboardMixin {
    /** Target: private void renderScoreboardSidebar(GuiGraphicsExtractor, DeltaTracker). */
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void nightshade$hideSidebar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (ScoreboardTweaks.hideSidebar()) {
            ci.cancel();
        }
    }

    /** Target: private void displayScoreboardSidebar(GuiGraphicsExtractor, Objective) - scale around the right edge. */
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void nightshade$scalePush(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        float scale = ScoreboardTweaks.scale();
        float lower = ScoreboardTweaks.lower();
        graphics.pose().pushMatrix();
        if (scale != 1f || lower != 0f) {
            float ax = graphics.guiWidth();
            float ay = graphics.guiHeight() / 2f;
            graphics.pose().translate(ax, ay + lower * graphics.guiHeight());
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-ax, -ay);
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void nightshade$scalePop(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    /** Target: both Options.getBackgroundColor(float) reads in displayScoreboardSidebar (title bar and body). */
    @ModifyExpressionValue(method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I"))
    private int nightshade$background(int original) {
        return ScoreboardTweaks.background(original);
    }

    /** Target: PlayerScoreEntry.formatValue(NumberFormat) in the sidebar entry lambda - the red numbers. */
    @WrapOperation(method = "lambda$displayScoreboardSidebar$1",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerScoreEntry;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent nightshade$numbers(PlayerScoreEntry entry, NumberFormat format, Operation<MutableComponent> original) {
        if (ScoreboardTweaks.hideNumbers()) {
            return Component.empty();
        }
        return original.call(entry, format);
    }
}
