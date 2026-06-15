package com.qdrppl.newbridge;

import com.google.gson.*;
import com.qdrppl.newbridge.Hacks.Visual.ESP.RenderUtils;
import com.qdrppl.newbridge.UI.ClickGuiScreen;
import com.qdrppl.newbridge.UI.components.*;
import com.qdrppl.newbridge.UI.components.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;

public class Config {
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("newbridge.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save() {
        JsonObject root = new JsonObject();

        // 1. Module & Settings speichern
        JsonObject modulesObj = new JsonObject();
        for (Module module : ModuleManager.modules) {
            JsonObject moduleObj = new JsonObject();
            moduleObj.addProperty("enabled", module.enabled);

            JsonObject settings = new JsonObject();
            for (Component c : module.settings) {
                if (c instanceof Slider s) {
                    settings.addProperty(s.getLabel(), s.getValue());
                } else if (c instanceof ToggleButton t) {
                    settings.addProperty(t.getLabel(), t.enabled);
                } else if (c instanceof ModeButton m) {
                    settings.addProperty(m.getLabel(), m.getIndex());
                } else if (c instanceof ColorPicker cp) {
                    settings.addProperty(cp.getLabel(), cp.getColor());
                } else if (c instanceof BlockPicker bp) {
                    // Neues Format: Strukturierte Map für Blocks + ARGB (Farbe & Alpha)
                    JsonObject blockMapObj = new JsonObject();
                    for (Block block : bp.selectedBlocks) {
                        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                        int color = RenderUtils.BLOCK_COLORS.getOrDefault(block, 0x6600FFFF);
                        blockMapObj.addProperty(blockId, color);
                    }
                    settings.add(bp.getLabel(), blockMapObj);
                }
            }
            moduleObj.add("settings", settings);
            modulesObj.add(module.name, moduleObj);
        }
        root.add("modules", modulesObj);

        // 2. Keybinds speichern
        JsonObject bindsObj = new JsonObject();
        for (Map.Entry<String, Integer> entry : ClickGuiScreen.keybinds.entrySet()) {
            bindsObj.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("keybinds", bindsObj);

        // 3. GUI Settings (Slider) speichern
        JsonObject guiObj = new JsonObject();
        guiObj.addProperty("panelWidth", ClickGuiScreen.dynColW);
        guiObj.addProperty("rowHeight", ClickGuiScreen.dynModH);
        root.add("gui_settings", guiObj);

        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        File configFile = CONFIG_PATH.toFile();
        if (!configFile.exists()) return;

        try (Reader r = new FileReader(configFile)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;

            // 1. Module & Settings laden
            if (root.has("modules")) {
                JsonObject modulesObj = root.getAsJsonObject("modules");
                for (Module module : ModuleManager.modules) {
                    if (!modulesObj.has(module.name)) continue;
                    JsonObject moduleObj = modulesObj.getAsJsonObject(module.name);

                    // Status (Enabled/Disabled)
                    boolean savedEnabled = moduleObj.has("enabled") && moduleObj.get("enabled").getAsBoolean();
                    if (savedEnabled != module.enabled) {
                        module.toggle();
                    }

                    // Settings
                    if (moduleObj.has("settings")) {
                        JsonObject settings = moduleObj.getAsJsonObject("settings");
                        for (Component c : module.settings) {
                            loadComponentSetting(c, settings);
                        }
                    }
                }
            }

            // 2. Keybinds laden
            if (root.has("keybinds")) {
                JsonObject bindsObj = root.getAsJsonObject("keybinds");
                ClickGuiScreen.keybinds.clear();
                for (Map.Entry<String, JsonElement> entry : bindsObj.entrySet()) {
                    ClickGuiScreen.keybinds.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }

            // 3. GUI Settings laden
            if (root.has("gui_settings")) {
                JsonObject guiObj = root.getAsJsonObject("gui_settings");
                if (guiObj.has("panelWidth")) ClickGuiScreen.dynColW = guiObj.get("panelWidth").getAsInt();
                if (guiObj.has("rowHeight")) ClickGuiScreen.dynModH = guiObj.get("rowHeight").getAsInt();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadComponentSetting(Component c, JsonObject settings) {
        if (c instanceof Slider s && settings.has(s.getLabel())) {
            s.setValue(settings.get(s.getLabel()).getAsDouble());
        } else if (c instanceof ToggleButton t && settings.has(t.getLabel())) {
            t.setValue(settings.get(t.getLabel()).getAsBoolean());
        } else if (c instanceof ModeButton m && settings.has(m.getLabel())) {
            m.setIndex(settings.get(m.getLabel()).getAsInt());
        } else if (c instanceof ColorPicker cp && settings.has(cp.getLabel())) {
            cp.setColor(settings.get(cp.getLabel()).getAsInt());
        } else if (c instanceof BlockPicker bp && settings.has(bp.getLabel())) {
            bp.selectedBlocks.clear();

            JsonElement el = settings.get(bp.getLabel());

            if (el != null && el.isJsonObject()) {
                JsonObject blockMapObj = el.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : blockMapObj.entrySet()) {
                    Identifier loc = Identifier.parse(entry.getKey());
                    int savedColor = entry.getValue().getAsInt();

                    BuiltInRegistries.BLOCK.getOptional(loc).ifPresent(block -> {
                        bp.selectedBlocks.add(block);
                        RenderUtils.BLOCK_COLORS.put(block, savedColor);
                    });
                }
            }
        }
    }
}