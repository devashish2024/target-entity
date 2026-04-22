package vortex.targetentity.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Custom render pipelines for the glow halo renderer.
 * Ported from ClickCrystals' ClickCrystalsRenderPipelines.
 */
@Environment(EnvType.CLIENT)
public final class GlowRenderPipelines {

    public static final ColorTargetState WITH_BLEND = new ColorTargetState(BlendFunction.TRANSLUCENT);

    /** No depth test — always rendered on top */
    public static final DepthStencilState DEPTH_NONE = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    /** Standard depth test — respects geometry occlusion */
    public static final DepthStencilState DEPTH_LEQUAL = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);

    public static final RenderPipeline PIPELINE_TRI_STRIP_CULL = RenderPipeline
            .builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("pipeline/te_fill_pipeline")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withColorTargetState(WITH_BLEND)
            .withCull(true)
            .withDepthStencilState(DEPTH_LEQUAL)
            .build();

    private GlowRenderPipelines() {
    }
}
