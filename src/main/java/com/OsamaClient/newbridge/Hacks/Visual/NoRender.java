package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;

public class NoRender extends Module {
    public static NoRender INSTANCE;

    // Overlay- & Effekt-Werte
    public boolean pumpkin = true;
    public boolean powderedSnow = true;
    public boolean portal = true;
    public boolean potions = true;
    public boolean vignette = true;
    public boolean scoreboard = true;
    public boolean spyglass = true;
    public boolean crosshair = false;
    public boolean title = true;
    public boolean heldItemName = false;
    public boolean nausea = true;
    public boolean blindness = true;
    public boolean darkness = true;
    public boolean fire = true;

    public NoRender() {
        super("NoRender", "Deaktiviert verschiedene Overlays", Category.VISUAL);
        INSTANCE = this;

        // ToggleButtons für die ClickGUI über 'this.settings.add' hinzufügen
        this.settings.add(new ToggleButton("Pumpkin", pumpkin, val -> pumpkin = val).withDescription("Disables the pumpkin blur overlay when wearing a carved pumpkin."));
        this.settings.add(new ToggleButton("Powdered Snow", powderedSnow, val -> powderedSnow = val).withDescription("Disables the overlay when trapped in powdered snow."));
        this.settings.add(new ToggleButton("Portal", portal, val -> portal = val).withDescription("Disables the nether portal purple screen overlay animation."));
        this.settings.add(new ToggleButton("Potion Icons", potions, val -> potions = val).withDescription("Disables the active potion effect icons rendering on the HUD."));
        this.settings.add(new ToggleButton("Vignette", vignette, val -> vignette = val).withDescription("Disables the screen edge darkness vignette effect."));
        this.settings.add(new ToggleButton("Scoreboard", scoreboard, val -> scoreboard = val).withDescription("Disables rendering of the sidebar scoreboard."));
        this.settings.add(new ToggleButton("Spyglass", spyglass, val -> spyglass = val).withDescription("Disables the spyglass zoom scope overlay."));
        this.settings.add(new ToggleButton("Crosshair", crosshair, val -> crosshair = val).withDescription("Disables rendering of the crosshair."));
        this.settings.add(new ToggleButton("Title", title, val -> title = val).withDescription("Disables large title text messages displayed on screen."));
        this.settings.add(new ToggleButton("Held Item Name", heldItemName, val -> heldItemName = val).withDescription("Disables the popup name of items when selected in your hotbar."));
        this.settings.add(new ToggleButton("Nausea", nausea, val -> nausea = val).withDescription("Disables the nausea/portal warp screen distortion effect."));
        // Neu hinzugefügte Settings:
        this.settings.add(new ToggleButton("Blindness", blindness, val -> blindness = val).withDescription("Disables the blindness effect darkness overlay."));
        this.settings.add(new ToggleButton("Darkness", darkness, val -> darkness = val).withDescription("Disables the Warden's darkness effect overlay."));
        this.settings.add(new ToggleButton("Fire", fire, val -> fire = val).withDescription("Disables the fire overlay on your screen when you are burning."));
    }
}