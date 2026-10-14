package com.OsamaClient.newbridge.UI.gui.util;

import com.mojang.blaze3d.platform.InputConstants;

/** Short display names for key scancodes (Minecraft 26.3+, SDL3). */
public final class KeyNames {

    private KeyNames() {}

    public static String name(int key) {
        if (key < 0) return "";
        switch (key) {
            case InputConstants.KEY_RSHIFT: return "RShift";
            case InputConstants.KEY_LSHIFT: return "LShift";
            case InputConstants.KEY_RCONTROL: return "RCtrl";
            case InputConstants.KEY_LCONTROL: return "LCtrl";
            case InputConstants.KEY_RALT: return "RAlt";
            case InputConstants.KEY_LALT: return "LAlt";
            case InputConstants.KEY_TAB: return "Tab";
            case InputConstants.KEY_CAPSLOCK: return "Caps";
            case InputConstants.KEY_SPACE: return "Space";
            case InputConstants.KEY_RETURN: return "Enter";
            case InputConstants.KEY_INSERT: return "Insert";
            case InputConstants.KEY_DELETE: return "Delete";
            case InputConstants.KEY_HOME: return "Home";
            case InputConstants.KEY_END: return "End";
            case InputConstants.KEY_PAGEUP: return "PgUp";
            case InputConstants.KEY_PAGEDOWN: return "PgDn";
            case InputConstants.KEY_UP: return "Up";
            case InputConstants.KEY_DOWN: return "Down";
            case InputConstants.KEY_LEFT: return "Left";
            case InputConstants.KEY_RIGHT: return "Right";
            case InputConstants.KEY_0: return "0";
            case InputConstants.KEY_NUMPAD0: return "Num0";
            default: break;
        }
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) return "F" + (key - InputConstants.KEY_F1 + 1);
        if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) return "F" + (key - InputConstants.KEY_F13 + 13);
        if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) return "Num" + (key - InputConstants.KEY_NUMPAD1 + 1);
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) return String.valueOf((char) ('A' + key - InputConstants.KEY_A));
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) return String.valueOf((char) ('1' + key - InputConstants.KEY_1));
        return "Key" + key;
    }
}
