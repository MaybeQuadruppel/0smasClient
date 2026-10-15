package com.OsamaClient.newbridge.Hacks.Donut;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Warns when you have no totem of undying left. */
public class TotemAlert extends Module {

    public static TotemAlert INSTANCE;

    public double interval = 5;
    public boolean missing;
    private int cooldown;

    public TotemAlert() {
        super("TotemAlert", "Warns you when you have no totem of undying.", Category.Donut);
        INSTANCE = this;
        settings.add(new Slider("Interval", 1, 30, interval, 1, v -> interval = v).withDescription("Seconds between warnings."));
    }

    public static boolean hasTotem(Minecraft mc) {
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return true;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) return true;
        return false;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        missing = !hasTotem(mc);
        if (cooldown > 0) { cooldown--; return; }
        if (missing) {
            Notify.bar("No totem of undying left!");
            cooldown = (int) (interval * 20);
        }
    }
}
