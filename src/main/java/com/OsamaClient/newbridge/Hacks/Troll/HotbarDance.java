package com.OsamaClient.newbridge.Hacks.Troll;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;

/** Cycles through your hotbar so everyone sees you swap items. */
public class HotbarDance extends Module {

    public static HotbarDance INSTANCE;

    public double speed = 3;
    private int tick, prev = -1;

    public HotbarDance() {
        super("HotbarDance", "Rapidly cycles the selected hotbar slot.", Category.TROLL);
        INSTANCE = this;
        settings.add(new Slider("Speed", 1, 20, speed, 1, v -> speed = v).withDescription("Ticks per slot change."));
    }

    @Override
    public void onEnable() { prev = -1; tick = 0; }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (prev >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prev);
        prev = -1;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gui.screen() != null) return;
        if (prev < 0) prev = p.getInventory().getSelectedSlot();
        if (++tick % (int) speed != 0) return;
        var inv = p.getInventory();
        inv.setSelectedSlot((inv.getSelectedSlot() + 1) % 9);
    }
}
