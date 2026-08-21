package com.OsamaClient.newbridge.Hacks.Donut;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.TextBox;
import com.OsamaClient.newbridge.UI.components.ItemPicker;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class AutoSell extends Module {
    public static AutoSell INSTANCE;

    // Settings
    public String sellCommand = "/sell";
    public boolean sellAll = false;
    public boolean alwaysSell = false;    // Verkaufe auch bei Teilinventar
    public double delay = 80.0;
    public boolean randomizeDelay = true;

    public ItemPicker itemPicker;

    private long nextSellTime = 0;
    private final Random random = new Random();

    public AutoSell() {
        super("AutoSell", "Automatically sells items in the sell menu", Category.Donut);
        INSTANCE = this;

        this.itemPicker = new ItemPicker("Items").withDescription("Selects specific items to sell when 'Sell All' is disabled.");

        this.settings.add(new TextBox("Command", "Sell Command", text -> sellCommand = text)
                .withDescription("Sets the command used to open the sell GUI."));
        this.settings.add(new ToggleButton("Sell All", sellAll, val -> sellAll = val)
                .withDescription("Sells all items from the inventory regardless of the item filter."));
        this.settings.add(new ToggleButton("Always Sell", alwaysSell, val -> alwaysSell = val)
                .withDescription("Starts selling even if the inventory is not full, as long as target items are present."));
        this.settings.add(this.itemPicker);
        this.settings.add(new Slider("Delay (ms)", 0.0, 300.0, delay, val -> delay = val)
                .withDescription("Delay in milliseconds between selling each item."));
        this.settings.add(new ToggleButton("Randomize Delay", randomizeDelay, val -> randomizeDelay = val)
                .withDescription("Adds random variations to the delay to mimic natural clicking."));
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;
        if (client.gui.screen() instanceof InventoryScreen) return;

        AbstractContainerMenu container = client.player.containerMenu;
        if (container == null) return;

        boolean isSellGui = false;
        try {
            if (client.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
                String title = containerScreen.getTitle().getString();
                if (title != null && title.equalsIgnoreCase("Sell")) {
                    isSellGui = true;
                }
            }
        } catch (Exception ignored) {}

        if (!isSellGui) {
            // Prüfung: Muss nur feuern, wenn das Inventar voll ODER Always Sell aktiv ist
            if (!alwaysSell && !isInventoryFull(client)) {
                return;
            }

            // Wenn Always Sell aktiv ist, prüfen wir zusätzlich, ob überhaupt etwas zu verkaufen ist
            if (alwaysSell && !hasTargetItems(client)) {
                return;
            }

            if (System.currentTimeMillis() < nextSellTime) return;
            if (client.player.connection != null) {
                String cmd = sellCommand.startsWith("/") ? sellCommand.substring(1) : sellCommand;
                client.player.connection.sendCommand(cmd);
                nextSellTime = System.currentTimeMillis() + 1000L;
            }
            return;
        }

        if (System.currentTimeMillis() < nextSellTime) return;

        boolean actionTaken = false;
        for (int i = 54; i < container.slots.size(); i++) {
            ItemStack stack = container.slots.get(i).getItem();
            if (!stack.isEmpty()) {
                if (sellAll || isUsefulItem(stack)) {
                    client.gameMode.handleContainerInput(container.containerId, i, 0, ContainerInput.QUICK_MOVE, client.player);
                    actionTaken = true;
                    break;
                }
            }
        }

        if (!actionTaken) {
            boolean glassPaneFound = false;
            for (int i = 0; i < container.slots.size(); i++) {
                ItemStack slotStack = container.slots.get(i).getItem();
                if (!slotStack.isEmpty() && slotStack.getItem().getDescriptionId().contains("light_green_stained_glass_pane")) {
                    client.gameMode.handleContainerInput(container.containerId, i, 0, ContainerInput.PICKUP, client.player);
                    glassPaneFound = true;
                    break;
                }
            }

            // Wenn keine grüne Glasscheibe (Bestätigen-Button) gefunden wurde, schließe die GUI
            if (!glassPaneFound) {
                client.player.closeContainer();
                return;
            }
        }

        double currentDelay = delay;
        if (randomizeDelay && delay > 0) {
            currentDelay += (random.nextDouble() - 0.5) * (delay * 0.4);
        }
        nextSellTime = System.currentTimeMillis() + (long) Math.max(0, currentDelay);
    }

    private boolean isInventoryFull(Minecraft client) {
        for (int i = 9; i <= 44; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private boolean hasTargetItems(Minecraft client) {
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty() && (sellAll || isUsefulItem(stack))) return true;
        }
        return false;
    }

    private boolean isUsefulItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return itemPicker.selectedItems.contains(stack.getItem());
    }
}