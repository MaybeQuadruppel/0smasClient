package com.qdrppl.newbridge.Hacks.Movement;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ElytraFly extends Module {
    private int cooldown = 0;
    private double speedMultiplier = 0.15; // Wie stark der Schub pro Tick ist

    public ElytraFly() {
        super("ElytraBoost", "Lets you use Rockets even if you have none (SpaceBar)", Category.MOVEMENT);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;

        boolean isFlying = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                && mc.player.isFallFlying();

        if (isFlying && mc.options.keyJump.isDown()) {
            Vec3 look = mc.player.getLookAngle();
            Vec3 currentVel = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(currentVel.add(
                    look.x * speedMultiplier,
                    look.y * speedMultiplier,
                    look.z * speedMultiplier
            ));

            if (cooldown <= 0) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(
                        mc.player,
                        ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
                ));
                cooldown = 5;
            }
        }

        if (cooldown > 0) cooldown--;
    }
}


