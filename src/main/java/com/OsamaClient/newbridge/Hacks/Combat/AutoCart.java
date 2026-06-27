package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class AutoCart extends Module {
    public static AutoCart INSTANCE;
    public static BlockPos lastLanding = null;

    private int placementStep = 0;
    private int delayTicks = 0;

    public AutoCart() {
        super("InstaCart", "Automatically places a TNT- Cart if you shoot a Flame-Bow", Category.COMBAT);
        INSTANCE = this;
    }

    public static void setLanding(BlockPos pos) {
        lastLanding = pos;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!this.enabled || lastLanding == null) {
            placementStep = 0;
            return;
        }

        if (client.player == null || client.gameMode == null || client.level == null) return;

        double distSq = client.player.distanceToSqr(Vec3.atCenterOf(lastLanding));
        if (distSq > 20.0) return;

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        if (placementStep == 0) {
            int railSlot = findSlot(Items.RAIL, Items.POWERED_RAIL, Items.ACTIVATOR_RAIL);
            if (railSlot == -1) {
                lastLanding = null;
                return;
            }

            rotatePlayer(client, lastLanding);

            int oldSlot = client.player.getInventory().getSelectedSlot();
            client.player.getInventory().setSelectedSlot(railSlot);

            Vec3 hitVec = new Vec3(lastLanding.getX() + 0.5, lastLanding.getY() + 1.0, lastLanding.getZ() + 0.5);
            BlockHitResult railHit = new BlockHitResult(hitVec, Direction.UP, lastLanding, false);

            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, railHit);
            client.player.getInventory().setSelectedSlot(oldSlot);

            placementStep = 1;
            delayTicks = 2;
            return;
        }

        if (placementStep == 1) {
            int cartSlot = findSlot(Items.TNT_MINECART);
            if (cartSlot == -1) {
                lastLanding = null;
                placementStep = 0;
                return;
            }
            BlockPos railPos = lastLanding.above();

            rotatePlayer(client, railPos);

            int oldSlot = client.player.getInventory().getSelectedSlot();
            client.player.getInventory().setSelectedSlot(cartSlot);

            Vec3 hitVec = Vec3.atCenterOf(railPos);
            BlockHitResult cartHit = new BlockHitResult(hitVec, Direction.UP, railPos, false);

            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, cartHit);
            client.player.getInventory().setSelectedSlot(oldSlot);

            lastLanding = null;
            placementStep = 0;
        }
    }
    private void rotatePlayer(Minecraft client, BlockPos pos) {
        Vec3 targetVec = Vec3.atCenterOf(pos);

        double dx = targetVec.x - client.player.getX();
        double dy = targetVec.y - (client.player.getY() + client.player.getEyeHeight());
        double dz = targetVec.z - client.player.getZ();

        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90F);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));

        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
    }
    private int findSlot(net.minecraft.world.item.Item... items) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(i);
            for (net.minecraft.world.item.Item item : items) {
                if (stack.is(item)) return i;
            }
        }
        return -1;
    }
}