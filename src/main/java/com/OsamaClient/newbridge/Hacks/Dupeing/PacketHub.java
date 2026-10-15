package com.OsamaClient.newbridge.Hacks.Dupeing;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Central place that sees every outgoing packet (hooked from ClientConnectionMixin). Packet tools register gates
 * here: a gate either holds matching packets until they are flushed, or drops them.
 *
 * The idea (hold packets, do something, release them in one go) comes from the Autism client's PacketGateManager
 * (GPL-3.0, https://github.com/AutismDevelopment/Autism-Client); this is a compact reimplementation.
 */
public final class PacketHub {

    public enum Mode { DELAY, CANCEL }

    public static final class Gate {
        public final String id;
        public volatile Mode mode;
        public volatile Predicate<Packet<?>> filter;

        Gate(String id, Mode mode, Predicate<Packet<?>> filter) {
            this.id = id;
            this.mode = mode;
            this.filter = filter;
        }
    }

    private static final List<Gate> GATES = new ArrayList<>();
    private static final List<Packet<?>> HELD = new ArrayList<>();
    /**
     * Packets waiting for a paced release. Since 26.x the server kicks clients that send more than one position
     * packet in a tick ("Invalid move player packet"), so released movement is spread out: one position packet per tick.
     */
    private static final java.util.ArrayDeque<Packet<?>> PACED = new java.util.ArrayDeque<>();
    private static volatile long tickNo;
    private static volatile long lastPositionTick = -1;
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "newbridge-packet-hub");
        t.setDaemon(true);
        return t;
    });

    /** Test/debug: counts every packet that passes the hook, by class simple name. */
    public static volatile boolean tracking;
    /** Movement packets that claimed onGround = true while tracking (after AntiHunger rewrote them). */
    public static final AtomicInteger groundPackets = new AtomicInteger();
    private static final Map<String, AtomicInteger> SEEN = new ConcurrentHashMap<>();

    /** PingSpoof settings (outgoing keepalive/pong delay). */
    public static volatile int pingDelayMs;
    public static final AtomicInteger pingDelayed = new AtomicInteger();

    private PacketHub() {}

    // ── gates ──────────────────────────────────────────────────────────────────

    public static synchronized Gate open(String id, Mode mode, Predicate<Packet<?>> filter) {
        close(id);
        Gate g = new Gate(id, mode, filter);
        GATES.add(g);
        return g;
    }

    /** Removes the gate. Held packets stay queued until {@link #flush()} or {@link #discard()}. */
    public static synchronized void close(String id) {
        GATES.removeIf(g -> g.id.equals(id));
    }

    public static synchronized boolean isOpen(String id) {
        for (Gate g : GATES) if (g.id.equals(id)) return true;
        return false;
    }

    public static synchronized int heldCount() { return HELD.size(); }

    // ── hook ───────────────────────────────────────────────────────────────────

    /** @return true if the packet was taken (held or dropped) and must not be sent now. */
    public static boolean intercept(Packet<?> packet) {
        if (tracking) {
            SEEN.computeIfAbsent(packet.getClass().getSimpleName(), k -> new AtomicInteger()).incrementAndGet();
            if (packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket mv && mv.isOnGround()) groundPackets.incrementAndGet();
        }
        if (BYPASS.get()) return false;

        synchronized (PacketHub.class) {
            // The tick-end packet must never wait behind paced position packets: it is what tells the 26.x server a
            // new tick started, so delaying it makes the next paced position count as a second one in the same tick.
            if (!PACED.isEmpty() && !(packet instanceof net.minecraft.network.protocol.game.ServerboundClientTickEndPacket)) { // keep the order while a paced release is running
                PACED.add(packet);
                return true;
            }
            if (isPosition(packet)) {
                if (lastPositionTick == tickNo) {   // second position packet this tick would get us kicked
                    PACED.add(packet);
                    return true;
                }
            }
        }

        int ping = pingDelayMs;
        if (ping > 0 && (packet instanceof net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
                || packet instanceof net.minecraft.network.protocol.common.ServerboundPongPacket)) {
            pingDelayed.incrementAndGet();
            SCHEDULER.schedule(() -> sendNow(packet), ping, TimeUnit.MILLISECONDS);
            return true;
        }

        synchronized (PacketHub.class) {
            if (GATES.isEmpty()) {
                if (isPosition(packet)) lastPositionTick = tickNo;
                return false;
            }
            boolean delay = false;
            for (Gate g : GATES) {
                if (!g.filter.test(packet)) continue;
                if (g.mode == Mode.CANCEL) return true;
                delay = true;
            }
            if (delay) {
                HELD.add(packet);
                return true;
            }
            if (isPosition(packet)) lastPositionTick = tickNo;
        }
        return false;
    }

    // ── sending ────────────────────────────────────────────────────────────────

    /** Sends a packet past all gates. */
    public static void sendNow(Packet<?> packet) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        BYPASS.set(true);
        try {
            conn.send(packet);
        } catch (Throwable t) {
            System.out.println("[PacketHub] send failed for " + packet.getClass().getSimpleName() + ": " + t);
            t.printStackTrace();
        } finally {
            BYPASS.set(false);
        }
    }

    static boolean isPosition(Packet<?> p) {
        return p instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket m && m.hasPosition();
    }

    /** Releases every held packet in the original order (position packets paced one per tick). */
    public static int flush() { return flush(false); }

    /**
     * @param keepLatestPosition true while the gate stays open afterwards (no live position packet will follow), so the
     *                           newest held position is sent as the single position packet of the release.
     */
    public static int flush(boolean keepLatestPosition) {
        List<Packet<?>> copy;
        synchronized (PacketHub.class) {
            copy = new ArrayList<>(HELD);
            HELD.clear();
            // The 26.x server kicks clients that send two position packets between two tick-end packets, and the
            // client's own live position packet already carries the current position, so held ones are dropped.
            Packet<?> lastPos = null;
            for (Packet<?> p : copy) {
                if (isPosition(p)) lastPos = p;
                else PACED.add(p);
            }
            if (keepLatestPosition && lastPos != null) PACED.add(lastPos);
        }
        // Not sent here: the server resets its "position received" flag on the client's tick-end packet, so the
        // first paced position packet must go out at the start of the next tick (onClientTick).
        return copy.size();
    }

    /** Sends queued packets until (and including) the first position packet of this tick. */
    private static void drainPaced() {
        while (true) {
            Packet<?> p;
            synchronized (PacketHub.class) {
                p = PACED.peekFirst();
                if (p == null) return;
                if (isPosition(p)) {
                    if (lastPositionTick == tickNo) return;
                    lastPositionTick = tickNo;
                }
                PACED.pollFirst();
            }
            sendNow(p);
        }
    }

    /** Call once at the start of every client tick. */
    public static void onClientTick() {
        tickNo++;
        drainPaced();
        com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
    }

    public static synchronized int pacedCount() { return PACED.size(); }

    public static synchronized int discard() {
        int n = HELD.size();
        HELD.clear();
        PACED.clear();
        return n;
    }

    // ── packet name filters ────────────────────────────────────────────────────

    /** Predicate for a comma separated list of packet names (matches simple class name, with or without "Serverbound"). */
    public static Predicate<Packet<?>> byNames(String csv) {
        List<String> want = new ArrayList<>();
        for (String s : csv.split(",")) {
            String n = normalize(s);
            if (!n.isEmpty()) want.add(n);
        }
        if (want.isEmpty()) return p -> false;
        return p -> {
            String n = normalize(p.getClass().getSimpleName());
            for (String w : want) if (n.equals(w) || n.endsWith(w)) return true;
            return false;
        };
    }

    static String normalize(String s) {
        String n = s.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (n.endsWith("packet")) n = n.substring(0, n.length() - 6);
        if (n.startsWith("serverbound")) n = n.substring(11);
        return n;
    }

    // ── tracking (tests) ───────────────────────────────────────────────────────

    public static int seen(String simpleName) {
        AtomicInteger a = SEEN.get(simpleName);
        return a == null ? 0 : a.get();
    }

    public static void resetSeen() { SEEN.clear(); }
}
