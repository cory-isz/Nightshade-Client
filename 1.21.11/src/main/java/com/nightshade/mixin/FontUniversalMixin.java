package com.nightshade.mixin;

import com.nightshade.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
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
    private static final FontDescription NIGHTSHADE$INTER = new FontDescription.Resource(Identifier.fromNamespaceAndPath("nightshade", "inter"));
    private static final FontDescription NIGHTSHADE$POPPINS = new FontDescription.Resource(Identifier.fromNamespaceAndPath("nightshade", "poppins"));
    private static final FontDescription NIGHTSHADE$RUBIK = new FontDescription.Resource(Identifier.fromNamespaceAndPath("nightshade", "rubik"));

    /** Target: private GlyphSource getGlyphSource(FontDescription). */
    @ModifyVariable(method = "getGlyphSource", at = @At("HEAD"), argsOnly = true)
    private FontDescription nightshade$universal(FontDescription description) {
        if (!Theme.universalFont() || !FontDescription.DEFAULT.equals(description)) {
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
