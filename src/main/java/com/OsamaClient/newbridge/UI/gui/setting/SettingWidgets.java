package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.BlockPicker;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.components.EnchantmentPicker;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ItemPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.TextBox;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Maps a setting data holder to its inline editor. */
public final class SettingWidgets {

    /** Default outline color for newly selected items (same as the old picker). */
    public static final int DEFAULT_ITEM_COLOR = 0xFFFFD700;
    /** Default max price for newly selected enchantments (same as the old picker). */
    public static final int DEFAULT_ENCHANT_PRICE = 20;

    /** Enchantments offered by the picker (display names; AutoTrade matches them against book descriptions). */
    public static final List<String> ENCHANTMENTS = List.of(
            "Mending", "Unbreaking", "Efficiency", "Silk Touch", "Fortune",
            "Protection", "Fire Protection", "Feather Falling", "Blast Protection", "Projectile Protection",
            "Respiration", "Aqua Affinity", "Thorns", "Depth Strider", "Frost Walker", "Soul Speed", "Swift Sneak",
            "Sharpness", "Smite", "Bane of Arthropods", "Knockback", "Fire Aspect", "Looting", "Sweeping Edge",
            "Power", "Punch", "Flame", "Infinity",
            "Loyalty", "Impaling", "Riptide", "Channeling",
            "Multishot", "Quick Charge", "Piercing",
            "Density", "Breach", "Wind Burst",
            "Lure", "Luck of the Sea");

    private SettingWidgets() {}

    public static SettingWidget create(Component c) {
        if (c instanceof ToggleButton t) return new BoolWidget(t);
        if (c instanceof Slider s) return new SliderWidget(s);
        if (c instanceof ModeButton m) return new ModeWidget(m);
        if (c instanceof ColorPicker p) return new ColorWidget(p);
        if (c instanceof TextBox t) return new TextWidget(t);
        if (c instanceof EntityFilterPicker f) return new EntityFilterWidget(f);
        if (c instanceof BlockPicker b) return new ListPickerWidget(b, blocks(b));
        if (c instanceof ItemPicker i) return new ListPickerWidget(i, items(i));
        if (c instanceof EnchantmentPicker e) return new ListPickerWidget(e, enchantments(e));
        return new LabelWidget(c);
    }

    private static ListPickerWidget.Source blocks(BlockPicker picker) {
        return new ListPickerWidget.Source() {
            private List<ListPickerWidget.Entry> all;

            public String label() { return picker.getLabel(); }

            public List<ListPickerWidget.Entry> entries() {
                if (all == null) {
                    all = new ArrayList<>();
                    for (Block b : BuiltInRegistries.BLOCK) {
                        String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                        if (id.equals("minecraft:air")) continue;
                        all.add(new ListPickerWidget.Entry(b, id, b.getName().getString()));
                    }
                    all.sort(Comparator.comparing(ListPickerWidget.Entry::name, String.CASE_INSENSITIVE_ORDER));
                }
                return all;
            }

            public boolean selected(Object key) { return picker.selectedBlocks.contains(key); }

            public void toggle(Object key) {
                Block b = (Block) key;
                if (!picker.selectedBlocks.remove(b)) picker.selectedBlocks.add(b);
            }

            public int selectedCount() { return picker.selectedBlocks.size(); }
        };
    }

    private static ListPickerWidget.Source items(ItemPicker picker) {
        return new ListPickerWidget.Source() {
            private List<ListPickerWidget.Entry> all;

            public String label() { return picker.getLabel(); }

            public List<ListPickerWidget.Entry> entries() {
                if (all == null) {
                    all = new ArrayList<>();
                    for (Item it : BuiltInRegistries.ITEM) {
                        if (it == Items.AIR) continue;
                        String id = BuiltInRegistries.ITEM.getKey(it).toString();
                        all.add(new ListPickerWidget.Entry(it, id, it.getName(it.getDefaultInstance()).getString()));
                    }
                    all.sort(Comparator.comparing(ListPickerWidget.Entry::name, String.CASE_INSENSITIVE_ORDER));
                }
                return all;
            }

            public boolean selected(Object key) { return picker.selectedItems.containsKey(key); }

            public void toggle(Object key) {
                Item it = (Item) key;
                if (picker.selectedItems.remove(it) == null) picker.selectedItems.put(it, DEFAULT_ITEM_COLOR);
            }

            public int selectedCount() { return picker.selectedItems.size(); }

            public int dot(Object key) { return picker.selectedItems.getOrDefault(key, 0); }

            public SettingWidget editor(ListPickerWidget.Entry e) {
                Item it = (Item) e.key();
                ColorWidget w = new ColorWidget(picker, e.name(),
                        () -> picker.selectedItems.getOrDefault(it, DEFAULT_ITEM_COLOR),
                        c -> { if (picker.selectedItems.containsKey(it)) picker.selectedItems.put(it, c); }, false);
                w.setOpen(true);
                return w;
            }
        };
    }

    private static ListPickerWidget.Source enchantments(EnchantmentPicker picker) {
        return new ListPickerWidget.Source() {
            private final List<ListPickerWidget.Entry> all = new ArrayList<>();

            {
                for (String name : ENCHANTMENTS) all.add(new ListPickerWidget.Entry(name, name.toLowerCase(), name));
            }

            public String label() { return picker.getLabel(); }

            public List<ListPickerWidget.Entry> entries() { return all; }

            public boolean selected(Object key) { return picker.selectedEnchantments.containsKey(key); }

            public void toggle(Object key) {
                String name = (String) key;
                if (picker.selectedEnchantments.remove(name) == null) picker.selectedEnchantments.put(name, DEFAULT_ENCHANT_PRICE);
            }

            public int selectedCount() { return picker.selectedEnchantments.size(); }

            public SettingWidget editor(ListPickerWidget.Entry e) {
                String name = (String) e.key();
                Slider price = new Slider("Max price: " + name, 1, 64,
                        picker.selectedEnchantments.getOrDefault(name, DEFAULT_ENCHANT_PRICE), 1,
                        v -> { if (picker.selectedEnchantments.containsKey(name)) picker.selectedEnchantments.put(name, (int) Math.round(v)); })
                        .withDescription("Highest emerald price to accept for " + name);
                return new SliderWidget(price);
            }
        };
    }
}
