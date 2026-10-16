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
    // more HUD elements
    public boolean bossBars = false;
    public boolean subtitles = false;
    public boolean actionBar = false;
    public boolean sleepOverlay = false;
    public boolean healthBar = false;
    public boolean foodBar = false;
    public boolean vehicleHealth = false;
    public boolean savingIcon = false;
    // screen / camera / world
    public boolean waterOverlay = false;
    public boolean totemAnimation = false;
    public boolean hurtCam = false;
    public boolean viewBobbing = false;
    public boolean weather = false;
    public boolean particles = false;

    /** true while the module is on and the option is set. */
    public static boolean on(boolean option) { return INSTANCE != null && INSTANCE.enabled && option; }

    public NoRender() {
        super("NoRender", "Turns off annoying render effects and overlays.", Category.VISUAL);
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
        this.settings.add(new ToggleButton("Boss Bars", bossBars, val -> bossBars = val).withDescription("Hides the boss health bars at the top of the screen."));
        this.settings.add(new ToggleButton("Subtitles", subtitles, val -> subtitles = val).withDescription("Hides the sound subtitles."));
        this.settings.add(new ToggleButton("Action Bar", actionBar, val -> actionBar = val).withDescription("Hides the text above the hotbar (server messages, 'Notifications')."));
        this.settings.add(new ToggleButton("Sleep Overlay", sleepOverlay, val -> sleepOverlay = val).withDescription("Hides the dark fade while sleeping in a bed."));
        this.settings.add(new ToggleButton("Health Bar", healthBar, val -> healthBar = val).withDescription("Hides your hearts, armor and air bubbles (use a HUD module instead if you want your own)."));
        this.settings.add(new ToggleButton("Food Bar", foodBar, val -> foodBar = val).withDescription("Hides the hunger bar."));
        this.settings.add(new ToggleButton("Vehicle Health", vehicleHealth, val -> vehicleHealth = val).withDescription("Hides the health of the horse or other mount you ride."));
        this.settings.add(new ToggleButton("Saving Icon", savingIcon, val -> savingIcon = val).withDescription("Hides the small icon in the corner while the world is saved."));
        this.settings.add(new ToggleButton("Water Overlay", waterOverlay, val -> waterOverlay = val).withDescription("Hides the blue overlay on the screen when your head is under water."));
        this.settings.add(new ToggleButton("Totem Animation", totemAnimation, val -> totemAnimation = val).withDescription("Hides the totem of undying pop animation in the middle of the screen."));
        this.settings.add(new ToggleButton("Hurt Cam", hurtCam, val -> hurtCam = val).withDescription("No camera shake when you take damage."));
        this.settings.add(new ToggleButton("View Bobbing", viewBobbing, val -> viewBobbing = val).withDescription("No camera bobbing while walking (works like turning vanilla View Bobbing off)."));
        this.settings.add(new ToggleButton("Weather", weather, val -> weather = val).withDescription("No rain, snow or thunder on your screen: the weather is treated as clear (also removes the darker sky)."));
        this.settings.add(new ToggleButton("Particles", particles, val -> particles = val).withDescription("Hides all particles (smoke, hits, explosions, ...)."));
    }
}
