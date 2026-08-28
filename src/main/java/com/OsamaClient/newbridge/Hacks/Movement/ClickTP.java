package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClickTP extends Module {

    public double maxDistance = 200.0;
    private int cooldownTicks = 0;

    public ClickTP() {
        super("ClickTP", "Teleports you to the block you click on.", Category.MOVEMENT);
        this.settings.add(new Slider("Max Distance", 10.0, 200.0, maxDistance, val -> maxDistance = val)
                .withDescription("Maximum teleport distance in blocks."));
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;

        // Verhindert Dauer-Spamming beim Gedrückthalten
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // Abbruch, wenn das Item in der Hand eine Benutzungs-Animation hat (z. B. Bogen, Essen)
        if (client.player.getMainHandItem().getUseAnimation() != ItemUseAnimation.NONE) return;

        // Nur ausführen, wenn die Rechte Maustaste gedrückt wird
        if (!client.options.keyUse.isDown()) return;

        // Prüfen, ob eine Entität angeklickt wird oder Blöcke platziert werden
        if (client.hitResult != null) {
            if (client.hitResult.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) client.hitResult;
                if (client.player.interactOn(entityHit.getEntity(), InteractionHand.MAIN_HAND, entityHit.getLocation()) != InteractionResult.PASS) {
                    return;
                }
            }
            if (client.hitResult.getType() == HitResult.Type.BLOCK && client.player.getMainHandItem().getItem() instanceof BlockItem) {
                return;
            }
        }

        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();

        // Raycast berechnen (Blickrichtung * maxDistance)
        Vec3 direction = Vec3.directionFromRotation(camera.xRot(), camera.yRot()).scale(maxDistance + 10.0);
        Vec3 targetPos = cameraPos.add(direction);

        ClipContext context = new ClipContext(
                cameraPos,
                targetPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                client.player
        );

        BlockHitResult hitResult = client.level.clip(context);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hitResult.getBlockPos();
            Direction side = hitResult.getDirection();

            BlockState state = client.level.getBlockState(pos);

            // Verhindert Teleportation, wenn man auf interaktive Blöcke klickt (z. B. Truhen, Knöpfe)
            if (state.useWithoutItem(client.level, client.player, hitResult) != InteractionResult.PASS) {
                return;
            }

            VoxelShape shape = state.getCollisionShape(client.level, pos);
            if (shape.isEmpty()) shape = state.getShape(client.level, pos);

            double height = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);

            // Exakte Zielposition oben auf dem Block berechnen
            Vec3 newPos = new Vec3(pos.getX() + 0.5 + side.getStepX(), pos.getY() + height, pos.getZ() + 0.5 + side.getStepZ());

            // Pakete berechnen, um Vanilla/Paper Movement-Checks über große Distanzen auszutricksen
            int packetsRequired = (int) Math.ceil(client.player.position().distanceTo(newPos) / 10.0) - 1;
            if (packetsRequired > 19) packetsRequired = 0;

            if (client.getConnection() != null) {
                for (int i = 0; i < packetsRequired; i++) {
                    client.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, client.player.horizontalCollision));
                }
                client.getConnection().send(new ServerboundMovePlayerPacket.Pos(newPos.x, newPos.y, newPos.z, true, client.player.horizontalCollision));
            }

            // Client-seitige Position setzen
            client.player.setPos(newPos.x, newPos.y, newPos.z);
            cooldownTicks = 5;
        }
    }
}