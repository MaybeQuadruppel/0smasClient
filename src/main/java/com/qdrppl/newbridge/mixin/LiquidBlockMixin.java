package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.Hacks.Movement.Jesus;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public class LiquidBlockMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void onGetCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {

        Jesus jesus = (Jesus) ModuleManager.getModuleByName("Jesus");

        if (jesus != null && jesus.enabled) {

            if (context.isDescending()) {
                return;
            }

            boolean isLava = state.getFluidState().getType().isSame(net.minecraft.world.level.material.Fluids.LAVA);

            if (isLava && !jesus.walkOnLava) {
                return;
            }

            cir.setReturnValue(Shapes.block());
        }
    }
}