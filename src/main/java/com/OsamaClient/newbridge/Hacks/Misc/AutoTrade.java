package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.Hacks.Movement.pathing.MiningAction;
import com.OsamaClient.newbridge.UI.components.EnchantmentPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AutoTrade extends Module {

    // --- Settings ---
    private EnchantmentPicker enchantPicker;
    private double maxPrice = 20.0;
    private boolean autoClose = true;

    // --- State Machine Variablen ---
    private enum State {
        FINDING_TARGETS, INTERACTING, WAITING_FOR_MENU, BREAKING_LECTERN, PLACING_LECTERN
    }

    private State currentState = State.FINDING_TARGETS;
    private Villager targetVillager = null;
    private BlockPos lecternPos = null;
    private int actionTimer = 0;

    public AutoTrade() {
        super("AutoLibrarian", "Automatically rolls for specific enchanted books.", Category.MISC);

        enchantPicker = new EnchantmentPicker("Enchantments");
        this.settings.add(enchantPicker);

        this.settings.add(new Slider("Max Price", 1.0, 64.0, maxPrice, val -> maxPrice = val));
        this.settings.add(new ToggleButton("Auto Close", autoClose, val -> autoClose = val));
    }

    @Override
    public void onEnable() {
        currentState = State.FINDING_TARGETS;
        targetVillager = null;
        lecternPos = null;
        actionTimer = 0;
        MiningAction.resetMiningState();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (mc.gui.screen() instanceof MerchantScreen merchantScreen) {
            checkTrades(mc, merchantScreen);
            return;
        }

        if (actionTimer > 0) {
            actionTimer--;
            return;
        }

        switch (currentState) {
            case FINDING_TARGETS:
                findTargets(mc);
                break;

            case INTERACTING:
                if (targetVillager != null && targetVillager.isAlive()) {
                    // FIX: Die neue Methode .is() zum Vergleichen von Holder und ResourceKey
                    if (targetVillager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                        net.minecraft.world.phys.EntityHitResult hitResult = new net.minecraft.world.phys.EntityHitResult(targetVillager);
                        mc.gameMode.interact(mc.player, targetVillager, hitResult, InteractionHand.MAIN_HAND);

                        currentState = State.WAITING_FOR_MENU;
                        actionTimer = 20;
                    }
                } else {
                    currentState = State.FINDING_TARGETS;
                }
                break;

            case WAITING_FOR_MENU:
                currentState = State.BREAKING_LECTERN;
                break;

            case BREAKING_LECTERN:
                if (lecternPos == null) {
                    currentState = State.FINDING_TARGETS;
                    return;
                }

                if (mc.level.getBlockState(lecternPos).isAir()) {
                    MiningAction.resetMiningState();
                    currentState = State.PLACING_LECTERN;
                    actionTimer = 10;
                } else {
                    MiningAction.mineBlock(mc, lecternPos);
                }
                break;

            case PLACING_LECTERN:
                int lecternSlot = findLecternInHotbar(mc);
                if (lecternSlot == -1) {
                    this.toggle();
                    return;
                }

                int oldSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(lecternSlot);

                BlockPos blockBelow = lecternPos.below();
                BlockHitResult hitResult = new BlockHitResult(
                        Vec3.atCenterOf(blockBelow).add(0, 0.5, 0),
                        Direction.UP,
                        blockBelow,
                        false
                );

                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
                mc.player.swing(InteractionHand.MAIN_HAND);

                mc.player.getInventory().setSelectedSlot(oldSlot);

                currentState = State.INTERACTING;
                actionTimer = 30;
                break;
        }
    }

    private void checkTrades(Minecraft mc, MerchantScreen merchantScreen) {
        boolean foundTarget = false;
        var offers = merchantScreen.getMenu().getOffers();

        for (int i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            ItemStack resultItem = offer.getResult();

            if (resultItem.is(Items.ENCHANTED_BOOK)) {
                int price = offer.getBaseCostA().getCount();

                var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(resultItem).keySet();

                for (var enchantHolder : enchantments) {
                    String enchantName = enchantHolder.value().description().getString().toLowerCase();

                    for (String target : enchantPicker.selectedEnchantments.keySet()) {
                        if (enchantName.contains(target.toLowerCase()) && price <= maxPrice) {
                            foundTarget = true;
                            break;
                        }
                    }
                    if (foundTarget) break;
                }
            }
        }

        if (foundTarget) {
            this.toggle();
        } else if (autoClose) {
            mc.player.closeContainer();
            currentState = State.BREAKING_LECTERN;
            actionTimer = 5;
        }
    }

    private void findTargets(Minecraft mc) {
        AABB box = mc.player.getBoundingBox().inflate(5);
        List<Villager> villagers = mc.level.getEntitiesOfClass(Villager.class, box);

        if (villagers.isEmpty()) return;

        targetVillager = villagers.get(0);

        BlockPos playerPos = mc.player.blockPosition();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LECTERN)) {
                        lecternPos = pos;
                        currentState = State.INTERACTING;
                        return;
                    }
                }
            }
        }
    }

    private int findLecternInHotbar(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.LECTERN)) {
                return i;
            }
        }
        return -1;
    }
}