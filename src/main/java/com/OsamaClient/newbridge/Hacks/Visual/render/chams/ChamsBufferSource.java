package com.OsamaClient.newbridge.Hacks.Visual.render.chams;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class ChamsBufferSource {
    private final StagedVertexBuffer stagedBuffer = new StagedVertexBuffer(
            () -> "ChamsBufferSource", RenderType.BIG_BUFFER_SIZE);
    private final List<StagedVertexBuffer.Draw> draws = new ArrayList<>();
    private final List<RenderType> drawTypes = new ArrayList<>();

    public VertexConsumer getBuffer(RenderType renderType) {
        if (!drawTypes.isEmpty() && drawTypes.getLast() == renderType
                && renderType.canConsolidateConsecutiveGeometry()) {
            return stagedBuffer.getVertexBuilder(draws.getLast());
        }

        StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(renderType.format(),
                renderType.primitiveTopology(), renderType.sortOnUpload()
                        ? RenderSystem.getProjectionType().vertexSorting() : null);

        draws.add(draw);
        drawTypes.add(renderType);
        return stagedBuffer.getVertexBuilder(draw);
    }

    public void uploadAndDraw() {
        if (draws.isEmpty()) return;

        try {
            stagedBuffer.upload();

            // 26.3: draws need a render pass from the caller -> one pass on the main target for all of ours
            var target = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget();
            try (com.mojang.renderpearl.api.commands.RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "newbridge chams", target.getColorTextureView(), java.util.Optional.empty(),
                            target.hasDepth() ? target.getDepthTextureView() : null, java.util.OptionalDouble.empty())) {
                for (int i = 0; i < draws.size(); i++) {
                    draw(drawTypes.get(i), draws.get(i), pass);
                }
            }
        } finally {
            stagedBuffer.endFrame();
            draws.clear();
            drawTypes.clear();
        }
    }

    public void close() {
        draws.clear();
        drawTypes.clear();
        stagedBuffer.close();
    }

    private void draw(RenderType type, StagedVertexBuffer.Draw draw, com.mojang.renderpearl.api.commands.RenderPass pass) {
        StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
        if (info != null) {
            type.prepare().drawFromBuffer(info, pass);
        }
    }
}