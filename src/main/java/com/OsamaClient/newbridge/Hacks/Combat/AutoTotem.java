package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;

import java.util.concurrent.ThreadLocalRandom;

public class AutoTotem extends Module {
    public static AutoTotem INSTANCE;

    // Settings
    public boolean legitMode = false;     // Erfordert geöffnetes Inventar & Hover über Totem
    public boolean alwaysEquip = false;   // true = Immer nachrüsten (auch ohne Pop), false = nur nach Pop
    public boolean autoClose = false;     // Schließt das Inventar automatisch nach dem Ausrüsten im Legit Mode
    public double delay = 1.0;            // Delay in Ticks
    public boolean randomDelay = false;   // Zufälliger Delay

    // Interner State
    private int timer = -1;
    private int currentTargetDelay = 0;
    private boolean attemptedEquip = false; // Verhindert Endlosschleifen/Hängenbleiben im Rage Modus
    private boolean wasInventoryOpen = false;

    public AutoTotem() {
        super("AutoTotem", "Auto Equips a Totem after Pop", Category.COMBAT);

        // GUI Settings
        this.settings.add(new ToggleButton("Legit Mode", legitMode, val -> legitMode = val).withDescription("Requires you to hover your mouse over the totem to equip it legitimately."));
        this.settings.add(new ToggleButton("Always Equip", alwaysEquip, val -> alwaysEquip = val).withDescription("Controls whether to equip after a pop or when your offhand is empty."));
        this.settings.add(new ToggleButton("Auto Close", autoClose, val -> autoClose = val).withDescription("Automatically closes the inventory screen after equipping a totem."));
        this.settings.add(new Slider("Delay Ticks", 0.0, 40.0, delay, val -> delay = val).withDescription("Sets the delay in ticks before equipping another totem."));
        this.settings.add(new ToggleButton("Randomize", randomDelay, val -> randomDelay = val).withDescription("Adds a random variance to the delay to mimic human behavior."));

        INSTANCE = this;
    }

    /**
     * Wird aufgerufen, wenn ein Totem gepoppt wird.
     */
    public void onTotemPop() {
        if (!this.enabled) return;

        attemptedEquip = false; // Reset Anti-Stuck bei Pop

        if (timer == -1) {
            startDelayTimer();
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (!this.enabled || client.player == null || client.level == null) return;

        boolean isInvOpen = client.gui.screen() instanceof AbstractContainerScreen<?>;
        if (isInvOpen && !wasInventoryOpen) {
            attemptedEquip = false;
        }
        wasInventoryOpen = isInvOpen;

        boolean hasTotemInOffhand = client.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING;

        if (hasTotemInOffhand) {
            timer = -1;
            attemptedEquip = false;
            return;
        }

        if (alwaysEquip && timer == -1 && !attemptedEquip) {
            startDelayTimer();
        }

        if (timer != -1) {
            if (timer < currentTargetDelay) {
                timer++;
                return;
            }

            if (legitMode) {
                if (client.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
                    Slot hoveredSlot = ((AbstractContainerScreenAccessor) containerScreen).getHoveredSlot();

                    if (hoveredSlot != null && hoveredSlot.hasItem() && hoveredSlot.getItem().getItem() == Items.TOTEM_OF_UNDYING) {
                        client.gameMode.handleContainerInput(
                                containerScreen.getMenu().containerId,
                                hoveredSlot.index,
                                40,
                                ContainerInput.SWAP,
                                client.player
                        );

                        timer = -1;
                        attemptedEquip = true;


                        if (autoClose) {
                            client.player.closeContainer();
                        }
                    }
                }
                return;
            }


            int totemSlot = findTotemSlot(client.player.getInventory());

            if (totemSlot != -1) {
                int slot = totemSlot < 9 ? totemSlot + 36 : totemSlot;

                client.gameMode.handleContainerInput(0, slot, 0, ContainerInput.PICKUP, client.player);
                client.gameMode.handleContainerInput(0, 45, 0, ContainerInput.PICKUP, client.player);
                client.gameMode.handleContainerInput(0, slot, 0, ContainerInput.PICKUP, client.player);
            }

            timer = -1;
            attemptedEquip = true;
        }
    }

    private void startDelayTimer() {
        if (randomDelay) {
            currentTargetDelay = ThreadLocalRandom.current().nextInt(24, 37);
        } else {
            currentTargetDelay = (int) delay;
        }
        timer = 0;
    }

    private int findTotemSlot(Inventory inv) {
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) return i;
        }
        return -1;
    }
}