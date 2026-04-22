package vortex.targetentity.glow;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import vortex.targetentity.ModConfig;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks which entities currently have an active glow ring.
 *
 * <p>
 * <b>Item drops always glow</b> — duration never applies to them.
 *
 * <p>
 * For living entities (mobs, players):
 * <ul>
 * <li>If {@code glowDurationSeconds == 0}, infinite mode — glow always
 * active.</li>
 * <li>Otherwise, an entity glows when recently spawned (tickCount heuristic)
 * OR when explicitly "touched" via {@link #touch(Entity)} (called on
 * hurt).</li>
 * </ul>
 *
 * <p>
 * Hurt-based touch is applied in MixinLevelRenderer by checking
 * {@code entity.hurtTime > 0} before drawing — this field is set client-side
 * (via sync packet) so it works correctly in both SP and MP.
 */
@Environment(EnvType.CLIENT)
public final class GlowTracker {

    /** entity → expiry time nanos. WeakHashMap auto-cleans removed entities. */
    private static final Map<Entity, Long> EXPIRY_MAP = new WeakHashMap<>();

    /** Reset/extend the glow timer for a living entity. */
    public static void touch(Entity entity) {
        int dur = ModConfig.get().glowDurationSeconds;
        if (dur <= 0)
            return;
        EXPIRY_MAP.put(entity, System.nanoTime() + (long) dur * 1_000_000_000L);
    }

    /** Returns true if this entity should currently show a glow ring. */
    public static boolean isActive(Entity entity) {
        // Item drops ALWAYS glow — duration setting never applies to them
        if (entity instanceof ItemEntity)
            return true;

        int dur = ModConfig.get().glowDurationSeconds;
        if (dur <= 0)
            return true; // infinite mode

        // Newly-spawned: glow for first <dur> seconds via tickCount
        if (entity.tickCount < dur * 20)
            return true;

        // Explicit timer set by touch() on hurt
        Long expiry = EXPIRY_MAP.get(entity);
        if (expiry == null)
            return false;
        boolean active = System.nanoTime() < expiry;
        if (!active)
            EXPIRY_MAP.remove(entity);
        return active;
    }

    private GlowTracker() {
    }
}
