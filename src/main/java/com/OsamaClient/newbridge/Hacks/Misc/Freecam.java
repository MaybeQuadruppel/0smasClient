package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

public class Freecam extends Module {

    public static boolean isActive = false;
    public static Vec3 cameraPos;
    public static float cameraYaw;
    public static float cameraPitch;
    public static double speedValue = 1.0;

    private CameraType originalPerspective;
    private static long lastFrameTime = System.nanoTime();

    public Freecam() {
        super("Freecam", "Cuts your Camera from your Player.", Category.MOVEMENT);
        this.settings.add(new Slider("Speed", 0.1, 3.0, speedValue, val -> speedValue = val).withDescription("Controls the movement speed of the camera."));
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.toggle();
            return;
        }

        super.onEnable();

        cameraPos = mc.gameRenderer.mainCamera().position();
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();

        originalPerspective = mc.options.getCameraType();
        // Bugfix: vorher wurde originalPerspective nur GESPEICHERT, aber nie
        // tatsächlich auf Third-Person umgestellt. Ohne diesen Wechsel greift
        // an anderer Stelle im Spiel weiterhin die First-Person-spezifische
        // Logik, die das eigene Spielermodell versteckt - man sieht seinen
        // echten Körper also gar nicht, obwohl EntityRenderDispatcherMixin
        // das Rendern eigentlich erzwingt.
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        lastFrameTime = System.nanoTime();
        isActive = true;
    }

    // Diese Methode wird jetzt über das CameraMixin bei jedem gerenderten Frame aufgerufen
    public static void renderMovement() {
        if (!isActive || cameraPos == null) return;
        Minecraft mc = Minecraft.getInstance();

        // Zeitdifferenz berechnen (Delta Time) -> Wichtig für die flüssige Bewegung
        long currentTime = System.nanoTime();
        double deltaTime = (currentTime - lastFrameTime) / 1000000000.0;
        lastFrameTime = currentTime;

        if (deltaTime > 0.1) deltaTime = 0.1;

        boolean forward = mc.options.keyUp.isDown();
        boolean backward = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();

        Vec3 forwardVec = Vec3.directionFromRotation(0, cameraYaw);
        Vec3 rightVec = Vec3.directionFromRotation(0, cameraYaw + 90);
        Vec3 movement = Vec3.ZERO;

        if (forward)  movement = movement.add(forwardVec);
        if (backward) movement = movement.subtract(forwardVec);
        if (left)     movement = movement.subtract(rightVec);
        if (right)    movement = movement.add(rightVec);

        // Multiplikator für framerate-unabhängige Geschwindigkeit
        double speedMultiplier = speedValue * 15.0 * deltaTime;

        if (movement.lengthSqr() > 0) {
            movement = movement.normalize().scale(speedMultiplier);
        }

        double yMove = 0;
        if (up)   yMove += speedMultiplier;
        if (down) yMove -= speedMultiplier;

        cameraPos = cameraPos.add(movement.x, yMove, movement.z);
    }

    @Override
    public void onDisable() {
        isActive = false;
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null && originalPerspective != null) {
            mc.options.setCameraType(originalPerspective);
        }

        super.onDisable();
    }
}