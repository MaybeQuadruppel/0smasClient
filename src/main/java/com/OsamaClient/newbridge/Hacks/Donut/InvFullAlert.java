package com.OsamaClient.newbridge.Hacks.Donut;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;

/** Warns when your inventory is almost full (useful while AFK mining). */
public class InvFullAlert extends Module {

    public static InvFullAlert INSTANCE;

    public double freeSlots = 3;
    public boolean full;
    public int free;
    private int cooldown;

    public InvFullAlert() {
        super("InvFullAlert", "Warns when only a few free inventory slots are left.", Category.Donut);
        INSTANCE = this;
        settings.add(new Slider("Free Slots", 0, 36, freeSlots, 1, v -> freeSlots = v).withDescription("Warn at this many free slots or fewer."));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        int f = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) f++;
        free = f;
        full = f <= freeSlots;
        if (cooldown > 0) { cooldown--; return; }
        if (full) {
            Notify.bar("Inventory almost full: " + f + " free");
            cooldown = 200;
        }
    }
}
