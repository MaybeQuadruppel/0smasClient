package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.BendHolder;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.BendRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives every ModelPart a bend state and makes the bend of a limb apply to the limb's cubes and its child parts. */
@Mixin(ModelPart.class)
public abstract class ModelPartBendMixin implements BendHolder {

    @Unique private boolean newbridge$bendOn;
    @Unique private float newbridge$pivot, newbridge$bx, newbridge$by, newbridge$bz;
    @Unique private BendHolder newbridge$prev;

    @Override
    public void newbridge$setBend(float pivotY, float xRot, float yRot, float zRot) {
        newbridge$pivot = pivotY;
        newbridge$bx = xRot;
        newbridge$by = yRot;
        newbridge$bz = zRot;
        newbridge$bendOn = Math.abs(xRot) > 1.0E-4f || Math.abs(yRot) > 1.0E-4f || Math.abs(zRot) > 1.0E-4f;
    }

    @Override public void newbridge$clearBend() { newbridge$bendOn = false; }
    @Override public boolean newbridge$hasBend() { return newbridge$bendOn; }
    @Override public float newbridge$bendPivotY() { return newbridge$pivot; }
    @Override public float newbridge$bendX() { return newbridge$bx; }
    @Override public float newbridge$bendY() { return newbridge$by; }
    @Override public float newbridge$bendZ() { return newbridge$bz; }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
    private void newbridge$enterBend(PoseStack stack, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        newbridge$prev = BendRender.current;
        if (newbridge$bendOn) BendRender.current = this;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("RETURN"))
    private void newbridge$leaveBend(PoseStack stack, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        BendRender.current = newbridge$prev;
        newbridge$prev = null;
    }
}
