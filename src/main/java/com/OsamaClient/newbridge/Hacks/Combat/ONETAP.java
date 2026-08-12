package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

public class ONETAP extends Module {

    private int fallHeight     = 49;
    private int spamPackets    = 8;
    private int attackCount    = 5;
    private int heightIncrease = 9;

    public ONETAP() {
        super("ONETAP", "Spoofs Y position to boost mace damage", Category.COMBAT);
        settings.add(new Slider("FallHeight", 0, 45, 49, v -> fallHeight = v.intValue()).withDescription("Sets the spoofed fall height for mace damage calculation."));
        settings.add(new Slider("SpamPackets", 0, 17, 8, v -> spamPackets = v.intValue()).withDescription("Number of packets sent to spoof the fall height."));
        settings.add(new Slider("Attacks", 0, 10, 5, v -> attackCount = v.intValue()).withDescription("Number of attacks executed during the exploit."));
        settings.add(new Slider("HeightIncrease", 0, 20, 9, v -> heightIncrease = v.intValue()).withDescription("Controls the vertical increase amount for the spoof."));
    }

    @Override
    public void onAttack(Minecraft mc, LivingEntity target) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (!mc.player.getMainHandItem().is(Items.MACE)) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean onGround = mc.player.onGround();

        for (int i = 0; i < spamPackets; i++) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), onGround, mc.player.horizontalCollision));
        }

        int currentHeight = fallHeight;

        for (int i = 0; i < attackCount; i++) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + currentHeight, z, false, false));
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, true, false));
            mc.getConnection().send(new ServerboundAttackPacket(target.getId()));
            currentHeight += heightIncrease;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, false));
    }
}