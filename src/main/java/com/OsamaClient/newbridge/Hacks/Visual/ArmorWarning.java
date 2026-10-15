package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Warns in the action bar when an armor piece is about to break. */
public class ArmorWarning extends Module {

    public static ArmorWarning INSTANCE;

    public double percent = 15;
    public String lastWarning;
    private int cooldown;

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] NAMES = {"Helmet", "Chestplate", "Leggings", "Boots"};

    public ArmorWarning() {
        super("ArmorWarning", "Action bar warning when armor durability is low.", Category.VISUAL);
        INSTANCE = this;
        settings.add(new Slider("Threshold", 1, 50, percent, 1, v -> percent = v).withDescription("Warn below this percent of durability."));
    }

    /** Text for the most worn armor piece below the threshold, or null. */
    public String check(Minecraft mc) {
        String worst = null;
        float worstRatio = 1f;
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack s = mc.player.getItemBySlot(SLOTS[i]);
            if (s.isEmpty() || !s.isDamageableItem()) continue;
            float ratio = 1f - (float) s.getDamageValue() / s.getMaxDamage();
            if (ratio * 100f < percent && ratio < worstRatio) {
                worstRatio = ratio;
                worst = NAMES[i] + " low: " + Math.round(ratio * 100f) + "%";
            }
        }
        return worst;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        if (cooldown > 0) { cooldown--; return; }
        String w = check(mc);
        lastWarning = w;
        if (w != null) {
            Notify.bar(w);
            cooldown = 100;
        }
    }
}
