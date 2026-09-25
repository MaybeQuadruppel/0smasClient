package com.OsamaClient.newbridge.config;

import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.EnchantmentPicker;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.TextBox;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCodecTest {

    /** Module with every MC-free setting type. */
    static final class TestModule extends Module {
        final ToggleButton bool = new ToggleButton("Bool", false, null);
        final Slider slider = new Slider("Range", 0, 10, 4.5, 0.5, null);
        final ModeButton mode = new ModeButton("Mode", List.of("Single", "Multi", "Switch"), 0, null);
        final ColorPicker color = new ColorPicker("Color", 0xFF6C8CFF, null);
        final TextBox text = new TextBox("Text", "hello", null);
        final EntityFilterPicker filter = new EntityFilterPicker("Targets");
        final EnchantmentPicker enchants = new EnchantmentPicker("Books");
        final Slider duplicate = new Slider("Range", 0, 100, 50, 1, null);
        int enables;

        TestModule(String name) {
            super(name, "test", Category.MISC);
            settings.add(bool);
            settings.add(slider);
            settings.add(mode);
            settings.add(color);
            settings.add(text);
            settings.add(filter);
            settings.add(enchants);
            settings.add(duplicate);
        }

        @Override
        public void onEnable() { enables++; }
    }

    private static final ConfigCodec.Ids NO_REGISTRIES = new ConfigCodec.Ids() {
        public String blockId(Object block) { return null; }
        public Object block(String id) { return null; }
        public String itemId(Object item) { return null; }
        public Object item(String id) { return null; }
    };

    private static ConfigCodec.Data roundTrip(ConfigCodec.Data data) {
        String json = ConfigCodec.toJson(data.root());
        return new ConfigCodec.Data(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void roundTripsEveryMcFreeSettingType() {
        TestModule a = new TestModule("KillAura");
        a.key = 82;
        a.enabled = true;
        a.bool.setValue(true);
        a.slider.setValue(7.5);
        a.mode.setIndex(2);
        a.color.setColor(0x80112233);
        a.text.setText("abc");
        a.filter.filters.put("Animals", true);
        a.filter.colors.put("Players", 0xFFFF0000);
        a.enchants.selectedEnchantments.put("Mending", 12);
        a.duplicate.setValue(77);
        Map<String, ConfigCodec.PanelState> panels = new HashMap<>();
        panels.put("COMBAT", new ConfigCodec.PanelState(120, 40, true));

        ConfigCodec.Data saved = roundTrip(ConfigCodec.encode(List.of(a), panels, NO_REGISTRIES));

        TestModule b = new TestModule("KillAura");
        Map<String, ConfigCodec.PanelState> loadedPanels = new HashMap<>();
        ConfigCodec.decode(saved, List.of(b), loadedPanels, NO_REGISTRIES, Module::toggle);

        assertTrue(b.enabled);
        assertEquals(1, b.enables, "enabled modules are toggled on through the callback");
        assertEquals(82, b.key);
        assertTrue(b.bool.enabled);
        assertEquals(7.5, b.slider.getValue());
        assertEquals(2, b.mode.getIndex());
        assertEquals(0x80112233, b.color.getColor());
        assertEquals("abc", b.text.getText());
        assertTrue(b.filter.filters.get("Animals"));
        assertEquals(0xFFFF0000, b.filter.getColor("Players"));
        assertEquals(Map.of("Mending", 12), b.enchants.selectedEnchantments);
        assertEquals(77, b.duplicate.getValue(), "duplicate labels are kept apart (#2)");
        assertEquals(new ConfigCodec.PanelState(120, 40, true), loadedPanels.get("COMBAT"));
    }

    @Test
    void storesModeByNameAndFallsBackToFirstMode() {
        TestModule a = new TestModule("M");
        a.mode.setIndex(1);
        JsonObject root = ConfigCodec.encode(List.of(a), Map.of(), NO_REGISTRIES).root();
        JsonObject settings = root.getAsJsonObject("modules").getAsJsonObject("M").getAsJsonObject("settings");
        assertEquals("Multi", settings.get("Mode").getAsString());

        settings.addProperty("Mode", "DoesNotExist");
        TestModule b = new TestModule("M");
        b.mode.setIndex(2);
        ConfigCodec.decode(new ConfigCodec.Data(root), List.of(b), new HashMap<>(), NO_REGISTRIES, Module::toggle);
        assertEquals(0, b.mode.getIndex());
    }

    @Test
    void ignoresUnknownModulesSettingsAndWrongTypes() {
        String json = """
                {"version":1,
                 "modules":{
                   "Unknown":{"enabled":true},
                   "M":{"enabled":"yes","key":"x","settings":{"Range":"far","Bool":5,"Nope":1,"Color":"#zz","Targets":7}}
                 },
                 "panels":{"VISUAL":{"x":"a"}}}
                """;
        TestModule b = new TestModule("M");
        Map<String, ConfigCodec.PanelState> panels = new HashMap<>();
        assertDoesNotThrow(() -> ConfigCodec.decode(new ConfigCodec.Data(JsonParser.parseString(json).getAsJsonObject()),
                List.of(b), panels, NO_REGISTRIES, Module::toggle));
        assertFalse(b.enabled);
        assertEquals(-1, b.key);
        assertEquals(4.5, b.slider.getValue());
        assertEquals(0xFF6C8CFF, b.color.getColor());
        assertTrue(panels.isEmpty());
    }

    @Test
    void enableCallbackFailureDoesNotStopLoading() {
        TestModule a = new TestModule("A");
        a.enabled = true;
        TestModule a2 = new TestModule("B");
        a2.enabled = true;
        a2.slider.setValue(9);
        ConfigCodec.Data saved = roundTrip(ConfigCodec.encode(List.of(a, a2), Map.of(), NO_REGISTRIES));

        TestModule b = new TestModule("A");
        TestModule b2 = new TestModule("B");
        List<String> tried = new ArrayList<>();
        ConfigCodec.decode(saved, List.of(b, b2), new HashMap<>(), NO_REGISTRIES, m -> {
            tried.add(m.name);
            throw new IllegalStateException("no world yet");
        });
        assertEquals(List.of("A", "B"), tried);
        assertEquals(9, b2.slider.getValue());
    }

    @Test
    void disabledModulesStayDisabledAndAreNotToggled() {
        TestModule a = new TestModule("A");
        ConfigCodec.Data saved = roundTrip(ConfigCodec.encode(List.of(a), Map.of(), NO_REGISTRIES));
        TestModule b = new TestModule("A");
        ConfigCodec.decode(saved, List.of(b), new HashMap<>(), NO_REGISTRIES, Module::toggle);
        assertFalse(b.enabled);
        assertEquals(0, b.enables);
    }

    @Test
    void writesFloatBackedValuesWithoutBinaryNoise() {
        TestModule a = new TestModule("M");
        a.slider.setValue((float) 3.8); // what modules passing float defaults end up with
        JsonObject settings = ConfigCodec.encode(List.of(a), Map.of(), NO_REGISTRIES).root()
                .getAsJsonObject("modules").getAsJsonObject("M").getAsJsonObject("settings");
        assertEquals("4.0", settings.get("Range").toString()); // snapped to step 0.5
        a.duplicate.setValue((float) 37.3);
        Slider fine = new Slider("Fine", 0, 10, (float) 3.8, 0, null);
        a.settings.add(fine);
        settings = ConfigCodec.encode(List.of(a), Map.of(), NO_REGISTRIES).root()
                .getAsJsonObject("modules").getAsJsonObject("M").getAsJsonObject("settings");
        assertEquals("3.8", settings.get("Fine").toString());
    }
}
