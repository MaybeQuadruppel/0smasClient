package com.OsamaClient.newbridge;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import com.google.gson.*;
import com.OsamaClient.newbridge.Hacks.Visual.ESP.RenderUtils;
import com.OsamaClient.newbridge.UI.ClickGuiScreen;
import com.OsamaClient.newbridge.UI.components.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
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
                    // Strukturierte Map für Blocks + ARGB (Farbe & Alpha)
                    JsonObject blockMapObj = new JsonObject();
                    for (Block block : bp.selectedBlocks) {
                        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                        int color = RenderUtils.BLOCK_COLORS.getOrDefault(block, 0x6600FFFF);
                        blockMapObj.addProperty(blockId, color);
                    }
                    settings.add(bp.getLabel(), blockMapObj);
                } else if (c instanceof ItemPicker ip) {
                    JsonArray itemsArr = new JsonArray();
                    for (Item item : ip.selectedItems) {
                        itemsArr.add(BuiltInRegistries.ITEM.getKey(item).toString());
                    }
                    settings.add(ip.getLabel(), itemsArr);
                } else if (c instanceof EntityFilterPicker efp) {
                    JsonObject efpObj = new JsonObject();

                    JsonObject filtersObj = new JsonObject();
                    for (Map.Entry<String, Boolean> entry : efp.filters.entrySet()) {
                        filtersObj.addProperty(entry.getKey(), entry.getValue());
                    }
                    efpObj.add("filters", filtersObj);

                    JsonObject colorsObj = new JsonObject();
                    for (Map.Entry<String, Integer> entry : efp.colors.entrySet()) {
                        colorsObj.addProperty(entry.getKey(), entry.getValue());
                    }
                    efpObj.add("colors", colorsObj);

                    settings.add(efp.getLabel(), efpObj);
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

        // 3. GUI Settings (Slider, Panel-Layout) speichern
        JsonObject guiObj = new JsonObject();
        guiObj.addProperty("panelWidth", ClickGuiScreen.dynColW);
        guiObj.addProperty("rowHeight", ClickGuiScreen.dynModH);

        JsonObject panelPosObj = new JsonObject();
        for (Map.Entry<Module.Category, int[]> entry : ClickGuiScreen.panelPos.entrySet()) {
            JsonObject posObj = new JsonObject();
            posObj.addProperty("x", entry.getValue()[0]);
            posObj.addProperty("y", entry.getValue()[1]);
            panelPosObj.add(entry.getKey().name(), posObj);
        }
        guiObj.add("panelPositions", panelPosObj);

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

                if (guiObj.has("panelPositions")) {
                    JsonObject panelPosObj = guiObj.getAsJsonObject("panelPositions");
                    for (Map.Entry<String, JsonElement> entry : panelPosObj.entrySet()) {
                        try {
                            Module.Category cat = Module.Category.valueOf(entry.getKey());
                            JsonObject posObj = entry.getValue().getAsJsonObject();
                            ClickGuiScreen.panelPos.put(cat, new int[]{
                                    posObj.get("x").getAsInt(),
                                    posObj.get("y").getAsInt()
                            });
                        } catch (IllegalArgumentException ignored) {
                            // unbekannte/veraltete Kategorie in der gespeicherten Config, überspringen
                        }
                    }
                }
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
        } else if (c instanceof ItemPicker ip && settings.has(ip.getLabel())) {
            ip.selectedItems.clear();

            JsonElement el = settings.get(ip.getLabel());

            if (el != null && el.isJsonArray()) {
                for (JsonElement itemEl : el.getAsJsonArray()) {
                    Identifier loc = Identifier.parse(itemEl.getAsString());
                    BuiltInRegistries.ITEM.getOptional(loc).ifPresent(ip.selectedItems::add);
                }
            }
        } else if (c instanceof EntityFilterPicker efp && settings.has(efp.getLabel())) {
            JsonElement el = settings.get(efp.getLabel());

            if (el != null && el.isJsonObject()) {
                JsonObject efpObj = el.getAsJsonObject();

                if (efpObj.has("filters")) {
                    JsonObject filtersObj = efpObj.getAsJsonObject("filters");
                    for (Map.Entry<String, JsonElement> entry : filtersObj.entrySet()) {
                        if (efp.filters.containsKey(entry.getKey())) {
                            efp.filters.put(entry.getKey(), entry.getValue().getAsBoolean());
                        }
                    }
                }

                if (efpObj.has("colors")) {
                    JsonObject colorsObj = efpObj.getAsJsonObject("colors");
                    for (Map.Entry<String, JsonElement> entry : colorsObj.entrySet()) {
                        efp.colors.put(entry.getKey(), entry.getValue().getAsInt());
                    }
                }
            }
        }
    }
}