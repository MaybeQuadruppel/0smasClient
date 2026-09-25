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
    public final Slider scale = new Slider("Scale", 1.0, 3.0, 1.5, 0.25, null)
            .withDescription("GUI size in physical pixels per unit (independent of Minecraft's GUI scale)");
    public final Slider radius = new Slider("Radius", 0, 5, 2.5, 0.5, null)
            .withDescription("Corner radius");
    public final Slider glow = new Slider("Glow", 0, 1, 0.6, 0.05, null)
            .withDescription("Strength of the accent glow");
    public final Slider animSpeed = new Slider("Anim Speed", 0.25, 3, 1, 0.25, null)
            .withDescription("Animation speed multiplier");
    public final ModeButton background = new ModeButton("Background", List.of("Dim", "None", "Blur"), 0, null)
            .withDescription("What is drawn behind the GUI");
    public final ToggleButton outline = new ToggleButton("Outline", true, null)
            .withDescription("Thin outline around panels");
    public final ToggleButton descriptions = new ToggleButton("Descriptions", true, null)
            .withDescription("Show module descriptions as tooltips");

    public ClickGuiModule() {
        super("ClickGui", "Opens this GUI", Category.CLIENT);
        INSTANCE = this;
        key = GLFW.GLFW_KEY_RIGHT_SHIFT;
        settings.add(accent);
        settings.add(scale);
        settings.add(radius);
        settings.add(glow);
        settings.add(animSpeed);
        settings.add(background);
        settings.add(outline);
        settings.add(descriptions);
    }

    @Override
    public void toggle() {
        // never stays enabled: toggling just opens (or closes) the GUI
        enabled = false;
        ClickGui.INSTANCE.toggleOpen();
    }
}
