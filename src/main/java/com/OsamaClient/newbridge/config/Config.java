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
        load();
        GuiEvents.onClosed(Config::save);
        GuiEvents.onChanged(Config::save);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
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

    /** Lädt ein Profil (Module werden passend an/aus geschaltet). GUI-Layout bleibt unverändert. */
    public static boolean loadProfile(String name) {
        Path f = profileFile(name);
        if (f == null || !Files.isRegularFile(f)) return false;
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
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
            LOG.error("Could not load profile {}", f, e);
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
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, ConfigCodec.toJson(data.root()), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not save {}", f, e);
        }
    }
}
