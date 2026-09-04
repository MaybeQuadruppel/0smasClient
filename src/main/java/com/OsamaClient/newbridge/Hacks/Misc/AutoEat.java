package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.event.Subscribe;
// TODO: Importiere hier dein korrektes Tick-Event (z.B. ClientTickEvent oder UpdateEvent)
// import com.OsamaClient.newbridge.event.TickEvent;
import com.OsamaClient.newbridge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public class AutoEat extends Module {

    private boolean isEating = false;

    public AutoEat() {
        super("AutoEat", "Automatically eats food when hungry", Category.MISC);

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (!this.enabled) {

            if (isEating) stopEating();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int foodLevel = mc.player.getFoodData().getFoodLevel();
        if (foodLevel <= 16) {
            int foodSlot = findFoodSlot(mc);

            if (foodSlot != -1) {
                if (mc.player.getInventory().getSelectedSlot() != foodSlot) {
                    mc.player.getInventory().setSelectedSlot(foodSlot);
                }

                // Rechte Maustaste simulieren
                mc.options.keyUse.setDown(true);
                isEating = true;
            } else if (isEating) {

                stopEating();
            }
        } else if (isEating) {
            stopEating();
        }
    }

    private void stopEating() {
        Minecraft.getInstance().options.keyUse.setDown(false);
        isEating = false;
    }

    private int findFoodSlot(Minecraft mc) {

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (stack.has(DataComponents.FOOD)) {
                return i;
            }
        }
        return -1;
    }
    @Override
    public void onDisable() {
        super.onDisable();
        if (isEating) {
            stopEating();
        }
    }
}