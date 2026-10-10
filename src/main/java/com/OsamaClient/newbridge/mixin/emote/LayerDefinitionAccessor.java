package com.OsamaClient.newbridge.mixin.emote;

import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LayerDefinition.class)
public interface LayerDefinitionAccessor {
    @Accessor("mesh")
    MeshDefinition newbridge$getMesh();
}