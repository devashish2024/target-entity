package vortex.targetentity.glow;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

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
 * <li>If {@code glowDurationSeconds == 0}, infinite mode — glow always active.</li>
 * <li>Otherwise, the entity glows only after the local player explicitly deals
 * damage to it (melee via {@code MultiPlayerGameMode.attack()} or a
 * player-owned projectile). The glow lasts {@code glowDurationSeconds}.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class GlowTracker {

    /** entity → expiry time nanos. WeakHashMap auto-cleans removed entities. */
    private static final Map<Entity, Long> EXPIRY_MAP = new WeakHashMap<>();

    /**
     * Short-lived flag: the local player just swung at / fired at this entity.
     * TTL is generous (3 s) to cover network round-trip latency.
     * Keyed by entity; value = nanos when the flag expires.
     */
    private static final Map<Entity, Long> DIRECT_ATTACK_MAP = new WeakHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called by {@code MixinMultiPlayerGameMode} when the local player directly
     * attacks an entity (melee + any attack() call).
     */
    public static void markDirectAttack(Entity entity) {
        // Flag expires after 3 s — long enough to survive a laggy server round-trip.
        DIRECT_ATTACK_MAP.put(entity, System.nanoTime() + 3_000_000_000L);
    }

    /**
     * Returns true if {@link #markDirectAttack} was called for this entity within
     * the last 3 seconds and hasn't been consumed yet (one-shot per hit).
     * Consuming the flag prevents a single swing from triggering multiple rings.
     */
    public static boolean consumeDirectAttack(Entity entity) {
        Long expiry = DIRECT_ATTACK_MAP.get(entity);
        if (expiry == null || System.nanoTime() >= expiry) {
            DIRECT_ATTACK_MAP.remove(entity);
            return false;
        }
        DIRECT_ATTACK_MAP.remove(entity); // consume
        return true;
    }

    /** Reset/extend the glow timer for a living entity using the given duration. */
    public static void touch(Entity entity, int durationSeconds) {
        if (durationSeconds <= 0)
            return;
        EXPIRY_MAP.put(entity, System.nanoTime() + (long) durationSeconds * 1_000_000_000L);
    }

    /** Returns true if this entity should currently show its effect. */
    public static boolean isActive(Entity entity, int durationSeconds) {
        // Item drops ALWAYS show — duration setting never applies to them
        if (entity instanceof ItemEntity)
            return true;

        if (durationSeconds <= 0)
            return true; // infinite mode — always on

        // Timer-based: only active if touch() was called and hasn't expired
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
