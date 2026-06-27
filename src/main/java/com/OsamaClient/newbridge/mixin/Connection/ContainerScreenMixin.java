package com.OsamaClient.newbridge.mixin.Connection;

//import com.qdrppl.newbridge.Hacks.Dupeing.ContainerOverlay;
//import net.minecraft.client.gui.GuiGraphicsExtractor;
//import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
//import net.minecraft.client.input.MouseButtonEvent;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//
//@Mixin(AbstractContainerScreen.class)
//public class ContainerScreenMixin {
//
//    // ── Render ────────────────────────────────────────────────────────────────
//
//    @Inject(method = "extractRenderState", at = @At("TAIL"))
//    private void onRender(GuiGraphicsExtractor graphics,
//                          int mouseX, int mouseY, float delta,
//                          CallbackInfo ci) {
//        ContainerOverlay.draw(graphics, mouseX, mouseY);
//    }
//
//    // ── Mouse click ───────────────────────────────────────────────────────────
//
//    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
//    private void onMouseClicked(MouseButtonEvent event,
//                                boolean isDoubleClick,
//                                CallbackInfoReturnable<Boolean> cir) {
//        if (ContainerOverlay.handleClick(event.x(), event.y(), event.button())) {
//            cir.setReturnValue(true);
//            cir.cancel();
//        }
//    }
//
//    // ── Mouse drag ────────────────────────────────────────────────────────────
//    // FIX: mouseDragged still uses the classic (double,double,int,double,double)
//    // signature in 26.1.2 — only mouseClicked/mouseReleased use MouseButtonEvent.
//
//    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
//    private void onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
//        if (button == 0 && ContainerOverlay.handleDrag(mouseX, mouseY)) {
//            cir.setReturnValue(true);
//            cir.cancel();
//        }
//    }
//
//    // ── Mouse release ─────────────────────────────────────────────────────────
//
//    @Inject(method = "mouseReleased", at = @At("HEAD"))
//    private void onMouseReleased(MouseButtonEvent event,
//                                 CallbackInfoReturnable<Boolean> cir) {
//        ContainerOverlay.handleRelease();
//    }
//}