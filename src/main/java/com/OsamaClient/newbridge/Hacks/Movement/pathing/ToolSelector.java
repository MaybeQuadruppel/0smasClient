package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolSelector {

    private static final int MIN_DURABILITY = 5;

    private ToolSelector() {}

    /**
     * Bestes Werkzeug aus dem Hotbar für den gegebenen Block.
     *      * Gibt -1 zurück wenn gar kein Werkzeug verfügbar.
            *      * Fallback: nimmt irgendeinen nicht-leeren Slot wenn kein Tool passt.
     *      */
    public static int selectBestPickaxe(Minecraft mc, BlockState state) {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        float bestSpeed = -1f;
        int fallbackSlot = -1; // irgendein Item als letzter Ausweg

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            // Durability check
            if (stack.isDamageableItem()) {
                int remaining = stack.getMaxDamage() - stack.getDamageValue();
                if (remaining < MIN_DURABILITY) continue;
            }

            if (fallbackSlot == -1) fallbackSlot = slot;

            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }


        if (bestSlot == -1) return fallbackSlot;
        return bestSlot;
    }

    public static int selectBestTool(Minecraft mc, BlockState state) {
        return selectBestPickaxe(mc, state);
    }
}