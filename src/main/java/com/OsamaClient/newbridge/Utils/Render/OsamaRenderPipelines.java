package com.OsamaClient.newbridge.Utils.Render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class OsamaRenderPipelines {

    private static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath("newbridge", path);
    }

    // Pipeline für Flächen ohne Depth-Test (ESP durch Wände)
    public static final RenderPipeline WORLD_COLORED = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(identifier("pipeline/world_colored"))
                    .withDepthStencilState(Optional.empty()) // Depth-Test aus
                    .build()
    );

    // Pipeline für Linien ohne Depth-Test (ESP durch Wände)
    public static final RenderPipeline WORLD_COLORED_LINES = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(identifier("pipeline/world_colored_lines"))
                    .withDepthStencilState(Optional.empty()) // Depth-Test aus
                    .build()
    );



    private OsamaRenderPipelines() {}
}