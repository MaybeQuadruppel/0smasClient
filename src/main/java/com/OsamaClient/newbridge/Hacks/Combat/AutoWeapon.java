package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/** Switches to the best weapon in the hotbar when you attack an entity. */
public class AutoWeapon extends Module {

    public static AutoWeapon INSTANCE;

    public boolean preferAxe = false;
    public boolean switchBack = true;

    private int previousSlot = -1;

    public AutoWeapon() {
        super("AutoWeapon", "Selects the best weapon in your hotbar when you hit something.", Category.COMBAT);
        INSTANCE = this;
        settings.add(new ToggleButton("Prefer Axe", preferAxe, v -> preferAxe = v).withDescription("Prefer axes over swords of similar damage"));
        settings.add(new ToggleButton("Switch Back", switchBack, v -> switchBack = v).withDescription("Return to the previous slot after attacking"));
    }

    public static double score(ItemStack stack, boolean preferAxe) {
        if (stack.isEmpty()) return 0;
        double dmg = 0;
        var mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for (var e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ATTACK_DAMAGE) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                dmg += e.modifier().amount();
        }
        boolean weapon = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
        if (!weapon) return 0;
        if (preferAxe && stack.is(ItemTags.AXES)) dmg += 1.5;
        if (!preferAxe && stack.is(ItemTags.SWORDS)) dmg += 1.5;
        return dmg;
    }

    /** Best hotbar slot or -1. */
    public static int bestSlot(net.minecraft.world.entity.player.Inventory inv, boolean preferAxe) {
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < 9; i++) {
            double s = score(inv.getItem(i), preferAxe);
            if (s > bestScore) { bestScore = s; best = i; }
        }
        return best;
    }

    @Override
    public void onDisable() { previousSlot = -1; }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gui.screen() != null) return;
        boolean attacking = mc.options.keyAttack.isDown() && mc.hitResult instanceof EntityHitResult;
        var inv = p.getInventory();
        if (attacking) {
            int best = bestSlot(inv, preferAxe);
            if (best >= 0 && best != inv.getSelectedSlot()) {
                if (previousSlot < 0) previousSlot = inv.getSelectedSlot();
                inv.setSelectedSlot(best);
            }
        } else if (previousSlot >= 0) {
            if (switchBack) inv.setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }
}
