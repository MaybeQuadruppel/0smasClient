package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class ChestStealer extends Module {

    public static ChestStealer INSTANCE;

    // Settings
    public boolean stealAll = false;
    public boolean autoClose = true;
    public double delay = 80.0;
    public boolean randomizeDelay = true;


    public ItemPicker itemPicker;

    private long nextStealTime = 0;
    private final Random random = new Random();

    public ChestStealer() {
        super("ChestStealer", "Steals items automatically from chests", Category.MISC);
        INSTANCE = this;
        this.itemPicker = new ItemPicker("Items").withDescription("Selects specific items to steal when 'Steal All' is disabled.");
        this.settings.add(new ToggleButton("Steal All", stealAll, val -> stealAll = val).withDescription("Steals all items from the container regardless of item filter."));
        this.settings.add(this.itemPicker); // ItemPicker Dropdown in der GUI
        this.settings.add(new ToggleButton("Auto Close", autoClose, val -> autoClose = val).withDescription("Automatically closes the container once all target items are stolen."));
        this.settings.add(new Slider("Delay (ms)", 0.0, 300.0, delay, val -> delay = val).withDescription("Delay in milliseconds between taking each item."));
        this.settings.add(new ToggleButton("Randomize Delay", randomizeDelay, val -> randomizeDelay = val).withDescription("Adds random variations to the delay to mimic natural clicking."));
    }

    /**
     * Rufe diese Methode in deinem Haupt-Update/Tick Loop auf.
     */
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;
        if (client.gui.screen() instanceof InventoryScreen) {
            return;
        }
        if (!(client.player.containerMenu instanceof ChestMenu menu)) {
            return;
        }

        // Delay-Abfrage (Wartet x Millisekunden ab)
        if (System.currentTimeMillis() < nextStealTime) return;

        int chestSize = menu.getContainer().getContainerSize();
        boolean hasRemainingTargets = false;

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();

            if (!stack.isEmpty()) {
                if (stealAll || isUsefulItem(stack)) {
                    hasRemainingTargets = true;

                    client.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, client.player);

                    // Nächsten Klick-Zeitpunkt berechnen
                    double currentDelay = delay;
                    if (randomizeDelay && delay > 0) {
                        currentDelay += (random.nextDouble() - 0.5) * (delay * 0.4);
                    }
                    nextStealTime = System.currentTimeMillis() + (long) Math.max(0, currentDelay);
                    return; // Pro Tick nur ein Item nehmen, damit der Delay greift
                }
            }
        }
        if (!hasRemainingTargets && autoClose) {
            client.player.closeContainer();
        }
    }

    /**
     * Prüft, ob das Item im ItemPicker durch den Spieler ausgewählt wurde.
     */
    private boolean isUsefulItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // HIER IST DER FIX: .containsKey anstelle von .contains
        return itemPicker.selectedItems.containsKey(stack.getItem());
    }
}