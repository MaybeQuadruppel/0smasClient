package com.OsamaClient.newbridge.Hacks.Misc; // Passe das Package bei Bedarf an (z.B. Hacks.Player)

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
    public boolean stealAll = true;       // Nimmt ausnahmslos alle Items aus der Kiste
    public boolean autoClose = true;      // Schließt die Kiste automatisch, sobald sie leer ist
    public double delay = 80.0;           // Verzögerung in ms pro Item (Anti-Cheat / Legit-Look)
    public boolean randomizeDelay = true; // Kleine Zufallsabweichung beim Delay

    private long nextStealTime = 0;
    private final Random random = new Random();

    public ChestStealer() {
        super("ChestStealer", "Steals items automatically from chests", Category.MISC);
        INSTANCE = this;

        // Settings zur GUI hinzufügen
        this.settings.add(new ToggleButton("Steal All", stealAll, val -> stealAll = val));
        this.settings.add(new ToggleButton("Auto Close", autoClose, val -> autoClose = val));
        this.settings.add(new Slider("Delay (ms)", 0.0, 300.0, delay, val -> delay = val));
        this.settings.add(new ToggleButton("Randomize Delay", randomizeDelay, val -> randomizeDelay = val));
    }

    /**
     * Rufe diese Methode in deinem Haupt-Update/Tick Loop auf.
     */
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;

        // WICHTIG: Wenn der Spieler sein eigenes Inventar (E) öffnet -> SOFORT ABBRECHEN!
        if (client.gui.screen() instanceof InventoryScreen) {
            return;
        }

        // Nur ausführen, wenn wirklich ein Kisten-Menü (ChestMenu) geöffnet ist
        if (!(client.player.containerMenu instanceof ChestMenu menu)) {
            return;
        }

        // Delay-Abfrage (Wartet x Millisekunden ab)
        if (System.currentTimeMillis() < nextStealTime) return;

        int chestSize = menu.getContainer().getContainerSize(); // Anzahl der Kisten-Slots (27 bei einzelner, 54 bei doppelter Kiste)
        boolean isEmpty = true;

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();

            if (!stack.isEmpty()) {
                isEmpty = false;

                // Wenn "Steal All" aktiv ist ODER das Item nützlich ist
                if (stealAll || isUsefulItem(stack)) {
                    // Shift-Klick (Quick Move) ausführen, um das Item ins eigene Inventar zu verschieben
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

        // Sobald alle Items herausgeholt wurden, Kiste automatisch schließen
        if (isEmpty && autoClose) {
            client.player.closeContainer();
        }
    }

    /**
     * Platzhalter für nützliche Items, falls "Steal All" mal ausgeschaltet ist.
     */
    private boolean isUsefulItem(ItemStack stack) {
        return true;
    }
}