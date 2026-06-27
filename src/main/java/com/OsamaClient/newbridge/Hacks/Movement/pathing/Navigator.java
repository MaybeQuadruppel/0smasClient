package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Top-level navigation controller.
 * Path calculation runs on the MAIN THREAD in a time-sliced manner
 * to avoid thread-safety issues with mc.level block lookups.
 */
public final class Navigator {

    public static final Navigator INSTANCE = new Navigator();

    // ─── Config ───────────────────────────────────────────────────────────────

    /** Max A* iterations per tick (spread across multiple ticks to avoid lag) */
    private static final int ITERATIONS_PER_TICK = 2000;

    /** Start pre-calculating next segment when this many steps remain */
    private static final int REPLAN_STEPS_REMAINING = 10;

    // ─── State ────────────────────────────────────────────────────────────────

    private final MovementExecutor executor = new MovementExecutor();

    private Goal    currentGoal = null;
    private boolean active      = false;
    private int     generationId = 0;

    /** In-progress incremental pathfinder (runs a slice per tick on main thread) */
    private IncrementalPathfinder incrementalFinder = null;

    /** Pre-calculated next segment ready to swap in */
    private PathResult nextPath = null;

    private Consumer<Boolean> onComplete = null;

    private Navigator() {}

    // ─── Public API ───────────────────────────────────────────────────────────

    public void goTo(Minecraft mc, Goal goal) {
        goTo(mc, goal, null);
    }

    public void goTo(Minecraft mc, Goal goal, Consumer<Boolean> onComplete) {
        stop(mc);
        this.currentGoal  = goal;
        this.onComplete   = onComplete;
        this.active       = true;
        this.nextPath     = null;
        this.generationId++;
        startPathCalc(mc, mc.player.blockPosition(), goal);
    }

    public void stop(Minecraft mc) {
        generationId++;
        active             = false;
        currentGoal        = null;
        nextPath           = null;
        incrementalFinder  = null;
        executor.stop(mc);
    }

    public boolean isNavigating() {
        return active;
    }

    // ─── Main tick ────────────────────────────────────────────────────────────

    public void onTick(Minecraft mc) {
        if (!active || mc.player == null) return;

        // Goal reached?
        if (currentGoal != null) {
            BlockPos pp = mc.player.blockPosition();
            if (currentGoal.isInGoal(pp.getX(), pp.getY(), pp.getZ())) {
                chat(mc, "[Nav] Destination reached!");
                finishNavigation(mc, true);
                return;
            }
        }

        // ── Tick the incremental pathfinder (runs on main thread, safe) ───────
        if (incrementalFinder != null) {
            PathResult result = incrementalFinder.tick(ITERATIONS_PER_TICK);
            if (result != null) {
                // Pathfinder finished this tick
                incrementalFinder = null;
                onPathReady(mc, result);
            }
            // else: still running, continue next tick
        }

        // ── Tick the movement executor ─────────────────────────────────────────
        if (executor.isActive()) {
            executor.onTick(mc);

            int stepsLeft = executor.stepsRemaining();
            if (incrementalFinder == null && nextPath == null
                    && stepsLeft <= REPLAN_STEPS_REMAINING) {

                BlockPos startPos = executor.getDestination();
                if (startPos == null) startPos = mc.player.blockPosition();

                startPathCalc(mc, startPos, currentGoal);
            }

            // Current segment finished
            if (!executor.isActive()) {
                swapToNextSegment(mc);
            }

        } else if (incrementalFinder == null) {
            // Nothing running, nothing calculating — recalculate
            if (active && currentGoal != null) {
                startPathCalc(mc, mc.player.blockPosition(), currentGoal);
            }
        }
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private void startPathCalc(Minecraft mc, BlockPos start, Goal goal) {
        if (goal == null || mc.level == null) return;
        if (incrementalFinder != null) return; // already calculating
        if (goal.isInGoal(start.getX(), start.getY(), start.getZ())) {
            return;
        }

        incrementalFinder = new IncrementalPathfinder(mc, start, goal);
    }

    private void onPathReady(Minecraft mc, PathResult result) {
        if (!active) return;

        if (result.isEmpty()) {
            // Bad result — check if we're basically at the goal already
            if (currentGoal != null) {
                BlockPos pp = mc.player.blockPosition();
                if (currentGoal.isInGoal(pp.getX(), pp.getY(), pp.getZ())) {
                    chat(mc, "[Nav] Destination reached!");
                    finishNavigation(mc, true);
                    return;
                }
            }
            // Retry after a short delay (2 ticks) by leaving incrementalFinder null
            // onTick will restart it next time executor is idle
            if (!executor.isActive()) {
                chat(mc, "[Nav] No path found, retrying...");
            }
            return;
        }

        chat(mc, "[Nav] Path: " + result.moves.size() + " steps");

        if (!executor.isActive()) {
            executor.setPath(result);
            // Immediately start pre-calculating next segment
            startPathCalc(mc, mc.player.blockPosition(), currentGoal);
        } else {
            if (nextPath == null) {
                nextPath = result;
            }
        }
    }

    private void swapToNextSegment(Minecraft mc) {
        if (nextPath != null) {
            executor.setPath(nextPath);
            nextPath = null;
            // Pre-calc next segment
            if (incrementalFinder == null) {
                startPathCalc(mc, mc.player.blockPosition(), currentGoal);
            }
        }
        // else: wait for incrementalFinder to finish
    }

    private void finishNavigation(Minecraft mc, boolean success) {
        generationId++;
        active            = false;
        currentGoal       = null;
        nextPath          = null;
        incrementalFinder = null;
        executor.stop(mc);
        if (onComplete != null) {
            onComplete.accept(success);
            onComplete = null;
        }
    }

    private void chat(Minecraft mc, String msg) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }
}