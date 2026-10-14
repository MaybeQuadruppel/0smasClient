package com.OsamaClient.newbridge.Hacks.Client.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Gespeicherte Macros (global, nicht pro Server). */
public final class MacroStore {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/Macros");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Macro {
        public String name = "Macro";
        /** Taste (Scancode, siehe Keys), -1 = keine. */
        public int key = -1;
        /** Aktionen, getrennt mit ';' (Chat, /befehl, .client-befehl, wait N). */
        public String actions = "";
        public boolean enabled = true;
        /** Ticks zwischen zwei Aktionen. */
        public int delay = 2;
        /** true = key is a scancode (26.3+). Missing in files of older versions: key is converted from GLFW. */
        public boolean scancodes;
    }

    private final Path file;
    private List<Macro> macros = new ArrayList<>();
    private boolean loaded;

    public MacroStore(Path file) { this.file = file; }

    public List<Macro> list() {
        if (!loaded) {
            loaded = true;
            if (file != null && Files.isRegularFile(file)) {
                try {
                    List<Macro> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                            new TypeToken<List<Macro>>() {}.getType());
                    if (read != null) {
                        read.removeIf(m -> m == null || m.name == null);
                        read.forEach(m -> {
                            if (m.actions == null) m.actions = "";
                            if (!m.scancodes) {
                                m.key = com.OsamaClient.newbridge.UI.gui.input.Keys.fromGlfw(m.key);
                                m.scancodes = true;
                            }
                        });
                        macros = read;
                    }
                } catch (Exception e) {
                    LOG.error("Could not read {}", file, e);
                }
            }
        }
        return macros;
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            for (Macro m : list()) m.scancodes = true;
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(list()), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.error("Could not save {}", file, e);
        }
    }

    public Macro add(String name) {
        Macro m = new Macro();
        m.name = uniqueName(name);
        list().add(m);
        save();
        return m;
    }

    public String uniqueName(String base) {
        String b = base == null || base.isBlank() ? "Macro" : base.trim();
        if (find(b) == null) return b;
        for (int i = 2; ; i++) if (find(b + " " + i) == null) return b + " " + i;
    }

    public Macro find(String name) {
        for (Macro m : list()) if (m.name.equalsIgnoreCase(name)) return m;
        return null;
    }

    public boolean remove(Macro m) {
        boolean r = list().remove(m);
        if (r) save();
        return r;
    }
}
