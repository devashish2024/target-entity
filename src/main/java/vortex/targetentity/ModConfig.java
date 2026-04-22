package vortex.targetentity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.scores.Team;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ModConfig {

    // ── Singleton ────────────────────────────────────────────────────────────
    private static ModConfig INSTANCE = new ModConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("target-entity.json");

    public static ModConfig get() {
        return INSTANCE;
    }

    // ── Global toggles ───────────────────────────────────────────────────────
    /** Master enable for the ring (custom halo) effect. */
    public boolean ringEnabled = true;

    /** Master enable for the glow (vanilla spectral outline) effect. */
    public boolean glowEffectEnabled = true;

    /**
     * Per-entity-type effect mode.
     * RING  = custom coloured halo ring (current behaviour).
     * GLOW  = vanilla spectral-arrow white outline, client-side only. Not
     *          available for item drops (no visual difference).
     * OFF   = disabled.
     */
    public EntityMode playerMode = EntityMode.RING;
    public EntityMode mobMode    = EntityMode.RING;

    /**
     * Drops can only be RING or OFF — vanilla glow outline has no effect on
     * item entities, so GLOW is intentionally excluded.
     */
    public DropMode dropMode = DropMode.RING;

    /**
     * When true, ALL applicable mobs/players (passing filter) always show the
     * configured effect regardless of whether the local player has hit them.
     * Has no effect when the respective mode is OFF.
     */
    public boolean alwaysGlowPlayers = false;
    public boolean alwaysGlowMobs    = false;

    /**
     * 0 = infinite. 1–300 = seconds.
     * NOTE: ring duration NEVER applies to item drops — they always show.
     */
    public int ringDurationSeconds = 0;

    /** 0 = infinite. 1–300 = seconds for the vanilla glow outline on living entities. */
    public int glowDurationSeconds = 0;

    /** 0.0–1.0 multiplied into the alpha of every ring. Does not affect glow outline. */
    public float ringIntensity = 0.75f;

    // ── Auto-color ───────────────────────────────────────────────────────────
    /**
     * When true, dropped-item ring colour is derived from the item's display-name
     * colour (or, as a fallback, the item's rarity colour). Ignores
     * {@code colorDrops}.
     */
    public boolean autoColorDrops = true;

    /**
     * When true, player ring colour is derived from the player's team/name colour.
     * Falls back to {@code colorPlayers} when no colour can be determined.
     */
    public boolean autoColorPlayers = true;

    /**
     * When true, mob ring colour is derived from the mob's custom nametag colour.
     * Falls back to {@code colorMobs} when no nametag colour is set.
     */
    public boolean autoColorMobs = true;

    // ── Default colours (ARGB 0xAARRGGBB) ───────────────────────────────────
    public int colorDrops = 0xFFFFFFFF;
    public int colorPlayers = 0xFF55FFFF;
    public int colorMobs = 0xFF55FF55;

    // ── Per-entity-type custom colour overrides ──────────────────────────────
    /** registry-key → ARGB int, e.g. "minecraft:creeper" → 0xFF00FF00 */
    public Map<String, Integer> customColors = new HashMap<>();

    // ── Filter ───────────────────────────────────────────────────────────────
    /**
     * OFF – filters not active; everything glows.
     * BLACKLIST – everything glows EXCEPT ids in the list.
     * WHITELIST – ONLY ids in the list glow.
     */
    public FilterMode filterMode = FilterMode.OFF;

    /** Item registry keys to include/exclude (e.g. "minecraft:diamond"). */
    public Set<String> itemFilterList = new HashSet<>();
    /** Entity-type registry keys to include/exclude (e.g. "minecraft:creeper"). */
    public Set<String> mobFilterList = new HashSet<>();

    public enum FilterMode {
        OFF, BLACKLIST, WHITELIST
    }

    // ── Persistence ──────────────────────────────────────────────────────────
    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig loaded = GSON.fromJson(r, ModConfig.class);
                if (loaded != null)
                    INSTANCE = loaded;
            } catch (IOException e) {
                TargetEntity.LOGGER.error("[Target Entity] Failed to load config: {}", e.getMessage());
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            TargetEntity.LOGGER.error("[Target Entity] Failed to save config: {}", e.getMessage());
        }
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    /** Returns the configured {@link EntityMode} for a given entity kind. */
    public EntityMode modeFor(EntityKind kind) {
        return switch (kind) {
            case DROP   -> dropMode == DropMode.RING ? EntityMode.RING : EntityMode.OFF;
            case PLAYER -> playerMode;
            case MOB    -> mobMode;
        };
    }

    /**
     * Whether this entity kind should always show its effect (ignores hit-tracking).
     * True when the "always glow" toggle is on AND mode is not OFF.
     */
    public boolean alwaysGlows(EntityKind kind) {
        return switch (kind) {
            // Drops bypass hit-tracking whenever their mode is RING
            case DROP   -> dropMode == DropMode.RING;
            case PLAYER -> alwaysGlowPlayers && playerMode != EntityMode.OFF;
            case MOB    -> alwaysGlowMobs    && mobMode    != EntityMode.OFF;
        };
    }

    /**
     * Returns the effective duration (seconds) for {@code kind}'s current mode.
     * Ring entities use {@link #ringDurationSeconds}; glow entities use
     * {@link #glowDurationSeconds}. 0 means infinite.
     */
    public int durationFor(EntityKind kind) {
        return (modeFor(kind) == EntityMode.GLOW) ? glowDurationSeconds : ringDurationSeconds;
    }

    /**
     * Whether this entity should have ANY effect active (ring OR glow).
     */
    public boolean shouldGlow(EntityKind kind, String registryKey) {
        EntityMode mode = modeFor(kind);
        if (mode == EntityMode.OFF)
            return false;
        if (mode == EntityMode.RING && !ringEnabled)
            return false;
        if (mode == EntityMode.GLOW && !glowEffectEnabled)
            return false;

        if (filterMode == FilterMode.OFF)
            return true;

        Set<String> list = (kind == EntityKind.DROP) ? itemFilterList : mobFilterList;
        return switch (filterMode) {
            case BLACKLIST -> !list.contains(registryKey);
            case WHITELIST ->  list.contains(registryKey);
            case OFF -> true;
        };
    }

    /**
     * Resolves the final ARGB ring colour for a mob or player entity.
     * Generic fallback — prefer {@link #resolvePlayerColor} / {@link #resolveMobColor}.
     */
    public int resolveColor(EntityKind kind, String registryKey) {
        int base;
        if (customColors.containsKey(registryKey)) {
            base = customColors.get(registryKey);
        } else {
            base = switch (kind) {
                case DROP -> colorDrops;
                case PLAYER -> colorPlayers;
                case MOB -> colorMobs;
            };
        }
        return applyIntensity(base);
    }

    /**
     * Resolves the ring colour for a player, honouring {@link #autoColorPlayers}.
     * Priority: custom override → team colour → display-name colour → fallback.
     */
    public int resolvePlayerColor(Player player, String registryKey) {
        if (customColors.containsKey(registryKey)) {
            return applyIntensity(customColors.get(registryKey));
        }
        if (autoColorPlayers) {
            // Team colour is the standard source of player name colour in multiplayer
            Team team = player.getTeam();
            if (team != null) {
                Integer teamRgb = team.getColor().getColor();
                if (teamRgb != null) {
                    return applyIntensity(0xFF000000 | teamRgb);
                }
            }
            // Server-set display name style (e.g. coloured via scoreboards or plugins)
            TextColor tc = player.getDisplayName().getStyle().getColor();
            if (tc != null) {
                return applyIntensity(0xFF000000 | tc.getValue());
            }
        }
        return applyIntensity(colorPlayers);
    }

    /**
     * Resolves the ring colour for a mob, honouring {@link #autoColorMobs}.
     * Priority: custom override → nametag colour → fallback.
     */
    public int resolveMobColor(LivingEntity entity, String registryKey) {
        if (customColors.containsKey(registryKey)) {
            return applyIntensity(customColors.get(registryKey));
        }
        if (autoColorMobs) {
            Component customName = entity.getCustomName();
            if (customName != null) {
                TextColor tc = customName.getStyle().getColor();
                if (tc != null) {
                    return applyIntensity(0xFF000000 | tc.getValue());
                }
            }
        }
        return applyIntensity(colorMobs);
    }

    /**
     * Resolves the final ARGB ring colour for a dropped item, honouring
     * {@link #autoColorDrops} and per-item custom colour overrides.
     *
     * @param itemEntity the ItemEntity being rendered
     * @param itemKey    the *item* registry key (e.g. "minecraft:diamond")
     */
    public int resolveDropColor(ItemEntity itemEntity, String itemKey) {
        // Per-item custom override always wins
        if (customColors.containsKey(itemKey)) {
            return applyIntensity(customColors.get(itemKey));
        }

        if (autoColorDrops) {
            ItemStack stack = itemEntity.getItem();
            // 1. Try item display-name colour
            Component name = stack.getHoverName();
            TextColor tc = name.getStyle().getColor();
            if (tc != null) {
                return applyIntensity(0xFF000000 | tc.getValue());
            }
            // 2. Fallback: rarity colour
            Rarity rarity = stack.getRarity();
            int rgb = switch (rarity) {
                case COMMON -> 0xFFFFFF;
                case UNCOMMON -> 0xFFFF55;
                case RARE -> 0x55FFFF;
                case EPIC -> 0xFF55FF;
                // null/default (shouldn't happen with current enum but be safe)
                default -> 0xAAAAAA;
            };
            return applyIntensity(0xFF000000 | rgb);
        }

        return applyIntensity(colorDrops);
    }

    private int applyIntensity(int argb) {
        int a = (int) (((argb >> 24) & 0xFF) * ringIntensity);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    // ── EntityKind ────────────────────────────────────────────────────────────
    public enum EntityKind {
        DROP, PLAYER, MOB
    }

    // ── EntityMode ────────────────────────────────────────────────────────────
    /**
     * RING – coloured halo ring drawn by GlowRenderer (custom renderer).
     * GLOW – vanilla spectral-arrow white outline via isCurrentlyGlowing() override.
     * OFF  – no effect.
     *
     * Note: GLOW is not supported for item drops; they use DropMode instead.
     */
    public enum EntityMode {
        RING, GLOW, OFF
    }

    // ── DropMode ───────────────────────────────────────────────────────────────
    /** Restricted mode for item drops: RING or OFF only (no vanilla glow outline). */
    public enum DropMode {
        RING, OFF
    }
}
