package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Surround: boxes your feet in with blocks (obsidian first) and repairs holes. Based on the Surround module of
 * the Autism client (GPL-3.0), simplified: it places on the floor next to you, so every neighbour needs ground.
 */
public class Surround extends Module {

    public static Surround INSTANCE;

    public double blocksPerTick = 2;
    public boolean center = true;
    public boolean disableOnJump = false;
    public int placed;

    public Surround() {
        super("Surround", "Places blocks around your feet so crystals and anchors cannot reach you.", Category.COMBAT);
        INSTANCE = this;
        settings.add(new Slider("Blocks Per Tick", 1, 4, blocksPerTick, 1, v -> blocksPerTick = v).withDescription("How many blocks are placed each tick."));
        settings.add(new ToggleButton("Center", center, v -> center = v).withDescription("Move to the middle of your block before placing."));
        settings.add(new ToggleButton("Disable On Jump", disableOnJump, v -> disableOnJump = v).withDescription("Turn off when you leave the ground."));
    }

    private static int findSlot(net.minecraft.world.entity.player.Player p) {
        int any = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem)) continue;
            if (s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN)) return i;
            if (any < 0) any = i;
        }
        return any;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (disableOnJump && !p.onGround()) { toggle(); return; }
        if (!p.onGround()) return;
        if (center) {
            double cx = Math.floor(p.getX()) + 0.5, cz = Math.floor(p.getZ()) + 0.5;
            if (Math.abs(p.getX() - cx) > 0.2 || Math.abs(p.getZ() - cz) > 0.2) {
                Vec3 d = new Vec3(cx - p.getX(), 0, cz - p.getZ());
                p.setDeltaMovement(d.x * 0.5, p.getDeltaMovement().y, d.z * 0.5);
            }
        }
        BlockPos feet = p.blockPosition();
        int slot = findSlot(p);
        if (slot < 0) return;
        int budget = (int) blocksPerTick;
        int prev = p.getInventory().getSelectedSlot();
        boolean switched = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (budget <= 0) break;
            BlockPos pos = feet.relative(d);
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
            if (!mc.level.getEntities(p, new net.minecraft.world.phys.AABB(pos), e -> !e.isSpectator() && e.blocksBuilding).isEmpty()) continue;
            BlockPos support = pos.below();
            if (mc.level.getBlockState(support).isAir() || mc.level.getBlockState(support).canBeReplaced()) continue;
            if (!switched) { p.getInventory().setSelectedSlot(slot); switched = true; }
            Vec3 hitPos = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hitPos, Direction.UP, support, false));
            com.OsamaClient.newbridge.Utils.SwingUtil.swing(p, InteractionHand.MAIN_HAND);
            placed++;
            budget--;
        }
        if (switched) p.getInventory().setSelectedSlot(prev);
    }
}
