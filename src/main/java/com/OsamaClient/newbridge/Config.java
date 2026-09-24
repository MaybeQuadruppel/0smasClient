package com.OsamaClient.newbridge;

import com.OsamaClient.newbridge.Hacks.Visual.ESP.RenderUtils;
import com.OsamaClient.newbridge.UI.ClickGuiScreen;
import com.OsamaClient.newbridge.UI.GuiManager;
import com.OsamaClient.newbridge.UI.GuiMode;
import com.OsamaClient.newbridge.UI.ModernClickGuiScreen;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import com.OsamaClient.newbridge.UI.UISettingsModule;
import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import com.google.gson.*;
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
            FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("newbridge.json");

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();


    // ========================================================================
    // SAVE
    // ========================================================================

    public static void save() {
        JsonObject root = serializeState();

        // ====================================================================
        // WRITE
        // ====================================================================

        try (
                Writer writer =
                        new FileWriter(
                                CONFIG_PATH.toFile()
                        )
        ) {

            GSON.toJson(
                    root,
                    writer
            );

        } catch (IOException e) {

            e.printStackTrace();
        }
    }


    // ========================================================================
    // SERIALIZE
    // ========================================================================
    // Wird sowohl von save() (zentrale newbridge.json) als auch von
    // ProfileManager (einzelne Profil-Dateien) genutzt, damit beide Stellen
    // garantiert dasselbe Format schreiben und nicht auseinanderlaufen.

    public static JsonObject serializeState() {

        JsonObject root =
                new JsonObject();


        // ====================================================================
        // MODULES
        // ====================================================================

        JsonObject modulesObj =
                new JsonObject();

        for (Module module :
                ModuleManager.modules) {

            /*
             * UISettingsModule ist ein GUI-Container und kein normales
             * Modul. Es wird separat unter gui_settings gespeichert.
             */
            if (module instanceof UISettingsModule) {
                continue;
            }

            JsonObject moduleObj =
                    new JsonObject();

            moduleObj.addProperty(
                    "enabled",
                    module.enabled
            );

            JsonObject settings =
                    new JsonObject();

            for (Component c :
                    module.settings) {

                if (c instanceof Slider s) {

                    settings.addProperty(
                            s.getLabel(),
                            s.getValue()
                    );

                } else if (c instanceof ToggleButton t) {

                    settings.addProperty(
                            t.getLabel(),
                            t.enabled
                    );

                } else if (c instanceof ModeButton m) {

                    settings.addProperty(
                            m.getLabel(),
                            m.getIndex()
                    );

                } else if (c instanceof ColorPicker cp) {

                    settings.addProperty(
                            cp.getLabel(),
                            cp.getColor()
                    );

                } else if (c instanceof BlockPicker bp) {

                    JsonObject blockMap =
                            new JsonObject();

                    for (Block block :
                            bp.selectedBlocks) {

                        String id =
                                BuiltInRegistries.BLOCK
                                        .getKey(block)
                                        .toString();

                        int color =
                                RenderUtils.BLOCK_COLORS
                                        .getOrDefault(
                                                block,
                                                0x6600FFFF
                                        );

                        blockMap.addProperty(
                                id,
                                color
                        );
                    }

                    settings.add(
                            bp.getLabel(),
                            blockMap
                    );

                } else if (c instanceof ItemPicker ip) {

                    JsonObject itemMap =
                            new JsonObject();

                    for (Map.Entry<Item, Integer> entry :
                            ip.selectedItems.entrySet()) {

                        String id =
                                BuiltInRegistries.ITEM
                                        .getKey(
                                                entry.getKey()
                                        )
                                        .toString();

                        itemMap.addProperty(
                                id,
                                entry.getValue()
                        );
                    }

                    settings.add(
                            ip.getLabel(),
                            itemMap
                    );

                } else if (c instanceof EntityFilterPicker efp) {

                    JsonObject filterObj =
                            new JsonObject();

                    JsonObject filters =
                            new JsonObject();

                    for (Map.Entry<String, Boolean> entry :
                            efp.filters.entrySet()) {

                        filters.addProperty(
                                entry.getKey(),
                                entry.getValue()
                        );
                    }

                    JsonObject colors =
                            new JsonObject();

                    for (Map.Entry<String, Integer> entry :
                            efp.colors.entrySet()) {

                        colors.addProperty(
                                entry.getKey(),
                                entry.getValue()
                        );
                    }

                    filterObj.add(
                            "filters",
                            filters
                    );

                    filterObj.add(
                            "colors",
                            colors
                    );

                    settings.add(
                            efp.getLabel(),
                            filterObj
                    );
                }
            }

            moduleObj.add(
                    "settings",
                    settings
            );

            modulesObj.add(
                    module.name,
                    moduleObj
            );
        }

        root.add(
                "modules",
                modulesObj
        );


        // ====================================================================
        // KEYBINDS
        // ====================================================================

        JsonObject bindsObj =
                new JsonObject();

        for (Map.Entry<String, Integer> entry :
                ClickGuiScreen.keybinds.entrySet()) {

            bindsObj.addProperty(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        root.add(
                "keybinds",
                bindsObj
        );


        // ====================================================================
        // NEW GUI SETTINGS
        // ====================================================================

        JsonObject gui =
                new JsonObject();

        gui.addProperty(
                "scale",
                UISettings.scale
        );

        gui.addProperty(
                "fontScale",
                UISettings.fontScale
        );

        gui.addProperty(
                "compactMode",
                UISettings.compactMode
        );

        gui.addProperty(
                "sidebarWidthScale",
                UISettings.sidebarWidthScale
        );

        gui.addProperty(
                "columnWidthScale",
                UISettings.columnWidthScale
        );

        gui.addProperty(
                "moduleGapScale",
                UISettings.moduleGapScale
        );

        gui.addProperty(
                "panelAlpha",
                UISettings.panelAlpha
        );

        gui.addProperty(
                "animationsEnabled",
                UISettings.animationsEnabled
        );

        gui.addProperty(
                "roundedCorners",
                UISettings.roundedCorners
        );

        gui.addProperty(
                "soundVolume",
                UISettings.soundVolume
        );

        gui.addProperty(
                "soundEnabled",
                UISettings.soundEnabled
        );

        gui.addProperty(
                "theme",
                Theme.getActive().ordinal()
        );

        gui.addProperty(
                "guiOpenKey",
                UISettings.guiOpenKey
        );

        gui.addProperty(
                "guiMode",
                GuiManager.getMode().name()
        );

        gui.addProperty(
                "modernScale",
                UISettings.modernScale
        );

        gui.addProperty(
                "guiModeToggleKey",
                UISettings.guiModeToggleKey
        );

        // Gespeicherte Farb-Favoriten des ColorPickers (über alle Picker geteilt).
        JsonArray favorites =
                new JsonArray();

        for (Integer favColor : ColorPicker.getFavorites()) {
            favorites.add(favColor);
        }

        gui.add(
                "colorFavorites",
                favorites
        );

        // Panel-Positionen der Modern-GUI (Meteor-Stil).
        JsonObject panels =
                new JsonObject();

        for (Map.Entry<Module.Category, ModernClickGuiScreen.PanelState> e :
                ModernClickGuiScreen.getPanelStates().entrySet()) {

            if (!e.getValue().placed) continue;

            JsonObject ps =
                    new JsonObject();
            ps.addProperty("x", e.getValue().x);
            ps.addProperty("y", e.getValue().y);
            ps.addProperty("collapsed", e.getValue().collapsed);
            panels.add(e.getKey().name(), ps);
        }

        gui.add(
                "panels",
                panels
        );

        root.add(
                "gui_settings",
                gui
        );


        return root;
    }


    // ========================================================================
    // LOAD
    // ========================================================================

    public static void load() {

        File file =
                CONFIG_PATH.toFile();

        if (!file.exists()) {
            return;
        }

        try (
                Reader reader =
                        new FileReader(file)
        ) {

            JsonObject root =
                    GSON.fromJson(
                            reader,
                            JsonObject.class
                    );

            if (root == null) {
                return;
            }

            deserializeState(root);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // ========================================================================
    // DESERIALIZE
    // ========================================================================
    // Wird sowohl von load() (zentrale newbridge.json) als auch von
    // ProfileManager (einzelne Profil-Dateien) genutzt, damit ein geladenes
    // Profil exakt denselben Effekt hat wie ein normaler Config-Load.

    public static void deserializeState(JsonObject root) {

        // ================================================================
        // MODULES
        // ================================================================

        if (root.has("modules")) {

            JsonObject modules =
                    root.getAsJsonObject(
                            "modules"
                    );

            for (Module module :
                    ModuleManager.modules) {

                if (module instanceof UISettingsModule) {
                    continue;
                }

                if (!modules.has(module.name)) {
                    continue;
                }

                JsonObject moduleObj =
                        modules.getAsJsonObject(
                                module.name
                        );

                boolean enabled =
                        moduleObj.has("enabled")
                                && moduleObj
                                .get("enabled")
                                .getAsBoolean();

                if (enabled != module.enabled) {
                    module.toggle();
                }

                if (moduleObj.has("settings")) {

                    JsonObject settings =
                            moduleObj.getAsJsonObject(
                                    "settings"
                            );

                    for (Component c :
                            module.settings) {

                        loadComponentSetting(
                                c,
                                settings
                        );
                    }
                }
            }
        }


        // ================================================================
        // KEYBINDS
        // ================================================================

        if (root.has("keybinds")) {

            JsonObject binds =
                    root.getAsJsonObject(
                            "keybinds"
                    );

            ClickGuiScreen.keybinds.clear();

            for (Map.Entry<String, JsonElement> entry :
                    binds.entrySet()) {

                ClickGuiScreen.keybinds.put(
                        entry.getKey(),
                        entry.getValue().getAsInt()
                );
            }
        }


        // ================================================================
        // GUI SETTINGS
        // ================================================================

        if (root.has("gui_settings")) {

            JsonObject gui =
                    root.getAsJsonObject(
                            "gui_settings"
                    );

            loadGuiSettings(gui);
        }
    }


    // ========================================================================
    // LOAD GUI
    // ========================================================================

    private static void loadGuiSettings(
            JsonObject gui
    ) {

        if (gui.has("scale")) {

            UISettings.scale =
                    clamp(
                            gui.get("scale").getAsFloat(),
                            UISettings.SCALE_MIN,
                            UISettings.SCALE_MAX
                    );
        }

        if (gui.has("fontScale")) {

            UISettings.fontScale =
                    clamp(
                            gui.get("fontScale").getAsFloat(),
                            UISettings.FONT_SCALE_MIN,
                            UISettings.FONT_SCALE_MAX
                    );
        }

        if (gui.has("compactMode")) {

            UISettings.compactMode =
                    gui.get("compactMode")
                            .getAsBoolean();
        }

        if (gui.has("sidebarWidthScale")) {

            UISettings.sidebarWidthScale =
                    clamp(
                            gui.get("sidebarWidthScale").getAsFloat(),
                            UISettings.SIDEBAR_WIDTH_MIN,
                            UISettings.SIDEBAR_WIDTH_MAX
                    );
        }

        if (gui.has("columnWidthScale")) {

            UISettings.columnWidthScale =
                    clamp(
                            gui.get("columnWidthScale").getAsFloat(),
                            UISettings.COLUMN_WIDTH_MIN,
                            UISettings.COLUMN_WIDTH_MAX
                    );
        }

        if (gui.has("moduleGapScale")) {

            UISettings.moduleGapScale =
                    clamp(
                            gui.get("moduleGapScale").getAsFloat(),
                            UISettings.MODULE_GAP_MIN,
                            UISettings.MODULE_GAP_MAX
                    );
        }

        if (gui.has("panelAlpha")) {

            UISettings.panelAlpha =
                    Math.max(
                            0,
                            Math.min(
                                    255,
                                    gui.get("panelAlpha")
                                            .getAsInt()
                            )
                    );
        }

        if (gui.has("animationsEnabled")) {

            UISettings.animationsEnabled =
                    gui.get("animationsEnabled")
                            .getAsBoolean();
        }

        if (gui.has("roundedCorners")) {

            UISettings.roundedCorners =
                    gui.get("roundedCorners")
                            .getAsBoolean();
        }

        if (gui.has("soundVolume")) {

            UISettings.soundVolume =
                    clamp(
                            gui.get("soundVolume")
                                    .getAsFloat(),
                            0f,
                            1f
                    );
        }

        if (gui.has("soundEnabled")) {

            UISettings.soundEnabled =
                    gui.get("soundEnabled")
                            .getAsBoolean();
        }

        if (gui.has("theme")) {

            int index =
                    gui.get("theme")
                            .getAsInt();

            Theme[] themes =
                    Theme.values();

            if (index >= 0
                    && index < themes.length) {

                Theme.setActive(
                        themes[index]
                );
            }
        }

        if (gui.has("guiOpenKey")) {

            UISettings.guiOpenKey =
                    gui.get("guiOpenKey")
                            .getAsInt();
        }

        if (gui.has("guiMode")) {
            try {
                GuiManager.setMode(
                        GuiMode.valueOf(
                                gui.get("guiMode").getAsString()
                        )
                );
            } catch (IllegalArgumentException ignored) {
                // unbekannter Modus -> Standard (Classic) behalten
            }
        }

        if (gui.has("modernScale")) {
            UISettings.setModernScale(
                    gui.get("modernScale").getAsFloat()
            );
        }

        if (gui.has("guiModeToggleKey")) {
            UISettings.guiModeToggleKey =
                    gui.get("guiModeToggleKey").getAsInt();
        }

        if (gui.has("colorFavorites")
                && gui.get("colorFavorites").isJsonArray()) {

            java.util.List<Integer> favorites =
                    new java.util.ArrayList<>();

            for (JsonElement el :
                    gui.getAsJsonArray("colorFavorites")) {

                try {
                    favorites.add(el.getAsInt());
                } catch (RuntimeException ignored) {
                    // fehlerhaften Eintrag überspringen
                }
            }

            ColorPicker.loadFavorites(favorites);
        }

        if (gui.has("panels")
                && gui.get("panels").isJsonObject()) {

            JsonObject panels =
                    gui.getAsJsonObject("panels");

            for (String key : panels.keySet()) {
                try {
                    JsonObject ps = panels.getAsJsonObject(key);
                    ModernClickGuiScreen.loadPanel(
                            key,
                            ps.get("x").getAsDouble(),
                            ps.get("y").getAsDouble(),
                            ps.has("collapsed") && ps.get("collapsed").getAsBoolean()
                    );
                } catch (RuntimeException ignored) {
                    // fehlerhaften Panel-Eintrag überspringen
                }
            }
        }

        UISettings.applyToSounds();
    }


    // ========================================================================
    // COMPONENT LOAD
    // ========================================================================

    private static void loadComponentSetting(
            Component c,
            JsonObject settings
    ) {

        if (c instanceof Slider s
                && settings.has(s.getLabel())) {

            s.setValue(
                    settings
                            .get(s.getLabel())
                            .getAsDouble()
            );

        } else if (c instanceof ToggleButton t
                && settings.has(t.getLabel())) {

            t.setValue(
                    settings
                            .get(t.getLabel())
                            .getAsBoolean()
            );

        } else if (c instanceof ModeButton m
                && settings.has(m.getLabel())) {

            m.setIndex(
                    settings
                            .get(m.getLabel())
                            .getAsInt()
            );

        } else if (c instanceof ColorPicker cp
                && settings.has(cp.getLabel())) {

            cp.setColor(
                    settings
                            .get(cp.getLabel())
                            .getAsInt()
            );

        } else if (c instanceof BlockPicker bp
                && settings.has(bp.getLabel())) {

            bp.selectedBlocks.clear();

            JsonElement element =
                    settings.get(bp.getLabel());

            if (element != null
                    && element.isJsonObject()) {

                JsonObject blocks =
                        element.getAsJsonObject();

                for (Map.Entry<String, JsonElement> entry :
                        blocks.entrySet()) {

                    Identifier id;

                    try {
                        id =
                                Identifier.parse(
                                        entry.getKey()
                                );
                    } catch (Exception ignored) {
                        continue;
                    }

                    int color =
                            entry.getValue()
                                    .getAsInt();

                    BuiltInRegistries.BLOCK
                            .getOptional(id)
                            .ifPresent(block -> {

                                bp.selectedBlocks.add(
                                        block
                                );

                                RenderUtils.BLOCK_COLORS.put(
                                        block,
                                        color
                                );
                            });
                }
            }

        } else if (c instanceof ItemPicker ip
                && settings.has(ip.getLabel())) {

            ip.selectedItems.clear();

            JsonElement element =
                    settings.get(ip.getLabel());

            if (element != null
                    && element.isJsonObject()) {

                JsonObject items =
                        element.getAsJsonObject();

                for (Map.Entry<String, JsonElement> entry :
                        items.entrySet()) {

                    Identifier id;

                    try {
                        id =
                                Identifier.parse(
                                        entry.getKey()
                                );
                    } catch (Exception ignored) {
                        continue;
                    }

                    int color =
                            entry.getValue()
                                    .getAsInt();

                    BuiltInRegistries.ITEM
                            .getOptional(id)
                            .ifPresent(item ->
                                    ip.selectedItems.put(
                                            item,
                                            color
                                    )
                            );
                }

            } else if (element != null
                    && element.isJsonArray()) {

                /*
                 * Alte Config-Version.
                 */
                for (JsonElement itemElement :
                        element.getAsJsonArray()) {

                    Identifier id;

                    try {
                        id =
                                Identifier.parse(
                                        itemElement.getAsString()
                                );
                    } catch (Exception ignored) {
                        continue;
                    }

                    BuiltInRegistries.ITEM
                            .getOptional(id)
                            .ifPresent(item ->
                                    ip.selectedItems.put(
                                            item,
                                            0xFFFFD700
                                    )
                            );
                }
            }

        } else if (c instanceof EntityFilterPicker efp
                && settings.has(efp.getLabel())) {

            JsonElement element =
                    settings.get(
                            efp.getLabel()
                    );

            if (element != null
                    && element.isJsonObject()) {

                JsonObject object =
                        element.getAsJsonObject();

                if (object.has("filters")) {

                    JsonObject filters =
                            object.getAsJsonObject(
                                    "filters"
                            );

                    for (Map.Entry<String, JsonElement> entry :
                            filters.entrySet()) {

                        if (efp.filters.containsKey(
                                entry.getKey()
                        )) {

                            efp.filters.put(
                                    entry.getKey(),
                                    entry.getValue()
                                            .getAsBoolean()
                            );
                        }
                    }
                }

                if (object.has("colors")) {

                    JsonObject colors =
                            object.getAsJsonObject(
                                    "colors"
                            );

                    for (Map.Entry<String, JsonElement> entry :
                            colors.entrySet()) {

                        efp.colors.put(
                                entry.getKey(),
                                entry.getValue()
                                        .getAsInt()
                        );
                    }
                }
            }
        }
    }


    // ========================================================================
    // HELPERS
    // ========================================================================

    private static float clamp(
            float value,
            float min,
            float max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
}