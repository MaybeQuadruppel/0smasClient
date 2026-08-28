package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class ElytraFly extends Module {

    private int cooldown = 0;

    // --- Settings ---
    private double boostStrength = 0.5;
    private boolean hoverWhenIdle = false;

    public ElytraFly() {
        super("ElytraFly", "Boost your Elytra flight without rockets!", Category.MOVEMENT);

        this.settings.add(new Slider("Boost Strength", 0.1, 5.0, boostStrength, val -> boostStrength = val)
                .withDescription("How strong the spacebar boost should be."));

        this.settings.add(new ToggleButton("Hover When Idle", hoverWhenIdle, val -> hoverWhenIdle = val)
                .withDescription("Freezes you in the air when no movement keys are pressed."));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;

        if (cooldown > 0) cooldown--;

        // Check if the player is currently able to glide (has Elytra equipped and unbroken)
        boolean canGlide = false;
        for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
            if (LivingEntity.canGlideUsing(mc.player.getItemBySlot(equipmentSlot), equipmentSlot)) {
                canGlide = true;
                break;
            }
        }

        // If you can't glide or aren't flying, make sure normal gravity is restored
        if (!canGlide || !mc.player.isFallFlying()) {
            resetHover(mc);
            return;
        }

        // --- Hover Logic ---
        // Check if any movement key is pressed (WASD, Space, Shift)
        boolean isMoving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() ||
                mc.options.keyLeft.isDown() || mc.options.keyRight.isDown() ||
                mc.options.keyJump.isDown() || mc.options.keyShift.isDown();

        if (hoverWhenIdle && !isMoving) {
            // Cancel vanilla gravity to freeze mid-air
            mc.player.getAbilities().flying = true;
            mc.player.setDeltaMovement(0, 0, 0);
        } else {
            // Restore gravity when moving
            resetHover(mc);
        }

        // --- Boost Logic ---
        boolean isCtrlPressed = mc.options.keySprint.isDown();
        boolean isSpacePressed = mc.options.keyJump.isDown();

        // Trigger boost only when sprinting and jumping mid-air
        if (isSpacePressed && isCtrlPressed && cooldown <= 0) {
            Vec3 look = mc.player.getLookAngle();
            Vec3 currentVel = mc.player.getDeltaMovement();

            mc.player.setDeltaMovement(currentVel.add(
                    look.x * boostStrength,
                    look.y * boostStrength,
                    look.z * boostStrength
            ));

            cooldown = 10; // 10 ticks cooldown between boosts
        }
    }

    /**
     * Resets the fake creative flight so gravity applies normally again.
     */
    private void resetHover(Minecraft mc) {
        if (mc.player != null && !mc.player.isCreative() && mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = false;
        }
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        resetHover(mc);
        super.onDisable();
    }
}