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

public final class MovementExecutor {

    // ─── State ────────────────────────────────────────────────────────────────

    private PathResult path       = null;
    private int        stepIndex  = 0;
    private int        mineIndex  = 0;
    private StepState  stepState  = StepState.PREPPING;

    // ─── Mining state ─────────────────────────────────────────────────────────

    private BlockPos currentMineTarget = null;
    private boolean  miningStarted     = false;

    // ─── Anti-stuck ───────────────────────────────────────────────────────────

    private long lastProgressTime              = 0;
    private Vec3 lastCheckPos                  = null;
    private static final long STUCK_TIMEOUT_MS = 4000;

    // ─── Enums ────────────────────────────────────────────────────────────────

    private enum StepState { PREPPING, RUNNING }

    // ─── Public API ───────────────────────────────────────────────────────────

    public boolean isActive() {
        return path != null && stepIndex < path.moves.size() && !path.isEmpty();
    }

    public int stepsRemaining() {
        if (path == null) return 0;
        return Math.max(0, path.moves.size() - stepIndex);
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

        // Anti-stuck: reset timer on any XZ movement (Y changes during mining/falling)
        long now = System.currentTimeMillis();
        if (lastCheckPos == null ||
                Math.abs(playerPos.x - lastCheckPos.x) > 0.02 ||
                Math.abs(playerPos.z - lastCheckPos.z) > 0.02) {
            lastCheckPos     = playerPos;
            lastProgressTime = now;
        }
        // Also reset timer while actively mining
        if (stepState == StepState.PREPPING && miningStarted) {
            lastProgressTime = now;
        }
        if (now - lastProgressTime > STUCK_TIMEOUT_MS) {
            chat(mc, "[Nav] Stuck! Recalculating.");
            stop(mc);
            return;
        }

        MoveResult currentMove  = path.moves.get(stepIndex);
        BlockPos   nextWaypoint = path.waypoints.get(stepIndex + 1);

        switch (stepState) {

            case PREPPING:
                if (mineIndex < currentMove.positionsToBreak.length) {
                    BlockPos   target = currentMove.positionsToBreak[mineIndex];
                    BlockState state  = mc.level.getBlockState(target);

                    if (state.isAir() || BlockHelper.miningCost(mc, target) == 0) {
                        mineIndex++;
                        currentMineTarget = null;
                        miningStarted     = false;
                        break;
                    }

                    if (!BlockHelper.canReach(mc, target)) {
                        moveToward(mc, target, false);
                        return;
                    }

                    int toolSlot = ToolSelector.selectBestPickaxe(mc, state);
                    if (toolSlot == -1) {
                        chat(mc, "[Nav] No tool available!");
                        stop(mc);
                        return;
                    }
                    mc.player.getInventory().setSelectedSlot(toolSlot);

                    Direction face = getBestMinableFace(mc, target);
                    if (face == null) {
                        // All faces blocked — skip this block
                        mineIndex++;
                        currentMineTarget = null;
                        miningStarted     = false;
                        break;
                    }

                    Vec3 faceCenter = Vec3.atCenterOf(target).add(
                            face.getStepX() * 0.5,
                            face.getStepY() * 0.5,
                            face.getStepZ() * 0.5);
                    smoothLookAt(mc, faceCenter);

                    if (!isLookingAt(mc, target)) {
                        resetKeys(mc);
                        return;
                    }

                    resetKeys(mc);

                    if (!target.equals(currentMineTarget)) {
                        if (miningStarted && mc.gameMode != null) {
                            mc.gameMode.stopDestroyBlock();
                        }
                        if (mc.gameMode != null) mc.gameMode.startDestroyBlock(target, face);
                        currentMineTarget = target;
                        miningStarted     = true;
                    } else {
                        if (mc.gameMode != null) mc.gameMode.continueDestroyBlock(target, face);
                    }
                    return;

                } else {
                    currentMineTarget = null;
                    miningStarted     = false;
                    stepState         = StepState.RUNNING;
                    if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
                }
                // fall through

            case RUNNING:
                if (isPillarMove(currentMove)) {
                    executePillarUp(mc, nextWaypoint);
                } else {
                    moveToward(mc, nextWaypoint, true);
                }

                // ── Waypoint reached check ────────────────────────────────────
                if (hasReachedWaypoint(mc, playerPos, nextWaypoint)) {
                    stepIndex++;
                    mineIndex         = 0;
                    stepState         = StepState.PREPPING;
                    currentMineTarget = null;
                    miningStarted     = false;
                    if (!isActive()) stop(mc);
                }
                break;
        }
    }

    // ─── Waypoint reached ─────────────────────────────────────────────────────

    /**
     * Checks if the player has reached the next waypoint.
     *
     * Handles three cases:
     * 1. Normal horizontal movement  → within 0.8 blocks on same Y
     * 2. Going up (pillar/ascend)    → player Y matches waypoint Y
     * 3. Going down (drop/dig)       → player Y matches waypoint Y
     *    Special case: if waypoint is directly below and we just mined,
     *    the player needs to fall — check Y level rather than XZ distance.
     */
    private boolean hasReachedWaypoint(Minecraft mc, Vec3 playerPos, BlockPos wp) {
        BlockPos playerBlock = mc.player.blockPosition();
        int playerY = playerBlock.getY();
        int wpY     = wp.getY();

        double distXZ = Math.sqrt(
                Math.pow(playerPos.x - (wp.getX() + 0.5), 2) +
                        Math.pow(playerPos.z - (wp.getZ() + 0.5), 2));

        if (wpY == playerY) {
            boolean inBlock = (playerBlock.getX() == wp.getX() && playerBlock.getZ() == wp.getZ());
            return inBlock || distXZ < 0.3;
        } else if (wpY < playerY) {
            return playerY <= wpY && distXZ < 1.0;
        } else {
            // Going UP
            return playerY >= wpY && distXZ < 1.0;
        }
    }

    // ─── Face selection ───────────────────────────────────────────────────────

    /**
     * Returns the best face to mine — only considers faces where the
     * adjacent block is fully passable (air, water, etc.).
     * Among valid faces picks the one most directly facing the player's eyes.
     */
    private Direction getBestMinableFace(Minecraft mc, BlockPos target) {
        if (mc.player == null || mc.level == null) return Direction.UP;

        Vec3 eyes   = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);

        double ex = eyes.x - center.x;
        double ey = eyes.y - center.y;
        double ez = eyes.z - center.z;
        double len = Math.sqrt(ex*ex + ey*ey + ez*ez);
        if (len == 0) return Direction.UP;
        ex /= len; ey /= len; ez /= len;

        Direction bestFace = null;
        double    bestDot  = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos adjacent = target.relative(dir);
            if (!BlockHelper.fullyPassable(mc, adjacent)) continue;

            double dot = dir.getStepX() * ex
                    + dir.getStepY() * ey
                    + dir.getStepZ() * ez;

            if (dot > bestDot) {
                bestDot  = dot;
                bestFace = dir;
            }
        }

        // Fallback: if all faces blocked (e.g. surrounded), use closest geometric face
        if (bestFace == null) {
            double ax = Math.abs(ex), ay = Math.abs(ey), az = Math.abs(ez);
            if (ay >= ax && ay >= az) bestFace = ey > 0 ? Direction.UP   : Direction.DOWN;
            else if (ax >= az)        bestFace = ex > 0 ? Direction.EAST  : Direction.WEST;
            else                      bestFace = ez > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        return bestFace;
    }

    // ─── Raycast check ────────────────────────────────────────────────────────

    private boolean isLookingAt(Minecraft mc, BlockPos target) {
        if (mc.hitResult == null) return false;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return false;
        BlockHitResult bhr = (BlockHitResult) mc.hitResult;
        return bhr.getBlockPos().equals(target);
    }

    // ─── Smooth look ──────────────────────────────────────────────────────────

    private void smoothLookAt(Minecraft mc, Vec3 targetPos) {
        if (mc.player == null) return;

        Vec3   eyes = mc.player.getEyePosition();
        double dx   = targetPos.x - eyes.x;
        double dy   = targetPos.y - eyes.y;
        double dz   = targetPos.z - eyes.z;
        double dxz  = Math.sqrt(dx * dx + dz * dz);

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
        if (path == null || stepIndex >= path.waypoints.size() - 1) return false;
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
            BlockPos   head1      = mc.player.blockPosition().above();
            BlockState head1State = mc.level.getBlockState(head1);
            if (!head1State.isAir()) {
                Direction face = getBestMinableFace(mc, head1);
                if (face != null) smoothLookAt(mc, Vec3.atCenterOf(head1).add(
                        face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5));
                return;
            }

            BlockPos   head2      = mc.player.blockPosition().above(2);
            BlockState head2State = mc.level.getBlockState(head2);
            if (!head2State.isAir() && BlockHelper.miningCost(mc, head2) < ActionCosts.COST_INF) {
                Direction face = getBestMinableFace(mc, head2);
                if (face != null) smoothLookAt(mc, Vec3.atCenterOf(head2).add(
                        face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5));
                return;
            }

            smoothLookAt(mc, Vec3.atCenterOf(currentBlock.above(2)));
            mc.player.jumpFromGround();

        } else {
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
            } else {
                moveToward(mc, dest, true);
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
    public BlockPos getDestination() {
        return (path != null && !path.isEmpty()) ? path.getDest() : null;
    }
}