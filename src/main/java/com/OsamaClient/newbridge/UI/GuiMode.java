package com.OsamaClient.newbridge.UI;

/**
 * Die beiden GUI-Modi des Clients.
 *
 * CLASSIC = die bestehende, verfeinerte {@link ClickGuiScreen} (Sidebar-Layout).
 * MODERN  = die neue, tabbasierte {@link ModernClickGuiScreen}.
 *
 * Welcher Modus aktiv ist, verwaltet {@link GuiManager}; die Auswahl wird über
 * Config persistiert.
 */
public enum GuiMode {
    CLASSIC("Classic"),
    MODERN("Modern");

    public final String displayName;

    GuiMode(String displayName) {
        this.displayName = displayName;
    }
}
