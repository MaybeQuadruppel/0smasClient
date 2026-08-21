package com.OsamaClient.newbridge.event;

import com.mojang.blaze3d.vertex.PoseStack;

public class Render3DEvent {
    private final PoseStack poseStack;
    private final float tickDelta;
    private final double cameraX, cameraY, cameraZ;

    public Render3DEvent(PoseStack poseStack, float tickDelta, double cameraX, double cameraY, double cameraZ) {
        this.poseStack = poseStack;
        this.tickDelta = tickDelta;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public double getCameraX() {
        return cameraX;
    }

    public double getCameraY() {
        return cameraY;
    }

    public double getCameraZ() {
        return cameraZ;
    }

}