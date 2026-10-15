package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Eats a golden apple from the hotbar when your health gets low. */
public class AutoGap extends Module {

    public static AutoGap INSTANCE;

    public double hearts = 6;
    public boolean enchanted = true;
    public double cooldown = 40;

    private boolean eating;
    private int eatTicks, cool, prevSlot = -1;

    public AutoGap() {
        super("AutoGap", "Eats a golden apple from the hotbar when your health is low.", Category.COMBAT);
        INSTANCE = this;
        settings.add(new Slider("Health", 1, 10, hearts, 0.5, v -> hearts = v).withDescription("Eat at or below this many hearts."));
        settings.add(new ToggleButton("Enchanted Apples", enchanted, v -> enchanted = v).withDescription("Also use enchanted golden apples."));
        settings.add(new Slider("Cooldown", 0, 200, cooldown, 5, v -> cooldown = v).withDescription("Ticks to wait between apples."));
    }

    @Override
    public void onDisable() { stop(Minecraft.getInstance()); }

    private void stop(Minecraft mc) {
        if (mc.options != null) mc.options.keyUse.setDown(false);
        if (prevSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
        prevSlot = -1;
        eating = false;
    }

    private int findApple(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(Items.GOLDEN_APPLE) || (enchanted && s.is(Items.ENCHANTED_GOLDEN_APPLE))) return i;
        }
        return -1;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gui.screen() != null || p.isCreative() || p.isSpectator()) return;
        if (cool > 0) cool--;
        if (eating) {
            eatTicks++;
            mc.options.keyUse.setDown(true);
            if (eatTicks > 5 && !p.isUsingItem()) {
                stop(mc);
                cool = (int) cooldown;
            } else if (eatTicks > 80) {
                stop(mc);
            }
            return;
        }
        if (cool > 0 || p.getHealth() > hearts * 2f) return;
        int slot = findApple(mc);
        if (slot < 0) return;
        prevSlot = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        eating = true;
        eatTicks = 0;
    }
}
