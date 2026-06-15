package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AttributeSwapping extends Module {

    public boolean spearSwap = true;
    public boolean maceSwap = true;

    private int ticksToWait = -1;
    private int originalSlot = -1;
    private boolean isSwapped = false;

    public AttributeSwapping() {
        super("AttrSwap", "Does Attribute swapping for you", Category.COMBAT);

        this.settings.add(new ToggleButton("Spear Lunge Swap", spearSwap, val -> spearSwap = val));
        this.settings.add(new ToggleButton("Mace/Sword Swap", maceSwap, val -> maceSwap = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) return;

        if (spearSwap && !isSwapped && client.options.keyAttack.isDown() && client.options.keySprint.isDown()) {
            ItemStack handItem = client.player.getMainHandItem();
            String itemName = BuiltInRegistries.ITEM.getKey(handItem.getItem()).getPath();


            if (!itemName.contains("sword") && !itemName.contains("axe") && !itemName.contains("mace") && !itemName.contains("spear")) {


                int spearSlot = findItemInHotbarByText(client, "spear");

                if (spearSlot != -1) {
                    this.originalSlot = client.player.getInventory().getSelectedSlot();


                    client.player.getInventory().setSelectedSlot(spearSlot);

                    this.isSwapped = true;
                    this.ticksToWait = 1;
                }
            }
        }


        if (isSwapped) {
            if (ticksToWait <= 0) {
                client.player.getInventory().setSelectedSlot(originalSlot);
                isSwapped = false;
                originalSlot = -1;
            } else {
                ticksToWait--;
            }
        }
    }

    @Override
    public void onAttack(Minecraft mc, LivingEntity target) {
        if (mc.player == null || isSwapped || !maceSwap || !mc.options.keySprint.isDown()) return;

        ItemStack handItem = mc.player.getMainHandItem();
        int targetSlot = -1;

        if (handItem.is(Items.NETHERITE_SWORD)) {
            targetSlot = findItemInHotbar(mc, Items.MACE);
        } else if (handItem.is(Items.MACE)) {
            targetSlot = findItemInHotbar(mc, Items.NETHERITE_SWORD);
        }

        if (targetSlot != -1) {
            this.originalSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(targetSlot);
            this.isSwapped = true;
            this.ticksToWait = 1;
        }
    }

    private int findItemInHotbar(Minecraft client, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findItemInHotbarByText(Minecraft client, String text) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (name.contains(text)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        isSwapped = false;
        originalSlot = -1;
        ticksToWait = -1;
    }
}