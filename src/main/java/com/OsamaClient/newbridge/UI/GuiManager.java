package com.OsamaClient.newbridge.UI;

import net.minecraft.client.gui.screens.Screen;

/**
 * Zentrale Verwaltung des aktiven GUI-Modus (Classic vs. Modern).
 *
 * Bewusst additiv gehalten: der Manager ERSETZT nichts an der bestehenden
 * {@link ClickGuiScreen}, sondern entscheidet nur, welcher Screen beim Öffnen
 * der GUI instanziiert wird. Dadurch bleibt der bewährte Classic-Pfad
 * unverändert und risikofrei.
 *
 * Der Modus wird von Config gelesen/geschrieben.
 */
public final class GuiManager {

    private static GuiMode mode = GuiMode.CLASSIC;

    private GuiManager() {}

    public static GuiMode getMode() {
        return mode;
    }

    public static void setMode(GuiMode newMode) {
        if (newMode != null) mode = newMode;
    }

    public static void toggleMode() {
        mode = (mode == GuiMode.CLASSIC) ? GuiMode.MODERN : GuiMode.CLASSIC;
    }

    /** Erzeugt den passenden Screen für den aktuell aktiven Modus. */
    public static Screen createScreen() {
        return switch (mode) {
            case MODERN -> new ModernClickGuiScreen();
            case CLASSIC -> new ClickGuiScreen();
        };
    }
}
