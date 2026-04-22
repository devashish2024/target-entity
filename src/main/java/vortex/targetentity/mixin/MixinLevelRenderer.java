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
import net.minecraft.world.entity.projectile.Projectile;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vortex.targetentity.ModConfig;
import vortex.targetentity.ModConfig.EntityKind;
import vortex.targetentity.ModConfig.EntityMode;
import vortex.targetentity.glow.GlowTracker;
import vortex.targetentity.render.GlowRenderer;

import java.util.Map;
import java.util.WeakHashMap;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    /**
     * Tracks the previous hurtTime for each entity so we can detect the exact
     * frame when a hit lands (transition from lower to higher value).
     * WeakHashMap ensures entries are GC'd when entities are unloaded.
     */
    private static final Map<Entity, Integer> PREV_HURT_TIMES = new WeakHashMap<>();

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

            // Detect the exact frame a hit lands: hurtTime spikes from a lower
            // value to a higher one (always resets to max then counts down).
            // Only trigger when the local player was the attacker:
            //   a) Direct melee — flagged by MixinMultiPlayerGameMode.attack()
            //   b) Player-owned projectile — a Projectile with getOwner()==mc.player
            //      that is still in the world and within 4 blocks of this entity.
            if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
                int prev = PREV_HURT_TIMES.getOrDefault(entity, 0);
                if (le.hurtTime > prev) {
                    // hurtTime just spiked — a hit registered this frame
                    if (GlowTracker.consumeDirectAttack(entity)
                            || hasNearbyPlayerProjectile(entity, mc)) {
                        GlowTracker.touch(entity);
                    }
                }
                PREV_HURT_TIMES.put(entity, le.hurtTime);
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

            // GLOW mode uses the vanilla outline effect (handled by MixinEntity).
            // Only draw a ring for RING mode.
            if (cfg.modeFor(kind) != EntityMode.RING)
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

    /**
     * Returns true if any projectile currently in the world is owned by the
     * local player AND is within 4 blocks of {@code target}.
     *
     * <p>This covers bows, crossbows, tridents, and any other ranged weapon that
     * creates a {@link Projectile} entity. Arrows remain embedded in the entity
     * briefly after impact, so the proximity check reliably catches the hit frame.
     */
    private static boolean hasNearbyPlayerProjectile(Entity target, Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Projectile p
                    && p.getOwner() == mc.player
                    && p.distanceToSqr(target) < 16.0) { // 4 blocks squared
                return true;
            }
        }
        return false;
    }
}
