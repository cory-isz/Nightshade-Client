package com.nightshade.mixin;

import com.nightshade.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Universal Font: text that asks for Minecraft's default font gets the client font (Inter, Poppins or Rubik) instead,
 * so chat, vanilla screens, tooltips, signs and name tags all match the client. Characters the font does not have
 * still fall back to Minecraft's own glyphs.
 */
@Mixin(Font.class)
public abstract class FontUniversalMixin {
    private static final ResourceLocation NIGHTSHADE$INTER = ResourceLocation.fromNamespaceAndPath("nightshade", "inter");
    private static final ResourceLocation NIGHTSHADE$POPPINS = ResourceLocation.fromNamespaceAndPath("nightshade", "poppins");
    private static final ResourceLocation NIGHTSHADE$RUBIK = ResourceLocation.fromNamespaceAndPath("nightshade", "rubik");

    /** Target: FontSet getFontSet(ResourceLocation). */
    @ModifyVariable(method = "getFontSet", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation nightshade$universal(ResourceLocation description) {
        if (!Theme.universalFont() || !Style.DEFAULT_FONT.equals(description)) {
            return description;
        }
        return switch (Theme.font()) {
            case "Inter" -> NIGHTSHADE$INTER;
            case "Poppins" -> NIGHTSHADE$POPPINS;
            case "Rounded" -> NIGHTSHADE$RUBIK;
            default -> description;
        };
    }
}
