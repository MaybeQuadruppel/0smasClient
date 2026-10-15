package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Refills a nearly empty hotbar stack from your inventory. */
public class AutoReplenish extends Module {

    public static AutoReplenish INSTANCE;

    public double threshold = 8;
    public double delay = 4;
    private int wait;

    public AutoReplenish() {
        super("AutoReplenish", "Refills hotbar stacks (blocks, food, pearls) from your inventory.", Category.MISC);
        INSTANCE = this;
        settings.add(new Slider("Threshold", 1, 32, threshold, 1, v -> threshold = v).withDescription("Refill when the stack has this many items or fewer."));
        settings.add(new Slider("Delay", 1, 20, delay, 1, v -> delay = v).withDescription("Ticks between refills."));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (wait > 0) { wait--; return; }
        var inv = p.getInventory();
        for (int h = 0; h < 9; h++) {
            ItemStack hot = inv.getItem(h);
            if (hot.isEmpty() || hot.getMaxStackSize() <= 1 || hot.getCount() > threshold) continue;
            for (int i = 9; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, hot)) {
                    mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, i, 0, ContainerInput.QUICK_MOVE, p);
                    wait = (int) delay;
                    return;
                }
            }
        }
    }
}
