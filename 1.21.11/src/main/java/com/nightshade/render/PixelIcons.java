package com.nightshade.render;

import com.nightshade.module.ModuleCategory;
import net.minecraft.client.gui.GuiGraphics;

/** Tiny hand-made pixel icons (7x7) drawn with fills, so the GUI needs no texture assets. */
public final class PixelIcons {
    private static final String[] HUD = {
            "#######",
            "#.....#",
            "#.##..#",
            "#.....#",
            "#..####",
            "#.....#",
            "#######"};
    private static final String[] VISUAL = {
            ".......",
            "..###..",
            ".#...#.",
            "#..#..#",
            ".#...#.",
            "..###..",
            "......."};
    private static final String[] PERFORMANCE = {
            "....##.",
            "...##..",
            "..##...",
            ".######",
            "...##..",
            "..##...",
            ".##...."};
    private static final String[] UTILITY = {
            ".##..##",
            ".#...#.",
            ".#####.",
            "..###..",
            "..###..",
            "..###..",
            "...#..."};
    private static final String[] INTERFACE = {
            "#######",
            "#.#...#",
            "#######",
            "#.#...#",
            "#.#.#.#",
            "#.#...#",
            "#######"};
    /** Crossed swords for the PvP tab. */
    private static final String[] PVP = {
            "#.....#",
            ".#...#.",
            "..#.#..",
            "...#...",
            "..#.#..",
            "##...##",
            "##...##"};
    /** Five-petal nightshade flower used for the brand mark. */
    private static final String[] FLOWER = {
            "...##...",
            "..####..",
            "##.##.##",
            "###oo###",
            ".##oo##.",
            "..#..#..",
            ".##..##.",
            ".#....#."};
    private static final String[] SEARCH = {
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".###...",
            "....##.",
            ".....##"};

    private PixelIcons() {
    }

    public static void category(GuiGraphics ctx, ModuleCategory category, int x, int y, int color) {
        draw(ctx, switch (category) {
            case HUD -> HUD;
            case VISUAL -> VISUAL;
            case PERFORMANCE -> PERFORMANCE;
            case UTILITY -> UTILITY;
            case INTERFACE -> INTERFACE;
            case PVP -> PVP;
        }, x, y, color, color);
    }

    public static void flower(GuiGraphics ctx, int x, int y, int petal, int center) {
        draw(ctx, FLOWER, x, y, petal, center);
    }

    public static void search(GuiGraphics ctx, int x, int y, int color) {
        draw(ctx, SEARCH, x, y, color, color);
    }

    // ------------------------------------------------------------------ main menu icons (9x9; 'o' = accent pixels, '+' = shaded)
    public static final String[] MENU_SINGLEPLAYER = {
            "....oo...",
            "..oooooo.",
            "oooooooo+",
            "#ooooo++#",
            "###oo+###",
            "####+####",
            "####+####",
            ".###+###.",
            "...#+#..."};
    public static final String[] MENU_MULTIPLAYER = {
            "..##.....",
            ".####.oo.",
            ".####oooo",
            "..##.oooo",
            ".....oo..",
            "####.....",
            "####oooo.",
            "####oooo.",
            "....oooo."};
    public static final String[] MENU_REALMS = {
            "#.#.#.#.#",
            "#########",
            ".#######.",
            ".##ooo##.",
            ".#o###o#.",
            ".#o#.#o#.",
            ".###.###.",
            ".###.###.",
            "#########"};
    public static final String[] MENU_MODS = {
            "...##....",
            "..####...",
            "####o####",
            "#oooooo##",
            "##ooooo#.",
            "#oooooo##",
            "####o####",
            "..####...",
            "...##...."};
    public static final String[] MENU_ACCOUNTS = {
            "...###...",
            "..#####..",
            "..#####..",
            "..#####..",
            "...###...",
            ".........",
            ".ooooooo.",
            "ooooooooo",
            "ooooooooo"};
    public static final String[] MENU_SETTINGS = {
            "....#....",
            ".#.###.#.",
            "..#####..",
            ".###o###.",
            "###ooo###",
            ".###o###.",
            "..#####..",
            ".#.###.#.",
            "....#...."};
    public static final String[] MENU_OPTIONS = {
            "..o......",
            "#####.###",
            "..o......",
            ".........",
            "......o..",
            "###.#####",
            "......o..",
            ".........",
            "####o####"};
    public static final String[] MENU_QUIT = {
            "....#....",
            ".#..#..#.",
            "#...#...#",
            "#.......#",
            "#.......#",
            "#.......#",
            ".#.....#.",
            "..#####..",
            "........."};
    public static final String[] MENU_PLAY = {
            ".#.......",
            ".##......",
            ".###.....",
            ".####....",
            ".#####...",
            ".####....",
            ".###.....",
            ".##......",
            ".#......."};

    public static final String[] ICON_VIDEO = {
            "#########",
            "#ooooooo#",
            "#ooooooo#",
            "#ooooooo#",
            "#ooooooo#",
            "#########",
            "....#....",
            "..#####..",
            "........."};
    public static final String[] ICON_CONTROLS = {
            "#########",
            "#.#.#.#.#",
            "#########",
            "#.#.#.#.#",
            "#########",
            "#.#ooo#.#",
            "#########",
            ".........",
            "........."};
    public static final String[] ICON_SOUND = {
            "....#....",
            "...##..o.",
            "..###.o..",
            "####..o.o",
            "####..o.o",
            "####..o.o",
            "..###.o..",
            "...##..o.",
            "....#...."};
    public static final String[] ICON_LANGUAGE = {
            "..#####..",
            ".#.#.#.#.",
            "#..#.#..#",
            "#########",
            "#..#.#..#",
            "#########",
            "#..#.#..#",
            ".#.#.#.#.",
            "..#####.."};
    public static final String[] ICON_CHAT = {
            "#########",
            "#.......#",
            "#.o.o.o.#",
            "#.......#",
            "#########",
            "..##.....",
            ".##......",
            "##.......",
            "........."};
    public static final String[] ICON_EYE = {
            ".........",
            "...###...",
            ".##...##.",
            "#..ooo..#",
            "#..ooo..#",
            ".##...##.",
            "...###...",
            ".........",
            "........."};
    public static final String[] ICON_STATS = {
            ".......o.",
            ".......o.",
            "....o..o.",
            "....o..o.",
            ".o..o..o.",
            ".o..o..o.",
            ".o..o..o.",
            "#########",
            "........."};
    public static final String[] ICON_TROPHY = {
            "#.#####.#",
            "#.#ooo#.#",
            ".##ooo##.",
            "..#ooo#..",
            "...###...",
            "....#....",
            "....#....",
            "..#####..",
            "........."};
    public static final String[] ICON_LAN = {
            ".o.....o.",
            "o..ooo..o",
            "o.o...o.o",
            "o.o.#.o.o",
            "o..o#o..o",
            ".o..#..o.",
            "....#....",
            "....#....",
            "..#####.."};
    public static final String[] ICON_STAR = {
            "....o....",
            "....o....",
            "...ooo...",
            "ooooooooo",
            ".ooooooo.",
            "..ooooo..",
            "..oo.oo..",
            ".oo...oo.",
            "........."};
    public static final String[] ICON_HUD = {
            "#########",
            "#oo...oo#",
            "#oo...oo#",
            "#.......#",
            "#.......#",
            "#.......#",
            "#ooo.ooo#",
            "#########",
            "........."};
    public static final String[] ICON_ONLINE = {
            "..#####..",
            ".#..o..#.",
            "#..ooo..#",
            "#.ooooo.#",
            "#..ooo..#",
            "#...o...#",
            ".#.....#.",
            "..#####..",
            "........."};
    public static final String[] ICON_SKIN = {
            "..#####..",
            "..#o#o#..",
            "..#####..",
            "...###...",
            ".#######.",
            "#.#####.#",
            "#.#####.#",
            "..##.##..",
            "..##.##.."};

    /** Draws a menu icon at integer scale; '#' pixels use {@code color}, 'o' pixels use {@code accent}. */
    public static void menu(GuiGraphics ctx, String[] rows, int x, int y, int scale, int color, int accent) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int col = 0;
            while (col < line.length()) {
                char c = line.charAt(col);
                if (c == '#' || c == 'o' || c == '+') {
                    int end = col + 1;
                    while (end < line.length() && line.charAt(end) == c) {
                        end++; // one fill per run of same-coloured pixels
                    }
                    int py = y + row * scale;
                    int fill = c == '#' ? color : c == 'o' ? accent : ColorUtil.lerp(color, 0xFF000000, 0.35f);
                    ctx.fill(x + col * scale, py, x + end * scale, py + scale, fill);
                    col = end;
                } else {
                    col++;
                }
            }
        }
    }

    private static void draw(GuiGraphics ctx, String[] rows, int x, int y, int color, int alt) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int col = 0;
            while (col < line.length()) {
                char c = line.charAt(col);
                int end = col + 1;
                while (end < line.length() && line.charAt(end) == c) {
                    end++;
                }
                if (c == '#' || c == 'o') {
                    ctx.fill(x + col, y + row, x + end, y + row + 1, c == '#' ? color : alt);
                }
                col = end;
            }
        }
    }
}
