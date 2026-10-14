package com.OsamaClient.newbridge.UI.gui.input;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Key / mouse constants of the client (Minecraft 26.3+: SDL3 scancodes, the same numbers KeyEvent.key()
 * delivers). Names follow the old GLFW constants so the code reads the same. {@link #fromGlfw} converts key
 * codes saved by older versions (GLFW) into scancodes.
 */
public final class Keys {

    private Keys() {}

    public static final int KEY_A = InputConstants.KEY_A, KEY_B = InputConstants.KEY_B, KEY_C = InputConstants.KEY_C,
            KEY_F = InputConstants.KEY_F, KEY_K = InputConstants.KEY_K, KEY_M = InputConstants.KEY_M,
            KEY_N = InputConstants.KEY_N, KEY_R = InputConstants.KEY_R, KEY_S = InputConstants.KEY_S,
            KEY_V = InputConstants.KEY_V, KEY_Y = InputConstants.KEY_Y, KEY_Z = InputConstants.KEY_Z;
    public static final int KEY_0 = InputConstants.KEY_0, KEY_1 = InputConstants.KEY_1, KEY_9 = InputConstants.KEY_9;
    public static final int KEY_F1 = InputConstants.KEY_F1;
    public static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int KEY_ENTER = InputConstants.KEY_RETURN;
    public static final int KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int KEY_TAB = InputConstants.KEY_TAB;
    public static final int KEY_SPACE = InputConstants.KEY_SPACE;
    public static final int KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int KEY_DELETE = InputConstants.KEY_DELETE;
    public static final int KEY_INSERT = InputConstants.KEY_INSERT;
    public static final int KEY_HOME = InputConstants.KEY_HOME;
    public static final int KEY_END = InputConstants.KEY_END;
    public static final int KEY_PAGE_UP = InputConstants.KEY_PAGEUP;
    public static final int KEY_PAGE_DOWN = InputConstants.KEY_PAGEDOWN;
    public static final int KEY_LEFT = InputConstants.KEY_LEFT;
    public static final int KEY_RIGHT = InputConstants.KEY_RIGHT;
    public static final int KEY_UP = InputConstants.KEY_UP;
    public static final int KEY_DOWN = InputConstants.KEY_DOWN;
    public static final int KEY_CAPS_LOCK = InputConstants.KEY_CAPSLOCK;
    public static final int KEY_LEFT_SHIFT = InputConstants.KEY_LSHIFT, KEY_RIGHT_SHIFT = InputConstants.KEY_RSHIFT;
    public static final int KEY_LEFT_CONTROL = InputConstants.KEY_LCONTROL, KEY_RIGHT_CONTROL = InputConstants.KEY_RCONTROL;
    public static final int KEY_LEFT_ALT = InputConstants.KEY_LALT, KEY_RIGHT_ALT = InputConstants.KEY_RALT;
    public static final int KEY_KP_0 = InputConstants.KEY_NUMPAD0, KEY_KP_9 = InputConstants.KEY_NUMPAD9;

    public static final int MOUSE_BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int MOUSE_BUTTON_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    public static final int MOUSE_BUTTON_MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;

    public static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
    public static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    public static final int MOD_ALT = InputConstants.MOD_ALT;

    /** True for a letter key A..Z. */
    public static boolean isLetter(int key) { return key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z; }

    /** Converts a key code saved by an older (GLFW based) version into the current scancode; -1 stays -1. */
    public static int fromGlfw(int g) {
        if (g < 0) return g;
        if (g >= 65 && g <= 90) return InputConstants.KEY_A + (g - 65);
        if (g == 48) return InputConstants.KEY_0;
        if (g >= 49 && g <= 57) return InputConstants.KEY_1 + (g - 49);
        if (g >= 290 && g <= 301) return InputConstants.KEY_F1 + (g - 290);
        if (g >= 302 && g <= 313) return InputConstants.KEY_F13 + (g - 302);
        if (g == 320) return InputConstants.KEY_NUMPAD0;
        if (g >= 321 && g <= 329) return InputConstants.KEY_NUMPAD1 + (g - 321);
        return switch (g) {
            case 32 -> InputConstants.KEY_SPACE;
            case 39 -> InputConstants.KEY_APOSTROPHE;
            case 44 -> InputConstants.KEY_COMMA;
            case 45 -> InputConstants.KEY_MINUS;
            case 46 -> InputConstants.KEY_PERIOD;
            case 47 -> InputConstants.KEY_SLASH;
            case 59 -> InputConstants.KEY_SEMICOLON;
            case 61 -> InputConstants.KEY_EQUALS;
            case 91 -> InputConstants.KEY_LBRACKET;
            case 92 -> InputConstants.KEY_BACKSLASH;
            case 93 -> InputConstants.KEY_RBRACKET;
            case 96 -> InputConstants.KEY_GRAVE;
            case 161 -> 100;                              // GLFW_KEY_WORLD_1 = ISO "<" key
            case 256 -> InputConstants.KEY_ESCAPE;
            case 257 -> InputConstants.KEY_RETURN;
            case 258 -> InputConstants.KEY_TAB;
            case 259 -> InputConstants.KEY_BACKSPACE;
            case 260 -> InputConstants.KEY_INSERT;
            case 261 -> InputConstants.KEY_DELETE;
            case 262 -> InputConstants.KEY_RIGHT;
            case 263 -> InputConstants.KEY_LEFT;
            case 264 -> InputConstants.KEY_DOWN;
            case 265 -> InputConstants.KEY_UP;
            case 266 -> InputConstants.KEY_PAGEUP;
            case 267 -> InputConstants.KEY_PAGEDOWN;
            case 268 -> InputConstants.KEY_HOME;
            case 269 -> InputConstants.KEY_END;
            case 280 -> InputConstants.KEY_CAPSLOCK;
            case 281 -> InputConstants.KEY_SCROLLLOCK;
            case 282 -> InputConstants.KEY_NUMLOCK;
            case 283 -> InputConstants.KEY_PRINTSCREEN;
            case 284 -> InputConstants.KEY_PAUSE;
            case 330 -> 99;                               // keypad decimal
            case 331 -> 84;                               // keypad divide
            case 332 -> InputConstants.KEY_MULTIPLY;
            case 333 -> 86;                               // keypad subtract
            case 334 -> InputConstants.KEY_ADD;
            case 335 -> InputConstants.KEY_NUMPADENTER;
            case 336 -> InputConstants.KEY_NUMPADEQUALS;
            case 340 -> InputConstants.KEY_LSHIFT;
            case 341 -> InputConstants.KEY_LCONTROL;
            case 342 -> InputConstants.KEY_LALT;
            case 343 -> InputConstants.KEY_LGUI;
            case 344 -> InputConstants.KEY_RSHIFT;
            case 345 -> InputConstants.KEY_RCONTROL;
            case 346 -> InputConstants.KEY_RALT;
            case 347 -> InputConstants.KEY_RGUI;
            case 348 -> 118;                              // menu
            default -> -1;                                // unknown: unbind instead of binding a random key
        };
    }
}
