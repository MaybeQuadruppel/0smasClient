package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.ItemPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Throws out stacks of the items you pick as junk. */
public class AutoDrop extends Module {

    public static AutoDrop INSTANCE;

    public final ItemPicker junk = new ItemPicker("Junk Items");
    public double delay = 2;
    private int wait;

    public AutoDrop() {
        super("AutoDrop", "Drops the junk items you select as soon as they enter your inventory.", Category.MISC);
        INSTANCE = this;
        settings.add(junk.withDescription("Items that get dropped."));
        settings.add(new Slider("Delay", 0, 20, delay, 1, v -> delay = v).withDescription("Ticks between drops."));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gameMode == null || mc.gui.screen() != null || junk.selectedItems.isEmpty()) return;
        if (wait > 0) { wait--; return; }
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !junk.selectedItems.containsKey(s.getItem())) continue;
            int slot = i < 9 ? 36 + i : i;
            mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, 1, ContainerInput.THROW, p);
            wait = (int) delay;
            return;
        }
    }
}
