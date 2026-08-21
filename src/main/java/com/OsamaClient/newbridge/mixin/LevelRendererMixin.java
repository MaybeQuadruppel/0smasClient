package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsRenderQueue;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At("HEAD")
    )
    private void newbridge$beginDelayedOverlays(GraphicsResourceAllocator allocator,
                                                DeltaTracker tickCounter, boolean renderBlockOutline,
                                                CameraRenderState cameraState, Matrix4fc positionMatrix,
                                                GpuBufferSlice gpuBufferSlice, Vector4f vector4f,
                                                boolean shouldRenderSky, CallbackInfo ci) {
        // Chams-Queue zu Beginn des Frames leeren
        ChamsRenderQueue.clear();
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At("RETURN")
    )
    private void newbridge$fireRender3DEvent(GraphicsResourceAllocator allocator,
                                             DeltaTracker tickCounter, boolean renderBlockOutline,
                                             CameraRenderState cameraState, Matrix4fc positionMatrix,
                                             GpuBufferSlice gpuBufferSlice, Vector4f vector4f,
                                             boolean shouldRenderSky, CallbackInfo ci) {

        PoseStack matrixStack = new PoseStack();
        matrixStack.mulPose(positionMatrix);

        // Zugriff auf die Kamera-Position
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        double camX = camera.position().x();
        double camY = camera.position().y();
        double camZ = camera.position().z();

        // 1. Chams ausführen, falls welche in der Queue liegen
        if (ChamsRenderQueue.hasPending()) {
            ChamsRenderQueue.flush(matrixStack);
        }

        // 2. Dein allgemeines 3D-Render-Event (für Block-ESP, EnderChest-ESP etc.) feuern
        Render3DEvent event = new Render3DEvent(matrixStack, (float) tickCounter.getGameTimeDeltaTicks(), camX, camY, camZ);
        EntryPoint.EVENT_BUS.post(event);
    }
}