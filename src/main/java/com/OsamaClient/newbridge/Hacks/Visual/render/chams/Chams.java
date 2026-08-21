package com.OsamaClient.newbridge.Hacks.Visual.render.chams;

import com.OsamaClient.newbridge.Hacks.Visual.render.ChamsRenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsPipeline;
import com.OsamaClient.newbridge.mixin.chams.RenderTypeStateAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class Chams {

    public static final int FULLBRIGHT = 0xF000F0;

    private Chams() {
    }

    public static RenderType chamsVisible(RenderType original) {
        Identifier texture = textureOf(original);
        return texture == null ? null : ChamsRenderTypes.visible(texture, ChamsPipeline.visible());
    }

    public static RenderType chamsOccluded(RenderType original) {
        Identifier texture = textureOf(original);
        return texture == null ? null : ChamsRenderTypes.occluded(texture, ChamsPipeline.occluded());
    }

    private static Identifier textureOf(RenderType original) {
        try {
            RenderSetup setup = ((RenderTypeStateAccessor) (Object) original).newbridge$getState();
            return setup == null ? null : ChamsRenderTypes.textureOf(setup);
        } catch (Throwable t) {
            return null;
        }
    }
}
