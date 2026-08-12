package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class Teleport extends Module {
    public static Teleport INSTANCE;

    public double stepDistance = 50.0;
    public int tickDelay = 2; // Ticks zwischen den Paketen

    private Vec3 targetPos = null;
    private int delayCounter = 0;

    public Teleport() {
        super("Teleport", "Instantly teleports you in steps to bypass rubberbands", Category.MOVEMENT);
        INSTANCE = this;

        // UI-Slider für Schrittweite und Frequenz
        this.settings.add(new Slider("Step Range", 5.0, 100.0, stepDistance, val -> stepDistance = val).withDescription("Sets the maximum distance per teleport step."));
        this.settings.add(new Slider("Tick Delay", 0.0, 10.0, (double) tickDelay, val -> tickDelay = val.intValue()).withDescription("Sets the delay in ticks between each teleport step."));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public static Teleport getInstance() {
        if (INSTANCE == null) INSTANCE = new Teleport();
        return INSTANCE;
    }

    public void setTarget(double x, double y, double z) {
        this.targetPos = new Vec3(x, y, z);
        this.delayCounter = 0;
        this.enabled = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§a[Teleport] Starting stepped teleport to X: " + x + " Y: " + y + " Z: " + z));
        }
    }

    public void onTick(Minecraft client) {
        if (!this.enabled || targetPos == null || client.player == null || client.getConnection() == null) {
            return;
        }

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        Vec3 currentPos = client.player.position();
        double distanceToTarget = currentPos.distanceTo(targetPos);

        if (distanceToTarget <= stepDistance) {
            client.player.setPos(targetPos.x, targetPos.y, targetPos.z);
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    targetPos.x, targetPos.y, targetPos.z,
                    client.player.onGround(),
                    client.player.horizontalCollision
            ));
            client.player.sendSystemMessage(Component.literal("§a[Teleport] Teleport completed!"));
            targetPos = null;
            this.enabled = false;
            return;
        }

        // Zwischenschritt berechnen
        Vec3 direction = targetPos.subtract(currentPos).normalize();
        Vec3 nextPos = currentPos.add(direction.scale(stepDistance));

        client.player.setPos(nextPos.x, nextPos.y, nextPos.z);
        client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                nextPos.x, nextPos.y, nextPos.z,
                client.player.onGround(),
                client.player.horizontalCollision
        ));

        delayCounter = tickDelay;
    }
}