//
//package com.OsamaClient.newbridge.Hacks.Visual;
//
//import com.OsamaClient.newbridge.EntryPoint;
//import com.OsamaClient.newbridge.UI.components.Module;
//import com.OsamaClient.newbridge.UI.components.Slider;
//import com.OsamaClient.newbridge.UI.components.ToggleButton;
//import com.OsamaClient.newbridge.event.Render3DEvent;
//import com.OsamaClient.newbridge.event.Subscribe;
//import com.mojang.blaze3d.vertex.PoseStack;
//import net.minecraft.client.Camera;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.Font;
//import net.minecraft.client.renderer.;
//import net.minecraft.network.chat.Component;
//import net.minecraft.util.Mth;
//import net.minecraft.world.entity.Entity;
//import net.minecraft.world.entity.LivingEntity;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.phys.Vec3;
//import org.joml.Matrix4f;
//
//public class Nametags extends Module {
//
//    public boolean renderPlayers = true;
//    public boolean renderMobs = false;
//    public boolean showHealth = true;
//    public boolean showDistance = true;
//    public double renderDistance = 64.0;
//    public double scale = 1.0;
//
//    public Nametags() {
//        super("Nametags", "Draws clean 3D Billboard Nametags.", Category.VISUAL);
//
//        this.settings.add(new ToggleButton("Players", renderPlayers, val -> renderPlayers = val));
//        this.settings.add(new ToggleButton("Mobs", renderMobs, val -> renderMobs = val));
//        this.settings.add(new ToggleButton("Show Health", showHealth, val -> showHealth = val));
//        this.settings.add(new ToggleButton("Show Distance", showDistance, val -> showDistance = val));
//        this.settings.add(new Slider("Render Distance", 10.0, 256.0, renderDistance, val -> renderDistance = val));
//        this.settings.add(new Slider("Scale", 0.5, 2.0, scale, val -> scale = val));
//
//        EntryPoint.EVENT_BUS.subscribe(this);
//    }
//
//    @Subscribe
//    public void onRender3D(Render3DEvent event) {
//        if (!this.enabled) return;
//
//        Minecraft client = Minecraft.getInstance();
//        if (client.level == null || client.player == null || client.gameRenderer.mainCamera() == null) return;
//
//        PoseStack poseStack = event.getPoseStack();
//        Camera camera = client.gameRenderer.mainCamera();
//        Vec3 camPos = camera.position();
//        float tickDelta = event.getTickDelta();
//
//        // Haupt-BufferSource des Spiels abfragen (wird von Minecraft automatisch gezeichnet)
//        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
//        double maxDistSqr = renderDistance * renderDistance;
//
//        for (Entity entity : client.level.entitiesForRendering()) {
//            if (!(entity instanceof LivingEntity living) || entity == client.player || !entity.isAlive()) continue;
//            if (client.player.distanceToSqr(entity) > maxDistSqr) continue;
//
//            boolean isPlayer = entity instanceof Player;
//            if ((isPlayer && renderPlayers) || (!isPlayer && renderMobs)) {
//
//                // Position über dem Kopf berechnen
//                double x = Mth.lerp(tickDelta, entity.xo, entity.getX()) - camPos.x;
//                double y = Mth.lerp(tickDelta, entity.yo, entity.getY()) - camPos.y + entity.getBbHeight() + 0.5;
//                double z = Mth.lerp(tickDelta, entity.zo, entity.getZ()) - camPos.z;
//
//                poseStack.pushPose();
//                poseStack.translate(x, y, z);
//
//                // Rotation zur Kamera
//                poseStack.mulPose(camera.rotation());
//
//                // Skalierung
//                float dist = client.player.distanceTo(living);
//                float fontScale = Math.max(1.0f, dist / 10.0f) * (float) scale * 0.025f;
//                poseStack.scale(-fontScale, -fontScale, fontScale);
//
//                // Text als Component erstellen
//                StringBuilder builder = new StringBuilder();
//                builder.append(living.getDisplayName().getString());
//
//                if (showHealth) {
//                    int health = (int) Math.ceil(living.getHealth());
//                    builder.append(" §a").append(health).append(" HP");
//                }
//
//                if (showDistance) {
//                    int d = (int) Math.round(dist);
//                    builder.append(" §7[").append(d).append("m]");
//                }
//
//                Component textComponent = Component.literal(builder.toString());
//                float halfWidth = client.font.width(textComponent) / 2.0f;
//                Matrix4f matrix = poseStack.last().pose();
//
//                // Text im 3D-Raum zeichnen
//                client.font.drawInBatch(
//                        textComponent,
//                        -halfWidth,
//                        0,
//                        0xFFFFFFFF,
//                        false,
//                        matrix,
//                        bufferSource,
//                        Font.DisplayMode.SEE_THROUGH,
//                        0x80000000,
//                        15728880
//                );
//
//                poseStack.popPose();
//            }
//        }
//    }
//}