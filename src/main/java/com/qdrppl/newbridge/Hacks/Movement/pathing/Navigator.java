package com.qdrppl.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Top-level navigation controller.
 *
 * Usage:
 *   Navigator.INSTANCE.goTo(mc, new GoalBlock(targetPos));
 *   Navigator.INSTANCE.stop(mc);
 *
 * Call Navigator.INSTANCE.onTick(mc) every game tick.
 *
 * Async path calculation (like Baritone) so the game doesn't freeze.
 */
public final class Navigator {

    public static final Navigator INSTANCE = new Navigator();

    // ─── State ────────────────────────────────────────────────────────────────

    private final MovementExecutor executor = new MovementExecutor();

    private Goal currentGoal = null;
    private boolean calculating = false;
    private final AtomicBoolean cancelCalc = new AtomicBoolean(false);

    /** Called when navigation finishes (success or failure) */
    private Consumer<Boolean> onComplete = null;

    private Navigator() {}

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Start navigating to a goal.
     * Path calculation happens on a background thread.
     */
    public void goTo(Minecraft mc, Goal goal) {
        goTo(mc, goal, null);
    }

    public void goTo(Minecraft mc, Goal goal, Consumer<Boolean> onComplete) {
        stop(mc);
        this.currentGoal = goal;
        this.onComplete  = onComplete;
        calculateAsync(mc, goal);
    }

    /**
     * Stop all navigation immediately.
     */
    public void stop(Minecraft mc) {
        cancelCalc.set(true);
        calculating = false;
        currentGoal = null;
        executor.stop(mc);
    }

    /**
     * Is the bot currently navigating?
     */
    public boolean isNavigating() {
        return executor.isActive() || calculating;
    }

    /**
     * Must be called every game tick.
     */
    public void onTick(Minecraft mc) {
        if (executor.isActive()) {
            executor.onTick(mc);

            // Check if we finished
            if (!executor.isActive()) {
                chat(mc, "§a[Nav] Destination reached");
                if (onComplete != null) onComplete.accept(true);
                onComplete = null;
                currentGoal = null;
            }
        }
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private void calculateAsync(Minecraft mc, Goal goal) {
        if (mc.player == null) return;
        calculating = true;
        cancelCalc.set(false);

        BlockPos start = mc.player.blockPosition();

        chat(mc, "§7[Nav] Calculating path...");

        CompletableFuture.supplyAsync(() -> {
            // This runs on a background thread
            // Note: reading world state from another thread is technically unsafe,
            // but Minecraft itself does this in many places and it's stable enough
            // for pathfinding (worst case: a block check is stale and we recalc)
            if (cancelCalc.get()) return PathResult.EMPTY;
            return Pathfinder.calculatePath(mc, start, goal);
        }).thenAcceptAsync(result -> {
            // Back on the main thread via scheduleTask
            calculating = false;
            if (cancelCalc.get()) return;

            if (result.isEmpty()) {
                chat(mc, "§c[Nav] No path found!");
                if (onComplete != null) onComplete.accept(false);
                onComplete = null;
                currentGoal = null;
                return;
            }

            chat(mc, "§7[Nav] Pfad found (" + result.moves.size() + " steps)");
            executor.setPath(result);

        }, task -> mc.submit(task)); // execute on main thread
    }

    private void chat(Minecraft mc, String msg) {
        if (mc.gui != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }
}
