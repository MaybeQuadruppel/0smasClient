package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteStateAccess;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateEmoteMixin implements EmoteStateAccess {
    @Unique
    private EmoteAnimation.Pose newbridge$emotePose;

    @Override public EmoteAnimation.Pose newbridge$getEmotePose() { return newbridge$emotePose; }

    @Override public void newbridge$setEmotePose(EmoteAnimation.Pose pose) { newbridge$emotePose = pose; }
}