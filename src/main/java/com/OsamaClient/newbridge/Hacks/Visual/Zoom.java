package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.input.Keys;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Zoom like OptiFine/WI-Zoom: divides the field of view by a zoom factor (the FOV option itself is never touched).
 * While zoomed, the mouse wheel (with the chosen modifier key) changes the zoom; it animates smoothly and the mouse
 * gets slower the further you zoom in.
 */
public class Zoom extends Module {

    public static Zoom INSTANCE;

    public double zoom = 4.0, maxZoom = 50.0, scrollStep = 1.25;
    public String scrollKey = "Alt";
    public boolean scroll = true, smooth = true, lowerSensitivity = true, resetOnRelease = true, showLevel = true;

    /** Zoom factor the view is animating towards, and the value currently used. */
    private double target = 1.0, current = 1.0;
    private long lastNanos;

    public Zoom() {
        super("Zoom", "Zooms in like OptiFine zoom; change the zoom with the mouse wheel.", Category.VISUAL);
        INSTANCE = this;
        holdToActivate = true;
        settings.add(new Slider("Zoom", 1.5, 20, zoom, 0.5, v -> { zoom = v; if (enabled) target = v; })
                .withDescription("How far it zooms in when you press the key (4 = four times closer)."));
        settings.add(new ToggleButton("Scroll Zoom", scroll, v -> scroll = v).withDescription("Change the zoom with the mouse wheel while zoomed."));
        settings.add(new ModeButton("Scroll Key", List.of("Alt", "None", "Ctrl", "Shift"), 0, v -> scrollKey = v)
                .withDescription("Key you hold together with the wheel. None = the wheel always zooms while zoomed (no hotbar scrolling)."));
        settings.add(new Slider("Max Zoom", 5, 100, maxZoom, 1, v -> maxZoom = v).withDescription("Limit for scrolling in."));
        settings.add(new ToggleButton("Smooth", smooth, v -> smooth = v).withDescription("Animate zooming in and out instead of jumping."));
        settings.add(new ToggleButton("Lower Sensitivity", lowerSensitivity, v -> lowerSensitivity = v)
                .withDescription("Slower mouse while zoomed, so aiming stays precise at high zoom."));
        settings.add(new ToggleButton("Reset On Release", resetOnRelease, v -> resetOnRelease = v)
                .withDescription("Start at the Zoom value every time. Off: keep the last scrolled zoom."));
        settings.add(new ToggleButton("Show Level", showLevel, v -> showLevel = v).withDescription("Show the zoom level in the action bar while scrolling."));
    }

    @Override
    public void onEnable() {
        if (resetOnRelease || target <= 1.0) target = zoom;
    }

    @Override
    public void onDisable() {
        if (!resetOnRelease) zoom = Math.max(1.5, target);
        target = 1.0;
    }

    /** Factor the FOV is divided by right now (1 = no zoom). Called every frame from the camera mixin. */
    public static double factor() {
        Zoom z = INSTANCE;
        if (z == null) return 1.0;
        long now = System.nanoTime();
        double dt = z.lastNanos == 0 ? 0 : Math.min(0.1, (now - z.lastNanos) / 1e9);
        z.lastNanos = now;
        double goal = z.enabled ? z.target : 1.0;
        if (!z.smooth) z.current = goal;
        else z.current += (goal - z.current) * Math.min(1.0, dt * 14.0);
        if (Math.abs(z.current - goal) < 0.002) z.current = goal;
        return z.current;
    }

    /** Mouse multiplier while zoomed. */
    public static double sensitivity() {
        Zoom z = INSTANCE;
        if (z == null || !z.enabled || !z.lowerSensitivity) return 1.0;
        return 1.0 / Math.max(1.0, z.current);
    }

    private boolean modifierHeld() {
        return switch (scrollKey) {
            case "None" -> true;
            case "Ctrl" -> UiInput.keyDown(Keys.KEY_LEFT_CONTROL) || UiInput.keyDown(Keys.KEY_RIGHT_CONTROL);
            case "Shift" -> UiInput.keyDown(Keys.KEY_LEFT_SHIFT) || UiInput.keyDown(Keys.KEY_RIGHT_SHIFT);
            default -> UiInput.keyDown(Keys.KEY_LEFT_ALT) || UiInput.keyDown(Keys.KEY_RIGHT_ALT);
        };
    }

    /** Called from the mouse mixin. @return true if the scroll was used for zooming (no hotbar scroll). */
    public static boolean onScroll(double amount) {
        Zoom z = INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (z == null || !z.enabled || !z.scroll || amount == 0 || mc.gui.screen() != null || !z.modifierHeld()) return false;
        z.scrollBy(amount);
        return true;
    }

    /** One wheel step in (positive) or out (negative); tests call this directly. */
    public void scrollBy(double amount) {
        double f = Math.pow(scrollStep, Math.signum(amount) * Math.min(3, Math.abs(amount)));
        target = Math.max(1.0, Math.min(maxZoom, target * f));
        if (showLevel) com.OsamaClient.newbridge.Utils.Notify.bar(String.format(Locale.ROOT, "Zoom %.1fx", target));
    }

    public double target() { return target; }
}
