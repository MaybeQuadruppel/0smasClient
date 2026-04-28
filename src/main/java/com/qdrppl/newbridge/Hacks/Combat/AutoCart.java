package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public class AutoCart extends Module {
    private boolean wasUsingItem = false;

    public AutoCart() {
        super("AutoCart", "Automatically places TNT-Minecarts where your arrow lands", Category.COMBAT);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || !this.enabled) return;

        boolean isUsingItem = client.player.isUsingItem();


        if (wasUsingItem && !isUsingItem) {
            ItemStack bow = client.player.getMainHandItem();
            if (bow.getItem() instanceof BowItem) {
                onArrowShoot(client);
            }
        }
        wasUsingItem = isUsingItem;
    }

    public void onArrowShoot(Minecraft client) {
        ItemStack bow = client.player.getMainHandItem();
        var enchantmentRegistry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        boolean isFlame = EnchantmentHelper.getItemEnchantmentLevel(enchantmentRegistry.getOrThrow(Enchantments.FLAME), bow) > 0;

        if (!isFlame) return;

        BlockHitResult hit = getLandingResult(client);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos baseBlock = hit.getBlockPos();
        BlockPos railPos = baseBlock.relative(hit.getDirection());
        double distanceSq = client.player.position().distanceToSqr(Vec3.atCenterOf(railPos));

        if (distanceSq > 36.0 || distanceSq < 0.5) {
            return;
        }

        executeAutoTNT(client, railPos, baseBlock);
    }

    private BlockHitResult getLandingResult(Minecraft client) {
        int useDuration = client.player.getUseItemRemainingTicks();
        int ticksHeld = client.player.getUseItem().getUseDuration(client.player) - useDuration;
        float pull = BowItem.getPowerForTime(ticksHeld);

        Vec3 lookVec = client.player.getLookAngle();

        Vec3 pos = client.player.getEyePosition().add(lookVec.scale(1.1)).subtract(0, 0.1, 0);
        Vec3 motion = lookVec.scale(pull * 2.8f);

        for (int i = 0; i < 100; i++) {
            Vec3 nextPos = pos.add(motion);

            BlockHitResult hit = client.level.clip(new ClipContext(
                    pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                return hit;
            }

            pos = nextPos;
            motion = motion.scale(0.99).subtract(0, 0.05, 0);
        }
        return null;
    }

    private void executeAutoTNT(Minecraft client, BlockPos railPos, BlockPos baseBlock) {
        int railSlot = findItemInHotbar(Items.RAIL);
        int tntCartSlot = findItemInHotbar(Items.TNT_MINECART);

        if (railSlot == -1 || tntCartSlot == -1) return;

        int oldSlot = client.player.getInventory().getSelectedSlot();

        client.player.getInventory().setSelectedSlot(railSlot);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(railPos), Direction.UP, baseBlock, false));

        client.player.getInventory().setSelectedSlot(tntCartSlot);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(railPos), Direction.UP, railPos, false));

        client.player.getInventory().setSelectedSlot(oldSlot);
    }

    private int findItemInHotbar(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (Minecraft.getInstance().player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }
}