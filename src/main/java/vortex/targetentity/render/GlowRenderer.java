package vortex.targetentity.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a smooth gradient cylinder (halo ring) around an entity's feet.
 * The ring fades from full colour at the bottom to transparent at the top,
 * creating a "glow" effect.
 */
@Environment(EnvType.CLIENT)
public final class GlowRenderer {

    /**
     * Draws a gradient cylinder at the given world-space (camera-relative)
     * position.
     *
     * @param matrices    current PoseStack
     * @param x           camera-relative X
     * @param y           camera-relative Y (feet)
     * @param z           camera-relative Z
     * @param radius      horizontal radius of the halo ring
     * @param height      vertical height of the halo fade
     * @param colorBottom ARGB colour at the base of the ring (opaque)
     * @param colorTop    ARGB colour at the top of the ring (transparent)
     */
    public static void fillCylGradient(
            PoseStack matrices,
            double x, double y, double z,
            double radius, double height,
            int colorBottom, int colorTop) {
        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f mat = matrices.last().pose();

        for (int i = 0; i <= 360; i += 30) {
            float angle = (float) Math.toRadians(i);
            float cx = (float) (x + Mth.cos(angle) * radius);
            float cz = (float) (z + Mth.sin(angle) * radius);
            buf.addVertex(mat, cx, (float) y, cz).setColor(colorBottom);
            buf.addVertex(mat, cx, (float) (y + height), cz).setColor(colorTop);
        }

        GlowRenderLayers.TRI_STRIP_CULL.draw(buf.buildOrThrow());
    }

    /**
     * Convenience overload: draws a halo ring sized for a given entity bounding
     * box.
     *
     * @param matrices current PoseStack
     * @param pos      camera-relative feet position
     * @param bbWidth  entity bounding-box width (from {@code entity.getBbWidth()})
     * @param color    ARGB base colour (full alpha at bottom, transparent at top)
     */
    public static void drawEntityHalo(
            PoseStack matrices, Vec3 pos, float bbWidth, int color) {
        // Radius = half the bb width plus a small clearance so the ring sits
        // *around* the entity, not through it.
        double radius = bbWidth * 0.5 + 0.08;
        // Height scales with radius so it looks proportional for all entity sizes.
        double height = radius * 0.55;
        int colorFade = 0x00FFFFFF & color; // same RGB but fully transparent
        fillCylGradient(matrices, pos.x, pos.y, pos.z, radius, height, color, colorFade);
    }

    private GlowRenderer() {
    }
}
