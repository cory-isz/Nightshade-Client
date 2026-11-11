package com.nightshade.hud;

import com.nightshade.module.HudModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps HUD components that are still at their default place from overlapping. Components are grouped into the
 * left, centre and right columns by their default position; inside a column each one sits at its natural place
 * unless that would overlap the one above, in which case it is pushed down. If a column then runs off the bottom,
 * it is squeezed back up from the bottom edge. Components the player has dragged keep exactly their own position.
 */
public final class HudLayout {
    private static final int GAP = 3;
    private static final int MARGIN = 2;

    private HudLayout() {
    }

    public static void apply(List<HudModule> modules, int screenW, int screenH) {
        List<List<HudModule>> columns = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (HudModule module : modules) {
            module.clearLayout();
            if (!module.hasCustomPosition()) {
                double x = module.defaultX();
                columns.get(x < 0.34 ? 0 : x > 0.66 ? 2 : 1).add(module);
            }
        }
        for (int i = 0; i < columns.size(); i++) {
            layoutColumn(columns.get(i), screenW, screenH, i);
        }
    }

    /**
     * Lays out one column. Components keep their natural place unless it would overlap the one above; a component
     * that would run off the bottom (or into the hotbar) starts a new sub-column beside the previous one, so a
     * crowded column wraps instead of piling up.
     */
    private static void layoutColumn(List<HudModule> column, int screenW, int screenH, int side) {
        if (column.isEmpty()) {
            return;
        }
        column.sort(Comparator.comparingDouble(HudModule::defaultY));
        int hotbarX1 = screenW / 2 - 122;
        int hotbarX2 = screenW / 2 + 122;
        int offset = 0;        // horizontal shift of the current sub-column
        int subWidth = 0;      // widest component in the current sub-column
        int cursorY = MARGIN;  // first free y in the current sub-column
        boolean subEmpty = true;
        for (HudModule m : column) {
            int w = m.getScaledWidth();
            int h = m.getScaledHeight();
            int naturalX = (int) Math.round(m.defaultX() * Math.max(0, screenW - w));
            int naturalY = (int) Math.round(m.defaultY() * Math.max(0, screenH - h));
            for (int attempt = 0; attempt < 4; attempt++) {
                int x = side == 2 ? naturalX - offset : naturalX + offset;
                x = Math.max(0, Math.min(x, screenW - w));
                boolean overHotbar = x < hotbarX2 && x + w > hotbarX1;
                int limit = screenH - (overHotbar ? 44 : MARGIN);
                int y = subEmpty ? naturalY : Math.max(naturalY, cursorY);
                if (y + h <= limit || subEmpty && attempt == 3) {
                    y = Math.max(0, Math.min(y, limit - h));
                    m.setLayoutPosition(x, y);
                    cursorY = y + h + GAP;
                    subWidth = Math.max(subWidth, w);
                    subEmpty = false;
                    break;
                }
                // Wrap: next sub-column beside this one, stacking from the top.
                offset += subWidth + GAP;
                subWidth = 0;
                cursorY = MARGIN;
                subEmpty = false;
                naturalY = MARGIN;
                if (attempt == 3) {
                    m.setLayoutPosition(x, Math.max(0, limit - h));
                }
            }
        }
    }
}
