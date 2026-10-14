package com.OsamaClient.newbridge.Hacks.Misc.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gefundene Stashes pro Server, als JSON. Nahe Funde (< MERGE_DIST) werden zusammengeführt. */
public final class StashStore {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/Stashes");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MERGE_DIST = 24;

    public static final class Stash {
        public int x, y, z;
        public String dimension;
        public int chests, barrels, shulkers, hoppers, other;
        public long firstSeen, lastSeen;

        public int total() { return chests + barrels + shulkers + hoppers + other; }

        /** Gewichteter Wert: Shulker zählen dreifach. */
        public int weight() { return chests + barrels + hoppers + other + shulkers * 3; }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (chests > 0) sb.append(chests).append(" chest ");
            if (barrels > 0) sb.append(barrels).append(" barrel ");
            if (shulkers > 0) sb.append(shulkers).append(" shulker ");
            if (hoppers > 0) sb.append(hoppers).append(" hopper ");
            if (other > 0) sb.append(other).append(" other ");
            return sb.toString().trim();
        }
    }

    private final Path file;
    private Map<String, List<Stash>> data = new LinkedHashMap<>();
    private boolean loaded;

    public StashStore(Path file) { this.file = file; }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            Type t = new TypeToken<LinkedHashMap<String, List<Stash>>>() {}.getType();
            Map<String, List<Stash>> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), t);
            if (read != null) {
                read.values().removeIf(l -> l == null);
                read.values().forEach(l -> l.removeIf(s -> s == null || s.dimension == null));
                data = read;
            }
        } catch (Exception e) {
            LOG.error("Could not read {}", file, e);
        }
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.error("Could not save {}", file, e);
        }
    }

    public List<Stash> list(String key) {
        ensureLoaded();
        if (key == null) return new ArrayList<>();
        return data.computeIfAbsent(key, k -> new ArrayList<>());
    }

    /** Nach Wert absteigend sortierte Kopie. */
    public List<Stash> sorted(String key) {
        List<Stash> out = new ArrayList<>(list(key));
        out.sort(Comparator.comparingInt(Stash::weight).reversed());
        return out;
    }

    /**
     * Fügt einen Fund ein oder aktualisiert einen nahen. @return true, wenn er NEU ist
     * (für die Benachrichtigung).
     */
    public boolean record(String key, Stash s) {
        for (Stash o : list(key)) {
            if (!o.dimension.equals(s.dimension)) continue;
            long dx = o.x - s.x, dz = o.z - s.z;
            if (dx * dx + dz * dz <= (long) MERGE_DIST * MERGE_DIST) {
                if (s.weight() > o.weight()) {          // größere Sicht übernehmen
                    o.x = s.x; o.y = s.y; o.z = s.z;
                    o.chests = s.chests; o.barrels = s.barrels; o.shulkers = s.shulkers;
                    o.hoppers = s.hoppers; o.other = s.other;
                }
                o.lastSeen = s.lastSeen;
                save();
                return false;
            }
        }
        list(key).add(s);
        save();
        return true;
    }

    public boolean remove(String key, Stash s) {
        boolean r = list(key).remove(s);
        if (r) save();
        return r;
    }

    public void clear(String key) {
        list(key).clear();
        save();
    }
}
