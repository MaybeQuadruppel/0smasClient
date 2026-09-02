package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkeletonESP extends Module {

    public double lineWidth = 2.0;
    public boolean renderPlayers = true;
    public boolean renderMobs = true;
    public boolean distanceColors = false;
    public double renderDistance = 64.0;

    public double forwardOffset = 0.0;
    public double verticalOffset = 1.35;
    public double horizontalOffset = 0.25;

    public double red = 255.0;
    public double green = 255.0;
    public double blue = 255.0;

    private enum MobCategory { BIPED, QUADRUPED, HORSE, ARACHNID, NONE }
    private static final Map<EntityType<?>, MobCategory> CATEGORY_CACHE = new ConcurrentHashMap<>();

    public SkeletonESP() {
        super("SkeletonESP", "Draws an anatomically aligned, animated stick-figure skeleton.", Category.VISUAL);

        this.settings.add(new ToggleButton("Players", renderPlayers, val -> renderPlayers = val));
        this.settings.add(new ToggleButton("Mobs", renderMobs, val -> renderMobs = val));
        this.settings.add(new ToggleButton("Distance Colors", distanceColors, val -> distanceColors = val));

        this.settings.add(new Slider("Render Distance", 10.0, 256.0, renderDistance, val -> renderDistance = val));
        this.settings.add(new Slider("Line Width", 0.5, 10.0, lineWidth, val -> lineWidth = val));

        this.settings.add(new Slider("Forward Offset (Legacy)", -0.3, 0.3, forwardOffset, val -> forwardOffset = val));
        this.settings.add(new Slider("Vertical Offset (Legacy)", 1.0, 1.6, verticalOffset, val -> verticalOffset = val));
        this.settings.add(new Slider("Shoulder Width (Legacy)", 0.0, 0.5, horizontalOffset, val -> horizontalOffset = val));

        this.settings.add(new Slider("Red", 0.0, 255.0, red, val -> red = val));
        this.settings.add(new Slider("Green", 0.0, 255.0, green, val -> green = val));
        this.settings.add(new Slider("Blue", 0.0, 255.0, blue, val -> blue = val));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.gameRenderer.mainCamera() == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        float tickDelta = event.getTickDelta();

        ChamsBufferSource bufferSource = new ChamsBufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        int staticColor = (255 << 24) | ((int) red << 16) | ((int) green << 8) | (int) blue;
        double maxDistSqr = renderDistance * renderDistance;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == client.player || !entity.isAlive()) continue;

            if (client.player.distanceToSqr(entity) > maxDistSqr) continue;

            boolean isPlayer = entity instanceof Player;
            if ((isPlayer && renderPlayers) || (!isPlayer && renderMobs)) {
                MobCategory category = getCategory(living);
                if (category == MobCategory.NONE) continue;

                int renderColor = distanceColors ? getColorFromDistance(client.player, living) : staticColor;

                switch (category) {
                    case HORSE     -> drawHorse(matrix, normalMatrix, lineConsumer, living, tickDelta, camPos, renderColor, (float) lineWidth);
                    case QUADRUPED -> drawQuadruped(matrix, normalMatrix, lineConsumer, living, tickDelta, camPos, renderColor, (float) lineWidth);
                    case ARACHNID  -> drawArachnid(matrix, normalMatrix, lineConsumer, living, tickDelta, camPos, renderColor, (float) lineWidth);
                    case BIPED     -> drawBiped(matrix, normalMatrix, lineConsumer, living, tickDelta, camPos, renderColor, (float) lineWidth);
                }
            }
        }

        bufferSource.uploadAndDraw();
    }

    private MobCategory getCategory(LivingEntity entity) {
        return CATEGORY_CACHE.computeIfAbsent(entity.getType(), type -> {
            if (entity instanceof AbstractHorse) return MobCategory.HORSE;
            if (entity instanceof Wolf || entity instanceof Cow ||
                    entity instanceof Pig || entity instanceof Sheep || entity instanceof Panda || entity instanceof Fox) {
                return MobCategory.QUADRUPED;
            }
            if (entity instanceof Spider) return MobCategory.ARACHNID;
            if (entity instanceof Slime || entity instanceof Ghast || entity instanceof Shulker || entity instanceof MagmaCube) {
                return MobCategory.NONE;
            }
            return MobCategory.BIPED;
        });
    }

    private void drawBiped(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, LivingEntity entity, float tickDelta, Vec3 camPos, int color, float lineWidth) {
        float h = entity.getBbHeight();
        float w = entity.getBbWidth();

        Vec3 basePos = getLerpedPos(entity, tickDelta);
        float yawRad = (float) Math.toRadians(-entity.yBodyRot);
        float amplitude = entity.walkAnimation.speed();
        float phase = entity.walkAnimation.position();

        float rightLegPitch = (float) (Mth.cos(phase * 0.6662f) * 1.0f * amplitude);
        float leftLegPitch = (float) (Mth.cos(phase * 0.6662f + Mth.PI) * 1.0f * amplitude);
        float rightArmPitch = leftLegPitch;
        float leftArmPitch = rightLegPitch;

        float hipHeight = h * 0.45f;
        float shoulderHeight = h * 0.80f;
        if (entity.isCrouching()) shoulderHeight -= h * 0.15f;
        float shoulderWidth = w * 0.35f;
        float armLength = -h * 0.45f;

        Vec3 chestBase = basePos.add(0, shoulderHeight, 0);
        Vec3 spineStart = basePos.add(0, hipHeight, 0);
        Vec3 headTop = chestBase.add(0, h * 0.2f, 0);

        Vec3 leftShoulder = chestBase.add(new Vec3(-shoulderWidth, 0, 0).yRot(yawRad));
        Vec3 rightShoulder = chestBase.add(new Vec3(shoulderWidth, 0, 0).yRot(yawRad));
        Vec3 leftHip = spineStart.add(new Vec3(-shoulderWidth * 0.8, 0, 0).yRot(yawRad));
        Vec3 rightHip = spineStart.add(new Vec3(shoulderWidth * 0.8, 0, 0).yRot(yawRad));

        Vec3 leftArmEnd = leftShoulder.add(new Vec3(0, armLength, 0).xRot(leftArmPitch).yRot(yawRad));
        Vec3 rightArmEnd = rightShoulder.add(new Vec3(0, armLength, 0).xRot(rightArmPitch).yRot(yawRad));
        Vec3 leftFoot = leftHip.add(new Vec3(0, -hipHeight, 0).xRot(leftLegPitch).yRot(yawRad));
        Vec3 rightFoot = rightHip.add(new Vec3(0, -hipHeight, 0).xRot(rightLegPitch).yRot(yawRad));

        drawWorldLine(matrix, normalMatrix, consumer, spineStart.subtract(camPos), chestBase.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, leftShoulder.subtract(camPos), rightShoulder.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, leftShoulder.subtract(camPos), leftArmEnd.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, rightShoulder.subtract(camPos), rightArmEnd.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, chestBase.subtract(camPos), headTop.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, leftHip.subtract(camPos), rightHip.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, leftHip.subtract(camPos), leftFoot.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, rightHip.subtract(camPos), rightFoot.subtract(camPos), color, lineWidth);
    }

    private void drawQuadruped(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, LivingEntity entity, float tickDelta, Vec3 camPos, int color, float lineWidth) {
        float h = entity.getBbHeight();
        float w = entity.getBbWidth();

        Vec3 basePos = getLerpedPos(entity, tickDelta);
        float yawRad = (float) Math.toRadians(-entity.yBodyRot);
        Vec3 forwardVec = new Vec3(0, 0, 1).yRot(yawRad);

        float length = w * 1.1f;
        float bodyHeight = h * 0.55f;
        float legHeight = bodyHeight;

        Vec3 frontBase = basePos.add(forwardVec.scale(length * 0.35)).add(0, bodyHeight, 0);
        Vec3 backBase = basePos.add(forwardVec.scale(-length * 0.35)).add(0, bodyHeight, 0);

        float width = w * 0.35f;
        Vec3 frontLeftShoulder = frontBase.add(new Vec3(-width, 0, 0).yRot(yawRad));
        Vec3 frontRightShoulder = frontBase.add(new Vec3(width, 0, 0).yRot(yawRad));
        Vec3 backLeftHip = backBase.add(new Vec3(-width, 0, 0).yRot(yawRad));
        Vec3 backRightHip = backBase.add(new Vec3(width, 0, 0).yRot(yawRad));

        float phase = entity.walkAnimation.position();
        float amp = entity.walkAnimation.speed();

        float frontLeftPitch = (float) (Mth.cos(phase * 0.6662f) * 1.0f * amp);
        float frontRightPitch = (float) (Mth.cos(phase * 0.6662f + Mth.PI) * 1.0f * amp);
        float backLeftPitch = frontRightPitch;
        float backRightPitch = frontLeftPitch;

        Vec3 frontLeftFoot = frontLeftShoulder.add(new Vec3(0, -legHeight, 0).xRot(frontLeftPitch).yRot(yawRad));
        Vec3 frontRightFoot = frontRightShoulder.add(new Vec3(0, -legHeight, 0).xRot(frontRightPitch).yRot(yawRad));
        Vec3 backLeftFoot = backLeftHip.add(new Vec3(0, -legHeight, 0).xRot(backLeftPitch).yRot(yawRad));
        Vec3 backRightFoot = backRightHip.add(new Vec3(0, -legHeight, 0).xRot(backRightPitch).yRot(yawRad));

        Vec3 head = frontBase.add(forwardVec.scale(length * 0.4)).add(0, h * 0.2, 0);

        drawWorldLine(matrix, normalMatrix, consumer, backBase.subtract(camPos), frontBase.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, frontLeftShoulder.subtract(camPos), frontRightShoulder.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, backLeftHip.subtract(camPos), backRightHip.subtract(camPos), color, lineWidth);

        drawWorldLine(matrix, normalMatrix, consumer, frontLeftShoulder.subtract(camPos), frontLeftFoot.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, frontRightShoulder.subtract(camPos), frontRightFoot.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, backLeftHip.subtract(camPos), backLeftFoot.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, backRightHip.subtract(camPos), backRightFoot.subtract(camPos), color, lineWidth);

        drawWorldLine(matrix, normalMatrix, consumer, frontBase.subtract(camPos), head.subtract(camPos), color, lineWidth);
    }

    private void drawHorse(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, LivingEntity entity, float tickDelta, Vec3 camPos, int color, float lineWidth) {
        float h = entity.getBbHeight();
        float w = entity.getBbWidth();

        Vec3 basePos = getLerpedPos(entity, tickDelta);
        float yawRad = (float) Math.toRadians(-entity.yBodyRot);
        Vec3 forwardVec = new Vec3(0, 0, 1).yRot(yawRad);

        float length = w * 1.6f;
        float bodyHeight = h * 0.6f;
        float legHeight = bodyHeight;

        // Speziell für Pferde: Vorderbeine weiter nach vorn unter die echte Schulter gesetzt
        Vec3 frontBase = basePos.add(forwardVec.scale(length * 0.45)).add(0, bodyHeight, 0);
        Vec3 backBase = basePos.add(forwardVec.scale(-length * 0.35)).add(0, bodyHeight * 0.95, 0);

        float width = w * 0.35f;
        Vec3 frontLeftShoulder = frontBase.add(new Vec3(-width, 0, 0).yRot(yawRad));
        Vec3 frontRightShoulder = frontBase.add(new Vec3(width, 0, 0).yRot(yawRad));
        Vec3 backLeftHip = backBase.add(new Vec3(-width, 0, 0).yRot(yawRad));
        Vec3 backRightHip = backBase.add(new Vec3(width, 0, 0).yRot(yawRad));

        float phase = entity.walkAnimation.position();
        float amp = entity.walkAnimation.speed();

        float frontLeftPitch = (float) (Mth.cos(phase * 0.6662f) * 1.0f * amp);
        float frontRightPitch = (float) (Mth.cos(phase * 0.6662f + Mth.PI) * 1.0f * amp);
        float backLeftPitch = frontRightPitch;
        float backRightPitch = frontLeftPitch;

        Vec3 frontLeftFoot = frontLeftShoulder.add(new Vec3(0, -legHeight, 0).xRot(frontLeftPitch).yRot(yawRad));
        Vec3 frontRightFoot = frontRightShoulder.add(new Vec3(0, -legHeight, 0).xRot(frontRightPitch).yRot(yawRad));
        Vec3 backLeftFoot = backLeftHip.add(new Vec3(0, -legHeight, 0).xRot(backLeftPitch).yRot(yawRad));
        Vec3 backRightFoot = backRightHip.add(new Vec3(0, -legHeight, 0).xRot(backRightPitch).yRot(yawRad));

        // Korrigierter Pferdekopf & Hals (verbindet sich sauber nach oben-vorne)
        Vec3 neckBase = frontBase.add(forwardVec.scale(length * 0.2));
        Vec3 head = neckBase.add(forwardVec.scale(length * 0.35)).add(0, h * 0.45, 0);

        drawWorldLine(matrix, normalMatrix, consumer, backBase.subtract(camPos), frontBase.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, frontLeftShoulder.subtract(camPos), frontRightShoulder.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, backLeftHip.subtract(camPos), backRightHip.subtract(camPos), color, lineWidth);

        drawWorldLine(matrix, normalMatrix, consumer, frontLeftShoulder.subtract(camPos), frontLeftFoot.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, frontRightShoulder.subtract(camPos), frontRightFoot.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, backLeftHip.subtract(camPos), backLeftFoot.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, backRightHip.subtract(camPos), backRightFoot.subtract(camPos), color, lineWidth);

        drawWorldLine(matrix, normalMatrix, consumer, frontBase.subtract(camPos), neckBase.subtract(camPos), color, lineWidth);
        drawWorldLine(matrix, normalMatrix, consumer, neckBase.subtract(camPos), head.subtract(camPos), color, lineWidth);
    }

    private void drawArachnid(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, LivingEntity entity, float tickDelta, Vec3 camPos, int color, float lineWidth) {
        float h = entity.getBbHeight();
        float w = entity.getBbWidth();

        Vec3 basePos = getLerpedPos(entity, tickDelta);
        float yawRad = (float) Math.toRadians(-entity.yBodyRot);
        Vec3 forwardVec = new Vec3(0, 0, 1).yRot(yawRad);

        float bodyHeight = h * 0.4f;
        Vec3 centerBase = basePos.add(0, bodyHeight, 0);
        Vec3 head = centerBase.add(forwardVec.scale(w * 0.3));
        Vec3 tail = centerBase.add(forwardVec.scale(-w * 0.4));

        // Lange Rückenlinie der Spinne
        drawWorldLine(matrix, normalMatrix, consumer, tail.subtract(camPos), head.subtract(camPos), color, lineWidth);

        float phase = entity.walkAnimation.position();
        float amp = entity.walkAnimation.speed();

        // 8 Beine korrekt seitlich am Körper verteilt (4 links, 4 rechts)
        for (int i = 0; i < 4; i++) {
            // Verteilt die Beine von vorne nach hinten entlang des Thorax
            float zOffset = (w * 0.25f) - (i * (w * 0.2f));

            Vec3 spinePoint = centerBase.add(forwardVec.scale(zOffset));
            Vec3 legRootL = spinePoint.add(new Vec3(-w * 0.35f, 0, 0).yRot(yawRad));
            Vec3 legRootR = spinePoint.add(new Vec3(w * 0.35f, 0, 0).yRot(yawRad));

            float animPitchL = (float) (Mth.cos(phase * 0.6662f + (i * 0.8f)) * amp * 0.8f);
            float animPitchR = (float) (Mth.cos(phase * 0.6662f + Mth.PI + (i * 0.8f)) * amp * 0.8f);

            // Beine strahlen seitlich ab und gehen runter zum Boden
            Vec3 footL = legRootL.add(new Vec3(-w * 0.5f, -bodyHeight, w * 0.15f).xRot(animPitchL).yRot(yawRad));
            Vec3 footR = legRootR.add(new Vec3(w * 0.5f, -bodyHeight, w * 0.15f).xRot(animPitchR).yRot(yawRad));

            drawWorldLine(matrix, normalMatrix, consumer, spinePoint.subtract(camPos), legRootL.subtract(camPos), color, lineWidth);
            drawWorldLine(matrix, normalMatrix, consumer, spinePoint.subtract(camPos), legRootR.subtract(camPos), color, lineWidth);
            drawWorldLine(matrix, normalMatrix, consumer, legRootL.subtract(camPos), footL.subtract(camPos), color, lineWidth);
            drawWorldLine(matrix, normalMatrix, consumer, legRootR.subtract(camPos), footR.subtract(camPos), color, lineWidth);
        }
    }

    private Vec3 getLerpedPos(LivingEntity entity, float tickDelta) {
        return new Vec3(
                Mth.lerp(tickDelta, entity.xo, entity.getX()),
                Mth.lerp(tickDelta, entity.yo, entity.getY()),
                Mth.lerp(tickDelta, entity.zo, entity.getZ())
        );
    }

    private void drawWorldLine(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, Vec3 start, Vec3 end, int color, float lineWidth) {
        line(matrix, normalMatrix, consumer,
                (float) start.x, (float) start.y, (float) start.z,
                (float) end.x, (float) end.y, (float) end.z,
                color, lineWidth);
    }

    private int getColorFromDistance(Player clientPlayer, LivingEntity target) {
        double distance = Math.sqrt(clientPlayer.distanceToSqr(target));
        double percent = Math.min(1.0, distance / 60.0);

        int r, g;
        if (percent < 0.33) {
            r = (int) (percent / 0.33 * 255);
            g = 255;
        } else if (percent < 0.66) {
            r = 255;
            g = 255 - (int) ((percent - 0.33) / 0.33 * 90);
        } else {
            r = 255;
            g = 165 - (int) ((percent - 0.66) / 0.34 * 165);
        }

        return (255 << 24) | (r << 16) | (g << 8) | 0;
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color, float lineWidth) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;

        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len != 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dy = 1.0f;
        }

        Vector3f normal = new Vector3f(dx, dy, dz);
        normal.mul(normalMatrix);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(lineWidth);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(lineWidth);
    }
}