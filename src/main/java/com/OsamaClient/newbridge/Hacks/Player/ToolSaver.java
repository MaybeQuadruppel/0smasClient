package com.OsamaClient.newbridge.Hacks.Player;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Switches away from a tool that is about to break. */
public class ToolSaver extends Module {

    public static ToolSaver INSTANCE;

    public double remaining = 10;

    public ToolSaver() {
        super("ToolSaver", "Swaps off tools and weapons with almost no durability left.", Category.PLAYER);
        INSTANCE = this;
        settings.add(new Slider("Remaining Uses", 1, 50, remaining, 1, v -> remaining = v).withDescription("Swap when this many uses or fewer are left."));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gui.screen() != null) return;
        var inv = p.getInventory();
        ItemStack held = inv.getItem(inv.getSelectedSlot());
        if (held.isEmpty() || !held.isDamageableItem()) return;
        if (held.getMaxDamage() - held.getDamageValue() > remaining) return;
        int best = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (i == inv.getSelectedSlot()) continue;
            if (s.isEmpty() || !s.isDamageableItem()) { best = i; if (s.isEmpty()) break; }
        }
        if (best >= 0) {
            inv.setSelectedSlot(best);
            Notify.bar(held.getHoverName().getString() + " is about to break");
        }
    }
}
