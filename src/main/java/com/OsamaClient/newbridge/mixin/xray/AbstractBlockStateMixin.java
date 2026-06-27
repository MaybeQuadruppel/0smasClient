package com.OsamaClient.newbridge.mixin.xray;

import com.OsamaClient.newbridge.Hacks.Visual.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes; // Genau das ist die richtige Klasse aus deinem Screenshot!
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class AbstractBlockStateMixin {

    @Shadow public abstract Block getBlock();

    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true)
    private void onSkipRendering(BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (XRay.isActive) {
            Block myBlock = this.getBlock();
            Block neighborBlock = neighborState.getBlock();

            boolean IAmSupported = XRay.isSupported(myBlock);
            boolean neighborSupported = XRay.isSupported(neighborBlock);

            if (IAmSupported) {
                if (!neighborSupported) {
                    cir.setReturnValue(false);
                } else {
                    cir.setReturnValue(myBlock == neighborBlock);
                }
            } else {
                cir.setReturnValue(true);
            }
        }
    }

    // DER FINALE FIX: Hebt das Occlusion Culling über die korrekte Shapes-Klasse auf
    @Inject(method = "getFaceOcclusionShape", at = @At("HEAD"), cancellable = true)
    private void onGetFaceOcclusionShape(Direction direction, CallbackInfoReturnable<VoxelShape> cir) {
        if (XRay.isActive && !XRay.isSupported(this.getBlock())) {
            // Shapes.empty() liefert das leere Shape in deinen Mappings!
            cir.setReturnValue(Shapes.empty());
        }
    }

    @Inject(method = "useShapeForLightOcclusion", at = @At("HEAD"), cancellable = true)
    private void onUseShapeForLightOcclusion(CallbackInfoReturnable<Boolean> cir) {
        if (XRay.isActive && !XRay.isSupported(this.getBlock())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getLightDampening", at = @At("HEAD"), cancellable = true)
    private void onGetLightDampening(CallbackInfoReturnable<Integer> cir) {
        if (XRay.isActive && !XRay.isSupported(this.getBlock())) {
            cir.setReturnValue(0);
        }
    }
}