package com.OsamaClient.newbridge.Hacks.Player;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Zieht automatisch die beste Rüstung aus dem Inventar an (ein Teil pro Durchgang, mit Verzögerung). */
public class AutoArmor extends Module {

    public static AutoArmor INSTANCE;
    private static final EquipmentSlot[] ORDER = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET};

    public double delay = 4;
    public String priority = "Protection";
    public boolean preferElytra = false, avoidBinding = true, onlyInventoryClosed = false;
    public double minDurability = 5;
    private int cooldown;
    /** Für Tests: Anzahl durchgeführter Wechsel. */
    public int swaps;

    public AutoArmor() {
        super("AutoArmor", "Equips your best armor automatically.", Category.PLAYER);
        INSTANCE = this;
        settings.add(new Slider("Delay", 0, 20, delay, 1, v -> delay = v).withDescription("Ticks between two armor swaps."));
        settings.add(new ModeButton("Priority", List.of("Protection", "Blast", "Fire", "Projectile"), 0, v -> priority = v)
                .withDescription("Which protection enchantment counts the most."));
        settings.add(new ToggleButton("Prefer Elytra", preferElytra, v -> preferElytra = v)
                .withDescription("Keeps/equips an elytra in the chest slot instead of a chestplate."));
        settings.add(new ToggleButton("Avoid Binding", avoidBinding, v -> avoidBinding = v)
                .withDescription("Never equips armor with Curse of Binding."));
        settings.add(new Slider("Min Durability %", 0, 50, minDurability, 1, v -> minDurability = v)
                .withDescription("Armor below this durability is treated as broken."));
        settings.add(new ToggleButton("Only Inventory Closed", onlyInventoryClosed, v -> onlyInventoryClosed = v)
                .withDescription("Only swaps while no screen is open (looks more legit)."));
    }

    public ArmorScorer.Prefs prefs() {
        ArmorScorer.Priority p = ArmorScorer.Priority.valueOf(priority.toUpperCase(Locale.ROOT));
        return new ArmorScorer.Prefs(p, preferElytra, avoidBinding, (int) minDurability);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (player.containerMenu != player.inventoryMenu) return;          // anderer Container offen
        var screen = mc.gui.screen();
        if (screen != null && (onlyInventoryClosed || !(screen instanceof InventoryScreen))) return;
        if (player.isCreative()) return;

        ArmorScorer.Prefs p = prefs();
        for (EquipmentSlot slot : ORDER) {
            ItemStack worn = player.getItemBySlot(slot);
            int idx = ArmorScorer.bestFor(slot, worn, i -> player.getInventory().getItem(i), 36, p);
            if (idx < 0) continue;
            equip(mc, player, idx, slot, worn.isEmpty());
            swaps++;
            cooldown = (int) delay;
            return;
        }
    }

    /** Menü-Slot der Rüstung im InventoryMenu. */
    static int armorMenuSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };
    }

    /** Spielerinventar-Index (0..35) -> Menü-Slot im InventoryMenu (Hotbar = 36..44). */
    static int inventoryMenuSlot(int index) {
        return index < 9 ? 36 + index : index;
    }

    private void equip(Minecraft mc, LocalPlayer player, int invIndex, EquipmentSlot slot, boolean wornEmpty) {
        int id = player.inventoryMenu.containerId;
        int from = inventoryMenuSlot(invIndex), to = armorMenuSlot(slot);
        if (wornEmpty) {
            mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.QUICK_MOVE, player);
        } else {
            mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, player); // neues Teil auf den Cursor
            mc.gameMode.handleContainerInput(id, to, 0, ContainerInput.PICKUP, player);   // tauschen
            mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, player); // altes zurück
        }
    }
}
