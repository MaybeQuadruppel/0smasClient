package com.OsamaClient.newbridge.UI.components;

/**
 * Basis für Modul-Einstellungen.
 *
 * Nach dem Entfernen der GUI ist dies nur noch eine Datenhülle: Größe/
 * Beschreibung und die Felder, die die Module lesen/schreiben. Es findet kein
 * Rendering und keine Eingabeverarbeitung mehr statt.
 */
public abstract class Component {
    public int x;
    public int y;
    public int width;
    public int height;

    protected int baseWidth;
    protected int baseHeight;
    protected String description = "";

    public Component(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseWidth = width;
        this.baseHeight = height;
    }

    /** Beibehalten für Kompatibilität mit vorhandenen Konstruktoren. */
    public void syncScaledSize(int baseW, int baseH, int minW, int minH) {
        this.baseWidth = baseW;
        this.baseHeight = baseH;
        this.width = Math.max(minW, baseW);
        this.height = Math.max(minH, baseH);
    }

    protected void syncHeight(int baseH, int minH) {
        this.baseHeight = baseH;
        this.height = Math.max(minH, baseH);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T withDescription(String description) {
        this.description = description;
        return (T) this;
    }
}
