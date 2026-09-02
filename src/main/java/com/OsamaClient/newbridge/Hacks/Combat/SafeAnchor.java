package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class SafeAnchor extends Module {

    private final RotationHelper rotationHelper = new RotationHelper();
    private static final float degPerTick = 180.0F;

    private float realYaw;
    private float realPitch;
    private float spoofedYaw;
    private float spoofedPitch;
    private boolean isSpoofing;

    public SafeAnchor() {
        super("SafeAnchor", "Safely shields, charges, and explodes Respawn Anchors.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        this.isSpoofing = false;
        this.rotationHelper.cancel();
    }

    @Override
    public void onDisable() {
        this.rotationHelper.cancel();
        this.isSpoofing = false;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.level == null || client.player == null || client.gameMode == null) {
            this.rotationHelper.cancel();
            this.isSpoofing = false;
            return;
        }

        try {
            // --- PRE-TICK SPOOF LOGIK ---
            if (this.rotationHelper.isActive()) {
                this.realYaw = client.player.getYRot();
                this.realPitch = client.player.getXRot();
                this.isSpoofing = true;

                client.player.setYRot(this.spoofedYaw);
                client.player.setXRot(this.spoofedPitch);

                if (this.rotationHelper.tick(client.player)) {
                    this.spoofedYaw = client.player.getYRot();
                    this.spoofedPitch = client.player.getXRot();
                    return;
                } else {
                    client.player.setYRot(this.realYaw);
                    client.player.setXRot(this.realPitch);
                    this.isSpoofing = false;
                }
            }

            // --- ANCHOR INTERACTION TRIGGER ---
            if (client.options.keyUse.isDown() && client.hitResult instanceof BlockHitResult anchorHit) {
                BlockPos anchorPos = anchorHit.getBlockPos();
                BlockState state = client.level.getBlockState(anchorPos);

                if (state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) >= 0) {
                    BlockPos playerPos = client.player.blockPosition();

                    if (canStartSequence(playerPos, anchorPos)) {
                        int anchorSlot = client.player.getInventory().getSelectedSlot();
                        int glowstoneSlot = findHotbarSlot(client, Items.GLOWSTONE);

                        if (glowstoneSlot != -1) {
                            client.player.getInventory().setSelectedSlot(glowstoneSlot);
                            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, anchorHit);

                            BlockPos calculatedPos = calculateBetweenPos(playerPos, anchorPos);
                            BlockPos supportPos = calculatedPos.below();

                            this.rotationHelper.startTopFace(supportPos, placementHit -> {
                                if (client.level.getBlockState(placementHit.getBlockPos()).isAir()) {
                                    this.rotationHelper.cancel();
                                    return;
                                }

                                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, placementHit);
                                client.player.getInventory().setSelectedSlot(anchorSlot);
                                this.rotationHelper.startTopFace(anchorPos,
                                        anchorTopHit -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, anchorTopHit));
                            });

                            this.spoofedYaw = client.player.getYRot();
                            this.spoofedPitch = client.player.getXRot();
                        }
                    }
                }
            }
        } finally {
            // Garantiert die Head/Rotation-Wiederherstellung bei jedem Tick
            restoreHeadAndRotations(client);
        }
    }

    private void restoreHeadAndRotations(Minecraft client) {
        if (this.isSpoofing && client.player != null) {
            client.player.setYHeadRot(this.spoofedYaw);
            client.player.yBodyRot = this.spoofedYaw;

            client.player.setYRot(this.realYaw);
            client.player.setXRot(this.realPitch);

            client.player.yRotO = this.realYaw;
            client.player.xRotO = this.realPitch;

            this.isSpoofing = false;
        }
    }

    private static int findHotbarSlot(Minecraft client, Item item) {
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean canStartSequence(BlockPos playerPos, BlockPos anchorPos) {
        int yDelta = playerPos.getY() - anchorPos.getY();
        int horizontalGap = Math.max(Math.abs(playerPos.getX() - anchorPos.getX()), Math.abs(playerPos.getZ() - anchorPos.getZ()));

        return (yDelta == 0 || yDelta == 1) && horizontalGap >= 2;
    }

    private static BlockPos calculateBetweenPos(BlockPos playerPos, BlockPos anchorPos) {
        int offsetX = Integer.signum(playerPos.getX() - anchorPos.getX());
        int offsetZ = Integer.signum(playerPos.getZ() - anchorPos.getZ());

        return anchorPos.offset(offsetX, 0, offsetZ);
    }

    private static Vec3 topCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
    }

    // --- ROTATION HELPER (MIT TRACKER ANBINDUNG) ---

    private static final class RotationHelper {
        private static final double FACE_EPSILON = 1.0E-4D;

        private boolean active;
        private BlockPos targetBlock;
        private Vec3 target;
        private float currentSpeed;
        private Consumer<BlockHitResult> onComplete;

        public boolean isActive() {
            return this.active;
        }

        public void startTopFace(BlockPos targetBlock, Consumer<BlockHitResult> onComplete) {
            this.targetBlock = targetBlock.immutable();
            this.target = topCenter(this.targetBlock);
            this.currentSpeed = 0.0F;
            this.onComplete = onComplete;
            this.active = true;
            Tracker.isAiming = true;
        }

        public boolean tick(LocalPlayer player) {
            if (!this.active) {
                return false;
            }

            BlockPos playerPos = player.blockPosition();
            int horizontalGap = Math.max(Math.abs(playerPos.getX() - this.targetBlock.getX()), Math.abs(playerPos.getZ() - this.targetBlock.getZ()));
            int yDelta = Math.abs(playerPos.getY() - this.targetBlock.getY());

            if (horizontalGap > 6 || yDelta >= 3) {
                this.cancel();
                return false;
            }

            BlockHitResult topFaceHit = getTopFaceHit(player, this.targetBlock);
            if (topFaceHit != null) {
                finish(topFaceHit);
                return true;
            }

            Rotation targetRotation = calculateRotation(player.getEyePosition(), this.target);
            float yawDelta = Mth.wrapDegrees(targetRotation.yaw - player.getYRot());
            float pitchDelta = Mth.clamp(targetRotation.pitch, -90.0F, 90.0F) - player.getXRot();
            float distance = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);

            float maxSpeed = Math.max(0.1F, degPerTick);
            float easingScale = Mth.clamp(distance / 20.0F, 0.4F, 1.0F);
            float targetSpeed = Math.max(1.0F, maxSpeed * easingScale);
            float acceleration = Math.max(2.0F, maxSpeed * 0.8F);

            if (this.currentSpeed < targetSpeed) {
                this.currentSpeed = Math.min(targetSpeed, this.currentSpeed + acceleration);
            } else {
                this.currentSpeed = Math.max(targetSpeed, this.currentSpeed - acceleration);
            }

            float step = Math.min(distance, this.currentSpeed);
            float scale = step / distance;
            float yaw = player.getYRot() + yawDelta * scale;
            float pitch = player.getXRot() + pitchDelta * scale;

            turnPlayer(player, yaw, pitch);
            return true;
        }

        public void cancel() {
            this.active = false;
            this.targetBlock = null;
            this.target = null;
            this.currentSpeed = 0.0F;
            this.onComplete = null;
            Tracker.isAiming = false;
        }

        private void finish(BlockHitResult hit) {
            Consumer<BlockHitResult> callback = this.onComplete;
            this.cancel();
            callback.accept(hit);
        }

        private static BlockHitResult getTopFaceHit(LocalPlayer player, BlockPos pos) {
            Vec3 eye = player.getEyePosition();
            Vec3 view = player.getViewVector(1.0F);
            double topY = pos.getY() + 1.0D;

            if (Math.abs(view.y) < 1.0E-6D) {
                return null;
            }

            double distance = (topY - eye.y) / view.y;
            if (distance <= 0.0D) {
                return null;
            }

            Vec3 hit = new Vec3(eye.x + view.x * distance, topY, eye.z + view.z * distance);
            if (hit.x < pos.getX() - FACE_EPSILON || hit.x > pos.getX() + 1.0D + FACE_EPSILON) {
                return null;
            }

            if (hit.z < pos.getZ() - FACE_EPSILON || hit.z > pos.getZ() + 1.0D + FACE_EPSILON) {
                return null;
            }

            return new BlockHitResult(hit, Direction.UP, pos, false);
        }

        private static Rotation calculateRotation(Vec3 from, Vec3 to) {
            double deltaX = to.x - from.x;
            double deltaY = to.y - from.y;
            double deltaZ = to.z - from.z;
            double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float yaw = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
            float pitch = (float) -(Mth.atan2(deltaY, horizontalDistance) * (180.0D / Math.PI));

            return new Rotation(yaw, pitch);
        }

        private static void turnPlayer(LocalPlayer player, float yaw, float pitch) {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private record Rotation(float yaw, float pitch) {
    }
}