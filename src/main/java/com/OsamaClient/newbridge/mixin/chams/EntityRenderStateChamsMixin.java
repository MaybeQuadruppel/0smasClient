package com.OsamaClient.newbridge.mixin.chams;

import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateChamsMixin implements ChamsHolder {

    @Unique private boolean newbridge$active;
    @Unique private int newbridge$visible;
    @Unique private int newbridge$occluded;

    @Override
    public void newbridge$setChams(boolean active, int visibleColor, int occludedColor) {
        this.newbridge$active = active;
        this.newbridge$visible = visibleColor;
        this.newbridge$occluded = occludedColor;
    }

    @Override
    public boolean newbridge$chamsActive() {
        return newbridge$active;
    }

    @Override
    public int newbridge$chamsVisible() {
        return newbridge$visible;
    }

    @Override
    public int newbridge$chamsOccluded() {
        return newbridge$occluded;
    }
}