package com.OsamaClient.newbridge.Hacks.Player;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * AirPlace: right click places the held block where you are looking even if no block is there.
 * Based on the AirPlace module of the Autism client (GPL-3.0).
 */
public class AirPlace extends Module {

    public static AirPlace INSTANCE;

    public double range = 5;
    private int cooldown;
    /** Number of blocks placed (tests / debugging). */
    public int placed;

    public AirPlace() {
        super("AirPlace", "Place blocks in mid air by right clicking where you look.", Category.PLAYER);
        INSTANCE = this;
        settings.add(new Slider("Range", 1, 6, range, 0.5, v -> range = v).withDescription("How far away you can place."));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (!mc.options.keyUse.isDown() || p.isHandsBusy()) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.MISS) return;
        InteractionHand hand = hand(p);
        if (hand == null) return;
        HitResult picked = p.pick(Math.max(1, Math.min(6, range)), 0f, false);
        if (!(picked instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        if (mc.level.isOutsideBuildHeight(pos) || !mc.level.getBlockState(pos).canBeReplaced()) return;
        InteractionResult r = mc.gameMode.useItemOn(p, hand, hit);
        if (r instanceof InteractionResult.Success) {
            com.OsamaClient.newbridge.Utils.SwingUtil.swing(p, hand);
            placed++;
        }
        cooldown = 3;
    }

    private static InteractionHand hand(net.minecraft.world.entity.player.Player p) {
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof BlockItem) return InteractionHand.MAIN_HAND;
        if (main.isEmpty() && p.getOffhandItem().getItem() instanceof BlockItem) return InteractionHand.OFF_HAND;
        return null;
    }
}
