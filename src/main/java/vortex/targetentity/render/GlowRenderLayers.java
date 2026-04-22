package vortex.targetentity.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Lazily-initialised render types for the glow halo cylinder.
 * Ported from ClickCrystals' ClickCrystalsRenderLayers.
 */
@Environment(EnvType.CLIENT)
public final class GlowRenderLayers {

    /**
     * Depth-tested, back-face-culled triangle strip. Used for the cylinder halo.
     */
    public static final RenderType TRI_STRIP_CULL;

    static {
        RenderSetup setup = RenderSetup.builder(GlowRenderPipelines.PIPELINE_TRI_STRIP_CULL)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup();

        TRI_STRIP_CULL = RenderType.create("te_layer_tri_strip_cull", setup);
    }

    private GlowRenderLayers() {
    }
}
