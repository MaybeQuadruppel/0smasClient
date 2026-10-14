package com.OsamaClient.newbridge.Hacks.Visual.render.chams;

import com.OsamaClient.newbridge.mixin.chams.RenderPipelineInvoker;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ChamsPipeline {
    private static RenderPipeline visible;
    private static RenderPipeline occluded;
    private static boolean tried;

    private ChamsPipeline() {
    }

    public static RenderPipeline visible() {
        ensureBuilt();
        return visible != null ? visible : RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    }

    public static RenderPipeline occluded() {
        ensureBuilt();
        return occluded != null ? occluded : RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    }

    private static void ensureBuilt() {
        if (tried) return;
        tried = true;
        RenderPipeline base = RenderPipelines.ENTITY_TRANSLUCENT_CULL;
        try {
            DepthStencilState baseDepth = base.getDepthStencilState();
            CompareOp visibleOp = baseDepth == null ? CompareOp.GREATER_THAN_OR_EQUAL : baseDepth.depthTest();
            CompareOp occludedOp = invertDepth(visibleOp);
            visible = clone(base, "chams_visible", new DepthStencilState(visibleOp, true));
            occluded = clone(base, "chams_occluded", new DepthStencilState(occludedOp, false));
        } catch (Throwable t) {
            visible = base;
            occluded = base;
        }
    }

    private static final Identifier CHAMS_FRAGMENT = Identifier.fromNamespaceAndPath("newbridgeclient", "core/chams");

    private static RenderPipeline clone(RenderPipeline base, String name, DepthStencilState depth) {
        java.util.Map<com.mojang.renderpearl.api.pipeline.ShaderType, Identifier> shaders =
                new java.util.EnumMap<>(com.mojang.renderpearl.api.pipeline.ShaderType.class);
        shaders.putAll(base.getShaders());
        shaders.put(com.mojang.renderpearl.api.pipeline.ShaderType.FRAGMENT, CHAMS_FRAGMENT);
        return RenderPipelineInvoker.newbridge$create(
                Identifier.fromNamespaceAndPath("newbridgeclient", "pipeline/" + name),
                shaders,
                base.getShaderDefines(),
                base.getBindGroupLayouts(),
                base.getColorTargetStates().toArray(new com.mojang.renderpearl.api.pipeline.ColorTargetState[0]),
                depth,
                base.getPolygonMode(),
                true,
                base.getVertexFormatBindings().toArray(new com.mojang.renderpearl.api.vertex.VertexFormat[0]),
                base.getPrimitiveTopology(),
                base.pushConstantSize(),
                base.getSortKey()
        );
    }

    private static CompareOp invertDepth(CompareOp op) {
        return switch (op) {
            case GREATER_THAN_OR_EQUAL -> CompareOp.LESS_THAN;
            case GREATER_THAN -> CompareOp.LESS_THAN_OR_EQUAL;
            case LESS_THAN_OR_EQUAL -> CompareOp.GREATER_THAN;
            case LESS_THAN -> CompareOp.GREATER_THAN_OR_EQUAL;
            default -> CompareOp.LESS_THAN;
        };
    }
}