package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Zielt Bögen/Armbrüste mit echter Flugbahn-Simulation:
 *  - Pfeilphysik (Luftwiderstand 0.99, Gravitation 0.05, Eigengeschwindigkeit des Spielers)
 *  - Geschwindigkeit des Gegners aus der Positions-Historie (inkl. Sprung/Fall mit Gravitation)
 *  - Ping- und Interpolations-Ausgleich
 *  - Silent Aim über Tracker (optional), mit sanftem Rückdrehen
 */
public class BowAimbot extends Module {

    private static final int MAX_TICKS = 120;           // max. Flugzeit in Ticks
    private static final int SOLVER_ITERATIONS = 10;
    private static final int HISTORY_SIZE = 5;          // 4 Intervalle für die Geschwindigkeit
    private static final double INTERP_TICKS = 1.5;     // Client sieht Gegner ca. so viele Ticks verzögert
    private static final double MAX_MISS = 2.0;         // darüber gilt das Ziel als nicht treffbar
    private static final double BODY_FACTOR = 0.6;      // Zielhöhe am Körper

    private double range = 60.0;
    private double smoothing = 2.0;
    private boolean predict = true;
    private boolean needLineOfSight = true;
    private boolean silent = true;
    private boolean pingComp = true;
    private float minDraw = 0.5F;   // Mindest-Spannung (0..1), darunter wird nicht gezielt

    private final EntityFilterPicker filter = new EntityFilterPicker("Targets");

    private final Map<Integer, ArrayDeque<Vec3>> history = new HashMap<>();
    private float vYaw, vPitch;   // virtuelle (gespoofte) Rotation
    private boolean spoofing;

    public BowAimbot() {
        super("BowAimbot", "Aims your bow at targets (with velocity lead, drop and ping compensation).", Category.COMBAT);

        // Wie bisher: standardmäßig nur Spieler
        filter.filters.put("Hostiles", false);

        settings.add(new Slider("Range", 10.0, 120.0, range, 5.0, v -> range = v)
                .withDescription("Maximum target distance"));
        settings.add(new Slider("Min Draw %", 0.0, 100.0, 50.0, 5.0, v -> minDraw = (float) (v / 100.0))
                .withDescription("Only aim once the bow is drawn at least this far (arrow power)"));
        settings.add(new Slider("Smoothing", 1.0, 10.0, smoothing, 0.5, v -> smoothing = v)
                .withDescription("1 = instant snap, higher = smoother"));
        settings.add(new ToggleButton("Predict", predict, v -> predict = v)
                .withDescription("Lead moving targets using their measured velocity (incl. jumping/falling)"));
        settings.add(new ToggleButton("Ping Compensation", pingComp, v -> pingComp = v)
                .withDescription("Adds your ping to the lead time"));
        settings.add(new ToggleButton("Line of Sight", needLineOfSight, v -> needLineOfSight = v)
                .withDescription("Only target entities you can see"));
        settings.add(new ToggleButton("Silent", silent, v -> silent = v)
                .withDescription("Aim only in the packets, your camera stays free"));
        settings.add(filter.withDescription("Which entities can be targeted"));
    }

    @Override
    public void onDisable() {
        spoofing = false;
        history.clear();
    }

    // ------------------------------------------------------------------ TICK

    @Override
    public void onTick(Minecraft mc) {
        if (!enabled || mc.player == null || mc.level == null) {
            spoofing = false;
            history.clear();
            return;
        }

        // Positions-Historie muss JEDEN Tick laufen, damit beim Spannen sofort eine Geschwindigkeit da ist
        updateHistory(mc);

        boolean aimed = false;
        double speed = shotSpeed(mc.player);
        if (speed > 0.0) {
            aimed = aim(mc, speed);
        }
        if (!aimed) {
            returnToReal(mc);
        }
    }

    /**
     * Abschussgeschwindigkeit des Pfeils oder -1, wenn gerade nichts geschossen wird.
     * Bogen: Geschwindigkeit hängt von der Spannung ab (BowItem.getPowerForTime * 3.0), die
     * Flugbahn wird jeden Tick mit der aktuellen Spannung neu berechnet - beim Loslassen
     * stimmt der Winkel also zur tatsächlichen Pfeilgeschwindigkeit.
     * Armbrust: feste Geschwindigkeit 3.15, wird schon beim Laden vorgezielt.
     */
    private double shotSpeed(LocalPlayer p) {
        if (p.isUsingItem()) {
            Item item = p.getUseItem().getItem();
            if (item instanceof BowItem) {
                float power = BowItem.getPowerForTime(p.getTicksUsingItem());
                if (power < Math.max(0.1F, minDraw)) return -1.0; // zu wenig gespannt
                return power * 3.0;
            }
            if (item instanceof CrossbowItem) return 3.15;        // lädt: schon vorzielen
            return -1.0;
        }
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        if ((main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main))
                || (off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off))) {
            return 3.15; // geladene Armbrust: Pfeil
        }
        return -1.0;
    }

    // -------------------------------------------------------------- ZIELEN

    private boolean aim(Minecraft mc, double speed) {
        Entity target = pickTarget(mc);
        if (target == null) return false;

        // Pfeil startet auf Augenhöhe - 0.1 und erbt die Bewegung des Schützen (y nur in der Luft)
        Vec3 start = mc.player.getEyePosition().subtract(0.0, 0.1, 0.0);
        Vec3 mv = mc.player.getDeltaMovement();
        Vec3 inherit = new Vec3(mv.x, mc.player.onGround() ? 0.0 : mv.y, mv.z);

        Vec3[] tpath = buildTargetPath(mc, target, lagTicks(mc));
        double[] sol = solve(start, speed, inherit, tpath);
        if (sol == null) return false;

        applyRotation(mc, (float) sol[0], (float) sol[1]);
        return true;
    }

    private Entity pickTarget(Minecraft mc) {
        Entity best = null;
        double bestDist = range;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive()) continue;
            if (!isValidTarget(e)) continue;
            double d = mc.player.distanceTo(e);
            if (d > bestDist) continue;
            if (needLineOfSight && !mc.player.hasLineOfSight(e)) continue;
            bestDist = d;
            best = e;
        }
        return best;
    }

    private int lagTicks(Minecraft mc) {
        double ticks = INTERP_TICKS;
        if (pingComp && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ticks += Math.min(10.0, info.getLatency() / 50.0);
        }
        return (int) Math.round(ticks);
    }

    // ------------------------------------------------- GEGNER-VORHERSAGE

    private void updateHistory(Minecraft mc) {
        Set<Integer> seen = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive()) continue;
            if (!isValidTarget(e)) continue;
            if (e.distanceTo(mc.player) > range + 20.0) continue;

            seen.add(e.getId());
            ArrayDeque<Vec3> h = history.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
            Vec3 pos = e.position();
            if (!h.isEmpty() && h.peekLast().distanceToSqr(pos) > 16.0) h.clear(); // Teleport
            h.addLast(pos);
            while (h.size() > HISTORY_SIZE) h.pollFirst();
        }
        history.keySet().retainAll(seen);
    }

    /** Geschwindigkeit in Blöcken pro Tick, gemittelt über die Historie. */
    private Vec3 velocityOf(Entity e) {
        ArrayDeque<Vec3> h = history.get(e.getId());
        if (!predict || h == null || h.size() < 2) return Vec3.ZERO;
        return h.peekLast().subtract(h.peekFirst()).scale(1.0 / (h.size() - 1));
    }

    /**
     * Pfad des Zielpunkts am Gegner. Index k = Position k Ticks nach dem Abschuss
     * (bereits um lagTicks nach vorne gerechnet). In der Luft mit Spielergravitation.
     */
    private Vec3[] buildTargetPath(Minecraft mc, Entity e, int lagTicks) {
        Vec3 vel = velocityOf(e);
        boolean airborne = predict && !e.onGround();
        double vy = airborne ? vel.y : 0.0;
        double groundY = airborne ? groundY(mc, e.position()) : e.getY();
        double bodyOffset = e.getBbHeight() * BODY_FACTOR;

        double x = e.getX(), y = e.getY(), z = e.getZ();
        Vec3[] path = new Vec3[MAX_TICKS + 2];
        for (int k = -lagTicks; k <= MAX_TICKS + 1; k++) {
            if (k >= 0) path[k] = new Vec3(x, y + bodyOffset, z);
            x += vel.x;
            z += vel.z;
            if (airborne) {
                y += vy;
                vy = (vy - 0.08) * 0.98;
                if (y < groundY) {
                    y = groundY;
                    vy = 0.0;
                }
            }
        }
        return path;
    }

    private static double groundY(Minecraft mc, Vec3 pos) {
        for (int dy = 0; dy < 40; dy++) {
            BlockPos bp = BlockPos.containing(pos.x, pos.y - 1.0E-3 - dy, pos.z);
            VoxelShape shape = mc.level.getBlockState(bp).getCollisionShape(mc.level, bp);
            if (!shape.isEmpty()) return bp.getY() + shape.max(Direction.Axis.Y);
        }
        return pos.y - 40.0;
    }

    private static Vec3 targetAt(Vec3[] path, double s) {
        int i = (int) Math.floor(s);
        return path[i].lerp(path[i + 1], s - i);
    }

    // ------------------------------------------------------------- SOLVER

    /**
     * Iteriert den Zielpunkt: Pfeilbahn simulieren, Punkt der größten Annäherung an den
     * (bewegten) Gegner suchen, Zielpunkt um den Fehler verschieben, wiederholen.
     * Rückgabe {yaw, pitch, miss} oder null, wenn kein Treffer möglich ist.
     */
    private double[] solve(Vec3 start, double speed, Vec3 inherit, Vec3[] tpath) {
        int n0 = Mth.clamp((int) (start.distanceTo(tpath[0]) / Math.max(speed, 0.5)), 0, MAX_TICKS);
        Vec3 aimPoint = tpath[n0];

        double bestMiss = Double.MAX_VALUE;
        double[] result = null;

        for (int it = 0; it < SOLVER_ITERATIONS; it++) {
            double[] ang = anglesTo(start, aimPoint);
            Vec3 vel = directionOf(ang[0], ang[1]).scale(speed).add(inherit);

            Vec3 p = start;
            double minDistSq = Double.MAX_VALUE;
            Vec3 minErr = Vec3.ZERO;

            for (int n = 1; n <= MAX_TICKS; n++) {
                Vec3 next = p.add(vel);
                vel = new Vec3(vel.x * 0.99, vel.y * 0.99 - 0.05, vel.z * 0.99);
                for (int sub = 1; sub <= 4; sub++) {
                    double f = sub / 4.0;
                    Vec3 arrowPos = p.lerp(next, f);
                    Vec3 err = targetAt(tpath, (n - 1) + f).subtract(arrowPos);
                    double d = err.lengthSqr();
                    if (d < minDistSq) {
                        minDistSq = d;
                        minErr = err;
                    }
                }
                p = next;
            }

            double miss = Math.sqrt(minDistSq);
            if (miss < bestMiss) {
                bestMiss = miss;
                result = new double[]{ang[0], ang[1], miss};
            }
            if (miss < 0.05) break;
            aimPoint = aimPoint.add(minErr);
        }

        return (result != null && bestMiss <= MAX_MISS) ? result : null;
    }

    private static double[] anglesTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.atan2(dz, dx) * (180.0D / Math.PI) - 90.0D;
        double pitch = -(Math.atan2(dy, horizontal) * (180.0D / Math.PI));
        return new double[]{yaw, Mth.clamp(pitch, -90.0D, 90.0D)};
    }

    private static Vec3 directionOf(double yaw, double pitch) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        return new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp);
    }

    // ----------------------------------------------------------- ROTATION

    private void applyRotation(Minecraft mc, float yaw, float pitch) {
        LocalPlayer p = mc.player;
        float s = (float) Math.max(1.0, smoothing);

        if (silent) {
            if (!spoofing) {
                vYaw = p.getYRot();
                vPitch = p.getXRot();
                spoofing = true;
            }
            // Immer additiv, damit der Yaw-Zahlenwert durchgehend bleibt (AimModulo360)
            vYaw += Mth.wrapDegrees(yaw - vYaw) / s;
            vPitch = Mth.clamp(vPitch + (pitch - vPitch) / s, -90.0F, 90.0F);
            Tracker.applySpoof(p, vYaw, vPitch);
        } else {
            float cy = p.getYRot();
            float cp = p.getXRot();
            p.setYRot(cy + Mth.wrapDegrees(yaw - cy) / s);
            p.setXRot(Mth.clamp(cp + (pitch - cp) / s, -90.0F, 90.0F));
        }
    }

    /** Nach dem Schuss sanft zur echten Blickrichtung zurückdrehen statt zu snappen. */
    private void returnToReal(Minecraft mc) {
        if (!spoofing) return;
        LocalPlayer p = mc.player;

        Tracker.applySpoof(p, vYaw, vPitch); // merkt sich die echte Rotation
        float dy = Mth.wrapDegrees(Tracker.getRealYaw() - vYaw);
        float dp = Tracker.getRealPitch() - vPitch;

        if (Math.abs(dy) < 0.5F && Math.abs(dp) < 0.5F) {
            spoofing = false;
            return;
        }
        float f = Math.max(1.0F / (float) Math.max(1.0, smoothing), 0.35F);
        vYaw += dy * f;
        vPitch = Mth.clamp(vPitch + dp * f, -90.0F, 90.0F);
        Tracker.applySpoof(p, vYaw, vPitch);
    }

    /** Wie in AimAssist: Gruppen-Filter des EntityFilterPickers direkt auswerten. */
    private boolean isValidTarget(Entity entity) {
        if (AntiBot.isBot(entity)) return false;
        if (entity == Minecraft.getInstance().player || !entity.isAlive()) return false;

        if (filter.isFilterEnabled("Players") && entity instanceof Player) return true;
        if (filter.isFilterEnabled("Hostiles") && entity instanceof Enemy) return true;
        if (filter.isFilterEnabled("Animals") && entity instanceof Animal) return true;
        if (filter.isFilterEnabled("NPCs") && entity instanceof Npc) return true;
        if (filter.isFilterEnabled("ArmorStands") && entity instanceof ArmorStand) return true;

        return false;
    }
}