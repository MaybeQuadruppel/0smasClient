package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Executes a PathResult tick by tick.
 *
 * State machine per waypoint step:
 *   PREPPING  → mine all positionsToBreak for this move (in order)
 *   RUNNING   → move toward the next waypoint
 *   DONE      → advance to next step
 */
public final class MovementExecutor {

    // ─── State ────────────────────────────────────────────────────────────────

    private PathResult path        = null;
    private int        stepIndex   = 0;
    private int        mineIndex   = 0;
    private StepState  stepState   = StepState.PREPPING;

    // ─── Mining state ─────────────────────────────────────────────────────────

    private BlockPos currentMineTarget = null;
    private boolean  miningStarted     = false;

    // ─── Anti-stuck ───────────────────────────────────────────────────────────

    private long lastProgressTime              = 0;
    private Vec3 lastCheckPos                  = null;
    private static final long STUCK_TIMEOUT_MS = 3000;

    // ─── Enums ────────────────────────────────────────────────────────────────

    private enum StepState { PREPPING, RUNNING }

    // ─── Public API ───────────────────────────────────────────────────────────

    public boolean isActive() {
        return path != null && stepIndex < path.moves.size() && !path.isEmpty();
    }

    public void setPath(PathResult path) {
        this.path              = path;
        this.stepIndex         = 0;
        this.mineIndex         = 0;
        this.stepState         = StepState.PREPPING;
        this.currentMineTarget = null;
        this.miningStarted     = false;
        this.lastProgressTime  = System.currentTimeMillis();
        this.lastCheckPos      = null;
    }

    public void stop(Minecraft mc) {
        path              = null;
        stepIndex         = 0;
        mineIndex         = 0;
        stepState         = StepState.PREPPING;
        currentMineTarget = null;
        miningStarted     = false;
        resetKeys(mc);
        if (mc.player != null && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    // ─── Main tick ────────────────────────────────────────────────────────────
    public void onTick(Minecraft mc) {
        if (!isActive() || mc.player == null || mc.level == null) return;

        Vec3 playerPos = mc.player.position();


        long now = System.currentTimeMillis();

        if (currentMineTarget != null) {
            lastProgressTime = now;
            lastCheckPos = playerPos;
        } else {
            if (lastCheckPos == null || playerPos.distanceToSqr(lastCheckPos) > 0.05) {
                lastCheckPos = playerPos;
                lastProgressTime = now;
            }
        }

        if (now - lastProgressTime > STUCK_TIMEOUT_MS) {
            chat(mc, "§c[Nav] Stuck! Stopping.");
            stop(mc);
            return;
        }

        MoveResult currentMove = path.moves.get(stepIndex);
        BlockPos nextWaypoint = path.waypoints.get(stepIndex + 1);

        switch (stepState) {
            case PREPPING:
                while (mineIndex < currentMove.positionsToBreak.length) {
                    BlockPos target = currentMove.positionsToBreak[mineIndex];
                    BlockState state = mc.level.getBlockState(target);

                    // 1. Wenn Block weg -> weiter zum nächsten
                    if (state.isAir() || BlockHelper.miningCost(mc, target) == 0) {
                        mineIndex++;
                        currentMineTarget = null;
                        continue;
                    }

                    Direction face = getClosestVisibleFace(mc, target);
                    Vec3 faceCenter = Vec3.atCenterOf(target).add(
                            face.getStepX() * 0.5,
                            face.getStepY() * 0.5,
                            face.getStepZ() * 0.5
                    );

                    smoothLookAt(mc, faceCenter);

                    // 5. Wenn zu weit weg -> näher ranlaufen
                    if (!BlockHelper.canReach(mc, target)) {
                        moveToward(mc, target, false);
                        return;
                    }

                    if (isLookingAt(mc, target)) {
                        if (!target.equals(currentMineTarget)) {
                            int toolSlot = ToolSelector.selectBestPickaxe(mc, state);

                            if (toolSlot != -1) {
                                mc.player.getInventory().setSelectedSlot(toolSlot);
                            }
                            if (currentMineTarget != null) {
                                mc.gameMode.stopDestroyBlock();
                            }
                            mc.gameMode.startDestroyBlock(target, face);
                            currentMineTarget = target;
                        } else {
                            mc.gameMode.continueDestroyBlock(target, face);
                        }
                    } else {
                        resetKeys(mc);
                    }
                    return;
                }
                stepState = StepState.RUNNING;
                currentMineTarget = null;
                break;

            case RUNNING:
                if (isPillarMove(currentMove)) {
                    executePillarUp(mc, nextWaypoint);
                } else {
                    moveToward(mc, nextWaypoint, true);
                }

                double dist = playerPos.distanceToSqr(nextWaypoint.getX() + 0.5, nextWaypoint.getY(), nextWaypoint.getZ() + 0.5);
                boolean waypointReached = (nextWaypoint.getY() != mc.player.blockPosition().getY())
                        ? (mc.player.blockPosition().getY() == nextWaypoint.getY() && dist < 1.5)
                        : (dist < 0.8);

                if (waypointReached) {
                    stepIndex++;
                    mineIndex = 0;
                    stepState = StepState.PREPPING;
                    currentMineTarget = null;
                    if (!isActive()) stop(mc);
                }
                break;
        }
    }

    // ─── FIX #1: Raycast check ────────────────────────────────────────────────

    /**
     * Returns true if the player's current crosshair raycast hits the given block.
     * This ensures we never call startDestroyBlock on a block we aren't looking at,
     * which would cause the client to mine through walls.
     */
    private boolean isLookingAt(Minecraft mc, BlockPos target) {
        if (mc.hitResult == null) return false;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return false;
        BlockHitResult bhr = (BlockHitResult) mc.hitResult;
        return bhr.getBlockPos().equals(target);
    }

    // ─── FIX #3: Descend head-block ───────────────────────────────────────────

    /**
     * Returns the face of the target block that is most directly visible from
     * the player's eyes — i.e. the face the player would naturally click on.
     *
     * For a block directly below the player's feet we return DOWN (look down).
     * For a block at head height we return the horizontal face toward the player.
     * This fixes descend: the block at y+1 (head height) gets face NORTH/SOUTH/
     * EAST/WEST so the bot looks sideways at it, not upward through the floor.
     */
    private Direction getClosestVisibleFace(Minecraft mc, BlockPos target) {
        if (mc.player == null || mc.level == null) return Direction.UP;

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 blockCenter = Vec3.atCenterOf(target);

        Direction bestFace = Direction.UP;
        double minDistance = Double.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = target.relative(dir);
            BlockState neighborState = mc.level.getBlockState(neighborPos);
            if (neighborState.isCollisionShapeFullBlock(mc.level, neighborPos)) {
                continue;
            }

            Vec3 faceCenter = blockCenter.add(
                    dir.getStepX() * 0.5,
                    dir.getStepY() * 0.5,
                    dir.getStepZ() * 0.5
            );

            double dist = eyes.distanceToSqr(faceCenter);

            if (dist < minDistance) {
                minDistance = dist;
                bestFace = dir;
            }
        }
        return minDistance == Double.MAX_VALUE ? Direction.UP : bestFace;
    }

    // ─── Smooth look ──────────────────────────────────────────────────────────

    /**
     * GCD-snapped humanized smooth look toward a world position.
     * Uses speed-factor + jitter for human-like rotation.
     */
    private void smoothLookAt(Minecraft mc, Vec3 targetPos) {
        if (mc.player == null) return;

        Vec3   eyes  = mc.player.getEyePosition();
        double dx    = targetPos.x - eyes.x;
        double dy    = targetPos.y - eyes.y;
        double dz    = targetPos.z - eyes.z;
        double dxz   = Math.sqrt(dx * dx + dz * dz);

        float targetYaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dxz)));

        float yawDiff   = Mth.wrapDegrees(targetYaw   - mc.player.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - mc.player.getXRot());

        double sens = mc.options.sensitivity().get();
        float  f    = (float)(sens * 0.6F + 0.2F);
        float  gcd  = f * f * f * 1.2F;

        float speedFactor = Math.max(0.15f, Math.min(0.6f, Math.abs(yawDiff) / 30f));
        float jitter      = (float)(Math.random() - 0.5f) * 0.10f;
        float pct         = speedFactor + jitter;

        float roundedYaw   = Math.round((yawDiff   * pct) / gcd) * gcd;
        float roundedPitch = Math.round((pitchDiff * pct) / gcd) * gcd;

        mc.player.setYRot(mc.player.getYRot() + roundedYaw);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + roundedPitch, -90, 90));
    }

    // ─── Movement ─────────────────────────────────────────────────────────────

    private void moveToward(Minecraft mc, BlockPos target, boolean allowJump) {
        Vec3 dest = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        smoothLookAt(mc, dest);

        double dx  = dest.x - mc.player.getX();
        double dz  = dest.z - mc.player.getZ();

        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float yawDiff   = Mth.wrapDegrees(targetYaw - mc.player.getYRot());

        boolean facing = Math.abs(yawDiff) < 30f;
        mc.options.keyUp.setDown(facing);
        mc.options.keySprint.setDown(facing && Math.abs(yawDiff) < 15f);

        if (mc.player.isInWater() || mc.player.isInLava()) {
            mc.options.keyJump.setDown(true);
            return;
        }
        mc.options.keyJump.setDown(false);

        if (allowJump && mc.player.onGround()) {
            boolean blocked = mc.player.horizontalCollision;
            boolean goingUp = target.getY() > mc.player.blockPosition().getY();
            if (blocked || goingUp) mc.player.jumpFromGround();
        }
    }

    // ─── Pillar Up ────────────────────────────────────────────────────────────

    private boolean isPillarMove(MoveResult move) {
        if (stepIndex == 0) return false;
        BlockPos src  = path.waypoints.get(stepIndex);
        BlockPos dest = path.waypoints.get(stepIndex + 1);
        return dest.getX() == src.getX()
                && dest.getZ() == src.getZ()
                && dest.getY() == src.getY() + 1;
    }

    private void executePillarUp(Minecraft mc, BlockPos dest) {
        if (mc.player == null) return;

        BlockPos currentBlock = path.waypoints.get(stepIndex);
        double targetX = currentBlock.getX() + 0.5;
        double targetZ = currentBlock.getZ() + 0.5;
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        double distToCenter = Math.sqrt(
                (px - targetX) * (px - targetX) + (pz - targetZ) * (pz - targetZ));

        if (distToCenter > 0.15) {
            moveToward(mc, currentBlock, false);
            return;
        }

        resetKeys(mc);

        if (mc.player.onGround()) {
            // Wait for PREPPING to clear head block
            BlockPos   headBlock  = mc.player.blockPosition().above();
            BlockState headState  = mc.level.getBlockState(headBlock);
            if (!headState.isAir()) {
                smoothLookAt(mc, Vec3.atCenterOf(headBlock));
                return;
            }
            smoothLookAt(mc, Vec3.atCenterOf(currentBlock.above(2)));
            mc.player.jumpFromGround();
        } else {
            // In air: look down and place block below
            smoothLookAt(mc, Vec3.atCenterOf(mc.player.blockPosition().below()));

            int placeSlot = findPlaceableBlockSlot(mc);
            if (placeSlot != -1) {
                mc.player.getInventory().setSelectedSlot(placeSlot);
                mc.options.keyAttack.setDown(false);

                BlockPos   below      = mc.player.blockPosition().below();
                BlockState belowState = mc.level.getBlockState(below);
                if (belowState.isAir()) {
                    mc.gameMode.useItemOn(
                            mc.player,
                            net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(
                                    Vec3.atCenterOf(below),
                                    Direction.UP,
                                    below,
                                    false
                            )
                    );
                }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int findPlaceableBlockSlot(Minecraft mc) {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem)
                return slot;
        }
        return -1;
    }

    public void resetKeys(Minecraft mc) {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
        if (mc.player != null) mc.player.setSprinting(false);
    }

    private void chat(Minecraft mc, String msg) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }
}