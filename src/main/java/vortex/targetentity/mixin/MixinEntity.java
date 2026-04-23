package vortex.targetentity.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vortex.targetentity.ModConfig;
import vortex.targetentity.ModConfig.EntityKind;
import vortex.targetentity.ModConfig.EntityMode;
import vortex.targetentity.glow.GlowTracker;

/**
 * Makes entities with GLOW mode appear with the vanilla spectral-arrow white
 * outline entirely client-side, without modifying any server-side state.
 *
 * <p>On the client, {@code Entity.isCurrentlyGlowing()} reads shared flag 6
 * (same flag {@code setGlowingTag(true)} sets). Overriding it to return
 * {@code true} is sufficient to trigger the outline render pass in
 * {@code LevelRenderer} without touching any packets or NBT.
 *
 * <p>{@code Entity.getTeamColor()} is also overridden for mod-controlled glow
 * entities so the outline is drawn in the entity's resolved ring color rather
 * than the default white.
 */
@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void te$injectGlowMode(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        ModConfig cfg = ModConfig.get();
        if (!cfg.glowEffectEnabled)
            return;

        // Classify
        EntityKind kind;
        if (self instanceof ItemEntity) {
            return; // drops never get GLOW mode
        } else if (self instanceof Player p) {
            if (p == mc.player)
                return; // don't glow self
            kind = EntityKind.PLAYER;
        } else if (self instanceof LivingEntity) {
            kind = EntityKind.MOB;
        } else {
            return;
        }

        if (cfg.modeFor(kind) != EntityMode.GLOW)
            return;

        // Resolve registry key
        String registryKey = BuiltInRegistries.ENTITY_TYPE
                .getKey(self.getType()).toString();

        if (!cfg.shouldGlow(kind, registryKey))
            return;
        if (!cfg.alwaysGlows(kind) && !GlowTracker.isActive(self, cfg.glowDurationSeconds))
            return;

        cir.setReturnValue(true);
    }

    /**
     * Forces the vanilla glow outline to use the mod-resolved color for entities
     * that are under mod-controlled GLOW mode. The outline renderer reads
     * {@code getTeamColor()} for every entity; returning our color here means
     * players/mobs glow in their auto-detected or configured color rather than
     * the default white.
     *
     * <p>Returns early (falling through to vanilla logic) for any entity that
     * is not currently showing a mod-controlled glow outline.
     */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void te$injectGlowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        ModConfig cfg = ModConfig.get();
        if (!cfg.glowEffectEnabled)
            return;

        EntityKind kind;
        if (self instanceof ItemEntity) {
            return;
        } else if (self instanceof Player p) {
            if (p == mc.player)
                return;
            kind = EntityKind.PLAYER;
        } else if (self instanceof LivingEntity) {
            kind = EntityKind.MOB;
        } else {
            return;
        }

        if (cfg.modeFor(kind) != EntityMode.GLOW)
            return;

        String registryKey = BuiltInRegistries.ENTITY_TYPE
                .getKey(self.getType()).toString();

        if (!cfg.shouldGlow(kind, registryKey))
            return;
        if (!cfg.alwaysGlows(kind) && !GlowTracker.isActive(self, cfg.glowDurationSeconds))
            return;

        // Resolve color using the same logic as the ring renderer
        final int argb;
        if (kind == EntityKind.PLAYER && self instanceof Player p) {
            argb = cfg.resolvePlayerColor(p, registryKey);
        } else if (self instanceof LivingEntity le) {
            argb = cfg.resolveMobColor(le, registryKey);
        } else {
            return;
        }

        // getTeamColor() returns packed RGB (no alpha)
        cir.setReturnValue(argb & 0x00FFFFFF);
    }
}
