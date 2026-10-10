package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditor;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditorScreen;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the extracted entity state of the Emote Editor preview and applies the orbit yaw to it.
 * Only active while the editor screen is open, so the normal inventory preview is never touched.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.InventoryScreen")
public abstract class InventoryScreenMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN")
    )
    private static void newbridge$markEmotePreview(
            LivingEntity entity,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {
        EntityRenderState state = cir.getReturnValue();
        if (state == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || !(mc.gui.screen() instanceof EmoteEditorScreen)) return;

        ((FabricRenderState) state).setData(EmoteEditor.PREVIEW_KEY, Boolean.TRUE);

        // Full 360 degree rotation: vanilla's helper only turns the model by ~30 degrees.
        // bodyRot 180 = facing the viewer; head is kept straight relative to the camera.
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = EmoteEditor.INSTANCE.previewBodyYaw();
            living.yRot = EmoteEditor.INSTANCE.previewHeadYaw();
            living.xRot = 0f;
        }
    }
}