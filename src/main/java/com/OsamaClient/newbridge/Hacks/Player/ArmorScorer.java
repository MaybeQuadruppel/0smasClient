package com.OsamaClient.newbridge.Hacks.Player;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Bewertet Rüstungsteile (Rüstung, Härte, Rückstoßresistenz, Schutz-Verzauberungen, Haltbarkeit).
 * Ohne Client-Abhängigkeit -> headless testbar.
 */
public final class ArmorScorer {

    public enum Priority { PROTECTION, BLAST, FIRE, PROJECTILE }

    public record Prefs(Priority priority, boolean preferElytra, boolean avoidBinding, int minDurabilityPercent) {
        public static final Prefs DEFAULT = new Prefs(Priority.PROTECTION, false, true, 5);
    }

    private ArmorScorer() {}

    /** Ausrüstungsslot des Items oder null. */
    public static EquipmentSlot slotOf(ItemStack s) {
        if (s.isEmpty()) return null;
        Equippable eq = s.get(DataComponents.EQUIPPABLE);
        if (eq == null) return null;
        EquipmentSlot slot = eq.slot();
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
    }

    /** Punktzahl für {@code slot}; Double.NEGATIVE_INFINITY wenn das Item dort nicht hingehört. */
    public static double score(ItemStack s, EquipmentSlot slot, Prefs p) {
        if (s.isEmpty()) return 0;
        if (slotOf(s) != slot) return Double.NEGATIVE_INFINITY;
        double[] attr = new double[3];
        var mods = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            mods.forEach(EquipmentSlotGroup.bySlot(slot), (holder, modifier) -> {
                if (holder.is(Attributes.ARMOR)) attr[0] += modifier.amount();
                else if (holder.is(Attributes.ARMOR_TOUGHNESS)) attr[1] += modifier.amount();
                else if (holder.is(Attributes.KNOCKBACK_RESISTANCE)) attr[2] += modifier.amount();
            });
        }
        double score = attr[0] * 10 + attr[1] * 4 + attr[2] * 20;

        ItemEnchantments ench = s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var e : ench.entrySet()) {
            Holder<Enchantment> h = e.getKey();
            int lvl = e.getIntValue();
            ResourceKey<Enchantment> k = h.unwrapKey().orElse(null);
            if (k == null) continue;
            if (k.equals(Enchantments.PROTECTION)) score += lvl * (p.priority() == Priority.PROTECTION ? 6 : 4);
            else if (k.equals(Enchantments.BLAST_PROTECTION)) score += lvl * (p.priority() == Priority.BLAST ? 6 : 2);
            else if (k.equals(Enchantments.FIRE_PROTECTION)) score += lvl * (p.priority() == Priority.FIRE ? 6 : 2);
            else if (k.equals(Enchantments.PROJECTILE_PROTECTION)) score += lvl * (p.priority() == Priority.PROJECTILE ? 6 : 2);
            else if (k.equals(Enchantments.UNBREAKING)) score += lvl * 0.5;
            else if (k.equals(Enchantments.MENDING)) score += 1;
            else if (k.equals(Enchantments.BINDING_CURSE) && p.avoidBinding()) score -= 10_000;
        }

        if (s.is(Items.ELYTRA)) score = p.preferElytra() ? 100_000 : -1; // nur nehmen, wenn gewünscht

        if (s.isDamageableItem() && s.getMaxDamage() > 0) {
            int left = s.getMaxDamage() - s.getDamageValue();
            if (left * 100 < p.minDurabilityPercent() * s.getMaxDamage()) score -= 5_000; // fast kaputt
        }
        return score;
    }

    /**
     * Index (0..35 im Spielerinventar) des besten Teils für den Slot, das besser ist als das getragene;
     * -1 wenn nichts besser ist.
     */
    public static int bestFor(EquipmentSlot slot, ItemStack worn, java.util.function.IntFunction<ItemStack> inventory, int size, Prefs p) {
        double current = worn.isEmpty() ? 0 : score(worn, slot, p);
        // Ein verfluchtes Teil kann man ohnehin nicht ablegen
        if (!worn.isEmpty() && hasBinding(worn)) return -1;
        int best = -1;
        double bestScore = current;
        for (int i = 0; i < size; i++) {
            ItemStack s = inventory.apply(i);
            if (s.isEmpty() || slotOf(s) != slot) continue;
            double sc = score(s, slot, p);
            if (sc > bestScore + 1e-6 && (worn.isEmpty() ? sc > 0 : true)) {
                bestScore = sc;
                best = i;
            }
        }
        return best;
    }

    public static boolean hasBinding(ItemStack s) {
        ItemEnchantments ench = s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> h : ench.keySet()) if (h.is(Enchantments.BINDING_CURSE)) return true;
        return false;
    }
}
