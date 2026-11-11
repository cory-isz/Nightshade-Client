package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.render.Fonts;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Player position, facing and biome. */
public final class CoordinatesHud extends HudModule {
    private final BooleanSetting showDirection = add(new BooleanSetting("Show Direction", "Show facing direction", true));
    private final BooleanSetting showBiome = add(new BooleanSetting("Show Biome", "Show current biome", false));
    private final BooleanSetting decimals = add(new BooleanSetting("Decimals", "Show one decimal place", false));
    private final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of axis labels", 0xFFB794FF));

    private final String[] lines = new String[3];
    private int lineCount;
    private int width = 60;
    private BlockPos lastBlockPos = BlockPos.ZERO;

    public CoordinatesHud() {
        super("Coordinates", "Shows your position, facing direction and biome", ModuleCategory.HUD, 0.0, 0.215);
    }

    @Override
    protected void refresh() {
        if (mc.player == null) {
            return;
        }
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        lineCount = 0;
        lines[lineCount++] = decimals.get()
                ? String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z)
                : mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
        if (showDirection.get()) {
            Direction facing = mc.player.getDirection();
            lines[lineCount++] = capitalize(facing.getSerializedName()) + " (" + axisHint(facing) + ")";
        }
        if (showBiome.get() && mc.level != null) {
            BlockPos pos = mc.player.blockPosition();
            if (!pos.equals(lastBlockPos) || lines[2] == null) {
                lastBlockPos = pos.immutable();
                Holder<Biome> biome = mc.level.getBiome(pos);
                lines[2] = biome.unwrapKey().map(key -> prettify(key.identifier().getPath())).orElse("Unknown");
            }
            lines[lineCount++] = lines[2];
        }
        int max = 0;
        for (int i = 0; i < lineCount; i++) {
            max = Math.max(max, Fonts.width(lines[i]));
        }
        width = max + Fonts.width("XYZ ") + 8;
    }

    private static String axisHint(Direction direction) {
        return switch (direction) {
            case NORTH -> "-Z";
            case SOUTH -> "+Z";
            case WEST -> "-X";
            case EAST -> "+X";
            default -> "";
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String prettify(String path) {
        StringBuilder builder = new StringBuilder();
        for (String part : path.split("_")) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(capitalize(part));
        }
        return builder.toString();
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return Math.max(1, lineCount) * (Fonts.height() + 2) + 5;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        String[] labels = {"XYZ", "Dir", "Bio"};
        int y = 4;
        for (int i = 0; i < lineCount; i++) {
            int labelIndex = i == 0 ? 0 : (i == 1 && showDirection.get()) ? 1 : 2;
            int x = Fonts.draw(context, labels[labelIndex] + " ", 4, y, labelColor.argb(), shadow()) - 1;
            Fonts.draw(context, lines[i], Math.max(x, 4 + Fonts.width("XYZ ")), y, textColor.argb(), shadow());
            y += Fonts.height() + 2;
        }
    }
}
