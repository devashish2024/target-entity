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
import net.minecraft.world.damagesource.DamageSource;
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

import java.util.ArrayList;
import java.util.List;
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
    private static Entity activeRingTarget;
    private static Entity previousRingTarget;
    private static long ringTransitionStartNanos;
    private static final long RING_TRANSITION_NANOS = 694_000_000L;

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
        if (!cfg.ringEnabled)
            return;

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();

        // Build a PoseStack from the model-view matrix (same approach as ClickCrystals)
        PoseStack matrices = new PoseStack();
        matrices.mulPose(new Matrix4f(modelViewMatrix));

        List<Entity> entities = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            entities.add(entity);

            EntityKind kind = classifyEntity(entity, mc);
            if (kind == null)
                continue;

            // Detect the exact frame a hit lands: hurtTime spikes from a lower
            // value to a higher one (always resets to max then counts down).
            // Only trigger when the local player was the attacker:
            // a) Direct melee — flagged by MixinMultiPlayerGameMode.attack()
            // b) Player-owned projectile — a Projectile with getOwner()==mc.player
            // that is still in the world and within 4 blocks of this entity.
            if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
                int prev = PREV_HURT_TIMES.getOrDefault(entity, 0);
                if (le.hurtTime > prev) {
                    // hurtTime just spiked — a hit registered this frame
                    if (wasHitByLocalPlayer(le, mc)) {
                        GlowTracker.touch(entity, cfg.durationFor(kind));
                        if (cfg.singleTargetRing && kind != EntityKind.DROP) {
                            setActiveSingleRingTarget(entity, cfg);
                        }
                    }
                }
                PREV_HURT_TIMES.put(entity, le.hurtTime);
            }
        }

        // Keep the previous active single target if still eligible.
        if (cfg.singleTargetRing && !isEligibleSingleRingTarget(activeRingTarget, cfg, mc)) {
            setActiveSingleRingTarget(findFallbackSingleRingTarget(entities, cfg, mc), cfg);
        }

        for (Entity entity : entities) {
            EntityKind kind = classifyEntity(entity, mc);
            if (kind == null)
                continue;

            // For drops, filter on the *item* registry key (not the ItemEntity type key).
            // For others, use the entity-type key.
            final String registryKey = getRegistryKey(entity, kind);

            if (!cfg.shouldGlow(kind, registryKey))
                continue;
            // Pass if always-on, otherwise require an active hit timer
            if (!cfg.alwaysGlows(kind) && !GlowTracker.isActive(entity, cfg.durationFor(kind)))
                continue;

            // GLOW mode uses the vanilla outline effect (handled by MixinEntity).
            // Only draw a ring for RING mode.
            if (cfg.modeFor(kind) != EntityMode.RING)
                continue;

            // Single-target mode applies only to living entities. Drops always keep
            // the current behaviour and may render simultaneously.
            if (cfg.singleTargetRing && kind != EntityKind.DROP) {
                if (!entity.equals(activeRingTarget))
                    continue;
            }

            Vec3 drawPos = computeRingDrawPos(entity, kind, tickDelta, camPos);

            if (cfg.singleTargetRing && kind != EntityKind.DROP && previousRingTarget != null) {
                double t = getRingTransitionProgress();
                if (t >= 1.0) {
                    previousRingTarget = null;
                } else if (isEligibleSingleRingTarget(previousRingTarget, cfg, mc)) {
                    Vec3 prevDrawPos = computeRingDrawPos(previousRingTarget, classifyEntity(previousRingTarget, mc),
                            tickDelta,
                            camPos);
                    double eased = 1.0 - Math.pow(1.0 - t, 3.0);
                    drawPos = new Vec3(
                            prevDrawPos.x + (drawPos.x - prevDrawPos.x) * eased,
                            prevDrawPos.y + (drawPos.y - prevDrawPos.y) * eased,
                            prevDrawPos.z + (drawPos.z - prevDrawPos.z) * eased);
                } else {
                    previousRingTarget = null;
                }
            }

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

            GlowRenderer.drawEntityHalo(matrices, drawPos, entity.getBbWidth(), color);
        }
    }

    /**
     * True only when this hit can be attributed to the local player.
     * Covers:
     * - Direct melee (flagged by MultiPlayerGameMode.attack)
     * - Player-owned projectiles (bow, crossbow, trident, snowball, etc.)
     *
     * Explicitly avoids broad attacker heuristics so indirect/environmental
     * damage attribution (e.g., TNT/anvil chains) does not incorrectly trigger.
     */
    private static boolean wasHitByLocalPlayer(net.minecraft.world.entity.LivingEntity target, Minecraft mc) {
        // Direct local attack path (melee / direct attack() flow).
        if (GlowTracker.consumeDirectAttack(target)) {
            return true;
        }

        // Prefer authoritative per-hit damage source attribution.
        DamageSource src = target.getLastDamageSource();
        if (src != null) {
            Entity direct = src.getDirectEntity();
            if (direct == mc.player) {
                return true;
            }
            if (direct instanceof Projectile p && p.getOwner() == mc.player) {
                return true;
            }
        }

        // Fallback for edge cases where source metadata is unavailable client-side.
        return hasNearbyPlayerProjectile(target, mc);
    }

    private static String getRegistryKey(Entity entity, EntityKind kind) {
        if (kind == EntityKind.DROP && entity instanceof ItemEntity ie) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(ie.getItem().getItem()).toString();
        }
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString();
    }

    private static void setActiveSingleRingTarget(Entity entity, ModConfig cfg) {
        if (!cfg.singleTargetRing || entity == null)
            return;
        if (entity.equals(activeRingTarget))
            return;

        previousRingTarget = activeRingTarget;
        activeRingTarget = entity;
        ringTransitionStartNanos = System.nanoTime();
    }

    private static double getRingTransitionProgress() {
        if (ringTransitionStartNanos <= 0L)
            return 1.0;
        long elapsed = System.nanoTime() - ringTransitionStartNanos;
        if (elapsed <= 0L)
            return 0.0;
        return Math.min(1.0, (double) elapsed / (double) RING_TRANSITION_NANOS);
    }

    private static Entity findFallbackSingleRingTarget(List<Entity> entities, ModConfig cfg, Minecraft mc) {
        Entity best = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (Entity entity : entities) {
            if (!isEligibleSingleRingTarget(entity, cfg, mc))
                continue;

            double distSqr = entity.distanceToSqr(mc.player);
            if (distSqr < bestDistSqr) {
                best = entity;
                bestDistSqr = distSqr;
            }
        }

        return best;
    }

    private static boolean isEligibleSingleRingTarget(Entity entity, ModConfig cfg, Minecraft mc) {
        if (entity == null || mc.level == null || mc.player == null)
            return false;
        if (!entity.isAlive() || entity.isRemoved())
            return false;

        EntityKind kind = classifyEntity(entity, mc);
        if (kind == null || kind == EntityKind.DROP)
            return false;

        if (cfg.modeFor(kind) != EntityMode.RING)
            return false;

        String registryKey = getRegistryKey(entity, kind);
        if (!cfg.shouldGlow(kind, registryKey))
            return false;

        return cfg.alwaysGlows(kind) || GlowTracker.isActive(entity, cfg.durationFor(kind));
    }

    private static Vec3 computeRingDrawPos(Entity entity, EntityKind kind, float tickDelta, Vec3 camPos) {
        // Interpolated world position converted into camera-relative space.
        double lx = entity.xOld + (entity.getX() - entity.xOld) * tickDelta;
        double ly = entity.yOld + (entity.getY() - entity.yOld) * tickDelta;
        double lz = entity.zOld + (entity.getZ() - entity.zOld) * tickDelta;
        Vec3 pos = new Vec3(lx - camPos.x, ly - camPos.y, lz - camPos.z);

        if (kind == EntityKind.DROP)
            return pos;

        // Preserve existing per-entity glide for living entities.
        double entityHeight = entity.getBbHeight();
        double radius = entity.getBbWidth() * 0.5 + 0.08;
        double ringHeight = radius * 0.55;
        double yMax = Math.max(0.0, entityHeight - ringHeight);
        double phase = (System.nanoTime() / 1_000_000_000.0) * Math.PI;
        double t = (1.0 - Math.cos(phase)) / 2.0;
        return new Vec3(pos.x, pos.y + yMax * t, pos.z);
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
     * <p>
     * This covers bows, crossbows, tridents, and any other ranged weapon that
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
