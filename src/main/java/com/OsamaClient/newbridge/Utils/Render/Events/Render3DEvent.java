package com.OsamaClient.newbridge.Utils.Render.Events; // Passe das Paket an deinen Client an

import com.OsamaClient.newbridge.Utils.Render.Renderer3D;
import com.mojang.blaze3d.vertex.PoseStack;

public class Render3DEvent {
    private static final Render3DEvent INSTANCE = new Render3DEvent();

    public PoseStack matrices;
    public Renderer3D renderer;
    public Renderer3D depthRenderer;
    public double frameTime;
    public float tickDelta;
    public double offsetX, offsetY, offsetZ;

    // Singleton-Get-Methode: Aktualisiert die Werte und gibt die statische Instanz zurück
    public static Render3DEvent get(PoseStack matrices, Renderer3D renderer, Renderer3D depthRenderer, float tickDelta, double offsetX, double offsetY, double offsetZ) {
        INSTANCE.matrices = matrices;
        INSTANCE.renderer = renderer;
        INSTANCE.depthRenderer = depthRenderer;
        INSTANCE.frameTime = 0; // Alternativ: Utils.frameTime, falls du ein Frame-Time-Tracking hast
        INSTANCE.tickDelta = tickDelta;
        INSTANCE.offsetX = offsetX;
        INSTANCE.offsetY = offsetY;
        INSTANCE.offsetZ = offsetZ;
        return INSTANCE;
    }

    // Praktische Überladung für Aufrufe mit nur einem Renderer & ohne Offset
    public static Render3DEvent get(PoseStack matrices, Renderer3D renderer, float tickDelta) {
        return get(matrices, renderer, renderer, tickDelta, 0.0, 0.0, 0.0);
    }
}