package com.OsamaClient.newbridge.Hacks.Combat.trap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plant einen kompletten Trap ("Fullbox") um eine Hitbox: Boden, Ring auf Fuß- und Kopfhöhe,
 * Deckel. Funktioniert auch, wenn der Gegner auf einer Blockkante steht (mehrere Spalten belegt).
 * Ohne Minecraft-Client-Abhängigkeit -> headless testbar.
 */
public final class TrapPlanner {

    public record Options(boolean floor, boolean feet, boolean head, boolean top) {
        public static final Options FULL = new Options(true, true, true, true);
    }

    /** Ein Platzier-Schritt: Zielposition, Stützblock und Face am Stützblock. */
    public record Placement(BlockPos target, BlockPos support, Direction face) {
        public Vec3 hitPoint() {
            return Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        }
    }

    private TrapPlanner() {}

    /** Alle Block-Spalten (x,z), die die Hitbox horizontal schneidet. */
    public static List<BlockPos> columns(AABB box, int y) {
        List<BlockPos> out = new ArrayList<>();
        int x0 = (int) Math.floor(box.minX + 1.0E-4), x1 = (int) Math.floor(box.maxX - 1.0E-4);
        int z0 = (int) Math.floor(box.minZ + 1.0E-4), z1 = (int) Math.floor(box.maxZ - 1.0E-4);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) out.add(new BlockPos(x, y, z));
        return out;
    }

    /**
     * Alle Positionen des Traps in sinnvoller Bau-Reihenfolge (Boden, Füße, Kopf, Deckel).
     * Positionen, die schon fest sind, werden weggelassen.
     */
    public static List<BlockPos> plan(BlockGetter level, AABB target, Options o) {
        int feetY = (int) Math.floor(target.minY + 1.0E-3);
        int headY = (int) Math.floor(target.maxY - 1.0E-3);
        List<BlockPos> feetCols = columns(target, feetY);
        Set<Long> occupied = new java.util.HashSet<>();
        for (int y = feetY; y <= headY; y++) for (BlockPos p : columns(target, y)) occupied.add(p.asLong());

        Set<BlockPos> out = new LinkedHashSet<>();
        if (o.floor()) for (BlockPos c : feetCols) out.add(c.below());
        for (int y = feetY; y <= headY; y++) {
            boolean isHeadLayer = y == headY && headY != feetY;
            if (isHeadLayer ? !o.head() : !o.feet()) continue;
            for (BlockPos c : columns(target, y)) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = c.relative(d);
                    if (!occupied.contains(n.asLong())) out.add(n);
                }
            }
        }
        if (o.top()) for (BlockPos c : columns(target, headY)) out.add(c.above());

        List<BlockPos> wanted = new ArrayList<>();
        for (BlockPos p : out) if (canPlaceInto(level.getBlockState(p))) wanted.add(p);
        return order(level, wanted, occupied);
    }

    /**
     * Sortiert so, dass jeder Block beim Setzen einen festen oder schon gesetzten Nachbarn hat.
     * Wo das nicht geht (z.B. Deckel über dem Kopf, Ring um ein schwebendes Ziel), werden
     * Hilfsblöcke eingefügt. Was gar nicht stützbar ist, kommt ans Ende (bleibt evtl. offen).
     */
    static List<BlockPos> order(BlockGetter level, List<BlockPos> wanted, Set<Long> occupied) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> available = new java.util.HashSet<>();
        List<BlockPos> remaining = new ArrayList<>(wanted);
        int helpers = 0;
        while (!remaining.isEmpty()) {
            boolean picked = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (hasSupport(level, remaining.get(i), available)) {
                    BlockPos p = remaining.remove(i);
                    result.add(p);
                    available.add(p);
                    picked = true;
                    break;
                }
            }
            if (picked) continue;
            // Hilfsblock: Nachbar eines offenen Blocks, der selbst schon Halt hat
            BlockPos helper = null;
            search:
            for (BlockPos r : remaining) {
                for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
                    BlockPos n = r.relative(d);
                    if (occupied.contains(n.asLong()) || available.contains(n) || remaining.contains(n)) continue;
                    if (!canPlaceInto(level.getBlockState(n))) continue;
                    if (hasSupport(level, n, available)) {
                        helper = n;
                        break search;
                    }
                }
            }
            if (helper == null || ++helpers > 8) {
                result.addAll(remaining); // nicht stützbar
                break;
            }
            result.add(helper);
            available.add(helper);
        }
        return result;
    }

    private static boolean hasSupport(BlockGetter level, BlockPos p, Set<BlockPos> available) {
        for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            if (available.contains(n) || isSolidSupport(level, n)) return true;
        }
        return false;
    }

    /** Ersetzbar (Luft, Gras, Wasser ...). */
    public static boolean canPlaceInto(BlockState s) {
        return s.isAir() || s.canBeReplaced();
    }

    /** Fester Block, an den man platzieren kann. */
    public static boolean isSolidSupport(BlockGetter level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty()
                && !s.getCollisionShape(level, p).isEmpty();
    }

    /**
     * Stützfläche für {@code target}: ein fester Nachbar (oder einer, der in diesem Durchgang schon
     * gesetzt wurde). Bevorzugt Unterseite, dann Seiten, dann oben. null = (noch) nicht platzierbar.
     */
    public static Placement support(BlockGetter level, BlockPos target, Set<BlockPos> placedThisRound, Vec3 eye, double reach) {
        Placement best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos s = target.relative(d);
            if (!isSolidSupport(level, s) && !placedThisRound.contains(s)) continue;
            Placement p = new Placement(target, s, d.getOpposite());
            double dist = eye == null ? 0 : eye.distanceTo(p.hitPoint());
            if (eye != null && dist > reach) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    /**
     * Nächste Platzierungen dieser Runde (max. {@code limit}). Später geplante Blöcke können auf
     * früher in derselben Runde gesetzte gestützt werden.
     */
    public static List<Placement> nextPlacements(BlockGetter level, List<BlockPos> plan, Vec3 eye, double reach,
                                                 int limit, java.util.function.Predicate<BlockPos> blockedByEntity) {
        List<Placement> out = new ArrayList<>();
        Set<BlockPos> placed = new java.util.HashSet<>();
        boolean progress = true;
        List<BlockPos> remaining = new ArrayList<>(plan);
        while (progress && out.size() < limit) {
            progress = false;
            for (int i = 0; i < remaining.size() && out.size() < limit; i++) {
                BlockPos t = remaining.get(i);
                if (blockedByEntity != null && blockedByEntity.test(t)) continue;
                Placement p = support(level, t, placed, eye, reach);
                if (p == null) continue;
                out.add(p);
                placed.add(t);
                remaining.remove(i--);
                progress = true;
            }
        }
        return out;
    }

    /** Rotation (yaw, pitch) vom Auge zum Punkt. */
    public static float[] rotationTo(Vec3 eye, Vec3 point) {
        Vec3 d = point.subtract(eye);
        double dxz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, dxz)));
        return new float[]{yaw, pitch};
    }
}
