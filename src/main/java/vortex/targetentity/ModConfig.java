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
    public boolean glowEnabled = true;
    public boolean glowDrops = true;
    public boolean glowPlayers = true;
    public boolean glowMobs = true;

    /**
     * 0 = infinite. 1–300 = seconds.
     * NOTE: glow duration NEVER applies to item drops — they always glow.
     */
    public int glowDurationSeconds = 0;

    /** 0.0–1.0 multiplied into the alpha of every ring. */
    public float glowIntensity = 0.75f;

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

    /**
     * Whether this entity should be rendered with a glow ring.
     *
     * @param kind        entity class
     * @param registryKey entity-type key (mobs/players) OR item registry key
     *                    (drops)
     */
    public boolean shouldGlow(EntityKind kind, String registryKey) {
        if (!glowEnabled)
            return false;

        boolean subToggle = switch (kind) {
            case DROP -> glowDrops;
            case PLAYER -> glowPlayers;
            case MOB -> glowMobs;
        };
        if (!subToggle)
            return false;

        if (filterMode == FilterMode.OFF)
            return true;

        // For drops we filter on the contained item's registry key.
        // For mobs/players we filter on the entity-type key.
        Set<String> list = (kind == EntityKind.DROP) ? itemFilterList : mobFilterList;
        return switch (filterMode) {
            case BLACKLIST -> !list.contains(registryKey);
            case WHITELIST -> list.contains(registryKey);
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
        int a = (int) (((argb >> 24) & 0xFF) * glowIntensity);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    // ── EntityKind ────────────────────────────────────────────────────────────
    public enum EntityKind {
        DROP, PLAYER, MOB
    }
}
