package com.OsamaClient.newbridge.UI.gui.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Our own pipelines for the ClickGUI. Only blaze3d pipeline config is used here (no MC GUI rendering):
 * the shaders live in {@code assets/newbridge/shaders/core/ui_*.vsh|fsh}.
 */
public final class UiPipelines {

    /** SDF shape vertex: position, color, shape (local.xy, mode, param), rect (halfW, halfH, radius, unused). */
    public static final VertexFormat SDF_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("Shape", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Rect", GpuFormat.RGBA32_FLOAT)
            .build();
    public static final int SDF_STRIDE = 12 + 4 + 16 + 16;

    /** Glyph vertex: position, uv, color. */
    public static final VertexFormat TEXT_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .build();
    public static final int TEXT_STRIDE = 12 + 8 + 4;

    public static RenderPipeline SDF;
    public static RenderPipeline TEXT;

    private UiPipelines() {}

    public static void register() {
        if (SDF != null) return;
        SDF = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(id("pipeline/ui_sdf"))
                .withVertexShader(id("core/ui_sdf"))
                .withFragmentShader(id("core/ui_sdf"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, SDF_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .build());

        TEXT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withLocation(id("pipeline/ui_text"))
                .withVertexShader(id("core/ui_text"))
                .withFragmentShader(id("core/ui_text"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, TEXT_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("newbridge", path);
    }
}
