package com.OsamaClient.newbridge.Hacks.Donut;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;

/**
 * RTPs every {@link #intervalSeconds} seconds into a random region (see {@link #REGIONS} - end/nether are
 * deliberately NOT in the list, as requested) and logs every attempt as one line of JSON in a file per
 * server under {@code <config>/newbridge/rtp-data/}. That file is the only state the module needs, so the
 * log survives client restarts automatically without any extra save/load logic.
 *
 * Success check: we remember position + dimension right before sending the command and compare them again
 * once the server should have answered (timeout {@link #VERIFY_TIMEOUT_MS}). A "successful" teleport is a
 * position change greater than {@link #MIN_TP_DISTANCE} blocks - normal movement/knockback in the meantime
 * stays below that. Runs on its own Fabric tick event rather than through a possible Module tick hook (I
 * couldn't find one anywhere in your codebase) - this way the module works independently of your Module
 * base class.
 *
 * TARGET MODE: in the map page (live server) you can mark a point + radius. Once sent, the module stops
 * rolling dice over all regions: it only RTPs into the one region that gives the best chance to land
 * inside the circle (see {@link RtpRegionGrid#overlaps}) and stops by itself as soon as a landing is
 * inside the radius. Clear the target on the page to go back to free exploring.
 *
 * ASSUMPTION, adjust to your mappings if needed: command is sent via {@code connection.sendCommand(String)}
 * (no leading slash). If that method is named differently on your mappings (e.g. {@code sendChatCommand}),
 * just swap that one line in {@link #sendRtp}.
 */
public class RtpFinder extends Module {

    public static RtpFinder instance;

    /** argToken = exact server command argument. Adjust the tokens if your server expects e.g. a space
     *  instead of an underscore (not 100% readable from the tab-complete screenshot). */
    public record Region(String argToken, String displayName, int color) {}

    public static final List<Region> REGIONS = List.of(
            new Region("asia", "Asia", 0xFFFF6B6B),
            new Region("east", "East", 0xFF4ECDC4),
            new Region("eu_central", "EU Central", 0xFFFFD166),
            new Region("eu_west", "EU West", 0xFF06D6A0),
            new Region("oceania", "Oceania", 0xFF118AB2),
            new Region("west", "West", 0xFF9B5DE5)
            // deliberately NO "end" / "nether" - see class javadoc
    );

    private static final long VERIFY_TIMEOUT_MS = 4000;
    private static final double MIN_TP_DISTANCE = 16.0;

    public final Slider intervalSeconds = new Slider("Interval", 2.0, 30.0, 5.0, 0.5, null)
            .withDescription("Seconds between two /rtp attempts");
    public final ToggleButton logFailedAttempts = new ToggleButton("Log Failed Attempts", true, null)
            .withDescription("Also log failed/timed-out attempts in addition to successes (useful for tuning the timing)");
    public final ToggleButton exportNow = new ToggleButton("Export HTML", false, val -> {
        if (val) requestExport = true;
    }).withDescription("Click once: exports a static snapshot of the log as an HTML file");
    public final ToggleButton liveServer = new ToggleButton("Live Server", false, val -> {
        if (val) RtpLiveServer.start(LIVE_PORT); else RtpLiveServer.stop();
    }).withDescription("Serves the map at http://localhost:" + LIVE_PORT + "/ - updates itself every few seconds, no re-export needed");

    private static final int LIVE_PORT = 8934;

    // ---- target mode (set from the live map page via RtpLiveServer, read on the client thread) ----

    /** Area marked in the browser. {@code region} = argToken of the best region, {@code hitChance} = chance per RTP. */
    public record Target(int x, int z, int radius, String region, double hitChance) {}

    private static volatile Target target;
    private static volatile boolean targetReached;
    private static volatile int targetAttempts;
    private static volatile double lastLandX, lastLandZ;
    private static volatile double lastTargetDist = -1;

    public static Target setTarget(int x, int z, int radius) {
        x = Math.max(-RtpRegionGrid.HALF, Math.min(RtpRegionGrid.HALF, x));
        z = Math.max(-RtpRegionGrid.HALF, Math.min(RtpRegionGrid.HALF, z));
        List<RtpRegionGrid.Overlap> overlaps = RtpRegionGrid.overlaps(x, z, radius);
        String region = overlaps.isEmpty() ? RtpRegionGrid.regionAt(x, z).token() : overlaps.get(0).region().token();
        double chance = overlaps.isEmpty() ? 0 : overlaps.get(0).hitChance();

        targetReached = false;
        targetAttempts = 0;
        lastTargetDist = -1;
        Target t = new Target(x, z, radius, region, chance);
        target = t;
        return t;
    }

    public static void clearTarget() {
        target = null;
        targetReached = false;
        targetAttempts = 0;
        lastTargetDist = -1;
    }

    /** JSON for the page's /status poll. */
    public static String statusJson() {
        Target t = target;
        RtpFinder inst = instance;
        boolean en = inst != null && inst.enabled;
        if (t == null) return "{\"hasTarget\":false,\"enabled\":" + en + "}";
        double dist = lastTargetDist;
        return "{\"hasTarget\":true,\"enabled\":" + en
                + ",\"x\":" + t.x() + ",\"z\":" + t.z() + ",\"r\":" + t.radius()
                + ",\"region\":\"" + t.region() + "\""
                + ",\"expectedAttempts\":" + (t.hitChance() > 0 ? Math.round(1.0 / t.hitChance()) : -1)
                + ",\"attempts\":" + targetAttempts
                + ",\"reached\":" + targetReached
                + ",\"lastX\":" + Math.round(lastLandX) + ",\"lastZ\":" + Math.round(lastLandZ)
                + ",\"lastDist\":" + (dist < 0 ? -1 : Math.round(dist * 10) / 10.0) + "}";
    }

    private enum State { IDLE, AWAITING_VERIFY }
    private State state = State.IDLE;
    private long lastAttemptMillis;
    private long sentMillis;
    private double preX, preY, preZ;
    private String preDimension;
    private Region pendingRegion;
    private volatile boolean requestExport;

    private final Random rng = new Random();
    private static boolean tickRegistered;

    public RtpFinder() {
        super("RtpFinder", "Periodically RTPs through every region and logs where it's quiet", Category.Donut);
        instance = this;
        settings.add(intervalSeconds);
        settings.add(logFailedAttempts);
        settings.add(exportNow);
        settings.add(liveServer);
        registerTick();
    }

    private void registerTick() {
        if (tickRegistered) return;
        tickRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (requestExport) {
                requestExport = false;
                exportNow.enabled = false; // fold the "button" back down
                doExport(mc);
            }
            if (enabled) tick(mc);
        });
    }

    private void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null || mc.getCurrentServer() == null) return;
        long now = System.currentTimeMillis();

        if (state == State.AWAITING_VERIFY) {
            double dist = distance(player.getX(), player.getY(), player.getZ(), preX, preY, preZ);
            boolean moved = dist > MIN_TP_DISTANCE;
            boolean timedOut = now - sentMillis > VERIFY_TIMEOUT_MS;
            if (moved) {
                logAttempt(mc, pendingRegion, true, player.getX(), player.getY(), player.getZ());
                state = State.IDLE;
                lastAttemptMillis = now;
                checkTarget(mc, player.getX(), player.getZ());
            } else if (timedOut) {
                if (logFailedAttempts.enabled) logAttempt(mc, pendingRegion, false, preX, preY, preZ);
                state = State.IDLE;
                lastAttemptMillis = now;
            }
            return; // don't send anything new while we're waiting for confirmation
        }

        if (now - lastAttemptMillis >= (long) (intervalSeconds.getValue() * 1000)) {
            sendRtp(mc, player);
        }
    }

    private void sendRtp(Minecraft mc, Player player) {
        Region region = pickRegion();
        Target t = target;
        if (t != null && !targetReached) targetAttempts++;
        preX = player.getX();
        preY = player.getY();
        preZ = player.getZ();
        preDimension = mc.level.dimension().identifier().toString();
        sentMillis = System.currentTimeMillis();
        pendingRegion = region;
        state = State.AWAITING_VERIFY;

        // ASSUMPTION: sendCommand(String) without leading slash - see class javadoc
        mc.player.connection.sendCommand("rtp " + region.argToken());
    }

    /** With an active target: only the best region for that area, otherwise a random one. */
    private Region pickRegion() {
        Target t = target;
        if (t != null && !targetReached) {
            for (Region r : REGIONS) {
                if (r.argToken().equals(t.region())) return r;
            }
        }
        return REGIONS.get(rng.nextInt(REGIONS.size()));
    }

    /** After a confirmed teleport: are we inside the marked area? If so, stop. */
    private void checkTarget(Minecraft mc, double x, double z) {
        Target t = target;
        if (t == null || targetReached) return;

        lastLandX = x;
        lastLandZ = z;
        double dist = Math.hypot(x - t.x(), z - t.z());
        lastTargetDist = dist;

        if (dist <= t.radius()) {
            targetReached = true;
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("\u00a7a[RtpFinder] Target area reached after "
                        + targetAttempts + " attempts at " + Math.round(x) + ", " + Math.round(z)
                        + " (distance " + Math.round(dist) + ")"));
            }
            if (enabled) toggle(); // stop rolling - we're where we wanted to be
        }
    }

    private void logAttempt(Minecraft mc, Region region, boolean success, double x, double y, double z) {
        try {
            BlockPos pos = BlockPos.containing(x, y, z);
            String biome = mc.level.getBiome(pos).unwrapKey()
                    .map(k -> k.identifier().toString()).orElse("unknown");
            String dim = mc.level.dimension().identifier().toString();

            String line = "{"
                    + "\"ts\":" + System.currentTimeMillis() + ","
                    + "\"region\":\"" + region.argToken() + "\","
                    + "\"success\":" + success + ","
                    + "\"x\":" + Math.round(x) + ","
                    + "\"y\":" + Math.round(y) + ","
                    + "\"z\":" + Math.round(z) + ","
                    + "\"dimension\":\"" + dim + "\","
                    + "\"biome\":\"" + biome + "\""
                    + "}";

            Path file = logFile(mc);
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException e) {
            // a lost log entry should never crash the module/client
        }
    }

    private static Path logFile(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        String safeName = (server != null ? server.ip : "unknown-server")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("newbridge").resolve("rtp-data").resolve(safeName + ".jsonl");
        RtpLiveServer.logFile = file; // live server always serves whatever we're currently logging to
        return file;
    }

    private void doExport(Minecraft mc) {
        try {
            Path in = logFile(mc);
            Path out = in.resolveSibling(in.getFileName().toString().replace(".jsonl", "") + "_map.html");
            RtpMapExporter.export(in, out);
        } catch (IOException e) {
            // if the export fails, the previous HTML file (if any) is simply left in place
        }
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public void toggle() {
        super.toggle();
        if (!enabled) state = State.IDLE; // clean restart on the next enable
    }
}