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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        if (c instanceof EntityFilterPicker f) return new EntityFilterWidget(f, new ListPickerWidget(f, entities(f)), new ListPickerWidget(f, excludedEntities(f)));
        if (c instanceof BlockPicker b) return new ListPickerWidget(b, blocks(b));
        if (c instanceof ItemPicker i) return new ListPickerWidget(i, items(i));
        if (c instanceof EnchantmentPicker e) return new ListPickerWidget(e, enchantments(e));
        return new LabelWidget(c);
    }

    /** Texture of a block as small icon (particle sprite = the recognizable face), cached. */
    private static final java.util.Map<Block, net.minecraft.client.renderer.texture.TextureAtlasSprite> BLOCK_ICONS = new java.util.HashMap<>();

    public static net.minecraft.client.renderer.texture.TextureAtlasSprite blockIcon(Block b) {
        var c = BLOCK_ICONS.get(b);
        if (c != null) return c;
        try {
            var mm = net.minecraft.client.Minecraft.getInstance().getModelManager();
            c = mm.getBlockStateModelSet().getParticleMaterial(b.defaultBlockState()).sprite();
            BLOCK_ICONS.put(b, c);
        } catch (RuntimeException e) {
            return null;
        }
        return c;
    }

    private static ListPickerWidget.Source blocks(BlockPicker picker) {
        return new ListPickerWidget.Source() {
            private List<ListPickerWidget.Entry> all;

            public String label() { return picker.getLabel(); }

            public boolean hasIcons() { return true; }

            public net.minecraft.client.renderer.texture.TextureAtlasSprite icon(Object key) { return blockIcon((Block) key); }

            public List<ListPickerWidget.Entry> entries() {
                if (all == null) {
                    all = new ArrayList<>();
                    Set<Set<Block>> processedFamilies = new HashSet<>();

                    for (Block b : BuiltInRegistries.BLOCK) {
                        String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                        if (id.equals("minecraft:air")) continue;

                        Set<Block> family = BlockPicker.familyOf(b);
                        if (family.size() > 1) {
                            if (processedFamilies.contains(family)) continue;
                            processedFamilies.add(family);

                            String name = family.contains(Blocks.SHULKER_BOX)
                                    ? "Shulker Box"
                                    : "Copper Chest";
                            all.add(new ListPickerWidget.Entry(b, id, name));
                        } else {
                            all.add(new ListPickerWidget.Entry(b, id, b.getName().getString()));
                        }
                    }
                    all.sort(Comparator.comparing(ListPickerWidget.Entry::name, String.CASE_INSENSITIVE_ORDER));
                }
                return all;
            }

            public boolean selected(Object key) {
                Block b = (Block) key;
                return picker.selectedBlocks.keySet().containsAll(BlockPicker.familyOf(b));
            }

            public void toggle(Object key) {
                Block b = (Block) key;
                picker.toggle(b);
            }

            public int selectedCount() {
                int count = 0;
                for (ListPickerWidget.Entry e : entries()) {
                    if (selected(e.key())) count++;
                }
                return count;
            }

            public int dot(Object key) {
                Block b = (Block) key;
                return picker.selectedBlocks.getOrDefault(b, 0);
            }

            public SettingWidget editor(ListPickerWidget.Entry e) {
                Block b = (Block) e.key();
                ColorWidget w = new ColorWidget(picker, e.name(),
                        () -> picker.colorOf(b),
                        c -> { if (picker.selectedBlocks.containsKey(b)) picker.setColor(b, c); }, false);
                w.setOpen(true);
                return w;
            }
        };
    }

    private static ListPickerWidget.Source entities(EntityFilterPicker picker) {
        return new ListPickerWidget.Source() {
            public String label() { return "Entities"; }

            public List<ListPickerWidget.Entry> entries() { return entityEntries(); }

            public boolean selected(Object key) { return picker.selectedEntities.containsKey(key); }

            public void toggle(Object key) {
                EntityType<?> t = (EntityType<?>) key;
                if (picker.selectedEntities.remove(t) == null) {
                    picker.selectedEntities.put(t, EntityFilterPicker.DEFAULT_COLOR);
                    picker.excludedEntities.remove(t); // include and exclude are mutually exclusive
                }
            }

            public int selectedCount() { return picker.selectedEntities.size(); }

            public int dot(Object key) { return picker.selectedEntities.getOrDefault(key, 0); }

            public SettingWidget editor(ListPickerWidget.Entry e) {
                EntityType<?> t = (EntityType<?>) e.key();
                ColorWidget w = new ColorWidget(picker, e.name(),
                        () -> picker.selectedEntities.getOrDefault(t, EntityFilterPicker.DEFAULT_COLOR),
                        c -> { if (picker.selectedEntities.containsKey(t)) picker.selectedEntities.put(t, c); }, false);
                w.setOpen(true);
                return w;
            }
        };
    }

    private static List<ListPickerWidget.Entry> entityEntries;

    /** Every entity type, sorted by name (built once, shared by the include and exclude lists). */
    private static List<ListPickerWidget.Entry> entityEntries() {
        if (entityEntries == null) {
            List<ListPickerWidget.Entry> all = new ArrayList<>();
            for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
                all.add(new ListPickerWidget.Entry(t, id, t.getDescription().getString()));
            }
            all.sort(Comparator.comparing(ListPickerWidget.Entry::name, String.CASE_INSENSITIVE_ORDER));
            entityEntries = all;
        }
        return entityEntries;
    }

    /** Entity types that are never shown, even if their group is enabled. */
    private static ListPickerWidget.Source excludedEntities(EntityFilterPicker picker) {
        return new ListPickerWidget.Source() {
            public String label() { return "Excluded"; }

            public List<ListPickerWidget.Entry> entries() { return entityEntries(); }

            public boolean selected(Object key) { return picker.excludedEntities.contains(key); }

            public void toggle(Object key) {
                EntityType<?> t = (EntityType<?>) key;
                if (!picker.excludedEntities.remove(t)) {
                    picker.excludedEntities.add(t);
                    picker.selectedEntities.remove(t); // include and exclude are mutually exclusive
                }
            }

            public int selectedCount() { return picker.excludedEntities.size(); }
        };
    }

    private static ListPickerWidget.Source items(ItemPicker picker) {
        return new ListPickerWidget.Source() {
            private List<ListPickerWidget.Entry> all;

            public String label() { return picker.getLabel(); }

            public boolean hasIcons() { return true; }

            public net.minecraft.client.renderer.texture.TextureAtlasSprite icon(Object key) {
                return key instanceof net.minecraft.world.item.BlockItem bi ? blockIcon(bi.getBlock()) : null;
            }

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

            public String detail(Object key) {
                Integer price = picker.selectedEnchantments.get(key);
                return price == null ? null : "max " + price;
            }

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