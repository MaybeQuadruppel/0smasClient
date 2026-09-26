package com.OsamaClient.newbridge.Hacks.Visual.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Split-Color-("Chams")-Pipelines für UNtexturierte ESP-Geometrie (Boxen/Linien),
 * nach demselben Prinzip wie {@link com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsPipeline}:
 * zwei Pipelines mit gegensätzlichem Depth-Test - eine zeichnet nur dort, wo die
 * Geometrie wirklich die vorderste Fläche ist (sichtbar), die andere nur dort, wo
 * etwas anderes bereits davor liegt (von Terrain verdeckt) - statt eines einzelnen
 * "immer sichtbar"-Passes ohne jeden Depth-Test wie {@link RenderTypes#storageEspFillSeeThrough()}
 * / {@link RenderTypes#storageEspLinesSeeThrough()}.
 *
 * WICHTIG - kein Depth-Write auf dem "visible"-Pass (mehr): beide Passes (Fill UND Lines, "visible"
 * UND "occluded") schreiben absichtlich NICHT in den Depth-Buffer, sie LESEN ihn nur. Der visible-Pass
 * hatte das früher getan ("damit sich überlappende Ziele untereinander noch korrekt verdecken"), aber
 * das führte zu Selbst-Verdeckung: die (näher liegende) Vorderseite/Fill einer Box schrieb ihre eigene
 * Tiefe, und die weiter entfernten Linien DERSELBEN Box (Rückseite, vom Spieler aus nicht direkt
 * angeschaut) wurden danach gegen diese eigene Vorderseite getestet statt gegen echtes Terrain - sie
 * erschienen dadurch fälschlich als "verdeckt" (rot), obwohl freie Sicht bestand. Ohne Depth-Write
 * testet jede Linie/jedes Face unabhängig gegen die tatsächliche Welt-Tiefe. Preis dafür: zwei sich
 * gegenseitig überlappende ESP-Ziele (Spieler A genau vor Spieler B) verdecken sich untereinander nicht
 * mehr ganz korrekt - deutlich seltener als der Selbst-Verdeckungs-Bug und optisch kaum auffällig.
 *
 * {@link com.OsamaClient.newbridge.Hacks.Visual.render,ChamsRenderTypes} passt hier nicht,
 * weil es einen Sampler/Textur-Binding erwartet (für die texturierten Entity-Body-Modelle) - eine
 * reine Farb-Box/-Linie hat keine Textur. Deshalb eigene, schlanke Pipelines direkt auf Basis von
 * {@link RenderPipelines#LINES} / {@link RenderPipelines#DEBUG_FILLED_BOX} - genau dieselben
 * Templates, von denen auch {@link RenderTypes}s "see-through"-Pipelines abgeleitet sind, nur mit
 * echtem (invertiertem) Depth-Test statt {@code Optional.empty()}.
 *
 * Werden - anders als {@link com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsPipeline} -
 * direkt über den normalen {@link RenderPipeline.Builder} gebaut statt über den
 * Reflection-Invoker: wir bauen hier von Grund auf neu statt eine fertige Pipeline zu "klonen",
 * der Invoker-Umweg ist also nicht nötig.
 */
public final class HitboxChamsPipeline {

    private static RenderPipeline visibleLinesPipeline, occludedLinesPipeline, visibleFillPipeline, occludedFillPipeline;
    private static RenderType visibleLines, occludedLines, visibleFill, occludedFill;

    private HitboxChamsPipeline() {}

    public static RenderType visibleLines() { ensureBuilt(); return visibleLines; }
    public static RenderType occludedLines() { ensureBuilt(); return occludedLines; }
    public static RenderType visibleFill() { ensureBuilt(); return visibleFill; }
    public static RenderType occludedFill() { ensureBuilt(); return occludedFill; }

    private static synchronized void ensureBuilt() {
        if (visibleLinesPipeline != null) return;

        CompareOp linesVisibleOp = depthOpOf(RenderPipelines.LINES);
        CompareOp linesOccludedOp = invertDepth(linesVisibleOp);
        CompareOp fillVisibleOp = depthOpOf(RenderPipelines.DEBUG_FILLED_BOX);
        CompareOp fillOccludedOp = invertDepth(fillVisibleOp);

        // Sichtbar: normaler Depth-Test (wie jede normale Geometrie), aber OHNE Depth-Write - sonst
        // "verdeckt" die nähere Vorderseite/Fill einer Box die weiter entfernten Linien DERSELBEN Box
        // (Selbst-Verdeckung statt echter Terrain-Verdeckung, siehe Klassenkommentar). Verdeckt:
        // invertierter Test (zeichnet nur, WO die Box gerade hinter etwas anderem liegt), ebenfalls ohne
        // Depth-Write, damit der "verdeckt"-Pass nicht den echten Tiefenwert der Wand überschreibt.
        visibleLinesPipeline = buildLinesPipeline("hitbox_chams_visible_lines", linesVisibleOp, false);
        occludedLinesPipeline = buildLinesPipeline("hitbox_chams_occluded_lines", linesOccludedOp, false);
        visibleFillPipeline = buildFillPipeline("hitbox_chams_visible_fill", fillVisibleOp, false);
        occludedFillPipeline = buildFillPipeline("hitbox_chams_occluded_fill", fillOccludedOp, false);

        visibleLines = RenderType.create("newbridge_hitbox_chams_visible_lines",
                RenderSetup.builder(visibleLinesPipeline).createRenderSetup());
        occludedLines = RenderType.create("newbridge_hitbox_chams_occluded_lines",
                RenderSetup.builder(occludedLinesPipeline).createRenderSetup());
        visibleFill = RenderType.create("newbridge_hitbox_chams_visible_fill",
                RenderSetup.builder(visibleFillPipeline).sortOnUpload().createRenderSetup());
        occludedFill = RenderType.create("newbridge_hitbox_chams_occluded_fill",
                RenderSetup.builder(occludedFillPipeline).sortOnUpload().createRenderSetup());
    }

    private static RenderPipeline buildLinesPipeline(String name, CompareOp op, boolean depthWrite) {
        return copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
                .withLocation(Identifier.fromNamespaceAndPath("newbridgeclient", "pipeline/" + name))
                .withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                .withPrimitiveTopology(PrimitiveTopology.LINES)
                .withDepthStencilState(Optional.of(new DepthStencilState(op, depthWrite)))
                .build();
    }

    private static RenderPipeline buildFillPipeline(String name, CompareOp op, boolean depthWrite) {
        return copyLayouts(RenderPipeline.builder(), RenderPipelines.DEBUG_FILLED_BOX)
                .withLocation(Identifier.fromNamespaceAndPath("newbridgeclient", "pipeline/" + name))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(Optional.of(new DepthStencilState(op, depthWrite)))
                .build();
    }

    /** Gleiche Fallback-Logik wie {@link com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsPipeline}:
     *  ohne eigenen Depth-Test am Template greift der Engine-Standard (reversed-Z, daher GEQUAL). */
    private static CompareOp depthOpOf(RenderPipeline base) {
        DepthStencilState state = base.getDepthStencilState();
        return state == null ? CompareOp.GREATER_THAN_OR_EQUAL : state.depthTest();
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

    private static RenderPipeline.Builder copyLayouts(RenderPipeline.Builder builder, RenderPipeline template) {
        for (BindGroupLayout layout : template.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        return builder;
    }
}