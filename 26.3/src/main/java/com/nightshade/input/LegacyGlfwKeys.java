package com.nightshade.input;

import java.util.HashMap;
import java.util.Map;

/**
 * GLFW key code -> vanilla key name, for configs written by Minecraft 1.21.x - 26.2 builds (GLFW) and loaded on
 * 26.3+ (SDL). Covers every key GLFW defines that Minecraft names.
 */
final class LegacyGlfwKeys {
    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            put(65 + (c - 'a'), String.valueOf(c));
        }
        for (int d = 0; d <= 9; d++) {
            put(48 + d, String.valueOf(d));
            put(320 + d, "keypad." + d);
        }
        for (int f = 1; f <= 25; f++) {
            put(289 + f, "f" + f);
        }
        put(32, "space"); put(39, "apostrophe"); put(44, "comma"); put(45, "minus"); put(46, "period"); put(47, "slash");
        put(59, "semicolon"); put(61, "equal"); put(91, "left.bracket"); put(92, "backslash"); put(93, "right.bracket");
        put(96, "grave.accent"); put(256, "escape"); put(257, "enter"); put(258, "tab"); put(259, "backspace");
        put(260, "insert"); put(261, "delete"); put(262, "right"); put(263, "left"); put(264, "down"); put(265, "up");
        put(266, "page.up"); put(267, "page.down"); put(268, "home"); put(269, "end"); put(280, "caps.lock");
        put(281, "scroll.lock"); put(282, "num.lock"); put(283, "print.screen"); put(284, "pause");
        put(330, "keypad.decimal"); put(331, "keypad.divide"); put(332, "keypad.multiply"); put(333, "keypad.subtract");
        put(334, "keypad.add"); put(335, "keypad.enter"); put(336, "keypad.equal");
        put(340, "left.shift"); put(341, "left.control"); put(342, "left.alt"); put(343, "left.win");
        put(344, "right.shift"); put(345, "right.control"); put(346, "right.alt"); put(347, "right.win"); put(348, "menu");
    }

    private LegacyGlfwKeys() {
    }

    private static void put(int code, String name) {
        NAMES.put(code, "key.keyboard." + name);
    }

    static String name(int glfwCode) {
        return NAMES.get(glfwCode);
    }
}
