package com.OsamaClient.newbridge.Hacks.Misc.stash;

import com.OsamaClient.newbridge.Hacks.Client.Profiles;
import com.OsamaClient.newbridge.Hacks.Misc.WaypointStore;
import com.OsamaClient.newbridge.Hacks.Misc.Waypoints;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.window.WindowManager;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * StashLogger: merkt sich Container-Ansammlungen (Kisten, Fässer, Shulker, Hopper ...), an denen
 * du vorbeikommst – mit Koordinaten, Inhalt-Zusammenfassung und Zeit. Liste über das Dock / ".stash".
 */
public class StashLogger extends Module {

    public static StashLogger INSTANCE;
    private static final long RESCAN_MS = 20_000;

    public double minWeight = 10, perTick = 8;
    public boolean notify = true, autoWaypoint = false, sound = true;
    public final StashStore store;
    private final Long2LongOpenHashMap scanned = new Long2LongOpenHashMap();
    private Object lastLevel;
    /** Für Tests: neu gefundene Stashes seit dem Einschalten. */
    public int found;

    public StashLogger() {
        this(FabricLoader.getInstance().getConfigDir().resolve("0smasclient-stashes.json"));
    }

    public StashLogger(java.nio.file.Path file) {
        super("StashLogger", "Logs container stashes (chests, shulkers, barrels ...) you pass by", Category.WORLD);
        INSTANCE = this;
        store = new StashStore(file);
        ToggleButton[] open = new ToggleButton[1];
        open[0] = new ToggleButton("Open List", false, v -> {
            if (v) {
                open[0].enabled = false;
                WindowManager.INSTANCE.open(new StashesWindow());
            }
        }).withDescription("Shows all found stashes (also: .stash)");
        settings.add(open[0]);
        settings.add(new Slider("Min Containers", 4, 60, minWeight, 1, v -> minWeight = v)
                .withDescription("How many containers count as a stash (shulker boxes count x3)."));
        settings.add(new Slider("Chunks/Tick", 1, 32, perTick, 1, v -> perTick = v)
                .withDescription("How many chunks are scanned per tick."));
        settings.add(new ToggleButton("Notify", notify, v -> notify = v).withDescription("Chat message for every new stash."));
        settings.add(new ToggleButton("Sound", sound, v -> sound = v).withDescription("Plays a sound for every new stash."));
        settings.add(new ToggleButton("Auto Waypoint", autoWaypoint, v -> autoWaypoint = v)
                .withDescription("Creates a waypoint for every new stash."));
    }

    public String key(Minecraft mc) { return Profiles.serverKey(mc); }

    @Override
    public void onEnable() {
        scanned.clear();
        found = 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            scanned.clear();
            lastLevel = mc.level;
        }
        int rd = Math.max(2, mc.options.renderDistance().get());
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        long now = System.currentTimeMillis();
        int budget = (int) perTick;
        for (int r = 0; r <= rd && budget > 0; r++) {
            for (int dx = -r; dx <= r && budget > 0; dx++) {
                for (int dz = -r; dz <= r && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int cx = pcx + dx, cz = pcz + dz;
                    long k = ChunkPos.pack(cx, cz);
                    if (now - scanned.getOrDefault(k, 0L) < RESCAN_MS) continue;
                    LevelChunk c = mc.level.getChunkSource().getChunkNow(cx, cz);
                    if (c == null) continue;
                    scanned.put(k, now);
                    budget--;
                    if (c.getBlockEntitiesPos().isEmpty()) continue;
                    scanAround(mc, cx, cz, now);
                }
            }
        }
    }

    private void scanAround(Minecraft mc, int cx, int cz, long now) {
        List<StashDetector.Found> found = new ArrayList<>();
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                LevelChunk c = mc.level.getChunkSource().getChunkNow(cx + ox, cz + oz);
                if (c == null) continue;
                for (BlockPos p : c.getBlockEntitiesPos()) {
                    found.add(new StashDetector.Found(p, c.getBlockState(p).getBlock()));
                }
            }
        }
        StashStore.Stash s = StashDetector.evaluate(found, Waypoints.dim(mc), (int) minWeight);
        if (s == null) return;
        s.firstSeen = s.lastSeen = now;
        if (store.record(key(mc), s)) onNewStash(mc, s);
    }

    private void onNewStash(Minecraft mc, StashStore.Stash s) {
        this.found++;
        if (notify && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§d[StashLogger] Stash at §f" + s.x + " " + s.y + " " + s.z
                    + "§d: " + s.summary() + "  §7(.stash)"));
        }
        if (sound) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f));
        if (autoWaypoint && Waypoints.INSTANCE != null) addWaypoint(mc, s);
    }

    public void addWaypoint(Minecraft mc, StashStore.Stash s) {
        WaypointStore ws = Waypoints.INSTANCE.store;
        String key = Waypoints.INSTANCE.serverKey(mc);
        String name = ws.uniqueName(key, "Stash " + s.weight());
        ws.put(key, new WaypointStore.Waypoint(name, s.x, s.y, s.z, s.dimension, 0xFFA78BFA, false));
    }

    public void handleCommand(String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            store.clear(key(Minecraft.getInstance()));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[StashLogger] Cleared."));
            return;
        }
        WindowManager.INSTANCE.openLater(new StashesWindow());
    }
}
