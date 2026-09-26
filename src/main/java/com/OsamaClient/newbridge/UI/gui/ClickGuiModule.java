package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** The "ClickGui" module: toggling it opens the GUI. Holds the GUI's own customization settings. */
public class ClickGuiModule extends Module {

    public static ClickGuiModule INSTANCE;

    public final ColorPicker accent = new ColorPicker("Accent", 0xFF6C8CFF, null)
            .withDescription("Accent color of the GUI");
    public final ToggleButton rainbowAccent = new ToggleButton("Rainbow Accent", false, null)
            .withDescription("Cycles the accent color through the rainbow over time (affects the GUI and the ArrayList)");
    public final Slider rainbowSpeed = new Slider("Rainbow Speed", 0.1, 5.0, 1.0, 0.1, null)
            .withDescription("Speed of the accent rainbow cycle");
    public final Slider scale = new Slider("Scale", 1.0, 5.0, 1.5, 0.25, null)
            .withDescription("GUI size in physical pixels per unit (independent of Minecraft's GUI scale)");
    public final Slider rowHeight = new Slider("Row Height", 9.0, 16.0, 11.0, 0.5, null)
            .withDescription("Height of each module row; more room for text and easier clicking at high scale");
    public final Slider panelWidth = new Slider("Panel Width", 80.0, 140.0, 92.0, 2.0, null)
            .withDescription("Width of each category panel");
    public final Slider radius = new Slider("Radius", 0, 5, 2.5, 0.5, null)
            .withDescription("Corner radius");
    public final Slider glow = new Slider("Glow", 0, 1, 0.6, 0.05, null)
            .withDescription("Strength of the accent glow");
    public final Slider animSpeed = new Slider("Anim Speed", 0.25, 3, 1, 0.25, null)
            .withDescription("Animation speed multiplier");
    public final Slider panelOpacity = new Slider("Panel Opacity", 0.4, 1.0, 0.94, 0.02, null)
            .withDescription("Background opacity of panels");
    public final ModeButton background = new ModeButton("Background", List.of("Dim", "None", "Blur"), 0, null)
            .withDescription("What is drawn behind the GUI");
    public final ToggleButton outline = new ToggleButton("Outline", true, null)
            .withDescription("Thin outline around panels");
    public final ToggleButton descriptions = new ToggleButton("Descriptions", true, null)
            .withDescription("Show module descriptions as tooltips");
    public final ToggleButton settingHighlight = new ToggleButton("Setting Highlight", true, null)
            .withDescription("Give an expanded module's settings a distinct background so they stand out from the row list");

    public ClickGuiModule() {
        super("ClickGui", "Opens this GUI", Category.CLIENT);
        INSTANCE = this;
        key = GLFW.GLFW_KEY_RIGHT_SHIFT;
        settings.add(accent);
        settings.add(rainbowAccent);
        settings.add(rainbowSpeed);
        settings.add(scale);
        settings.add(rowHeight);
        settings.add(panelWidth);
        settings.add(radius);
        settings.add(glow);
        settings.add(animSpeed);
        settings.add(panelOpacity);
        settings.add(background);
        settings.add(outline);
        settings.add(descriptions);
        settings.add(settingHighlight);
    }

    @Override
    public void toggle() {
        // never stays enabled: toggling just opens (or closes) the GUI
        enabled = false;
        ClickGui.INSTANCE.toggleOpen();
    }
}