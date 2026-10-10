package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Switches to the fastest hotbar tool for the block you are mining. */
public class AutoTool extends Module {

    private boolean switchBack = true;
    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", "Selects the best tool in your hotbar while mining.", Category.PLAYER);
        settings.add(new ToggleButton("Switch Back", switchBack, v -> switchBack = v)
                .withDescription("Return to the previous slot when you stop mining"));
    }

    @Override
    public void onDisable() {
        restore(Minecraft.getInstance());
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null) return;

        boolean mining = mc.options.keyAttack.isDown()
                && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.hitResult instanceof BlockHitResult;

        if (!mining) {
            restore(mc);
            return;
        }

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        var inv = p.getInventory();
        int bestSlot = -1;
        float bestSpeed = inv.getItem(inv.getSelectedSlot()).getDestroySpeed(state);
        for (int i = 0; i < 9; i++) {
            float s = inv.getItem(i).getDestroySpeed(state);
            if (s > bestSpeed) {
                bestSpeed = s;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0 && bestSlot != inv.getSelectedSlot()) {
            if (previousSlot < 0) previousSlot = inv.getSelectedSlot();
            inv.setSelectedSlot(bestSlot);
        }
    }

    private void restore(Minecraft mc) {
        if (switchBack && previousSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
    }
}