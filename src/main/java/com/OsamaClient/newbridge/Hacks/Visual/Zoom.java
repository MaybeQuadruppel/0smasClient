package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;

/** Narrows the field of view while enabled (bind it to a key, hold-to-activate works well). */
public class Zoom extends Module {

    public static Zoom INSTANCE;

    public double fov = 30;
    private int saved = -1;

    public Zoom() {
        super("Zoom", "Zooms in by lowering the field of view.", Category.VISUAL);
        INSTANCE = this;
        settings.add(new Slider("Zoom FOV", 30, 60, fov, 1, v -> { fov = v; if (enabled) apply(Minecraft.getInstance()); })
                .withDescription("Field of view while zoomed."));
    }

    private void apply(Minecraft mc) {
        if (mc.options == null) return;
        if (saved < 0) saved = mc.options.fov().get();
        mc.options.fov().set((int) fov);
    }

    @Override
    public void onEnable() { apply(Minecraft.getInstance()); }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (saved >= 0 && mc.options != null) mc.options.fov().set(saved);
        saved = -1;
    }
}
