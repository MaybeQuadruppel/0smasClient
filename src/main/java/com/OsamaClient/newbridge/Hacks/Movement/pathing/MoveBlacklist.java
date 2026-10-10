package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Merkt sich Moves (Kante von -> nach), die der MovementExecutor nicht ausführen konnte
 * (kein sichtbares Face, Block nicht platzierbar, Timeout, ...).
 *
 * Der Pathfinder überspringt diese Kanten für kurze Zeit. Ohne das berechnet er nach
 * einem Fehlschlag exakt denselben Pfad nochmal -> Endlos-Stuck.
 */
public final class MoveBlacklist {

    private static final long DURATION_MS = 30_000;

    private record Edge(long from, long to) {}

    private static final Map<Edge, Long> EDGES = new HashMap<>();

    private MoveBlacklist() {}

    public static void add(BlockPos from, BlockPos to) {
        EDGES.put(new Edge(hash(from.getX(), from.getY(), from.getZ()),
                        hash(to.getX(), to.getY(), to.getZ())),
                System.currentTimeMillis() + DURATION_MS);
    }

    public static boolean isBlocked(int fx, int fy, int fz, int tx, int ty, int tz) {
        if (EDGES.isEmpty()) return false;
        Edge e = new Edge(hash(fx, fy, fz), hash(tx, ty, tz));
        Long expiry = EDGES.get(e);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            EDGES.remove(e);
            return false;
        }
        return true;
    }

    public static void clear() {
        EDGES.clear();
    }

    /** Aufräumen abgelaufener Einträge (optional, z.B. bei goTo). */
    public static void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Edge, Long>> it = EDGES.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now) it.remove();
        }
    }

    private static long hash(int x, int y, int z) {
        return PathNode.longHash(x, y, z);
    }
}