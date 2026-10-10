package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.BendLimbs;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Splits the player model and the player's armor models (once) right before they are baked. */
@Mixin(EntityModelSet.class)
public abstract class EntityModelSetMixin {

    @Shadow private Map<ModelLayerLocation, LayerDefinition> roots;

    @Inject(method = "bakeLayer", at = @At("HEAD"))
    private void newbridge$bend(ModelLayerLocation location, CallbackInfoReturnable<ModelPart> cir) {
        LayerDefinition def = roots.get(location);
        if (def != null) BendLimbs.prepare(location, ((LayerDefinitionAccessor) def).newbridge$getMesh());
    }
}