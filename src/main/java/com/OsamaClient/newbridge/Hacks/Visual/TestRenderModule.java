package com.OsamaClient.newbridge.Hacks.Visual;
//
//import com.OsamaClient.newbridge.EntryPoint;
//import com.OsamaClient.newbridge.UI.components.Module;
//import com.OsamaClient.newbridge.Utils.Render.Color;
//import com.OsamaClient.newbridge.Utils.Render.Events.EventHandler;
//import com.OsamaClient.newbridge.Utils.Render.Events.Render3DEvent;
//import com.OsamaClient.newbridge.Utils.Render.Renderer3D;
//import net.minecraft.client.Minecraft;
//import net.minecraft.util.Mth;
//import net.minecraft.world.entity.monster.cubemob.Slime;
//
//public class TestRenderModule extends Module {
//
//    public TestRenderModule() {
//        super("TestRender", "Renders a box around all slimes in the world", Category.VISUAL);
//        EntryPoint.EVENT_BUS.subscribe(this);
//    }
//
//    @EventHandler
//    public void onRender3D(Render3DEvent event) {
//        Minecraft mc = Minecraft.getInstance();
//        if (mc.level == null) return;
//
//        float tickDelta = event.tickDelta;
//
//        for (var entity : mc.level.entitiesForRendering()) {
//            if (entity instanceof Slime slime) {
//                double x = Mth.lerp(tickDelta, slime.xo, slime.getX()) - event.offsetX;
//                double y = Mth.lerp(tickDelta, slime.yo, slime.getY()) - event.offsetY;
//                double z = Mth.lerp(tickDelta, slime.zo, slime.getZ()) - event.offsetZ;
//
//                float yaw = Mth.lerp(tickDelta, slime.yBodyRotO, slime.yBodyRot);
//
//                float w = slime.getBbWidth() / 2.0f;
//                float h = slime.getBbHeight();
//
//                event.renderer.boxRotatedNoDepth(
//                        x, y, z,
//                        w, h, yaw,
//                        new Color(255, 0, 0, 100),
//                        new Color(255, 0, 0, 255),
//                        Renderer3D.ShapeMode.BOTH
//                );
//            }
//        }
//    }
//}