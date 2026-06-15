package com.qdrppl.newbridge.Hacks.Misc;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
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

    public Freecam() {
        super("Freecam", "Erlaubt es dir, die Kamera vom Spieler zu trennen.", Category.MOVEMENT);
        this.settings.add(new Slider("Speed", 0.1, 3.0, speedValue, val -> speedValue = val));
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.toggle();
            return;
        }

        super.onEnable();

        cameraPos = mc.gameRenderer.getMainCamera().position();
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();

        originalPerspective = mc.options.getCameraType();
        isActive = true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || !isActive) return;

        // Abfrage der permanent gedrückten Tasten (Funktioniert jetzt perfekt!)
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


        if (movement.lengthSqr() > 0) {
            movement = movement.normalize().scale(speedValue * 0.4);
        }

        double yMove = 0;
        if (up)   yMove += speedValue * 0.4;
        if (down) yMove -= speedValue * 0.4;

        if (cameraPos != null) {
            cameraPos = cameraPos.add(movement.x, yMove, movement.z);
        }
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