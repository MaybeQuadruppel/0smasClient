package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the player owns: built-in emotes + saved custom emotes, and which of them sit on the wheel (in order).
 * Emotes that do not fit on the wheel simply stay in the library. Saved to {@code config/newbridge/emotes.json}.
 */
public final class EmoteLibrary {

    public static final int WHEEL_MAX = 16;
    public static final int NAME_MAX = 24;

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/EmoteLibrary");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Data {
        List<CustomEmote> custom = new ArrayList<>();
        List<String> wheel;
    }

    public record SaveResult(String name, boolean inWheel, String message) {}

    private static final List<CustomEmote> custom = new ArrayList<>();
    private static final Map<String, EmoteAnimation> compiled = new HashMap<>();
    private static final List<String> wheel = new ArrayList<>();
    private static boolean loaded;

    private EmoteLibrary() {}

    // ------------------------------------------------------------------ load / save

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("newbridge").resolve("emotes.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        boolean haveWheel = false;
        Path f = file();
        if (Files.isRegularFile(f)) {
            try {
                Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                if (d != null) {
                    if (d.custom != null) for (CustomEmote c : d.custom) if (c != null && c.sanitize()) custom.add(c);
                    compile();
                    if (d.wheel != null) {
                        haveWheel = true;
                        for (String n : d.wheel) {
                            if (n != null && find(n) != null && !wheel.contains(n) && wheel.size() < WHEEL_MAX) wheel.add(n);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOG.error("Could not read {}", f, e);
            }
        }
        compile();
        if (!haveWheel) for (EmoteAnimation a : Emotes.ALL) if (wheel.size() < WHEEL_MAX) wheel.add(a.name());
    }

    private static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Data d = new Data();
            d.custom = new ArrayList<>(custom);
            d.wheel = new ArrayList<>(wheel);
            Files.writeString(f, GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not save emotes", e);
        }
    }

    private static void compile() {
        compiled.clear();
        for (CustomEmote c : custom) {
            try {
                compiled.put(c.name, c.toAnimation());
            } catch (RuntimeException e) {
                LOG.warn("Custom emote '{}' is broken and was skipped", c.name, e);
            }
        }
    }

    // ------------------------------------------------------------------ lookup

    public static boolean isBuiltin(String name) {
        for (EmoteAnimation a : Emotes.ALL) if (a.name().equalsIgnoreCase(name)) return true;
        return false;
    }

    public static EmoteAnimation find(String name) {
        ensureLoaded();
        for (EmoteAnimation a : Emotes.ALL) if (a.name().equalsIgnoreCase(name)) return a;
        return compiled.get(name);
    }

    public static CustomEmote customByName(String name) {
        ensureLoaded();
        for (CustomEmote c : custom) if (c.name.equalsIgnoreCase(name)) return c;
        return null;
    }

    public static List<CustomEmote> customEmotes() {
        ensureLoaded();
        return Collections.unmodifiableList(custom);
    }

    /** Emotes on the wheel, in wheel order. */
    public static List<EmoteAnimation> wheelEmotes() {
        ensureLoaded();
        List<EmoteAnimation> out = new ArrayList<>();
        for (String n : wheel) {
            EmoteAnimation a = find(n);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** Library order: wheel emotes first (wheel order), then the rest (built-in, then custom). */
    public static List<String> orderedNames() {
        ensureLoaded();
        List<String> out = new ArrayList<>(wheel);
        for (EmoteAnimation a : Emotes.ALL) if (!wheel.contains(a.name())) out.add(a.name());
        for (CustomEmote c : custom) if (!wheel.contains(c.name)) out.add(c.name);
        return out;
    }

    public static boolean inWheel(String name) { ensureLoaded(); return wheel.contains(name); }

    public static int wheelIndex(String name) { ensureLoaded(); return wheel.indexOf(name); }

    public static int wheelSize() { ensureLoaded(); return wheel.size(); }

    // ------------------------------------------------------------------ wheel layout

    /** @return false if the emote could not be added because the wheel is full. */
    public static boolean toggleWheel(String name) {
        ensureLoaded();
        if (wheel.remove(name)) {
            save();
            return true;
        }
        if (wheel.size() >= WHEEL_MAX || find(name) == null) return false;
        wheel.add(name);
        save();
        return true;
    }

    public static void moveInWheel(String name, int delta) {
        ensureLoaded();
        int i = wheel.indexOf(name);
        if (i < 0) return;
        int j = Math.max(0, Math.min(wheel.size() - 1, i + delta));
        if (i == j) return;
        wheel.remove(i);
        wheel.add(j, name);
        save();
    }

    // ------------------------------------------------------------------ custom emotes

    private static boolean nameTaken(String n, String ignore) {
        if (isBuiltin(n)) return true;
        for (CustomEmote c : custom) {
            boolean self = ignore != null && c.name.equalsIgnoreCase(ignore);
            if (!self && c.name.equalsIgnoreCase(n)) return true;
        }
        return false;
    }

    /** Trims, limits the length and appends " 2", " 3"... until the name is unused (ignoring {@code ignore}). */
    public static String uniqueName(String base, String ignore) {
        ensureLoaded();
        String b = base == null || base.isBlank() ? "Custom Emote" : base.trim();
        if (b.length() > NAME_MAX) b = b.substring(0, NAME_MAX).trim();
        String n = b;
        int i = 2;
        while (nameTaken(n, ignore)) {
            String suffix = " " + i++;
            n = (b.length() + suffix.length() > NAME_MAX ? b.substring(0, NAME_MAX - suffix.length()) : b) + suffix;
        }
        return n;
    }

    /**
     * Saves (or overwrites when {@code originalName} is the emote being edited) and optionally puts it on the wheel.
     * When the wheel is full the emote is stored in the library only.
     */
    public static SaveResult saveCustom(CustomEmote e, String originalName, boolean wantWheel) {
        ensureLoaded();
        String name = uniqueName(e.name, originalName);
        e.name = name;

        int idx = -1;
        for (int i = 0; i < custom.size(); i++) {
            if (originalName != null && custom.get(i).name.equalsIgnoreCase(originalName)) idx = i;
        }
        if (idx >= 0) {
            String old = custom.get(idx).name;
            custom.set(idx, e);
            int w = wheel.indexOf(old);
            if (w >= 0) wheel.set(w, name);
        } else {
            custom.add(e);
        }

        boolean in = wheel.contains(name);
        String msg = "Saved '" + name + "'";
        if (wantWheel && !in) {
            if (wheel.size() < WHEEL_MAX) {
                wheel.add(name);
                in = true;
                msg += " and added to the wheel";
            } else {
                msg += " to the library (wheel is full)";
            }
        } else if (!wantWheel && in) {
            wheel.remove(name);
            in = false;
            msg += " (removed from the wheel)";
        }
        compile();
        save();
        return new SaveResult(name, in, msg);
    }

    public static void deleteCustom(String name) {
        ensureLoaded();
        custom.removeIf(c -> c.name.equalsIgnoreCase(name));
        wheel.remove(name);
        compile();
        save();
    }
}