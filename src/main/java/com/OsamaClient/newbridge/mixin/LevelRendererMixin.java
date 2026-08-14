package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.Render.Events.Render3DEvent;
import com.OsamaClient.newbridge.Utils.Render.Renderer3D;
import com.OsamaClient.newbridge.Utils.Render.Utils;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;
    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow @Final private LevelTargetBundle targets;
    @Shadow @Final @Mutable private RenderTarget entityOutlineTarget;

    @Unique private FeatureRenderDispatcher osama$renderDispatcher;
    @Unique private final Stack<RenderTarget> osama$framebufferStack = new ObjectArrayList<>();
    @Unique private final Stack<ResourceHandle<RenderTarget>> osama$framebufferHandleStack = new ObjectArrayList<>();

    // ------------------------------------------------------------------------
    // 1. 3D-RENDER HOOK (BOXEN, TRACERS, ESP)
    // ------------------------------------------------------------------------
    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void onRender3D(
            GraphicsResourceAllocator resourceAllocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci
    ) {
        PoseStack poseStack = new PoseStack();

        // WICHTIG: Die echte Kamera-Matrix (Position + Sichtwinkel) in den PoseStack laden!
        poseStack.last().pose().set(modelViewMatrix);

        Utils.rendering3D = true;

        Renderer3D renderer = EntryPoint.INSTANCE.getRenderer3D();
        renderer.begin();

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        var cameraPos = cameraState.pos;

        Render3DEvent event = Render3DEvent.get(
                poseStack,
                renderer,
                renderer,
                tickDelta,
                cameraPos.x,
                cameraPos.y,
                cameraPos.z
        );

        EntryPoint.INSTANCE.getEventBus().post(event);
        renderer.render(poseStack);

        Utils.rendering3D = false;
    }

    // ------------------------------------------------------------------------
    // 2. VANILLA OVERRIDES & MIXIN HOOKS
    // ------------------------------------------------------------------------

    @Inject(method = "checkPoseStack", at = @At("HEAD"), cancellable = true)
    private void onCheckPoseStack(PoseStack poseStack, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawHighlightedHitOutline(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LevelRenderState levelRenderState, CallbackInfo ci) {
        Module mod = ModuleManager.getModuleByName("BlockSelection");
        if (mod != null && mod.enabled) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(
            method = "addSkyPass",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;doesMobEffectBlockSky:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean modifyMobEffectBlocksSky(boolean original) {
        Module mod = ModuleManager.getModuleByName("NoRender");
        if (mod != null && mod.enabled) {
            return false;
        }
        return original;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
    }

    // ------------------------------------------------------------------------
    // 3. HELFER FÜR FRAMEBUFFER-SWITCHING
    // ------------------------------------------------------------------------
    public void osama$pushEntityOutlineFramebuffer(RenderTarget framebuffer) {
        osama$framebufferStack.push(this.entityOutlineTarget);
        this.entityOutlineTarget = framebuffer;

        osama$framebufferHandleStack.push(this.targets.entityOutline);
        this.targets.entityOutline = () -> framebuffer;
    }

    public void osama$popEntityOutlineFramebuffer() {
        this.entityOutlineTarget = osama$framebufferStack.pop();
        this.targets.entityOutline = osama$framebufferHandleStack.pop();
    }
}