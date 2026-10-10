package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared movement scheduler. A player can only execute one movement input at a
 * time, but multiple modules may keep independent navigation requests queued.
 */
public final class Navigator {

    public static final Navigator INSTANCE = new Navigator();

    public static final String OWNER_GOTO = "goto";
    public static final String OWNER_AUTO_MINER = "auto_miner";
    public static final String OWNER_BASE_FINDER = "base_finder";

    public static final int PRIORITY_BASE_FINDER = 10;
    public static final int PRIORITY_AUTO_MINER = 20;
    public static final int PRIORITY_GOTO = 30;

    private static final int ITERATIONS_PER_TICK = 2000;
    private static final int REPLAN_STEPS_REMAINING = 10;
    private static final int RETRY_COOLDOWN_TICKS = 10;
    private static final int MAX_EMPTY_RETRIES = 6;
    private static final int MAX_FAILURES = 8;

    private final MovementExecutor executor = new MovementExecutor();
    private final Map<String, NavigationJob> jobs = new LinkedHashMap<>();

    private NavigationJob activeJob;

    private Navigator() {}

    public void goTo(Minecraft mc, String owner, int priority, Goal goal) {
        goTo(mc, owner, priority, goal, null);
    }

    public void goTo(Minecraft mc, String owner, int priority, Goal goal, Consumer<Boolean> onComplete) {
        if (mc.player == null || mc.level == null || owner == null || owner.isEmpty() || goal == null) return;

        cancel(mc, owner, false);
        MoveBlacklist.purgeExpired();

        NavigationJob job = new NavigationJob(owner, priority, goal, onComplete);
        jobs.put(owner, job);
        selectJob(mc);
    }

    /** Compatibility entry point for callers outside the shared scheduler. */
    public void goTo(Minecraft mc, Goal goal) {
        goTo(mc, "legacy", 0, goal, null);
    }

    /** Compatibility entry point for callers outside the shared scheduler. */
    public void goTo(Minecraft mc, Goal goal, Consumer<Boolean> onComplete) {
        goTo(mc, "legacy", 0, goal, onComplete);
    }

    public void cancel(Minecraft mc, String owner) {
        cancel(mc, owner, true);
    }

    private void cancel(Minecraft mc, String owner, boolean notify) {
        NavigationJob job = jobs.remove(owner);
        if (job == null) return;

        if (job == activeJob) {
            executor.stop(mc);
            activeJob = null;
        }
        if (notify) complete(job, false);
    }

    /** Stops every queued request. Prefer {@link #cancel(Minecraft, String)} for modules. */
    public void stop(Minecraft mc) {
        for (NavigationJob job : jobs.values().toArray(NavigationJob[]::new)) {
            cancel(mc, job.owner, true);
        }
    }

    public boolean isNavigating(String owner) {
        return jobs.containsKey(owner);
    }

    public boolean isNavigating() {
        return !jobs.isEmpty();
    }

    public List<BlockPos> getRemainingPath(String owner) {
        NavigationJob job = jobs.get(owner);
        if (job == null) return List.of();
        if (job == activeJob) return executor.getRemainingWaypoints();
        return job.nextPath == null ? List.of() : List.copyOf(job.nextPath.waypoints);
    }

    /** Called once per client tick by the entry point, after modules submit work. */
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (activeJob != null) executor.stop(mc);
            activeJob = null;
            return;
        }

        selectJob(mc);
        if (activeJob == null) return;

        NavigationJob job = activeJob;
        BlockPos playerPos = mc.player.blockPosition();
        if (job.goal.isInGoal(playerPos.getX(), playerPos.getY(), playerPos.getZ())) {
            finishActive(mc, true);
            return;
        }

        if (job.retryCooldown > 0) job.retryCooldown--;

        if (job.incrementalFinder != null) {
            PathResult result = job.incrementalFinder.tick(ITERATIONS_PER_TICK);
            if (result != null) {
                job.incrementalFinder = null;
                onPathReady(mc, job, result);
                if (activeJob != job) return;
            }
        }

        if (executor.isActive()) {
            executor.onTick(mc);
            if (executor.consumeFailure()) {
                onExecutorFailure(mc, job);
                return;
            }

            int done = executor.getCompletedSteps();
            if (done != job.lastCompletedSteps) {
                job.lastCompletedSteps = done;
                job.failStreak = 0;
            }

            if (executor.isActive()) {
                if (job.incrementalFinder == null && job.nextPath == null && job.retryCooldown == 0
                        && executor.stepsRemaining() <= REPLAN_STEPS_REMAINING) {
                    BlockPos start = executor.getDestination();
                    startPathCalc(mc, job, start == null ? mc.player.blockPosition() : start);
                }
            } else {
                swapToNextSegment(mc, job);
            }
        } else if (job.incrementalFinder == null && job.retryCooldown == 0) {
            startPathCalc(mc, job, mc.player.blockPosition());
        }
    }

    private void selectJob(Minecraft mc) {
        NavigationJob best = jobs.values().stream()
                .max((left, right) -> Integer.compare(left.priority, right.priority))
                .orElse(null);

        if (best == activeJob) return;
        if (activeJob != null) pauseActive(mc);
        if (best == null) return;

        activeJob = best;
        best.nextPath = null;
        best.incrementalFinder = null;
        best.retryCooldown = 0;
        best.lastCompletedSteps = executor.getCompletedSteps();
        startPathCalc(mc, best, mc.player.blockPosition());
    }

    private void pauseActive(Minecraft mc) {
        executor.stop(mc);
        activeJob.nextPath = null;
        activeJob.incrementalFinder = null;
        activeJob = null;
    }

    private void startPathCalc(Minecraft mc, NavigationJob job, BlockPos start) {
        if (job.incrementalFinder != null || job.goal.isInGoal(start.getX(), start.getY(), start.getZ())) return;
        job.incrementalFinder = new IncrementalPathfinder(mc, start, job.goal);
    }

    private void onPathReady(Minecraft mc, NavigationJob job, PathResult result) {
        if (job != activeJob) return;

        if (result.isEmpty()) {
            BlockPos playerPos = mc.player.blockPosition();
            if (job.goal.isInGoal(playerPos.getX(), playerPos.getY(), playerPos.getZ())) {
                finishActive(mc, true);
                return;
            }
            if (executor.isActive()) {
                job.retryCooldown = 20;
                return;
            }
            if (++job.emptyStreak >= MAX_EMPTY_RETRIES) {
                chat(mc, "[Nav] No path found for " + job.owner + ".");
                finishActive(mc, false);
                return;
            }
            job.retryCooldown = RETRY_COOLDOWN_TICKS;
            return;
        }

        job.emptyStreak = 0;
        if (!executor.isActive()) {
            executor.setPath(result);
        } else if (job.nextPath == null) {
            job.nextPath = result;
        }
    }

    private void onExecutorFailure(Minecraft mc, NavigationJob job) {
        job.nextPath = null;
        job.incrementalFinder = null;
        job.retryCooldown = 3;
        if (++job.failStreak >= MAX_FAILURES) {
            chat(mc, "[Nav] Too many failed steps for " + job.owner + ".");
            finishActive(mc, false);
        }
    }

    private void swapToNextSegment(Minecraft mc, NavigationJob job) {
        if (job.nextPath == null) return;

        PathResult next = job.nextPath;
        job.nextPath = null;
        if (next.waypoints.isEmpty() || mc.player.blockPosition().distSqr(next.waypoints.get(0)) > 9) return;
        executor.setPath(next);
    }

    private void finishActive(Minecraft mc, boolean success) {
        NavigationJob job = activeJob;
        if (job == null) return;

        executor.stop(mc);
        activeJob = null;
        jobs.remove(job.owner);
        complete(job, success);
    }

    private void complete(NavigationJob job, boolean success) {
        if (job.onComplete != null) job.onComplete.accept(success);
    }

    private void chat(Minecraft mc, String message) {
        if (mc.gui != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }

    private static final class NavigationJob {
        final String owner;
        final int priority;
        final Goal goal;
        final Consumer<Boolean> onComplete;

        IncrementalPathfinder incrementalFinder;
        PathResult nextPath;
        int retryCooldown;
        int emptyStreak;
        int failStreak;
        int lastCompletedSteps;

        NavigationJob(String owner, int priority, Goal goal, Consumer<Boolean> onComplete) {
            this.owner = owner;
            this.priority = priority;
            this.goal = goal;
            this.onComplete = onComplete;
        }
    }
}
