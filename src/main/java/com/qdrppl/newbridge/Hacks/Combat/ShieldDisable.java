package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.*;
import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ShieldDisable extends Module {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean isExecuting = false;


    private boolean autoStrikeBack = true;
    private double range = 4.0;
    private int switchDelay = 50;

    public ShieldDisable() {
        super("ShieldDisable", "Breaks enemy shields automatically", Category.COMBAT);
        this.settings.add(new ToggleButton("Hit with prev. Item", true, val -> autoStrikeBack = val));
        this.settings.add(new Slider("Range", 1.0, 6.0, 3.5, val -> range = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || isExecuting) return;


        HitResult hit = client.hitResult;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player target) {

            if (target.isBlocking() && client.player.distanceTo(target) <= range) {
                executeShieldBreak(client, target);
            }
        }
    }

    private void executeShieldBreak(Minecraft client, Player target) {
        int oldSlot = client.player.getInventory().getSelectedSlot();
        int axeSlot = findAxe(client);

        if (axeSlot == -1) return;

        isExecuting = true;


        client.player.getInventory().setSelectedSlot(axeSlot);


        scheduler.schedule(() -> {
            client.execute(() -> {

                client.gameMode.attack(client.player, target);
                client.player.swing(InteractionHand.MAIN_HAND);

                scheduler.schedule(() -> {
                    client.execute(() -> {
                        client.player.getInventory().setSelectedSlot(oldSlot);

                        if (autoStrikeBack) {
                            client.gameMode.attack(client.player, target);
                            client.player.swing(InteractionHand.MAIN_HAND);
                        }

                        isExecuting = false;
                    });
                }, switchDelay, TimeUnit.MILLISECONDS);
            });
        }, switchDelay, TimeUnit.MILLISECONDS);
    }

    private int findAxe(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }
}