package com.qdrppl.newbridge.Hacks.Movement;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class Jesus extends Module {

    public boolean walkOnLava = true;

    public Jesus() {
        super("Jesus", "Allows you to walk on water and lava like a solid block.", Category.MOVEMENT);
        this.settings.add(new ToggleButton("Lava Support", walkOnLava, val -> walkOnLava = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        if (client.options.keyShift.isDown()) return;
        BlockPos pos = BlockPos.containing(client.player.getX(), client.player.getY(), client.player.getZ());
        FluidState fluidState = client.level.getFluidState(pos);

        if (!fluidState.isEmpty()) {
            boolean isLava = fluidState.getType().isSame(net.minecraft.world.level.material.Fluids.LAVA);
            if (isLava && !walkOnLava) return;
            Vec3 velocity = client.player.getDeltaMovement();
            client.player.setDeltaMovement(velocity.x, 0.12, velocity.z);
            client.player.resetFallDistance();
        }

        BlockPos posBelow = BlockPos.containing(client.player.getX(), client.player.getY() - 0.05, client.player.getZ());
        if (!client.level.getFluidState(posBelow).isEmpty()) {
            client.player.resetFallDistance();
        }
    }
}