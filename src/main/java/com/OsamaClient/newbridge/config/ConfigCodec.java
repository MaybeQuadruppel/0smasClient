package com.OsamaClient.newbridge.config;

import com.OsamaClient.newbridge.UI.components.BlockPicker;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.components.EnchantmentPicker;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ItemPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.TextBox;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Maps modules, their settings and the panel layout to / from JSON. Minecraft-free (registry lookups go
 * through {@link Ids}), so it can be unit-tested. Unknown or malformed entries are skipped, never thrown.
 */
public final class ConfigCodec {

    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Registry id lookups for block / item pickers (null = unknown). */
    public interface Ids {
        String blockId(Object block);
        Object block(String id);
        String itemId(Object item);
        Object item(String id);
    }

    public record PanelState(float x, float y, boolean collapsed) {}

    /** A single draggable HUD-overlay box's saved position (e.g. {@link com.OsamaClient.newbridge.Hacks.Visual.HudOverlay}). */
    public record WidgetPos(float x, float y) {}

    public record Data(JsonObject root) {}

    private ConfigCodec() {}

    public static String toJson(JsonObject root) { return GSON.toJson(root); }

    // ------------------------------------------------------------------ encode

    public static Data encode(List<? extends Module> modules, Map<String, PanelState> panels,
                              Map<String, WidgetPos> hudWidgets, Ids ids) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject mods = new JsonObject();
        for (Module m : modules) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled);
            o.addProperty("key", m.key);
            JsonObject settings = new JsonObject();
            Map<String, Integer> seen = new HashMap<>();
            for (Component c : m.settings) {
                JsonElement v = encodeSetting(c, ids);
                if (v != null) settings.add(uniqueKey(label(c), seen), v);
            }
            o.add("settings", settings);
            mods.add(m.name, o);
        }
        root.add("modules", mods);
        JsonObject ps = new JsonObject();
        panels.forEach((k, p) -> {
            JsonObject o = new JsonObject();
            o.addProperty("x", p.x());
            o.addProperty("y", p.y());
            o.addProperty("collapsed", p.collapsed());
            ps.add(k, o);
        });
        root.add("panels", ps);
        JsonObject hud = new JsonObject();
        hudWidgets.forEach((k, p) -> {
            JsonObject o = new JsonObject();
            o.addProperty("x", p.x());
            o.addProperty("y", p.y());
            hud.add(k, o);
        });
        root.add("hud", hud);
        return new Data(root);
    }

    private static JsonElement encodeSetting(Component c, Ids ids) {
        if (c instanceof ToggleButton t) return new JsonPrimitive(t.enabled);
        if (c instanceof Slider s) return new JsonPrimitive(clean(s.getValue()));
        if (c instanceof ModeButton m) return new JsonPrimitive(m.getMode());
        if (c instanceof ColorPicker p) return new JsonPrimitive(ColorUtil.toHex(p.getColor()));
        if (c instanceof TextBox t) return new JsonPrimitive(t.getText());
        if (c instanceof BlockPicker b) {
            JsonArray a = new JsonArray();
            for (Object block : b.selectedBlocks) {
                String id = ids.blockId(block);
                if (id != null) a.add(id);
            }
            return a;
        }
        if (c instanceof ItemPicker i) {
            JsonObject o = new JsonObject();
            i.selectedItems.forEach((item, color) -> {
                String id = ids.itemId(item);
                if (id != null) o.addProperty(id, ColorUtil.toHex(color));
            });
            return o;
        }
        if (c instanceof EnchantmentPicker e) {
            JsonObject o = new JsonObject();
            e.selectedEnchantments.forEach(o::addProperty);
            return o;
        }
        if (c instanceof EntityFilterPicker f) {
            JsonObject o = new JsonObject(), filters = new JsonObject(), colors = new JsonObject();
            f.filters.forEach(filters::addProperty);
            f.colors.forEach((k, v) -> colors.addProperty(k, ColorUtil.toHex(v)));
            o.add("filters", filters);
            o.add("colors", colors);
            return o;
        }
        return null;
    }

    // ------------------------------------------------------------------ decode

    /**
     * Applies saved values. Modules saved as enabled (and currently disabled) are passed to {@code enable};
     * a failing callback is swallowed so one module can't stop the rest from loading.
     */
    public static void decode(Data data, List<? extends Module> modules, Map<String, PanelState> panelsOut,
                              Map<String, WidgetPos> hudOut, Ids ids, Consumer<Module> enable) {
        JsonObject root = data.root();
        JsonObject mods = obj(root, "modules");
        if (mods != null) {
            for (Module m : modules) {
                JsonObject o = obj(mods, m.name);
                if (o == null) continue;
                JsonObject settings = obj(o, "settings");
                if (settings != null) {
                    Map<String, Integer> seen = new HashMap<>();
                    for (Component c : m.settings) {
                        JsonElement v = settings.get(uniqueKey(label(c), seen));
                        if (v == null) continue;
                        try {
                            decodeSetting(c, v, ids);
                        } catch (RuntimeException ignored) {
                            // malformed value for this setting: keep the default
                        }
                    }
                }
                Integer key = integer(o.get("key"));
                if (key != null) m.key = key;
                Boolean enabled = bool(o.get("enabled"));
                if (Boolean.TRUE.equals(enabled) && !m.enabled) {
                    try {
                        enable.accept(m);
                    } catch (RuntimeException ignored) {
                        // e.g. no world/player at startup
                    }
                }
            }
        }
        JsonObject ps = obj(root, "panels");
        if (ps != null) {
            for (Map.Entry<String, JsonElement> e : ps.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject p = e.getValue().getAsJsonObject();
                Double x = number(p.get("x")), y = number(p.get("y"));
                Boolean collapsed = bool(p.get("collapsed"));
                if (x == null || y == null) continue;
                panelsOut.put(e.getKey(), new PanelState(x.floatValue(), y.floatValue(), Boolean.TRUE.equals(collapsed)));
            }
        }
        JsonObject hud = obj(root, "hud");
        if (hud != null) {
            for (Map.Entry<String, JsonElement> e : hud.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject p = e.getValue().getAsJsonObject();
                Double x = number(p.get("x")), y = number(p.get("y"));
                if (x == null || y == null) continue;
                hudOut.put(e.getKey(), new WidgetPos(x.floatValue(), y.floatValue()));
            }
        }
    }

    private static void decodeSetting(Component c, JsonElement v, Ids ids) {
        if (c instanceof ToggleButton t) {
            Boolean b = bool(v);
            if (b != null) t.setValue(b);
        } else if (c instanceof Slider s) {
            Double d = number(v);
            if (d != null) s.setValue(d);
        } else if (c instanceof ModeButton m) {
            String name = string(v);
            if (name == null) return;
            int i = m.getModes().indexOf(name);
            m.setIndex(Math.max(0, i));
        } else if (c instanceof ColorPicker p) {
            Integer col = ColorUtil.parseHex(string(v));
            if (col != null) p.setColor(col);
        } else if (c instanceof TextBox t) {
            String s = string(v);
            if (s != null) t.setText(s);
        } else if (c instanceof BlockPicker b && v.isJsonArray()) {
            b.selectedBlocks.clear();
            for (JsonElement e : v.getAsJsonArray()) {
                Object block = ids.block(string(e));
                if (block != null) b.selectedBlocks.add((net.minecraft.world.level.block.Block) block);
            }
        } else if (c instanceof ItemPicker i && v.isJsonObject()) {
            i.selectedItems.clear();
            for (Map.Entry<String, JsonElement> e : v.getAsJsonObject().entrySet()) {
                Object item = ids.item(e.getKey());
                Integer col = ColorUtil.parseHex(string(e.getValue()));
                if (item != null && col != null) i.selectedItems.put((net.minecraft.world.item.Item) item, col);
            }
        } else if (c instanceof EnchantmentPicker en && v.isJsonObject()) {
            en.selectedEnchantments.clear();
            for (Map.Entry<String, JsonElement> e : v.getAsJsonObject().entrySet()) {
                Integer price = integer(e.getValue());
                if (price != null) en.selectedEnchantments.put(e.getKey(), price);
            }
        } else if (c instanceof EntityFilterPicker f && v.isJsonObject()) {
            JsonObject filters = obj(v.getAsJsonObject(), "filters"), colors = obj(v.getAsJsonObject(), "colors");
            if (filters != null) {
                for (Map.Entry<String, JsonElement> e : filters.entrySet()) {
                    Boolean b = bool(e.getValue());
                    if (b != null && f.filters.containsKey(e.getKey())) f.filters.put(e.getKey(), b);
                }
            }
            if (colors != null) {
                for (Map.Entry<String, JsonElement> e : colors.entrySet()) {
                    Integer col = ColorUtil.parseHex(string(e.getValue()));
                    if (col != null) f.colors.put(e.getKey(), col);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    public static String label(Component c) {
        if (c instanceof ToggleButton t) return t.getLabel();
        if (c instanceof Slider s) return s.getLabel();
        if (c instanceof ModeButton m) return m.getLabel();
        if (c instanceof ColorPicker p) return p.getLabel();
        if (c instanceof TextBox t) return t.getLabel();
        if (c instanceof BlockPicker b) return b.getLabel();
        if (c instanceof ItemPicker i) return i.getLabel();
        if (c instanceof EnchantmentPicker e) return e.getLabel();
        if (c instanceof EntityFilterPicker f) return f.getLabel();
        return c.getClass().getSimpleName();
    }

    /** Rounds away float-to-double noise (3.799999952316284 -> 3.8) so the file stays readable. */
    static double clean(double v) {
        return new java.math.BigDecimal(v).round(new java.math.MathContext(7)).stripTrailingZeros().doubleValue();
    }

    /** "Range", "Range#2", "Range#3", … for repeated labels within one module. */
    private static String uniqueKey(String label, Map<String, Integer> seen) {
        int n = seen.merge(label, 1, Integer::sum);
        return n == 1 ? label : label + "#" + n;
    }

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonPrimitive prim(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsJsonPrimitive() : null;
    }

    private static Boolean bool(JsonElement e) {
        JsonPrimitive p = prim(e);
        return p != null && p.isBoolean() ? p.getAsBoolean() : null;
    }

    private static Double number(JsonElement e) {
        JsonPrimitive p = prim(e);
        return p != null && p.isNumber() ? p.getAsDouble() : null;
    }

    private static Integer integer(JsonElement e) {
        Double d = number(e);
        return d == null ? null : (int) Math.round(d);
    }

    private static String string(JsonElement e) {
        JsonPrimitive p = prim(e);
        return p != null && p.isString() ? p.getAsString() : null;
    }
}