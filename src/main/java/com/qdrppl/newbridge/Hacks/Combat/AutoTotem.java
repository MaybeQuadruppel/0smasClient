package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import java.util.concurrent.ThreadLocalRandom;

public class AutoTotem extends Module {
    public static AutoTotem INSTANCE;

    private double delay = 1.0;
    private boolean randomDelay = false;

    private int timer = -1;
    private int currentTargetDelay = 0;

    public AutoTotem() {
        super("AutoTotem", "(Auto Equips a Totem after Pop)", Category.COMBAT);

        this.settings.add(new Slider("Delay Ticks", 0.0, 40.0, 1.0, val -> delay = val));
        this.settings.add(new ToggleButton("Randomize", false, val -> randomDelay = val));

        INSTANCE = this;
    }

    public void onTotemPop() {
        if (this.enabled && timer == -1) {
            if (randomDelay) {
                currentTargetDelay = ThreadLocalRandom.current().nextInt(24, 37);
            } else {
                currentTargetDelay = (int) delay;
            }
            timer = 0;
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || timer == -1) return;

        if (timer < currentTargetDelay) {
            timer++;
            return;
        }

        int totemSlot = findTotemSlot(client.player.getInventory());
        if (totemSlot != -1) {
            int slot = totemSlot < 9 ? totemSlot + 36 : totemSlot;
            client.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.PICKUP, client.player);
            client.gameMode.handleInventoryMouseClick(0, 45, 0, ClickType.PICKUP, client.player);
            client.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.PICKUP, client.player);
        }

        timer = -1;
    }

    private int findTotemSlot(Inventory inv) {
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) return i;
        }
        return -1;
    }
}