package vortex.targetentity.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vortex.targetentity.glow.GlowTracker;

/**
 * Intercepts the local player's direct attack so we can attribute the
 * resulting hurt animation to the local player (not to any other damage
 * source).
 *
 * <p>{@code MultiPlayerGameMode.attack()} is called ONLY for the local
 * player's own attacks — it is what sends the {@code ServerboundInteractPacket}
 * to the server. It covers every melee weapon (sword, axe, mace, trident
 * throw release, punch) as well as any modded tool that routes through the
 * standard attack flow.
 */
@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    /**
     * Mark the target so that when its {@code hurtTime} subsequently spikes in
     * the render loop we know the damage was from the local player.
     */
    @Inject(method = "attack", at = @At("HEAD"))
    private void te$onAttack(Player player, Entity target, CallbackInfo ci) {
        GlowTracker.markDirectAttack(target);
    }
}
