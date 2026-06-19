package com.qdrppl.newbridge.Hacks.Movement;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import com.qdrppl.newbridge.UI.components.Slider;
import com.qdrppl.newbridge.Utils.RenderBlock;
import com.qdrppl.newbridge.Utils.Renderpath;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class GoTo extends Module {

    public static boolean isActive = false;
    private static BlockPos targetPos = null;

    // ── Settings Fields ──────────────────────────────────────────────────────
    private boolean renderPathSetting = true;
    private float stopDistance = 2.0f;

    // ── Path state & Render Storage ──────────────────────────────────────────
    /** Smoothed waypoints (after string-pulling) — far fewer nodes than raw A*. */
    private List<BlockPos> path = new ArrayList<>();
    private List<BlockPos> rawPathForRender = new ArrayList<>();
    private int  pathIndex   = 0;
    private long lastRecalc  = 0;
    private static final long RECALC_INTERVAL_MS = 1000;
    private static final int  MAX_LOOKAHEAD_NODES = 6;

    // ── Stuck detection ──────────────────────────────────────────────────────
    private Vec3 lastCheckPos = null;
    private long lastProgressTime = 0;
    private static final long STUCK_TIMEOUT_MS = 1500;
    private static final double STUCK_MOVE_THRESHOLD_SQ = 0.04; // ~0.2 blocks

    public GoTo() {
        super("GoTo", "Automatically navigates to coordinates", Category.MISC);

        this.settings.add(new ToggleButton("Render Path", renderPathSetting, val -> renderPathSetting = val));
        this.settings.add(new Slider("Stop Distance", 1.0, 10.0, (double) stopDistance, val -> stopDistance = val.floatValue()));

        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            if (!enabled || !isActive || !renderPathSetting || path.isEmpty()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            // Build future path starting exactly at player position
            List<BlockPos> futurePath = new ArrayList<>();
            futurePath.add(mc.player.blockPosition());
            for (int i = pathIndex; i < path.size(); i++) {
                if (i < path.size()) {
                    futurePath.add(path.get(i));
                }
            }

            RenderBlock.begin();
            Renderpath.renderLineStrip(context, futurePath, 0.35f, 1.0f, 0.65f, 0.0f, 0.6f);
            RenderBlock.draw(mc);
        });
    }

    public static void setTarget(int x, int y, int z, boolean useY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int targetY = y;
        if (!useY) {
            int highestY = mc.level.getMaxY();
            BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(x, highestY, z);
            while (highestY > mc.level.getMinY()) {
                BlockState state = mc.level.getBlockState(checkPos);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    targetY = highestY + 1;
                    break;
                }
                highestY--;
                checkPos.setY(highestY);
            }
        }

        targetPos = new BlockPos(x, targetY, z);
        isActive  = true;
    }

    public static void stopNavigation() {
        targetPos = null;
        isActive  = false;
        resetKeys(Minecraft.getInstance());
    }

    @Override
    public void onDisable() {
        stopNavigation();
        path.clear();
        rawPathForRender.clear();
        pathIndex = 0;
        super.onDisable();
    }

    // ── Main tick ────────────────────────────────────────────────────────────
    @Override
    public void onTick(Minecraft client) {
        if (!enabled || !isActive || targetPos == null || client.player == null || client.level == null) {
            return;
        }

        Vec3 playerPos = client.player.position();

        // ── 1. MARATHON-FEATURE: Dynamisches Zwischenziel ───────────────────
        // Wir navigieren immer zu einem Punkt, der maximal 30 Blöcke entfernt ist,
        // um den A* Algorithmus nicht zu überlasten (Performance & Reichweite).
        BlockPos currentTarget = targetPos;
        double distToFinalSq = playerPos.distanceToSqr(Vec3.atCenterOf(targetPos));

        if (distToFinalSq > 900) { // 30^2 = 900
            Vec3 dir = Vec3.atCenterOf(targetPos).subtract(playerPos).normalize();
            Vec3 intermediate = playerPos.add(dir.scale(25));
            currentTarget = BlockPos.containing(intermediate.x, intermediate.y, intermediate.z);
        }

        // ── 2. Destination check ──────────────────────────────────────────────
        double maxAllowedDistSq = stopDistance * stopDistance;
        if (distToFinalSq < maxAllowedDistSq) {
            client.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("§a[GoTo] Destination reached!"));
            stopNavigation();
            return;
        }

        // ── 3. Chunk safety ──────────────────────────────────────────────────
        if (!client.level.getChunkSource().hasChunk(playerPos.x > 0 ? (int)playerPos.x >> 4 : ((int)playerPos.x - 16) >> 4,
                playerPos.z > 0 ? (int)playerPos.z >> 4 : ((int)playerPos.z - 16) >> 4)) {
            resetKeys(client);
            return;
        }

        // ── 4. Stuck detection ───────────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (lastCheckPos == null || playerPos.distanceToSqr(lastCheckPos) > STUCK_MOVE_THRESHOLD_SQ) {
            lastCheckPos = playerPos;
            lastProgressTime = now;
        }
        boolean stuck = (now - lastProgressTime) > STUCK_TIMEOUT_MS;

        // ── 5. Path (re)calculation ──────────────────────────────────────────
        boolean periodicCheckDue = (now - lastRecalc) > RECALC_INTERVAL_MS;
        boolean offCorridor = periodicCheckDue && !path.isEmpty() && pathIndex < path.size()
                && !hasLineOfSight(client, playerPos, path.get(pathIndex));

        if (path.isEmpty() || pathIndex >= path.size() || stuck || offCorridor) {

            calculatePath(client, client.player.blockPosition(), currentTarget);
            lastRecalc = now;
            lastProgressTime = now;
            lastCheckPos = playerPos;
        }

        if (path.isEmpty()) return;

        // ── 6. Pure-pursuit waypoint selection ───────────────────────────────
        while (pathIndex < path.size() - 1 && playerPos.distanceToSqr(Vec3.atCenterOf(path.get(pathIndex))) < 1.1) {
            pathIndex++;
        }

        int lookIndex = pathIndex;
        int maxLook = Math.min(path.size() - 1, pathIndex + MAX_LOOKAHEAD_NODES);
        while (lookIndex < maxLook && hasLineOfSight(client, playerPos, path.get(lookIndex + 1))) {
            lookIndex++;
        }
        BlockPos waypoint = path.get(lookIndex);

        // ── 7. Rotation (Head Movement) ──────────────────────────────────────
        double dx = waypoint.getX() + 0.5 - playerPos.x;
        double dz = waypoint.getZ() + 0.5 - playerPos.z;
        float desiredYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float yawDiff = Mth.wrapDegrees(desiredYaw - client.player.getYRot());
        client.player.setYRot(client.player.getYRot() + (yawDiff * 0.3f));

        // ── 8. Movement & Parkour ────────────────────────────────────────────
        double moveYawRad = Math.toRadians(client.player.getYRot());
        boolean forward = (dx * -Math.sin(moveYawRad) + dz * Math.cos(moveYawRad)) > 0.2;
        client.options.keyUp.setDown(forward);
        client.options.keySprint.setDown(forward && client.player.getFoodData().getFoodLevel() > 6);
        client.player.setSprinting(forward && client.player.getFoodData().getFoodLevel() > 6);

        // Jump & Swim Logic
        if (client.player.horizontalCollision && client.player.onGround()) client.player.jumpFromGround();
        if (client.player.isInWater() || client.player.isInLava()) client.options.keyJump.setDown(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Pathfinding (A* with proper open-set decrease-key + corner-cut guard)
    // ════════════════════════════════════════════════════════════════════════

    private static final class AStarNode implements Comparable<AStarNode> {
        final BlockPos pos;
        final AStarNode parent;
        final double g;
        final double h;

        AStarNode(BlockPos pos, AStarNode parent, double g, double h) {
            this.pos = pos; this.parent = parent; this.g = g; this.h = h;
        }
        double f() { return g + h; }
        @Override public int compareTo(AStarNode o) { return Double.compare(f(), o.f()); }
    }

    private static final int[][] NEIGHBOR_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private void calculatePath(Minecraft client, BlockPos start, BlockPos dynamicTarget) {
        // RADIKALER RESET: Verhindert Reste von alten Modul-Ausführungen
        this.path.clear();
        this.rawPathForRender.clear();
        this.pathIndex = 0;

        PriorityQueue<AStarNode> open = new PriorityQueue<>();
        Map<BlockPos, AStarNode> openMap = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        // Start mit euklidischer Distanz zum dynamischen Ziel
        double startH = start.distSqr(dynamicTarget);
        AStarNode startNode = new AStarNode(start, null, 0, startH);
        open.add(startNode);
        openMap.put(start, startNode);

        AStarNode best = startNode;
        int iterations = 0;
        final int MAX_ITERATIONS = 5000;

        while (!open.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            AStarNode current = open.poll();

            if (openMap.get(current.pos) != current) continue;
            openMap.remove(current.pos);
            closed.add(current.pos);

            // Prüfen, ob wir näher am Ziel sind (Heuristik-Verbesserung)
            if (current.pos.distSqr(dynamicTarget) < best.pos.distSqr(dynamicTarget)) {
                best = current;
            }

            // Ziel erreicht Check (nutzt dynamicTarget)
            if (current.pos.equals(dynamicTarget) || current.pos.distManhattan(dynamicTarget) <= 1) {
                buildSmoothedPath(client, current);
                return;
            }

            for (int[] dir : NEIGHBOR_DIRS) {
                // Diagonal-Check für sicherere Bewegung
                if (dir[0] != 0 && dir[1] != 0) {
                    if (!isWalkableColumn(client, current.pos.offset(dir[0], 0, 0))
                            || !isWalkableColumn(client, current.pos.offset(0, 0, dir[1]))) {
                        continue;
                    }
                }

                BlockPos rawNext = current.pos.offset(dir[0], 0, dir[1]);
                BlockPos validPos = getValidWalkablePos(client, rawNext);

                if (validPos == null || closed.contains(validPos)) continue;

                double stepCost = (dir[0] != 0 && dir[1] != 0) ? 1.414 : 1.0;
                double yDiff = validPos.getY() - current.pos.getY();
                stepCost += Math.abs(yDiff) * 0.2; // Kosten für Höhenunterschiede

                double newG = current.g + stepCost;

                AStarNode existing = openMap.get(validPos);
                if (existing != null && existing.g <= newG) continue;

                double nextH = validPos.distSqr(dynamicTarget);
                AStarNode neighbor = new AStarNode(validPos, current, newG, nextH);

                open.add(neighbor);
                openMap.put(validPos, neighbor);
            }
        }

        if (best != startNode && best.pos.distSqr(dynamicTarget) < startNode.pos.distSqr(dynamicTarget)) {
            buildSmoothedPath(client, best);
        } else {
            client.gui.getChat().addClientSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c[GoTo] No path found to target!"));
            stopNavigation();
        }
    }

    private boolean isWalkableColumn(Minecraft client, BlockPos pos) {
        BlockState feet = client.level.getBlockState(pos);
        BlockState head = client.level.getBlockState(pos.above());
        return feet.isAir() && head.isAir()
                && feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }

    private BlockPos getValidWalkablePos(Minecraft client, BlockPos pos) {
        for (int yOffset = 2; yOffset >= -3; yOffset--) {
            BlockPos checkPos = pos.above(yOffset);
            BlockState feet   = client.level.getBlockState(checkPos);
            BlockState head   = client.level.getBlockState(checkPos.above());
            BlockState ground = client.level.getBlockState(checkPos.below());

            if (feet.isAir() && head.isAir()
                    && !ground.isAir() && ground.getFluidState().isEmpty()) {

                if (yOffset == 2) {
                    BlockState blockAtEyeLevel = client.level.getBlockState(pos.above(1));
                    if (!blockAtEyeLevel.isAir()) {
                        continue;
                    }
                }
                return checkPos;
            }
        }
        return null;
    }

    private void buildSmoothedPath(Minecraft client, AStarNode endNode) {
        List<BlockPos> raw = new ArrayList<>();
        AStarNode cur = endNode;
        while (cur != null) {
            raw.add(0, cur.pos);
            cur = cur.parent;
        }

        this.rawPathForRender = new ArrayList<>(raw);

        List<BlockPos> smoothed = new ArrayList<>();
        smoothed.add(raw.get(0));
        int i = 0;
        while (i < raw.size() - 1) {
            int j = raw.size() - 1;
            while (j > i + 1 && !hasLineOfSight(client, Vec3.atCenterOf(raw.get(i)), raw.get(j))) {
                j--;
            }
            smoothed.add(raw.get(j));
            i = j;
        }

        this.path = smoothed;
        this.pathIndex = 0;
    }

    private boolean hasLineOfSight(Minecraft client, Vec3 from, BlockPos toPos) {
        Vec3 to = Vec3.atCenterOf(toPos);
        double dist = from.distanceTo(to);
        if (dist < 0.1) return true;

        int steps = (int) Math.ceil(dist * 2);
        for (int s = 1; s <= steps; s++) {
            double t = s / (double) steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;

            BlockPos sample = BlockPos.containing(x, y, z);
            BlockState feet   = client.level.getBlockState(sample);
            BlockState head   = client.level.getBlockState(sample.above());
            BlockState ground = client.level.getBlockState(sample.below());

            if (!feet.isAir() || !head.isAir()) return false;
            if (ground.isAir() || !ground.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static void resetKeys(Minecraft client) {
        if (client.options != null) {
            client.options.keyUp.setDown(false);
            client.options.keyDown.setDown(false);
            client.options.keyLeft.setDown(false);
            client.options.keyRight.setDown(false);
            client.options.keySprint.setDown(false);
            client.options.keyJump.setDown(false);
        }
        if (client.player != null) {
            client.player.setSprinting(false);
        }
    }
}