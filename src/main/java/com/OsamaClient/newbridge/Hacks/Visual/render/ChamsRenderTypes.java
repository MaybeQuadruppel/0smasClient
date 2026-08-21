package com.OsamaClient.newbridge.Hacks.Visual.render;

import com.OsamaClient.newbridge.mixin.chams.RenderSetupAccessor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChamsRenderTypes {
    private static final Map<Identifier, RenderType> VISIBLE = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> OCCLUDED = new ConcurrentHashMap<>();

    private ChamsRenderTypes() {
    }

    public static Identifier textureOf(RenderSetup setup) {
        if (setup == null) return null;
        try {
            Map<String, ?> textures = ((RenderSetupAccessor) (Object) setup).newbridge$getTextures();
            if (textures == null) return null;
            Object binding = textures.get("Sampler0");
            if (binding == null) return null;

            // Da TextureBinding package-private ist, holen wir die 'location' per Reflection
            Field locationField = binding.getClass().getDeclaredField("location");
            locationField.setAccessible(true);
            return (Identifier) locationField.get(binding);
        } catch (Throwable t) {
            return null;
        }
    }

    public static RenderType visible(Identifier texture, RenderPipeline pipeline) {
        return VISIBLE.computeIfAbsent(texture, tex -> RenderType.create(
                "osama_chams_visible",
                RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", tex)
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup()
        ));
    }

    public static RenderType occluded(Identifier texture, RenderPipeline pipeline) {
        return OCCLUDED.computeIfAbsent(texture, tex -> RenderType.create(
                "osama_chams_occluded",
                RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", tex)
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup()
        ));
    }
}