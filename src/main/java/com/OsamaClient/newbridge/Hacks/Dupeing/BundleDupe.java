package com.OsamaClient.newbridge.Hacks.Dupeing;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.Utils.Notify;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

/**
 * BundleDupe (V2 timing loop): needs a bundle holding exactly one stack in a hotbar slot and the plain inventory
 * open state. Port of BundleDupeV2Action from the Autism client (GPL-3.0). Whether a server is vulnerable depends
 * entirely on the server version/plugins; this only runs the packet sequence.
 */
public class BundleDupe extends Module {

    public static BundleDupe INSTANCE;

    public double pickupDelayMs = 30, putBackDelayMs = 20, dropDelayMs = 50;
    public double bundlePackets = 20, hotbarSlot = 0, bundleIndex = -1, maxCycles = 0;

    public volatile int cycles;
    private volatile boolean running;
    private Thread worker;

    public BundleDupe() {
        super("BundleDupe", "Runs the bundle dupe V2 packet loop on a bundle in your hotbar.", Category.DUPE);
        INSTANCE = this;
        settings.add(new Slider("Pickup Delay", 0, 200, pickupDelayMs, 5, v -> pickupDelayMs = v).withDescription("ms after picking up / selecting."));
        settings.add(new Slider("Put Back Delay", 0, 200, putBackDelayMs, 5, v -> putBackDelayMs = v).withDescription("ms after putting the stack back."));
        settings.add(new Slider("Drop Delay", 0, 200, dropDelayMs, 5, v -> dropDelayMs = v).withDescription("ms between the drop clicks."));
        settings.add(new Slider("Bundle Packets", 1, 2000, bundlePackets, 1, v -> bundlePackets = v).withDescription("Select-bundle packets per cycle."));
        settings.add(new Slider("Hotbar Slot", 0, 8, hotbarSlot, 1, v -> hotbarSlot = v).withDescription("Hotbar slot of the bundle (0 = first)."));
        settings.add(new Slider("Bundle Index", -1, 63, bundleIndex, 1, v -> bundleIndex = v).withDescription("Bundle item index (-1 = none selected)."));
        settings.add(new Slider("Max Cycles", 0, 500, maxCycles, 1, v -> maxCycles = v).withDescription("Stop after this many cycles (0 = until disabled)."));
    }

    public boolean isRunning() { return running; }

    private ItemStack stack(Minecraft mc) {
        return mc.player.getInventory().getItem(Math.max(0, Math.min(8, (int) hotbarSlot)));
    }

    private boolean ready(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || !(mc.player.containerMenu instanceof InventoryMenu)) return false;
        ItemStack s = stack(mc);
        if (s.isEmpty() || !s.is(Items.BUNDLE)) return false;
        BundleContents c = s.get(DataComponents.BUNDLE_CONTENTS);
        return c != null && c.size() == 1;
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) {
            Notify.bar("Need a bundle with exactly one stack in hotbar slot " + ((int) hotbarSlot + 1));
            enabled = false;
            return;
        }
        cycles = 0;
        running = true;
        worker = new Thread(() -> loop(mc), "newbridge-bundledupe");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void onDisable() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    private void loop(Minecraft mc) {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                if (mc.player == null || mc.getConnection() == null) break;
                int hs = 36 + Math.max(0, Math.min(8, (int) hotbarSlot));
                click(mc, hs, 1);
                int n = Math.max(1, Math.min(2000, (int) bundlePackets));
                for (int i = 0; i < n && running; i++) mc.getConnection().send(new ServerboundSelectBundleItemPacket(hs, (int) bundleIndex));
                if (!sleep((int) pickupDelayMs)) break;
                click(mc, hs, 0);
                if (!sleep((int) putBackDelayMs)) break;
                ItemStack first = stack(mc);
                if (!(first.isEmpty() || first.is(Items.BUNDLE))) {
                    click(mc, hs, 1);
                    if (!sleep((int) dropDelayMs)) break;
                    click(mc, -999, 0);
                    if (!sleep((int) dropDelayMs)) break;
                }
                cycles++;
                if (maxCycles > 0 && cycles >= maxCycles) break;
            }
        } finally {
            running = false;
            enabled = false;
        }
    }

    private void click(Minecraft mc, int slot, int button) {
        var menu = mc.player.containerMenu;
        if (menu == null) return;
        mc.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short) slot, (byte) button,
                ContainerInput.PICKUP, new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
    }

    private boolean sleep(int ms) {
        try {
            Thread.sleep(Math.max(0, ms));
            return running;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
