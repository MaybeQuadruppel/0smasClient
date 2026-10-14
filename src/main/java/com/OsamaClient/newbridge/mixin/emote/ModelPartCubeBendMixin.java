package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.BendHolder;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.BendRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a limb bend is active, cubes are drawn strip-subdivided and bent instead of the plain box. */
@Mixin(ModelPart.Cube.class)
public abstract class ModelPartCubeBendMixin {

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void newbridge$bentCompile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        BendHolder bend = BendRender.current;
        if (bend == null) return;
        BendRender.compile((ModelPart.Cube) (Object) this, pose, consumer, light, overlay, color, bend);
        ci.cancel();
    }
}
