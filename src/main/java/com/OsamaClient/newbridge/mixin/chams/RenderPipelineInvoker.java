package com.OsamaClient.newbridge.mixin.chams;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(RenderPipeline.class)
public interface RenderPipelineInvoker {
    @Invoker("<init>")
    static RenderPipeline newbridge$create(Identifier location, java.util.Map<com.mojang.renderpearl.api.pipeline.ShaderType, Identifier> shaders,
                                        ShaderDefines shaderDefines, java.util.Collection<BindGroupLayout> bindGroupLayouts,
                                        ColorTargetState[] colorTargetStates, DepthStencilState depthStencilState,
                                        PolygonMode polygonMode, boolean cull, VertexFormat[] vertexFormatPerBuffer,
                                        PrimitiveTopology primitiveTopology, int pushConstantSize, int sortKey) {
        throw new AssertionError();
    }
}