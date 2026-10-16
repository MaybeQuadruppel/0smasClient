package com.OsamaClient.newbridge.config;

import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.GuiEvents;
import com.OsamaClient.newbridge.Hacks.Visual.HudOverlay;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Saves / loads everything to {@code config/0smasclient.json}. A broken file is logged, never fatal. */
public final class Config {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/Config");

    private static final ConfigCodec.Ids REGISTRY_IDS = new ConfigCodec.Ids() {
        public String blockId(Object block) { return BuiltInRegistries.BLOCK.getKey((Block) block).toString(); }

        public Object block(String id) {
            Identifier rl = id == null ? null : Identifier.tryParse(id);
            return rl == null ? null : BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
        }

        public String itemId(Object item) { return BuiltInRegistries.ITEM.getKey((Item) item).toString(); }

        public Object item(String id) {
            Identifier rl = id == null ? null : Identifier.tryParse(id);
            return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        }

        public String entityTypeId(Object type) { return BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) type).toString(); }

        public Object entityType(String id) {
            Identifier rl = id == null ? null : Identifier.tryParse(id);
            return rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        }
    };

    private static boolean loaded;

    private Config() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("0smasclient.json");
    }

    /** Loads the config (call after ModuleManager.init()) and hooks up saving. */
    public static void init() {
        backupNow(); // safety copy of whatever the last session left behind
        load();
        GuiEvents.onClosed(Config::save);
        GuiEvents.onChanged(Config::save);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
    }

    // ------------------------------------------------------------------ backups

    /** Tests redirect this. */
    public static Path testBackupDir;
    private static final int KEEP_BACKUPS = 10;

    public static Path backupDir() {
        return testBackupDir != null ? testBackupDir : FabricLoader.getInstance().getConfigDir().resolve("0smasclient-backups");
    }

    /** Copies the current config file to a timestamped backup (keeps the newest 10). @return the backup name or null */
    public static String backupNow() {
        Path f = file();
        if (!Files.isRegularFile(f)) return null;
        try {
            Files.createDirectories(backupDir());
            String name = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Files.copy(f, backupDir().resolve(name + ".json"), StandardCopyOption.REPLACE_EXISTING);
            java.util.List<String> all = listBackups();
            for (int i = KEEP_BACKUPS; i < all.size(); i++) Files.deleteIfExists(backupDir().resolve(all.get(i) + ".json"));
            return name;
        } catch (IOException e) {
            LOG.warn("Config backup failed", e);
            return null;
        }
    }

    /** Backup names, newest first. */
    public static java.util.List<String> listBackups() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!Files.isDirectory(backupDir())) return out;
        try (var s = Files.list(backupDir())) {
            s.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String n = p.getFileName().toString();
                out.add(n.substring(0, n.length() - 5));
            });
        } catch (IOException ignored) {
        }
        out.sort(java.util.Comparator.reverseOrder());
        return out;
    }

    /** Restores a backup (null / "latest" = newest) over the live config and applies it. */
    public static boolean restoreBackup(String name) {
        java.util.List<String> all = listBackups();
        if (all.isEmpty()) return false;
        String pick = name == null || name.equalsIgnoreCase("latest") ? all.get(0) : name;
        Path b = backupDir().resolve(pick + ".json");
        if (!Files.isRegularFile(b)) return false;
        try {
            Files.copy(b, file(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return false;
        }
        load();
        return true;
    }

    public static void load() {
        Path f = file();
        loaded = true;
        if (!Files.isRegularFile(f)) return;
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            ConfigCodec.Data data = new ConfigCodec.Data(JsonParser.parseString(json).getAsJsonObject());
            Map<String, ConfigCodec.PanelState> panels = new HashMap<>();
            Map<String, ConfigCodec.WidgetPos> hud = new HashMap<>();
            ConfigCodec.decode(data, ModuleManager.modules, panels, hud, REGISTRY_IDS, m -> m.toggle());
            ClickGui.INSTANCE.applyPanelStates(panels);
            if (data.root().has("foldedGroups") && data.root().get("foldedGroups").isJsonArray()) {
                var keys = new java.util.ArrayList<String>();
                for (var el : data.root().getAsJsonArray("foldedGroups")) if (el.isJsonPrimitive()) keys.add(el.getAsString());
                com.OsamaClient.newbridge.UI.components.SubCategories.restoreFolded(keys);
            }
            if (HudOverlay.instance != null) HudOverlay.instance.applyWidgetPositions(hud);
        } catch (Exception e) {
            LOG.error("Could not read {}, using defaults (kept a copy as .broken)", f, e);
            try {
                Files.copy(f, f.resolveSibling(f.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    // ------------------------------------------------------------------ profiles

    /** Ordner mit allen Profilen: {@code config/0smasclient-profiles/<name>.json}. */
    public static Path profileDir() {
        if (testProfileDir != null) return testProfileDir;
        return FabricLoader.getInstance().getConfigDir().resolve("0smasclient-profiles");
    }

    /** Nur für Tests: Profilordner umbiegen. */
    public static Path testProfileDir;

    /** Benennt ein Profil um; false wenn Quelle fehlt, Ziel existiert oder Name ungültig ist. */
    public static boolean renameProfile(String from, String to) {
        Path a = profileFile(from), b = profileFile(to);
        if (a == null || b == null || !Files.isRegularFile(a) || Files.exists(b)) return false;
        try {
            Files.move(a, b);
            return true;
        } catch (IOException e) {
            LOG.error("Could not rename profile {} -> {}", a, b, e);
            return false;
        }
    }

    /** Namen aller Module, die im Profil eingeschaltet sind (für die Vorschau im Profil-Manager). */
    public static java.util.List<String> profileEnabledModules(String name) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Path f = profileFile(name);
        if (f == null || !Files.isRegularFile(f)) return out;
        try {
            com.google.gson.JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("modules") || !root.get("modules").isJsonObject()) return out;
            for (var e : root.getAsJsonObject("modules").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                var en = e.getValue().getAsJsonObject().get("enabled");
                if (en == null || !en.isJsonPrimitive() || !en.getAsBoolean()) continue;
                var mod = ModuleManager.getModuleByName(e.getKey());
                // immer aktive Hilfsmodule nicht als "Profil-Inhalt" zeigen
                if (mod instanceof com.OsamaClient.newbridge.Hacks.Client.Profiles
                        || mod instanceof com.OsamaClient.newbridge.UI.gui.ClickGuiModule) continue;
                out.add(e.getKey());
            }
        } catch (Exception e) {
            LOG.error("Could not read profile {}", f, e);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Letzte Änderung (ms) oder 0. */
    public static long profileModified(String name) {
        Path f = profileFile(name);
        try {
            return f != null && Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Erlaubte Profilnamen: Buchstaben, Ziffern, '-', '_', '.' (max. 48 Zeichen). */
    public static String sanitizeProfileName(String name) {
        if (name == null) return null;
        String clean = name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (clean.isEmpty() || clean.startsWith(".")) return null;
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }

    public static Path profileFile(String name) {
        String clean = sanitizeProfileName(name);
        return clean == null ? null : profileDir().resolve(clean + ".json");
    }

    /** Speichert Module, Keybinds und alle Einstellungen (ohne GUI-Layout) als Profil. */
    public static boolean saveProfile(String name) {
        Path f = profileFile(name);
        if (f == null) return false;
        try {
            ConfigCodec.Data data = ConfigCodec.encode(ModuleManager.modules, Map.of(), Map.of(), REGISTRY_IDS);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, ConfigCodec.toJson(data.root()), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not save profile {}", f, e);
            return false;
        }
    }

    public static final String SHARE_PREFIX = "NBCFG1:";

    /** Modules, binds and settings as one line of text to share (prefix + base64 of gzipped JSON). */
    public static String exportString() {
        try {
            ConfigCodec.Data data = ConfigCodec.encode(ModuleManager.modules, Map.of(), Map.of(), REGISTRY_IDS);
            String json = new com.google.gson.Gson().toJson(data.root());
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) {
                gz.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return SHARE_PREFIX + java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not export config", e);
            return null;
        }
    }

    /** Applies a string made by {@link #exportString()}; backs the current config up first. */
    public static boolean importString(String text) {
        if (text == null) return false;
        text = text.trim();
        if (!text.startsWith(SHARE_PREFIX)) return false;
        try {
            byte[] raw = java.util.Base64.getDecoder().decode(text.substring(SHARE_PREFIX.length()));
            String json;
            try (java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
                json = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
            }
            save();
            backupNow();
            return applyProfileJson(json);
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not import config", e);
            return false;
        }
    }

    /** Lädt ein Profil (Module werden passend an/aus geschaltet). GUI-Layout bleibt unverändert. */
    public static boolean loadProfile(String name) {
        Path f = profileFile(name);
        if (f == null || !Files.isRegularFile(f)) return false;
        try {
            return applyProfileJson(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.error("Could not load profile {}", f, e);
            return false;
        }
    }

    private static boolean applyProfileJson(String json) {
        try {
            com.google.gson.JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            ConfigCodec.Data data = new ConfigCodec.Data(root);

            // Profile- und GUI-Modul selbst nie über ein Profil schalten
            java.util.List<com.OsamaClient.newbridge.UI.components.Module> targets = new java.util.ArrayList<>();
            for (var m : ModuleManager.modules) {
                if (m instanceof com.OsamaClient.newbridge.Hacks.Client.Profiles
                        || m instanceof com.OsamaClient.newbridge.UI.gui.ClickGuiModule) continue;
                targets.add(m);
            }

            // decode() schaltet nur EIN -> Module, die im Profil aus sind, vorher ausschalten
            com.google.gson.JsonObject mods = root.has("modules") && root.get("modules").isJsonObject()
                    ? root.getAsJsonObject("modules") : new com.google.gson.JsonObject();
            for (var m : targets) {
                boolean want = false;
                if (mods.has(m.name) && mods.get(m.name).isJsonObject()) {
                    var en = mods.getAsJsonObject(m.name).get("enabled");
                    want = en != null && en.isJsonPrimitive() && en.getAsBoolean();
                }
                if (m.enabled && !want) {
                    try { m.toggle(); } catch (RuntimeException ignored) { }
                }
            }

            ConfigCodec.decode(data, targets, new HashMap<>(), new HashMap<>(), REGISTRY_IDS, m -> m.toggle());
            save(); // aktiver Zustand landet auch in der Hauptconfig
            return true;
        } catch (Exception e) {
            LOG.error("Could not apply profile json", e);
            return false;
        }
    }

    public static boolean deleteProfile(String name) {
        Path f = profileFile(name);
        if (f == null) return false;
        try {
            return Files.deleteIfExists(f);
        } catch (IOException e) {
            LOG.error("Could not delete profile {}", f, e);
            return false;
        }
    }

    public static java.util.List<String> listProfiles() {
        java.util.List<String> names = new java.util.ArrayList<>();
        Path dir = profileDir();
        if (!Files.isDirectory(dir)) return names;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String n = p.getFileName().toString();
                        names.add(n.substring(0, n.length() - 5));
                    });
        } catch (IOException e) {
            LOG.error("Could not list profiles in {}", dir, e);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static void save() {
        if (!loaded) return; // never overwrite a config we didn't read
        Path f = file();
        try {
            Map<String, ConfigCodec.WidgetPos> hud = HudOverlay.instance != null
                    ? HudOverlay.instance.widgetPositions() : Map.of();
            ConfigCodec.Data data = ConfigCodec.encode(ModuleManager.modules, ClickGui.INSTANCE.panelStates(), hud, REGISTRY_IDS);
            var folded = new com.google.gson.JsonArray();
            for (String k : com.OsamaClient.newbridge.UI.components.SubCategories.foldedKeys()) folded.add(k);
            data.root().add("foldedGroups", folded);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, ConfigCodec.toJson(data.root()), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not save {}", f, e);
        }
    }
}
