package com.OsamaClient.newbridge.Hacks.Misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

/**
 * Waypoint-Daten (pro Server-Schlüssel), Laden/Speichern als JSON. Ohne Minecraft-Abhängigkeiten,
 * damit es headless getestet werden kann.
 */
public final class WaypointStore {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/Waypoints");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String DEATH_NAME = "Death";
    public static final int MAX_NAME = 32;

    public static final class Waypoint {
        public String name;
        public int x, y, z;
        public String dimension;
        public int color;
        public boolean death;
        /** Ausgeblendet: nicht in der Welt zeichnen (bleibt in der Liste). */
        public boolean hidden;
        public long created;

        public Waypoint() {}

        public Waypoint(String name, int x, int y, int z, String dimension, int color, boolean death) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.color = color;
            this.death = death;
            this.created = System.currentTimeMillis();
        }

        public String initial() {
            return death ? "X" : name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        }
    }

    private final Path file;
    private Map<String, List<Waypoint>> data = new LinkedHashMap<>();
    private boolean loaded;

    public WaypointStore(Path file) {
        this.file = file;
    }

    public Path file() { return file; }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            Type type = new TypeToken<LinkedHashMap<String, List<Waypoint>>>() {}.getType();
            Map<String, List<Waypoint>> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
            if (read != null) {
                read.values().removeIf(l -> l == null);
                read.values().forEach(l -> l.removeIf(w -> w == null || w.name == null || w.dimension == null));
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
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not save {}", file, e);
        }
    }

    /** Live-Liste des Servers (wird bei Bedarf angelegt). {@code key == null} -> leere Wegwerf-Liste. */
    public List<Waypoint> list(String key) {
        ensureLoaded();
        if (key == null) return new ArrayList<>();
        return data.computeIfAbsent(key, k -> new ArrayList<>());
    }

    /** Sortierte Kopie: Tod zuerst, dann nach Name. */
    public List<Waypoint> sorted(String key) {
        List<Waypoint> out = new ArrayList<>(list(key));
        out.sort(Comparator.comparing((Waypoint w) -> !w.death).thenComparing(w -> w.name.toLowerCase()));
        return out;
    }

    public Waypoint find(String key, String name) {
        for (Waypoint w : list(key)) if (w.name.equalsIgnoreCase(name)) return w;
        return null;
    }

    public static String cleanName(String name) {
        if (name == null) return null;
        String n = name.trim();
        if (n.isEmpty()) return null;
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }

    /** Fügt hinzu bzw. ersetzt einen gleichnamigen Waypoint. */
    public Waypoint put(String key, Waypoint w) {
        w.name = cleanName(w.name);
        if (w.name == null) throw new IllegalArgumentException("empty name");
        List<Waypoint> list = list(key);
        list.removeIf(o -> o.name.equalsIgnoreCase(w.name));
        list.add(w);
        save();
        return w;
    }

    /** Eindeutiger Name ("Base", "Base 2", ...). */
    public String uniqueName(String key, String base) {
        String b = cleanName(base);
        if (b == null) b = "Waypoint";
        if (find(key, b) == null) return b;
        for (int i = 2; ; i++) {
            String n = b + " " + i;
            if (find(key, n) == null) return n;
        }
    }

    /** Umbenennen; false, wenn der Name leer oder schon vergeben ist. */
    public boolean rename(String key, Waypoint w, String newName) {
        String n = cleanName(newName);
        if (n == null) return false;
        Waypoint other = find(key, n);
        if (other != null && other != w) return false;
        w.name = n;
        save();
        return true;
    }

    public boolean remove(String key, Waypoint w) {
        boolean r = list(key).remove(w);
        if (r) save();
        return r;
    }

    public boolean remove(String key, String name) {
        boolean r = list(key).removeIf(w -> w.name.equalsIgnoreCase(name));
        if (r) save();
        return r;
    }

    public void clear(String key) {
        list(key).clear();
        save();
    }

    /** Ersetzt den Death-Waypoint der Dimension. */
    public Waypoint setDeath(String key, int x, int y, int z, String dimension, int color) {
        List<Waypoint> list = list(key);
        list.removeIf(w -> w.death && w.dimension.equals(dimension));
        Waypoint w = new Waypoint(DEATH_NAME, x, y, z, dimension, color, true);
        list.add(w);
        save();
        return w;
    }

    /** Kurzname für die Dimension (OW / N / E / sonst Pfad). */
    public static String dimShort(String dim) {
        if (dim == null) return "?";
        return switch (dim) {
            case "minecraft:overworld" -> "OW";
            case "minecraft:the_nether" -> "N";
            case "minecraft:the_end" -> "E";
            default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        };
    }
}
