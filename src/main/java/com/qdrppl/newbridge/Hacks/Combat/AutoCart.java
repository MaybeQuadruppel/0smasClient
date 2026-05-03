package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

public class AutoCart extends Module {
    public static AutoCart INSTANCE;
    public static BlockPos lastLanding = null;

    public AutoCart() {
        super("InstaCart", "Platziert Schiene + TNT-Minecart an Pfeil-Landestelle", Category.COMBAT);
        INSTANCE = this;
    }

    public static void setLanding(BlockPos pos) {
        lastLanding = pos;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        lastLanding = null;
    }

    public void performActions(BlockPos landing) {
        if (landing == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;

        int railSlot = findHotbarSlot(client.player.getInventory(), stack ->
                stack.is(Items.RAIL) || stack.is(Items.POWERED_RAIL) || stack.is(Items.ACTIVATOR_RAIL)
        );

        int cartSlot = findHotbarSlot(client.player.getInventory(), stack -> stack.is(Items.TNT_MINECART));

        if (railSlot == -1 || cartSlot == -1) return;

        client.player.getInventory().setSelectedSlot(railSlot);
        BlockHitResult railHit = new BlockHitResult(new Vec3(landing.getX() + 0.5, landing.getY() + 1.0, landing.getZ() + 0.5), Direction.UP, landing, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, railHit);


        client.player.getInventory().setSelectedSlot(cartSlot);
        BlockPos railPos = landing.above();
        BlockHitResult cartHit = new BlockHitResult(new Vec3(railPos.getX() + 0.5, railPos.getY() + 0.5, railPos.getZ() + 0.5), Direction.UP, railPos, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, cartHit);
    }

    private int findHotbarSlot(Inventory inv, Predicate<ItemStack> predicate) {
        for (int i = 0; i < 9; i++) {
            if (predicate.test(inv.getItem(i))) return i;
        }
        return -1;
    }
}