package vortex.targetentity.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vortex.targetentity.ModConfig;
import vortex.targetentity.ModConfig.EntityKind;
import vortex.targetentity.glow.GlowTracker;
import vortex.targetentity.render.GlowRenderer;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void te$renderEntityGlows(
            GraphicsResourceAllocator resourceAllocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            ChunkSectionsToRender chunkSectionsToRender,
            CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        ModConfig cfg = ModConfig.get();
        if (!cfg.glowEnabled)
            return;

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();

        // Build a PoseStack from the model-view matrix (same approach as ClickCrystals)
        PoseStack matrices = new PoseStack();
        matrices.mulPose(new Matrix4f(modelViewMatrix));

        for (Entity entity : mc.level.entitiesForRendering()) {
            EntityKind kind = classifyEntity(entity, mc);
            if (kind == null)
                continue;

            // Extend glow timer whenever the entity is hurt (hurtTime is set client-side
            // via sync packet, so this works correctly in both SP and MP).
            if (entity instanceof net.minecraft.world.entity.LivingEntity le && le.hurtTime > 0) {
                GlowTracker.touch(entity);
            }

            // For drops, filter on the *item* registry key (not the ItemEntity type key).
            // For others, use the entity-type key.
            final String registryKey;
            if (kind == EntityKind.DROP && entity instanceof ItemEntity ie) {
                registryKey = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(ie.getItem().getItem()).toString();
            } else {
                registryKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(entity.getType()).toString();
            }

            if (!cfg.shouldGlow(kind, registryKey))
                continue;
            if (!GlowTracker.isActive(entity))
                continue;

            // Interpolated position
            double lx = entity.xOld + (entity.getX() - entity.xOld) * tickDelta;
            double ly = entity.yOld + (entity.getY() - entity.yOld) * tickDelta;
            double lz = entity.zOld + (entity.getZ() - entity.zOld) * tickDelta;

            // Camera-relative position
            Vec3 pos = new Vec3(lx - camPos.x, ly - camPos.y, lz - camPos.z);

            // Resolve color — each kind has its own auto-color logic
            final int color;
            if (kind == EntityKind.DROP && entity instanceof ItemEntity ie) {
                color = cfg.resolveDropColor(ie, registryKey);
            } else if (kind == EntityKind.PLAYER && entity instanceof Player p) {
                color = cfg.resolvePlayerColor(p, registryKey);
            } else if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
                color = cfg.resolveMobColor(le, registryKey);
            } else {
                color = cfg.resolveColor(kind, registryKey);
            }
            float bbWidth = entity.getBbWidth();

            GlowRenderer.drawEntityHalo(matrices, pos, bbWidth, color);
        }
    }

    /**
     * Classifies an entity into DROP, PLAYER, or MOB.
     * Returns null for entities we never glow (e.g. projectiles, the local player).
     */
    private static EntityKind classifyEntity(Entity entity, Minecraft mc) {
        if (entity instanceof ItemEntity)
            return EntityKind.DROP;
        if (entity instanceof Player p) {
            // Optionally skip the local player to avoid self-highlight
            return p == mc.player ? null : EntityKind.PLAYER;
        }
        // All living entities that are not players count as MOB
        if (entity instanceof net.minecraft.world.entity.LivingEntity
                && !(entity instanceof Player)) {
            return EntityKind.MOB;
        }
        return null;
    }
}
