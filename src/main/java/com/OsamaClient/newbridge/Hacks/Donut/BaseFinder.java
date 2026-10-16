package com.OsamaClient.newbridge.Hacks.Donut;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.GoalNear;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.GoalYLevel;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.PathRenderer;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.PathNode;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Sucht automatisch nach Spieler-Basen anhand typischer Blöcke
 * (Spawner, Barrel, Hopper, Chest, Shulker Box, Ender Chest), meidet dabei aber
 * natürlich generierte Strukturen wie Trial Chambers, und prüft ob eine
 * gefundene Base bereits gegriefed wurde (z.B. viel Obsidian = Crystal-PvP-Reste).
 *
 * Ablauf: SCANNING -> (DESCENDING, falls nichts in der Nähe & falsche Tiefe)
 * -> TRAVELING (via Navigator/GoalBlock) -> INSPECTING (Kamera dreht smooth
 * zur Base, danach Obsidian-Check) -> stoppt sich selbst bei Erfolg.
 */
public class BaseFinder extends Module {

    private static final Set<Block> TARGET_BLOCKS = createTargetBlocks();
    private static final Set<Block> NATURAL_STRUCTURE_BLOCKS = createNaturalStructureBlocks();

    private static Set<Block> createTargetBlocks() {
        Set<Block> blocks = new HashSet<>(Set.<Block>of(
                Blocks.SPAWNER, Blocks.BARREL, Blocks.HOPPER,
                Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.SHULKER_BOX
        ));
        blocks.addAll(Blocks.DYED_SHULKER_BOX.asList());
        return Collections.unmodifiableSet(blocks);
    }

    private static Set<Block> createNaturalStructureBlocks() {
        Set<Block> blocks = new HashSet<>(Set.<Block>of(
                Blocks.TRIAL_SPAWNER,
                Blocks.VAULT,
                Blocks.REINFORCED_DEEPSLATE
        ));

        // Kupfer-Kollektionen (WeatheringCopperCollection)
        blocks.addAll(Blocks.CHISELED_COPPER.asList());
        blocks.addAll(Blocks.COPPER_GRATE.asList());
        blocks.addAll(Blocks.COPPER_BULB.asList());
        blocks.addAll(Blocks.COPPER_DOOR.asList());
        blocks.addAll(Blocks.COPPER_TRAPDOOR.asList());
        blocks.addAll(Blocks.COPPER_CHEST.asList());

        return Collections.unmodifiableSet(blocks);
    }

    private static final int CLUSTER_RADIUS_SQ   = 6 * 6;  // wie weit Kandidaten auseinander liegen dürfen = 1 Site
    private static final int NATURAL_CHECK_RADIUS = 8;     // Umkreis-Check auf Trial-Chamber-Blöcke
    private static final int GRIEF_CHECK_RADIUS   = 10;    // Umkreis-Check auf Obsidian
    private static final int OBSIDIAN_GRIEF_THRESHOLD = 6; // ab so vielen Obsidian-Blöcken: "gegriefed"
    private static final int MIN_CONTAINERS_FOR_BASE  = 2; // ohne Hopper/Spawner: mind. 2 Storage-Blöcke nötig
    private static final double MAX_TRAVEL_DIST_SQ = 8.0 * 8.0;
    private static final int ARRIVE_RANGE = 3;
    private static final int TARGET_Y_TOLERANCE = 8;       // ab wann "nah genug" an der Grab-Tiefe

    // 30x30-Flächen-Check: mind. 20 Target-Blöcke in der Fläche = eindeutig eine Base
    // (Lagerraum/Farm), unabhängig von der normalen Cluster/Hopper/Spawner-Regel.
    private static final int DENSITY_AREA_HALF   = 15; // 15+15+1 = 30
    private static final int DENSITY_VERTICAL    = 10;
    private static final int DENSITY_MIN_COUNT   = 20;

    private final Slider scanRadius = new Slider("Scan Radius", 16.0, 64.0, 32.0, val -> {});
    private final Slider targetY    = new Slider("Target Mining Y", -64.0, 0.0, -50.0, val -> {});
    private final Slider aimFov     = new Slider("Aim FOV", 30.0, 180.0, 180.0, val -> {});
    private final Slider aimSmooth  = new Slider("Aim Smoothness", 0.05, 1.0, 0.35, val -> {});
    private int pathColor = 0xFFC084FC;
    private int targetColor = 0xFFFF6B6B;
    private final PathRenderer pathRenderer = new PathRenderer();

    private enum Phase { SCANNING, DESCENDING, TRAVELING, INSPECTING }

    private Phase phase = Phase.SCANNING;
    private int tickDelay = 0;
    private int inspectTicks = 0;
    private Site currentSite;
    private final Set<Long> blacklist = new HashSet<>(); // Zentren bereits geprüfter/gegriefter Basen
    private final SmoothAim aim = new SmoothAim();

    public BaseFinder() {
        super("BaseFinder", "Searches for player bases and checks whether they are already griefed.", Category.WORLD);
        this.settings.add(scanRadius.withDescription("Radius around the player to scan for bases."));
        this.settings.add(targetY.withDescription("Y level the bot digs/travels to while searching, if nothing is found nearby."));
        this.settings.add(aimFov.withDescription("Yaw window within which the camera is allowed to turn."));
        this.settings.add(aimSmooth.withDescription("How fast/smooth the camera turns toward a found base."));
        this.settings.add(new ColorPicker("Path Color", pathColor, color -> pathColor = color));
        this.settings.add(new ColorPicker("Target Color", targetColor, color -> targetColor = color));
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onEnable() {
        phase = Phase.SCANNING;
        currentSite = null;
        blacklist.clear();
        tickDelay = 0;
        inspectTicks = 0;
    }

    @Override
    public void onDisable() {
        Navigator.INSTANCE.cancel(Minecraft.getInstance(), Navigator.OWNER_BASE_FINDER);
        phase = Phase.SCANNING;
        currentSite = null;
        inspectTicks = 0;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;

        switch (phase) {
            case SCANNING -> tickScan(client);
            case DESCENDING -> tickDescend(client);
            case TRAVELING -> tickTravel(client);
            case INSPECTING -> tickInspect(client);
        }
    }

    // ------------------------------------------------------------- SCANNING

    private void tickScan(Minecraft client) {
        if (tickDelay > 0) { tickDelay--; return; }

        List<Candidate> candidates = scanForCandidates(client, (int) scanRadius.getValue());
        List<Site> sites = clusterCandidates(candidates);

        BlockPos playerPos = client.player.blockPosition();
        Site best = null;
        double bestDist = Double.MAX_VALUE;

        for (Site site : sites) {
            if (blacklist.contains(hash(site.center))) continue;
            if (!qualifies(client, site)) continue;
            if (isNearNaturalStructure(client, site.center)) continue;

            double dist = site.center.distSqr(playerPos);
            if (dist < bestDist) { bestDist = dist; best = site; }
        }

        if (best == null) {
            int wantY = (int) targetY.getValue();
            if (Math.abs(playerPos.getY() - wantY) > TARGET_Y_TOLERANCE) {
                client.player.sendSystemMessage(Component.literal(
                        "§e[BaseFinder] Nothing nearby, digging to Y=" + wantY + "..."));
                Navigator.INSTANCE.goTo(client, Navigator.OWNER_BASE_FINDER,
                        Navigator.PRIORITY_BASE_FINDER, new GoalYLevel(wantY));
                phase = Phase.DESCENDING;
                return;
            }
            tickDelay = 60; // schon auf Ziel-Tiefe, nichts gefunden -> in 3s erneut scannen
            return;
        }

        currentSite = best;
        client.player.sendSystemMessage(Component.literal(
                "§e[BaseFinder] Possible base at " + best.center.toShortString() + ", traveling there..."));
        // GoalNear statt GoalBlock: das Zentrum liegt oft IN einer Kiste. Mit GoalBlock hätte der
        // Pathfinder versucht, genau dort zu stehen (Container abbauen / unerreichbar).
        Navigator.INSTANCE.goTo(client, Navigator.OWNER_BASE_FINDER,
                Navigator.PRIORITY_BASE_FINDER, new GoalNear(best.center, ARRIVE_RANGE));
        phase = Phase.TRAVELING;
    }

    private List<Candidate> scanForCandidates(Minecraft client, int radius) {
        List<Candidate> found = new ArrayList<>();
        BlockPos playerPos = client.player.blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (pos.getY() < client.level.getMinY() || pos.getY() >= client.level.getMaxY()) continue;

                    Block block = client.level.getBlockState(pos).getBlock();
                    if (TARGET_BLOCKS.contains(block) || block instanceof ShulkerBoxBlock) {
                        found.add(new Candidate(pos.immutable(), block));
                    }
                }
            }
        }
        return found;
    }

    /** Gruppiert nahe beieinanderliegende Kandidaten zu "Sites" (simple BFS über Distanz). */
    private List<Site> clusterCandidates(List<Candidate> candidates) {
        List<Site> sites = new ArrayList<>();
        boolean[] used = new boolean[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;
            List<Candidate> cluster = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(i);
            used[i] = true;

            while (!queue.isEmpty()) {
                int idx = queue.poll();
                Candidate c = candidates.get(idx);
                cluster.add(c);
                for (int j = 0; j < candidates.size(); j++) {
                    if (used[j]) continue;
                    if (c.pos.distSqr(candidates.get(j).pos) <= CLUSTER_RADIUS_SQ) {
                        used[j] = true;
                        queue.add(j);
                    }
                }
            }
            sites.add(new Site(cluster));
        }
        return sites;
    }

    /** Filtert einzelne Loot-Kisten (Shipwreck, Tempel, Iglu, ...) so gut es geht heraus. */
    private boolean qualifies(Minecraft client, Site site) {
        if (site.hasHopper()) return true;                                  // Hopper generiert NIE natürlich
        if (site.hasSpawner() && site.storageCount() >= 1) return true;     // Mob-Farm-Setup
        if (site.storageCount() >= MIN_CONTAINERS_FOR_BASE) return true;
        return hasHighDensity(client, site.center);                        // >=20 Target-Blöcke auf 30x30
    }

    /** Mind. DENSITY_MIN_COUNT Target-Blöcke innerhalb einer 30x30-Fläche (mit etwas Höhe). */
    private boolean hasHighDensity(Minecraft client, BlockPos center) {
        int count = 0;
        for (int x = -DENSITY_AREA_HALF; x <= DENSITY_AREA_HALF; x++) {
            for (int y = -DENSITY_VERTICAL; y <= DENSITY_VERTICAL; y++) {
                for (int z = -DENSITY_AREA_HALF; z <= DENSITY_AREA_HALF; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (p.getY() < client.level.getMinY() || p.getY() >= client.level.getMaxY()) continue;

                    Block b = client.level.getBlockState(p).getBlock();
                    if (TARGET_BLOCKS.contains(b) || b instanceof ShulkerBoxBlock) {
                        count++;
                        if (count >= DENSITY_MIN_COUNT) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNearNaturalStructure(Minecraft client, BlockPos center) {
        int r = NATURAL_CHECK_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (p.getY() < client.level.getMinY() || p.getY() >= client.level.getMaxY()) continue;
                    if (NATURAL_STRUCTURE_BLOCKS.contains(client.level.getBlockState(p).getBlock())) return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------ DESCENDING

    private void tickDescend(Minecraft client) {
        if (Navigator.INSTANCE.isNavigating(Navigator.OWNER_BASE_FINDER)) return;
        // Zieltiefe erreicht (oder Navigator hat aufgegeben) -> sofort neu scannen
        phase = Phase.SCANNING;
        tickDelay = 0;
    }

    // ------------------------------------------------------------ TRAVELING

    private void tickTravel(Minecraft client) {
        if (currentSite == null) { phase = Phase.SCANNING; return; }

        if (Navigator.INSTANCE.isNavigating(Navigator.OWNER_BASE_FINDER)) return;

        // Navigator fertig (angekommen ODER aufgegeben, z.B. kein Pfad gefunden)
        BlockPos playerPos = client.player.blockPosition();
        if (playerPos.distSqr(currentSite.center) > MAX_TRAVEL_DIST_SQ) {
            client.player.sendSystemMessage(Component.literal("§c[BaseFinder] Could not reach base, skipping."));
            blacklistCurrent();
            phase = Phase.SCANNING;
            tickDelay = 20;
            return;
        }

        client.player.sendSystemMessage(Component.literal(
                "§a[BaseFinder] Reached base at " + currentSite.center.toShortString() + ", inspecting..."));
        // Vorher wurde hier direkt gestoppt -> die Inspect-Phase (Grief-Check) lief nie.
        phase = Phase.INSPECTING;
        inspectTicks = 0;
    }

    // ----------------------------------------------------------- INSPECTING

    private void tickInspect(Minecraft client) {
        if (currentSite == null) { phase = Phase.SCANNING; return; }
        if (Navigator.INSTANCE.isNavigating()) return;

        inspectTicks++;
        Vec3 lookTarget = Vec3.atCenterOf(currentSite.center).add(0.0, 0.5, 0.0);
        boolean lookedAt = aim.tick(client, client.player, lookTarget, (float) aimFov.getValue(), (float) aimSmooth.getValue());

        // Entweder fertig geschaut, oder nach 2s (40 Ticks) trotzdem weitermachen (falls FOV es verhindert)
        if (!lookedAt && inspectTicks < 40) return;

        int obsidianCount = countObsidian(client, currentSite.center, GRIEF_CHECK_RADIUS);
        if (obsidianCount >= OBSIDIAN_GRIEF_THRESHOLD) {
            client.player.sendSystemMessage(Component.literal(
                    "§c[BaseFinder] Base at " + currentSite.center.toShortString()
                            + " looks griefed (obsidian: " + obsidianCount + "), continuing search."));
            blacklistCurrent();
            phase = Phase.SCANNING;
            tickDelay = 20;
            return;
        }

        client.player.sendSystemMessage(Component.literal(
                "§a[BaseFinder] Found an ungriefed base at " + currentSite.center.toShortString() + "! Stopping."));
        this.enabled = false;
        onDisable();
    }

    private int countObsidian(Minecraft client, BlockPos center, int r) {
        int count = 0;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (p.getY() < client.level.getMinY() || p.getY() >= client.level.getMaxY()) continue;
                    if (client.level.getBlockState(p).is(Blocks.OBSIDIAN)) count++;
                }
            }
        }
        return count;
    }

    private void blacklistCurrent() {
        if (currentSite != null) blacklist.add(hash(currentSite.center));
        currentSite = null;
    }

    private static long hash(BlockPos pos) {
        return PathNode.longHash(pos.getX(), pos.getY(), pos.getZ());
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (enabled && currentSite != null) {
            pathRenderer.render(event, Navigator.INSTANCE.getRemainingPath(Navigator.OWNER_BASE_FINDER),
                    currentSite.center, pathColor, targetColor);
        }
    }

    // ------------------------------------------------------------- HELFER

    private record Candidate(BlockPos pos, Block block) {}

    private static final class Site {
        final List<Candidate> candidates;
        final BlockPos center;

        Site(List<Candidate> candidates) {
            this.candidates = candidates;
            long sx = 0, sy = 0, sz = 0;
            for (Candidate c : candidates) {
                sx += c.pos().getX();
                sy += c.pos().getY();
                sz += c.pos().getZ();
            }
            int n = candidates.size();
            this.center = new BlockPos((int) (sx / n), (int) (sy / n), (int) (sz / n));
        }

        boolean hasHopper() {
            return candidates.stream().anyMatch(c -> c.block() == Blocks.HOPPER);
        }

        boolean hasSpawner() {
            return candidates.stream().anyMatch(c -> c.block() == Blocks.SPAWNER);
        }

        long storageCount() {
            return candidates.stream().filter(c ->
                    c.block() == Blocks.CHEST || c.block() == Blocks.TRAPPED_CHEST
                            || c.block() == Blocks.BARREL || c.block() == Blocks.ENDER_CHEST
                            || c.block() instanceof ShulkerBoxBlock
            ).count();
        }
    }

    /**
     * Smooth-Look, adaptiert aus der applyAim()-Logik: gleiche FOV-Begrenzung,
     * sensitivity-basiertes Quantisieren (gcd) und Jitter, nur dass hier ein
     * fixer Vec3-Punkt statt ein LivingEntity angevisiert wird. Gibt true zurück,
     * sobald die Blickrichtung nah genug dran ist.
     */
    private static final class SmoothAim {
        private final Random random = new Random();

        boolean tick(Minecraft client, LocalPlayer player, Vec3 target, float fov, float smoothness) {
            double dx = target.x - player.getX();
            double dz = target.z - player.getZ();
            double dy = target.y - (player.getY() + player.getEyeHeight());
            double distanceXZ = Math.sqrt(dx * dx + dz * dz);

            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, distanceXZ)));

            float yawDiff = Mth.wrapDegrees(targetYaw - player.getYRot());
            float pitchDiff = Mth.wrapDegrees(targetPitch - player.getXRot());

            if (Math.abs(yawDiff) < 1.0f && Math.abs(pitchDiff) < 1.0f) {
                return true;
            }

            if (Math.abs(yawDiff) <= fov) {
                double sensitivity = client.options.sensitivity().get();
                float f = (float) (sensitivity * 0.6F + 0.2F);
                float gcd = f * f * f * 1.2F;

                float speedFactor = Math.max(0.1f, Math.min(smoothness, Math.abs(yawDiff) / 30f));
                float jitter = (random.nextFloat() - 0.5f) * 0.12f;
                float pct = speedFactor + jitter;

                float moveYaw = yawDiff * pct;
                float movePitch = pitchDiff * pct;

                float roundedYaw = Math.round(moveYaw / gcd) * gcd;
                float roundedPitch = Math.round(movePitch / gcd) * gcd;

                player.setYRot(player.getYRot() + roundedYaw);
                player.setXRot(Mth.clamp(player.getXRot() + roundedPitch, -90, 90));
            }
            return false;
        }
    }
}
