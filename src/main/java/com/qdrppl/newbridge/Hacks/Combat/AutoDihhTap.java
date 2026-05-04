package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Items.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoDihhTap extends Module {
    public static AutoDihhTap INSTANCE;

    private int placementStep = 0;
    private int delayTicks = 0;
    private LivingEntity currentTarget = null;
    private BlockPos obsidianPos = null;

    public AutoDihhTap() {
        super("AutoDihhTap", "Automated Crystal Sequence", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!this.enabled || client.player == null) {
            placementStep = 0;
            currentTarget = null;
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (placementStep == 0) {
            HitResult hit = client.hitResult;
            if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
                if (!(target instanceof ArmorStand)) {
                    this.currentTarget = target;
                    this.placementStep = 1;
                }
            }
            return;
        }

        switch (placementStep) {
            case 1:
                if (switchToSword(client)) {
                    attackEntity(client);
                    placementStep = 2;
                    delayTicks = 1;
                }
                break;

            case 2: // Schritt 2: Obsidian platzieren
                if (switchToItem(client, Items.OBSIDIAN)) {
                    if (client.hitResult instanceof BlockHitResult blockHit) {
                        this.obsidianPos = blockHit.getBlockPos();
                        if (client.level.getBlockState(obsidianPos).getBlock() != Blocks.OBSIDIAN) {
                            interactBlock(client);
                        }
                        placementStep = 3;
                        delayTicks = 1;
                    } else {
                        placementStep = 0;
                    }
                }
                break;

            case 3: // Schritt 3: Crystal platzieren
                if (switchToItem(client, Items.END_CRYSTAL)) {
                    interactBlock(client);
                    placementStep = 4;
                    delayTicks = 1;
                }
                break;

            case 4: // Schritt 4: Crystal breaken
                attackEntity(client);
                placementStep = 5;
                delayTicks = 1;
                break;

            case 5: // Schritt 5: Double Tap (Zweiter Crystal)
                if (switchToItem(client, Items.END_CRYSTAL)) {
                    interactBlock(client);
                    placementStep = 6;
                    delayTicks = 1;
                }
                break;

            case 6: // Schritt 6: Finaler Hit mit Schwert & Reset
                if (switchToSword(client)) {
                    attackEntity(client);
                    // Optional: Danach auf Gapple für Sicherheit
                    switchToItem(client, Items.GOLDEN_APPLE);
                    placementStep = 0;
                    currentTarget = null;
                }
                break;
        }
    }

    // Hilfsmethode, um ein beliebiges Schwert in der Hotbar zu finden
    private boolean switchToSword(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(Items.NETHERITE_SWORD)) {
                client.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private void interactBlock(Minecraft client) {
        if (client.hitResult instanceof BlockHitResult blockHit) {
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, blockHit);
            client.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void attackEntity(Minecraft client) {
        if (client.hitResult instanceof EntityHitResult entityHit) {
            client.gameMode.attack(client.player, entityHit.getEntity());
            client.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean switchToItem(Minecraft client, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(item)) {
                client.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }
}