package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Tick-based module keybinds: a fresh press of a module's key toggles it (only while no screen is open). */
public final class Keybinds {

    private static final Map<Module, Boolean> WAS_DOWN = new HashMap<>();

    private Keybinds() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Keybinds::tick);
    }

    private static void tick(Minecraft mc) {
        boolean active = mc.gui.screen() == null && mc.getWindow() != null;
        for (Module m : ModuleManager.modules) {
            if (m.key < 0) {
                WAS_DOWN.remove(m);
                continue;
            }
            // track the real key state even while a screen is open, so closing the GUI with its own key
            // does not count as a fresh press on the next tick
            boolean down = UiInput.keyDown(m.key);
            boolean was = WAS_DOWN.getOrDefault(m, true); // treat "unknown" as held: no toggle on the first tick
            WAS_DOWN.put(m, down);
            if (active && down && !was) ClickGui.INSTANCE.safeToggle(m);
        }
    }
}
