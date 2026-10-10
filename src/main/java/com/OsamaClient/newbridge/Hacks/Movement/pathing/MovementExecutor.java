package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import com.OsamaClient.newbridge.Hacks.Movement.pathing.BlockInteraction.AimTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class MovementExecutor {

    private static final int IDLE_LIMIT  = 60;
    private static final int STEP_LIMIT  = 900;
    private static final int MINE_LIMIT  = 500;
    private static final int AIM_LIMIT   = 50;
    private static final int MAX_JUMPS   = 6;

    private static final boolean SWING_ON_MINE = true;

    private enum Phase { PREP, RUN }
    private enum Kind  { TRAVERSE, ASCEND, DESCEND, DIG_DOWN, PILLAR }

    private PathResult path      = null;
    private int        stepIndex = 0;
    private int        mineIndex = 0;
    private Phase      phase     = Phase.PREP;

    private boolean failed         = false;
    private int     completedSteps = 0;

    private int  stepTicks = 0;
    private int  idleTicks = 0;
    private Vec3 lastPos   = null;

    private BlockPos  mineTarget  = null;
    private AimTarget aim         = null;
    private int       aimTicks    = 0;
    private int       mineTicks   = 0;
    private int       noFaceTicks = 0;
    private boolean   destroying  = false;

    private int       jumpAttempts     = 0;
    private int       jumpCooldown     = 0;
    private int       placeCooldown    = 0;
    private AimTarget placeAim         = null;
    private int       placeAimAge      = 0;
    private int       pillarNoAimTicks = 0;

    public boolean isActive() {
        return path != null && !path.isEmpty() && stepIndex < path.moves.size();
    }

    public int stepsRemaining() {
        if (path == null) return 0;
        return Math.max(0, path.moves.size() - stepIndex);
    }

    public int getCompletedSteps() {
        return completedSteps;
    }

    public boolean consumeFailure() {
        boolean f = failed;
        failed = false;
        return f;
    }

    public void setPath(PathResult path) {
        this.path   = path;
        this.stepIndex = 0;
        this.failed = false;
        resetStep();
    }

    public void stop(Minecraft mc) {
        path      = null;
        stepIndex = 0;
        resetStep();
        resetKeys(mc);
        if (mc.player != null && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    public BlockPos getDestination() {
        return (path != null && !path.isEmpty()) ? path.getDest() : null;
    }

    public List<BlockPos> getRemainingWaypoints() {
        if (path == null || path.isEmpty() || stepIndex >= path.waypoints.size()) return List.of();
        return List.copyOf(path.waypoints.subList(stepIndex, path.waypoints.size()));
    }

    private void resetStep() {
        mineIndex        = 0;
        phase            = Phase.PREP;
        stepTicks        = 0;
        idleTicks        = 0;
        lastPos          = null;
        jumpAttempts     = 0;
        jumpCooldown     = 0;
        placeCooldown    = 0;
        placeAim         = null;
        placeAimAge      = 0;
        pillarNoAimTicks = 0;
        resetMining();
    }

    private void resetMining() {
        mineTarget  = null;
        aim         = null;
        aimTicks    = 0;
        mineTicks   = 0;
        noFaceTicks = 0;
        destroying  = false;
    }

    private void fail(Minecraft mc, String reason) {
        if (path != null && stepIndex + 1 < path.waypoints.size()) {
            MoveBlacklist.add(path.waypoints.get(stepIndex), path.waypoints.get(stepIndex + 1));
        }
        chat(mc, "[Nav] Step failed: " + reason);
        stop(mc);
        failed = true;
    }

    public void onTick(Minecraft mc) {
        if (!isActive() || mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (mc.level.getFluidState(mc.player.blockPosition()).is(FluidTags.LAVA)) {
            fail(mc, "lava at feet");
            return;
        }

        stepTicks++;
        Vec3 pos = mc.player.position();
        if (lastPos == null || pos.distanceToSqr(lastPos) > 0.0009) {
            lastPos   = pos;
            idleTicks = 0;
        } else {
            idleTicks++;
        }
        if (idleTicks > IDLE_LIMIT) { fail(mc, "no progress"); return; }
        if (stepTicks > STEP_LIMIT) { fail(mc, "step timeout"); return; }

        MoveResult move = path.moves.get(stepIndex);
        BlockPos   from = path.waypoints.get(stepIndex);
        BlockPos   to   = path.waypoints.get(stepIndex + 1);
        Kind       kind = classify(from, to);

        // ── PREP: Blöcke abbauen ──────────────────────────────────────────────
        if (phase == Phase.PREP) {
            if (!mc.player.onGround() && !inFluid(mc)) {
                resetKeys(mc);
                return;
            }
            if (!prepare(mc, move, from)) return;
            phase = Phase.RUN;
        }

        // ── GHOST BLOCK CHECK IN RUN PHASE ─────────────────────────────────────
        // Falls der Server einen Block wiederhergestellt hat, gehen wir zurück in PREP
        for (BlockPos b : move.positionsToBreak) {
            BlockState bs = mc.level.getBlockState(b);
            if (!bs.isAir() && !bs.getCollisionShape(mc.level, b).isEmpty()) {
                phase = Phase.PREP;
                mineIndex = 0;
                resetMining();
                return;
            }
        }

        // ── RUN: Bewegung ─────────────────────────────────────────────────────
        switch (kind) {
            case PILLAR    -> runPillar(mc, from);
            case ASCEND    -> runAscend(mc, to);
            case TRAVERSE  -> walk(mc, to, true);
            case DESCEND, DIG_DOWN -> walk(mc, to, false);
        }

        if (isActive() && hasReachedWaypoint(mc, kind, to)) {
            completedSteps++;
            stepIndex++;
            resetStep();
            if (!isActive()) stop(mc);
        }
    }

    private Kind classify(BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        boolean horizontal = to.getX() != from.getX() || to.getZ() != from.getZ();
        if (dy > 0) return horizontal ? Kind.ASCEND  : Kind.PILLAR;
        if (dy < 0) return horizontal ? Kind.DESCEND : Kind.DIG_DOWN;
        return Kind.TRAVERSE;
    }

    private boolean prepare(Minecraft mc, MoveResult move, BlockPos from) {
        BlockPos[] breaks = move.positionsToBreak;

        while (mineIndex < breaks.length) {
            BlockPos t = breaks[mineIndex];
            BlockState bs = mc.level.getBlockState(t);
            if (!mc.level.isLoaded(t) || bs.isAir() || bs.getCollisionShape(mc.level, t).isEmpty()) {
                mineIndex++;
                resetMining();
                continue;
            }
            break;
        }

        if (mineIndex >= breaks.length) {
            resetMining();
            mc.gameMode.stopDestroyBlock();
            return true;
        }

        mineBlock(mc, breaks[mineIndex], from);
        return false;
    }

    private void mineBlock(Minecraft mc, BlockPos target, BlockPos from) {
        BlockState state = mc.level.getBlockState(target);

        if (BlockHelper.miningCost(mc, target) >= ActionCosts.COST_INF) {
            fail(mc, "cannot mine " + state.getBlock().getName().getString());
            return;
        }
        if (BlockHelper.touchesLava(mc, target)) {
            fail(mc, "lava next to " + target.toShortString());
            return;
        }

        int toolSlot = ToolSelector.selectBestPickaxe(mc, state);
        if (toolSlot == -1) {
            fail(mc, "no tool available");
            return;
        }
        mc.player.getInventory().setSelectedSlot(toolSlot);

        BlockHitResult ray = BlockInteraction.rayFromLook(mc);
        boolean looking = ray != null && ray.getBlockPos().equals(target);

        if (!looking) {
            if (aim == null || !target.equals(aim.block()) || aimTicks % 8 == 0) {
                aim = BlockInteraction.findMineFace(mc, target);
            }
            aimTicks++;

            if (aim == null) {
                noFaceTicks++;
                if (horizontalDist(mc, from) > 0.3 && noFaceTicks < 40) {
                    walk(mc, from, false);
                } else {
                    resetKeys(mc);
                    if (noFaceTicks > 15) fail(mc, "no visible face on " + target.toShortString());
                }
                return;
            }

            resetKeys(mc);
            BlockInteraction.smoothLookAt(mc, aim.point());
            if (aimTicks > AIM_LIMIT) fail(mc, "could not aim at " + target.toShortString());
            return;
        }

        aimTicks    = 0;
        noFaceTicks = 0;
        resetKeys(mc);
        if (aim != null && target.equals(aim.block())) {
            BlockInteraction.smoothLookAt(mc, aim.point());
        }

        Direction face = ray.getDirection();
        if (!target.equals(mineTarget)) {
            if (destroying) mc.gameMode.stopDestroyBlock();
            mc.gameMode.startDestroyBlock(target, face);
            mineTarget = target;
            destroying = true;
        } else {
            mc.gameMode.continueDestroyBlock(target, face);
        }
        if (SWING_ON_MINE) mc.player.swing(InteractionHand.MAIN_HAND);

        idleTicks = 0;
        if (++mineTicks > MINE_LIMIT) fail(mc, "mining takes too long");
    }

    private void walk(Minecraft mc, BlockPos wp, boolean allowSprint) {
        if (isLavaNow(mc, wp)) {
            resetKeys(mc);
            return;
        }

        Vec3 dest = new Vec3(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5);
        double dist = horizontalDist(mc, wp);

        mc.options.keyJump.setDown(inFluid(mc));

        if (dist < 0.12) {
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            return;
        }

        BlockInteraction.smoothLookAt(mc, new Vec3(dest.x, mc.player.getEyeY(), dest.z));
        float yawDiff = yawDiffTo(mc, dest);

        boolean facing = Math.abs(yawDiff) < 30f;
        mc.options.keyUp.setDown(facing);
        mc.options.keySprint.setDown(allowSprint && facing && Math.abs(yawDiff) < 12f && dist > 0.8);
    }

    private void runAscend(Minecraft mc, BlockPos to) {
        if (isLavaNow(mc, to)) {
            resetKeys(mc);
            return;
        }

        Vec3 dest = new Vec3(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        double dist = horizontalDist(mc, to);

        BlockInteraction.smoothLookAt(mc, new Vec3(dest.x, mc.player.getEyeY(), dest.z));
        float yawDiff = yawDiffTo(mc, dest);
        boolean facing = Math.abs(yawDiff) < 20f;

        boolean onGround = mc.player.onGround();
        if (jumpCooldown > 0) jumpCooldown--;

        mc.options.keyUp.setDown(facing || !onGround);
        mc.options.keySprint.setDown(false);

        boolean jump = false;
        if (onGround && facing && jumpCooldown == 0
                && (dist <= 1.35 || (mc.player.horizontalCollision && dist < 2.0))) {
            if (++jumpAttempts > MAX_JUMPS) {
                fail(mc, "cannot climb onto " + to.toShortString());
                return;
            }
            jump = true;
            jumpCooldown = 10;
        }
        mc.options.keyJump.setDown(jump || inFluid(mc));
    }

    private void runPillar(Minecraft mc, BlockPos from) {
        if (placeCooldown > 0) placeCooldown--;
        if (jumpCooldown  > 0) jumpCooldown--;

        boolean onGround = mc.player.onGround();
        double  dist     = horizontalDist(mc, from);

        if (onGround && dist > 0.2) {
            walk(mc, from, false);
            return;
        }
        resetKeys(mc);

        BlockState atPlace = mc.level.getBlockState(from);
        if (!atPlace.isAir() && !atPlace.getCollisionShape(mc.level, from).isEmpty()) return;

        int slot = BlockHelper.findPlaceableSlot(mc);
        if (slot < 0) {
            fail(mc, "no placeable blocks in hotbar");
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);

        if (placeAim == null || (placeAimAge++ % 4 == 0)) {
            placeAim = BlockInteraction.findPlaceFace(mc, from);
        }
        if (placeAim == null) {
            if (++pillarNoAimTicks > 20) fail(mc, "nothing to place against at " + from.toShortString());
            return;
        }
        pillarNoAimTicks = 0;

        BlockHitResult ray = BlockInteraction.rayFromLook(mc);
        boolean valid = ray != null
                && ray.getBlockPos().relative(ray.getDirection()).equals(from)
                && BlockInteraction.isSupport(mc, ray.getBlockPos());

        if (!valid) {
            BlockInteraction.smoothLookAt(mc, placeAim.point());
            return;
        }

        if (onGround) {
            if (jumpCooldown == 0) {
                if (++jumpAttempts > MAX_JUMPS) {
                    fail(mc, "pillar: jump/place failed too often");
                    return;
                }
                mc.options.keyJump.setDown(true);
                jumpCooldown = 8;
            }
            return;
        }

        if (placeCooldown == 0 && !mc.player.getBoundingBox().intersects(new AABB(from))) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, ray);
            mc.player.swing(InteractionHand.MAIN_HAND);
            placeCooldown = 4;
        }
    }

    private boolean hasReachedWaypoint(Minecraft mc, Kind kind, BlockPos wp) {
        BlockPos pb     = mc.player.blockPosition();
        double   distXZ = horizontalDist(mc, wp);
        boolean  inXZ   = pb.getX() == wp.getX() && pb.getZ() == wp.getZ();

        if (kind == Kind.TRAVERSE) {
            return pb.getY() == wp.getY() && (inXZ || distXZ < 0.3);
        }

        if (!mc.player.onGround()) return false;

        boolean levelOk = (kind == Kind.ASCEND || kind == Kind.PILLAR)
                ? pb.getY() >= wp.getY()
                : pb.getY() <= wp.getY();
        return levelOk && (inXZ || distXZ < 0.5);
    }

    private double horizontalDist(Minecraft mc, BlockPos wp) {
        double dx = mc.player.getX() - (wp.getX() + 0.5);
        double dz = mc.player.getZ() - (wp.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private float yawDiffTo(Minecraft mc, Vec3 dest) {
        double dx = dest.x - mc.player.getX();
        double dz = dest.z - mc.player.getZ();
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Mth.wrapDegrees(targetYaw - mc.player.getYRot());
    }

    private boolean inFluid(Minecraft mc) {
        return mc.player.isInWater() || mc.player.isInLava();
    }

    private boolean isLavaNow(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return false;
        return mc.level.getFluidState(pos).is(FluidTags.LAVA)
                || mc.level.getFluidState(pos.above()).is(FluidTags.LAVA);
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
        if (mc.gui != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
        }
    }
}
