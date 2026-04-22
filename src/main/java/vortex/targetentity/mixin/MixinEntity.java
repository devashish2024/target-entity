package vortex.targetentity.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
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
        if (!cfg.glowEnabled)
            return;

        // Classify
        EntityKind kind;
        if (self instanceof ItemEntity) {
            return; // drops never get GLOW mode
        } else if (self instanceof Player p) {
            if (p == mc.player)
                return; // don't glow self
            kind = EntityKind.PLAYER;
        } else if (self instanceof net.minecraft.world.entity.LivingEntity) {
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
        if (!GlowTracker.isActive(self))
            return;

        cir.setReturnValue(true);
    }
}
