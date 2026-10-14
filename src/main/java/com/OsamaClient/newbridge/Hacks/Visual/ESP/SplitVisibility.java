package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Sichtbarkeits-Splitting für die ESP (ohne Render-Abhängigkeiten, daher auch headless testbar).
 *
 * Alle Koordinaten sind kamera-relativ. Ein Punkt gilt als sichtbar, wenn der Strahl
 * Kamera -> Punkt keinen Block trifft. Entities werden NIE als Hindernis gezählt
 * (ClipContext prüft nur Block-Kollision).
 */
public final class SplitVisibility {

    /** Empfängt die Teilstücke einer Linie: Parameter t0..t1 (0..1) und ob sichtbar. */
    public interface SegmentSink { void segment(float t0, float t1, boolean visible); }

    public static final float EDGE_STEP = 0.2f;      // Abtastabstand auf Kanten (Blöcke)
    public static final float CELL_SIZE = 0.3f;      // Zellgröße für Flächen
    public static final int MAX_EDGE_SAMPLES = 24;
    public static final int MAX_CELLS_PER_AXIS = 12;
    public static final int REFINE_STEPS = 5;        // Binärsuche für exakte Farbgrenze
    public static final int RAY_BUDGET = 25000;      // Schutz gegen FPS-Einbrüche

    /** Ergebnisse werden pro Objekt aufgezeichnet und die nächsten Frames wiederverwendet. */
    public static final int REFRESH_FRAMES = 3;
    private static final int TAPE_TTL_FRAMES = 120;

    private static final class Tape {
        float[] data = new float[128];
        int len;
        int recordedFrame;
        int lastUsed;
        boolean ready;

        void put(float v) {
            if (len == data.length) data = java.util.Arrays.copyOf(data, len * 2);
            data[len++] = v;
        }
    }

    private final java.util.Map<Integer, Tape> tapes = new java.util.HashMap<>();
    private Tape tape;
    private boolean replay;
    private int tapePos;
    private int frame;

    private BlockGetter level;
    private CollisionContext collision = CollisionContext.empty();
    private double eyeX, eyeY, eyeZ;
    private int raysLeft;
    private Runnable onError;

    /** Startet einen Frame. {@code level == null} = Split aus. */
    public void begin(BlockGetter level, CollisionContext collision, double eyeX, double eyeY, double eyeZ) {
        this.level = level;
        this.collision = collision == null ? CollisionContext.empty() : collision;
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.raysLeft = RAY_BUDGET;
        this.frame++;
        this.tape = null;
        if ((frame & 63) == 0) tapes.values().removeIf(t -> frame - t.lastUsed > TAPE_TTL_FRAMES);
    }

    /**
     * Beginnt die Aufzeichnung/Wiedergabe für ein Objekt ({@code key}, z.B. Entity-Id + Art).
     * Alle {@link #REFRESH_FRAMES} Frames (pro Key versetzt) wird neu geraycastet, dazwischen
     * wird das Ergebnis der letzten Messung wiederverwendet. Der Aufrufer muss in jedem Frame
     * dieselbe Abfolge von {@link #splitLine}/{@link #cellsFor}/{@link #cellVisible} ausführen.
     */
    public void beginTape(int key) {
        if (level == null) return;
        Tape t = tapes.computeIfAbsent(key, k -> new Tape());
        t.lastUsed = frame;
        boolean stale = !t.ready || (frame + key) % REFRESH_FRAMES == 0 || frame - t.recordedFrame >= REFRESH_FRAMES;
        tape = t;
        tapePos = 0;
        replay = !stale;
        if (!replay) {
            t.len = 0;
            t.ready = false;
            t.recordedFrame = frame;
        }
    }

    public void endTape() {
        if (tape != null && !replay) tape.ready = true;
        tape = null;
        replay = false;
    }

    /** Wiedergabe ist aus dem Takt geraten (anderer Ablauf als beim Aufzeichnen): live weiterrechnen. */
    private void abandonReplay() {
        if (tape != null) tape.ready = false;
        tape = null;
        replay = false;
    }

    /** Sichtbarkeit eines Zellmittelpunkts (aufgezeichnet/wiedergegeben, falls ein Tape aktiv ist). */
    public boolean cellVisible(double rx, double ry, double rz) {
        if (tape != null && replay) {
            if (tapePos < tape.len) return tape.data[tapePos++] != 0f;
            abandonReplay();
        }
        boolean v = isPointVisible(rx, ry, rz);
        if (tape != null) tape.put(v ? 1f : 0f);
        return v;
    }

    public void end() { level = null; }

    public boolean active() { return level != null; }

    public int raysLeft() { return raysLeft; }

    public void onError(Runnable r) { this.onError = r; }

    public boolean isPointVisible(double rx, double ry, double rz) {
        if (level == null) return true;
        double lenSq = rx * rx + ry * ry + rz * rz;
        if (lenSq < 1.0E-6) return true;
        raysLeft--;
        Vec3 from = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 to = new Vec3(eyeX + rx, eyeY + ry, eyeZ + rz);
        try {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, collision));
            if (hit.getType() == HitResult.Type.MISS) return true;
            // Punkte genau auf einer Blockoberfläche (z.B. Füße auf dem Boden) zählen nicht als verdeckt.
            double len = Math.sqrt(lenSq);
            return hit.getLocation().distanceTo(from) >= len - 0.06;
        } catch (RuntimeException e) {
            level = null;
            if (onError != null) onError.run();
            return true;
        }
    }

    /** Teilt die Linie a->b an allen Sichtbarkeitswechseln (Abtastung + Binärsuche). */
    public void splitLine(float ax, float ay, float az, float bx, float by, float bz, SegmentSink sink) {
        if (level == null) { sink.segment(0f, 1f, true); return; }
        if (tape != null && replay) {
            if (tapePos < tape.len) {
                int n = (int) tape.data[tapePos++];
                if (tapePos + n * 3 <= tape.len) {
                    for (int i = 0; i < n; i++) {
                        sink.segment(tape.data[tapePos], tape.data[tapePos + 1], tape.data[tapePos + 2] != 0f);
                        tapePos += 3;
                    }
                    return;
                }
            }
            abandonReplay();
        }
        if (tape != null) {
            int countAt = tape.len;
            tape.put(0f);
            int[] n = {0};
            final Tape t = tape;
            splitLineLive(ax, ay, az, bx, by, bz, (t0, t1, vis) -> {
                t.put(t0); t.put(t1); t.put(vis ? 1f : 0f);
                n[0]++;
                sink.segment(t0, t1, vis);
            });
            t.data[countAt] = n[0];
            return;
        }
        splitLineLive(ax, ay, az, bx, by, bz, sink);
    }

    private void splitLineLive(float ax, float ay, float az, float bx, float by, float bz, SegmentSink sink) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        if (raysLeft <= 0) { // Budget erschöpft: ganze Linie nach Mittelpunkt
            sink.segment(0f, 1f, isPointVisible(ax + dx * 0.5f, ay + dy * 0.5f, az + dz * 0.5f));
            return;
        }
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Mth.clamp((int) Math.ceil(len / EDGE_STEP), 1, MAX_EDGE_SAMPLES);
        boolean prev = isPointVisible(ax, ay, az);
        float segStart = 0f;
        for (int k = 1; k <= samples; k++) {
            float t = (float) k / samples;
            boolean cur = isPointVisible(ax + dx * t, ay + dy * t, az + dz * t);
            if (cur != prev) {
                float lo = (float) (k - 1) / samples, hi = t;
                for (int it = 0; it < REFINE_STEPS; it++) {
                    float mid = (lo + hi) * 0.5f;
                    if (isPointVisible(ax + dx * mid, ay + dy * mid, az + dz * mid) == prev) lo = mid;
                    else hi = mid;
                }
                float boundary = (lo + hi) * 0.5f;
                if (boundary - segStart > 1.0E-4f) sink.segment(segStart, boundary, prev);
                segStart = boundary;
                prev = cur;
            }
        }
        if (1f - segStart > 1.0E-4f) sink.segment(segStart, 1f, prev);
    }

    /** Zellenanzahl (pro Achse) für eine Fläche mit den Kantenlängen uLen/vLen. */
    public int cellsFor(float edgeLen) {
        if (tape != null && replay) {
            if (tapePos < tape.len) return (int) tape.data[tapePos++];
            abandonReplay();
        }
        int n = (level == null || raysLeft <= 0) ? 1 : Mth.clamp((int) Math.ceil(edgeLen / CELL_SIZE), 1, MAX_CELLS_PER_AXIS);
        if (tape != null) tape.put(n);
        return n;
    }
}
