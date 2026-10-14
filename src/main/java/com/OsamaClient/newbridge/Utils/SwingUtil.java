package com.OsamaClient.newbridge.Utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

/** Hand swing like vanilla (26.3+: the swing animation comes from the held item). */
public final class SwingUtil {

    private SwingUtil() {}

    public static void swing(LivingEntity entity, InteractionHand hand) {
        if (entity == null) return;
        SwingAnimation anim = entity.getItemInHand(hand).getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT);
        entity.swing(hand, anim, false);
    }
}
