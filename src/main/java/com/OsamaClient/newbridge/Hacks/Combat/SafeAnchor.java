package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SafeAnchor extends Module {

    private int stage = 0;
    private int timer = 0;
    private BlockPos anchorPos = null;
    private BlockPos betweenPos = null;   // Position des Glowstone-Schilds (zwischen dir und dem Anker)
    private BlockPos safeBlockPos = null; // Block unter dem Schild, auf dessen Oberseite platziert wird
    private int originalSlot = -1;

    public SafeAnchor() {
        super("SafeAnchor", "Safely shields, charges, and explodes Respawn Anchors without flagging GrimAC.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        this.stage = 0;
        this.timer = 0;
        this.anchorPos = null;
        this.betweenPos = null;
        this.safeBlockPos = null;
        this.originalSlot = -1;
        Tracker.isAiming = false;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.level == null || client.player == null || client.gameMode == null) {
            resetState();
            return;
        }

        // WICHTIG: Kein Tracker.restoreSpoof() hier! onTick läuft am Tick-ANFANG, das
        // Rotations-/Flying-Paket wird erst danach gesendet. Die Wiederherstellung der echten
        // Rotation passiert in MinecraftMixin (tick RETURN), also nach dem Paketversand.
        // Sonst sieht der Server die echte Rotation -> RotationPlace flaggt.

        // Wichtig für GrimAC (PacketOrderE): Nach einem useItemOn darf der Slot
        // erst nach mindestens einem vollen Tick (Flying-Paket) gewechselt werden.
        switch (stage) {
            case 0: {
                if (client.options.keyUse.isDown() && client.hitResult instanceof BlockHitResult anchorHit) {
                    BlockPos hitPos = anchorHit.getBlockPos();
                    BlockState state = client.level.getBlockState(hitPos);

                    if (state.is(Blocks.RESPAWN_ANCHOR)) {
                        int glowstoneSlot = findHotbarSlot(client, Items.GLOWSTONE);
                        if (glowstoneSlot != -1) {
                            anchorPos = hitPos;
                            betweenPos = calculateBetweenPos(client.player.blockPosition(), anchorPos);
                            safeBlockPos = betweenPos.below();
                            originalSlot = client.player.getInventory().getSelectedSlot();

                            client.options.keyUse.setDown(false); // Vanilla-Rechtsklick abfangen
                            setLookAt(client, anchorPos);
                            stage = 1;
                            timer = 0;
                        }
                    }
                }
                break;
            }

            case 1: {
                // Silent Aim auf die Oberseite des Stützblocks, Slot wechseln (VOR dem Use = ok),
                // dann Glowstone-Schild zwischen dir und dem Anker platzieren.
                setLookAt(client, safeBlockPos);
                int gsSlot = findHotbarSlot(client, Items.GLOWSTONE);
                if (gsSlot != -1) {
                    client.player.getInventory().setSelectedSlot(gsSlot);

                    boolean hasFloor = !client.level.getBlockState(safeBlockPos).isAir();
                    boolean targetFree = client.level.getBlockState(betweenPos).isAir();
                    if (hasFloor && targetFree) {
                        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                                getTopFaceHit(safeBlockPos));
                    }
                }
                stage = 2;
                timer = 0;
                break;
            }

            case 2: {
                // Mindestens 1 Leer-Tick nach dem Use, dann Glowstone in den Anker laden
                setLookAt(client, anchorPos);
                if (++timer >= 2) {
                    if (client.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                                getTopFaceHit(anchorPos));
                    }
                    stage = 3;
                    timer = 0;
                }
                break;
            }

            case 3: {
                // Leer-Tick abwarten, dann Slot wechseln und DANACH zünden
                setLookAt(client, anchorPos);
                if (++timer >= 2) {
                    int anchorSlot = findHotbarSlot(client, Items.RESPAWN_ANCHOR);
                    if (anchorSlot != -1) {
                        client.player.getInventory().setSelectedSlot(anchorSlot);
                    }
                    if (client.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                                getTopFaceHit(anchorPos));
                    }
                    stage = 4;
                    timer = 0;
                }
                break;
            }

            case 4: {
                // Erst mind. 1 Tick nach dem Zünden den Original-Slot zurück
                setLookAt(client, anchorPos);
                if (++timer >= 2) {
                    if (originalSlot != -1) {
                        client.player.getInventory().setSelectedSlot(originalSlot);
                    }
                    resetState();
                }
                break;
            }
        }
    }

    /** Silent Aim exakt auf die Mitte der Oberseite des Blocks. */
    private void setLookAt(Minecraft client, BlockPos pos) {
        Vec3 target = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        Vec3 eye = client.player.getEyePosition();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float rawYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        // Yaw an den echten (unbegrenzten) Yaw-Wert anhängen: nur der kürzeste Winkelunterschied
        // wird addiert. Sonst springt der Zahlenwert z.B. von 170 auf -170 (Delta 340) und
        // GrimAC (AimModulo360) flaggt, obwohl der echte Winkel nur 20° beträgt.
        float realYaw = client.player.getYRot();
        float targetYaw = realYaw + Mth.wrapDegrees(rawYaw - realYaw);
        float targetPitch = (float) -(Mth.atan2(dy, hDist) * (180.0D / Math.PI));

        Tracker.applySpoof(client.player, targetYaw, Mth.clamp(targetPitch, -90.0F, 90.0F));
        Tracker.isAiming = true;
    }

    private static int findHotbarSlot(Minecraft client, Item item) {
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static BlockPos calculateBetweenPos(BlockPos playerPos, BlockPos anchorPos) {
        int offsetX = Integer.signum(playerPos.getX() - anchorPos.getX());
        int offsetZ = Integer.signum(playerPos.getZ() - anchorPos.getZ());
        return anchorPos.offset(offsetX, 0, offsetZ);
    }

    /**
     * Cursor = exakte Mitte der Oberseite. Passt zum Blick aus setLookAt, liegt sicher
     * innerhalb von 0..1 (FabricatedPlace) und hat beim Laufen den größten Spielraum
     * zum Blockrand (RotationPlace).
     */
    private static BlockHitResult getTopFaceHit(BlockPos pos) {
        Vec3 hit = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        return new BlockHitResult(hit, Direction.UP, pos, false);
    }
}