package com.OsamaClient.newbridge.Utils.Render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

public interface IVertexConsumerProvider {
    VertexConsumer getBuffer(RenderType layer);

    void setOffset(int offsetX, int offsetY, int offsetZ);
}
