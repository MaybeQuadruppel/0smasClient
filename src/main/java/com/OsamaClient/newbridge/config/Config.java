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