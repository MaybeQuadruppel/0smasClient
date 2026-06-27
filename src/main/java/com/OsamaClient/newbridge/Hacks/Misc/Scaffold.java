package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.qdrppl.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Scaffold extends Module {

    public static Scaffold INSTANCE;

    public boolean sprint = true;
    public boolean tower = true;

    public Scaffold() {
        super("Scaffold", "Silent placement for 1.26+", Category.MISC);
        INSTANCE = this;
        this.settings.add(new ToggleButton("Allow Sprint", sprint, val -> sprint = val));
        this.settings.add(new ToggleButton("Tower", tower, val -> tower = val));
    }

    public void onUpdate(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;

        BlockPos pos = BlockPos.containing(client.player.getX(), client.player.getY() - 1.0, client.player.getZ());

        if (!client.level.getBlockState(pos).isAir()) return;

        BlockData data = getBlockData(pos, client);

        if (data != null) {

            if (tower && client.options.keyJump.isDown() && client.player.getDeltaMovement().x == 0 && client.player.getDeltaMovement().z == 0) {
                client.player.setDeltaMovement(client.player.getDeltaMovement().x, 0.42, client.player.getDeltaMovement().z);
            }

            float[] rotations = getRotations(data.pos, data.facing, client);

            float yaw = applyGCD(rotations[0], client.player.getYRot(), client);
            float pitch = applyGCD(rotations[1], client.player.getXRot(), client);

            client.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, client.player.onGround(), client.player.horizontalCollision));


            int slot = getBlockSlot(client);
            if (slot != -1) {
                int oldSlot = client.player.getInventory().getSelectedSlot();
                client.player.getInventory().setSelectedSlot(slot);

                Vec3 hitVec = Vec3.atCenterOf(data.pos).add(
                        data.facing.getStepX() * 0.5,
                        data.facing.getStepY() * 0.5,
                        data.facing.getStepZ() * 0.5
                );

                BlockHitResult hitResult = new BlockHitResult(hitVec, data.facing, data.pos, false);
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
                client.player.swing(InteractionHand.MAIN_HAND);

                client.getConnection().send(new ServerboundMovePlayerPacket.Rot(client.player.getYRot(), client.player.getXRot(), client.player.onGround(), client.player.horizontalCollision));
            }
        }
    }

    private float applyGCD(float target, float current, Minecraft client) {
        float f = client.options.sensitivity().get().floatValue() * 0.6F + 0.2F;
        float gcd = f * f * f * 1.2F;
        float diff = Mth.wrapDegrees(target - current);
        return current + (Math.round(diff / gcd) * gcd);
    }

    private int getBlockSlot(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            var stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private float[] getRotations(BlockPos pos, Direction facing, Minecraft client) {
        Vec3 hitVec = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5);
        double dx = hitVec.x - client.player.getX();
        double dy = hitVec.y - (client.player.getY() + client.player.getEyeHeight());
        double dz = hitVec.z - client.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{(float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0f, (float)-Math.toDegrees(Math.atan2(dy, dist))};
    }

    private BlockData getBlockData(BlockPos pos, Minecraft client) {
        for (Direction side : Direction.values()) {
            if (!client.level.getBlockState(pos.relative(side)).isAir()) return new BlockData(pos.relative(side), side.getOpposite());
        }
        return null;
    }

    private record BlockData(BlockPos pos, Direction facing) {}
}