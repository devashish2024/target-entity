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

    /** Slider max/sentinel for "Infinite" effect duration. */
    public static final int DURATION_INFINITE = 301;

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
     * RING = custom coloured halo ring (current behaviour).
     * GLOW = vanilla spectral-arrow white outline, client-side only. Not
     * available for item drops (no visual difference).
     * OFF = disabled.
     */
    public EntityMode playerMode = EntityMode.RING;
    public EntityMode mobMode = EntityMode.RING;

    /**
     * Drops can only be RING or OFF — vanilla glow outline has no effect on
     * item entities, so GLOW is intentionally excluded.
     */
    public DropMode dropMode = DropMode.RING;

    /**
     * When true, ALL applicable mobs/players (passing filter) always show the
     * configured highlight effect regardless of hit-tracking.
     * Has no effect when the respective mode is OFF.
     */
    public boolean alwaysGlowPlayers = false;
    public boolean alwaysGlowMobs = false;

    /**
     * 0 = disabled (no hit-based activation). 1–300 = seconds. 301 = infinite.
     * NOTE: ring duration NEVER applies to item drops — they always show.
     */
    public int ringDurationSeconds = 0;

    /**
     * 0 = disabled. 1–300 = seconds. 301 = infinite for vanilla glow on living
     * entities.
     */
    public int glowDurationSeconds = 0;

    /**
     * 0.0–1.0 multiplied into the alpha of every ring. Does not affect glow
     * outline.
     */
    public float ringIntensity = 0.75f;

    /**
     * When true, only one living target (mob/player) can have ring mode at once.
     * Drops are never affected by this option.
     */
    public boolean singleTargetRing = false;

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
            case DROP -> dropMode == DropMode.RING ? EntityMode.RING : EntityMode.OFF;
            case PLAYER -> playerMode;
            case MOB -> mobMode;
        };
    }

    /**
     * Whether this entity kind should always show its effect (ignores
     * hit-tracking).
     * True when the "always glow" toggle is on AND mode is not OFF.
     */
    public boolean alwaysGlows(EntityKind kind) {
        return switch (kind) {
            // Drops bypass hit-tracking whenever their mode is RING
            case DROP -> dropMode == DropMode.RING;
            case PLAYER -> alwaysGlowPlayers && playerMode != EntityMode.OFF;
            case MOB -> alwaysGlowMobs && mobMode != EntityMode.OFF;
        };
    }

    /**
     * Returns the effective duration (seconds) for {@code kind}'s current mode.
     * Ring entities use {@link #ringDurationSeconds}; glow entities use
     * {@link #glowDurationSeconds}. 0 means infinite.
     */
    public int durationFor(EntityKind kind) {
        int duration = (modeFor(kind) == EntityMode.GLOW) ? glowDurationSeconds : ringDurationSeconds;
        if (duration < 0)
            return 0;
        if (duration > DURATION_INFINITE)
            return DURATION_INFINITE;
        return duration;
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
            case WHITELIST -> list.contains(registryKey);
            case OFF -> true;
        };
    }

    /**
     * Resolves the final ARGB ring colour for a mob or player entity.
     * Generic fallback — prefer {@link #resolvePlayerColor} /
     * {@link #resolveMobColor}.
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
     * Priority: custom override → nametag colour → built-in species colour →
     * fallback.
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
            // Built-in per-species colour — gives each mob type a distinctive hue
            Integer builtin = BUILTIN_MOB_COLORS.get(registryKey);
            if (builtin != null) {
                return applyIntensity(builtin);
            }
        }
        return applyIntensity(colorMobs);
    }

    // ── Built-in per-species ring colours ────────────────────────────────────
    /**
     * Characteristic colours for vanilla mob types.
     * Used by {@link #resolveMobColor} when {@link #autoColorMobs} is true
     * and the entity has no coloured custom nametag.
     * Colours reflect each mob's dominant visual appearance.
     */
    private static final Map<String, Integer> BUILTIN_MOB_COLORS;
    static {
        Map<String, Integer> m = new HashMap<>();

        // ── Undead / Hostile ─────────────────────────────────────────────────
        m.put("minecraft:zombie", 0xFF4A7A1E); // sickly green flesh
        m.put("minecraft:zombie_villager", 0xFF5A8020); // zombie + villager tones
        m.put("minecraft:husk", 0xFFD4A464); // sandy / desert tan
        m.put("minecraft:drowned", 0xFF007777); // dark underwater cyan
        m.put("minecraft:skeleton", 0xFFCAC2AA); // pale bone white
        m.put("minecraft:stray", 0xFF88CCFF); // icy light blue
        m.put("minecraft:wither_skeleton", 0xFF222222); // near-black charred bone
        m.put("minecraft:bogged", 0xFF6B8040); // mossy brown-green
        m.put("minecraft:creeper", 0xFF44BB00); // vivid green
        m.put("minecraft:spider", 0xFF7A1C00); // dark reddish-brown
        m.put("minecraft:cave_spider", 0xFF1A144A); // dark indigo
        m.put("minecraft:enderman", 0xFF7700BB); // void purple
        m.put("minecraft:endermite", 0xFF551188); // muted purple
        m.put("minecraft:witch", 0xFF660080); // witch purple
        m.put("minecraft:vindicator", 0xFF556655); // dark grayish-green coat
        m.put("minecraft:pillager", 0xFF555555); // dark grey crossbow-wielder
        m.put("minecraft:evoker", 0xFF332233); // near-black dark robe
        m.put("minecraft:illusioner", 0xFF334433); // dark robe
        m.put("minecraft:ravager", 0xFF5C3020); // dark iron-brown
        m.put("minecraft:vex", 0xFF8090B8); // ghostly steel blue
        m.put("minecraft:blaze", 0xFFFF8800); // fiery orange
        m.put("minecraft:magma_cube", 0xFFDD4400); // red-orange lava
        m.put("minecraft:ghast", 0xFFDDDDDD); // pale white
        m.put("minecraft:zombie_piglin", 0xFFE8963C); // gold + sickly pink
        m.put("minecraft:piglin", 0xFFE87878); // pink flesh
        m.put("minecraft:piglin_brute", 0xFFB87820); // gold-armoured
        m.put("minecraft:hoglin", 0xFFB05040); // reddish-brown hide
        m.put("minecraft:zoglin", 0xFF9A8080); // pale undead hoglin
        m.put("minecraft:strider", 0xFFCC2200); // crimson-red
        m.put("minecraft:warden", 0xFF006688); // deep sculk teal
        m.put("minecraft:guardian", 0xFF008888); // ocean teal
        m.put("minecraft:elder_guardian", 0xFF88BBAA); // pale teal-grey
        m.put("minecraft:shulker", 0xFF9932CC); // purple
        m.put("minecraft:phantom", 0xFF1A2860); // dark indigo sky
        m.put("minecraft:silverfish", 0xFF909090); // stone grey
        m.put("minecraft:slime", 0xFF44BF44); // bright lime green
        m.put("minecraft:bat", 0xFF3D1800); // very dark brown
        m.put("minecraft:creaking", 0xFF6A3420); // dark bark red-brown

        // ── Neutral ───────────────────────────────────────────────────────────
        m.put("minecraft:bee", 0xFFFFAA00); // amber yellow
        m.put("minecraft:wolf", 0xFF888888); // grey
        m.put("minecraft:polar_bear", 0xFFDDDDEE); // snow white
        m.put("minecraft:panda", 0xFFDDDDDD); // white (black markings)
        m.put("minecraft:goat", 0xFFBBBBBB); // light grey
        m.put("minecraft:llama", 0xFFCCB880); // tan / straw-coloured
        m.put("minecraft:trader_llama", 0xFF4466CC); // blue trader blanket
        m.put("minecraft:iron_golem", 0xFF999999); // iron grey
        m.put("minecraft:snow_golem", 0xFFEEEEEE); // snow white

        // ── Passive ───────────────────────────────────────────────────────────
        m.put("minecraft:villager", 0xFFC4966A); // warm beige
        m.put("minecraft:wandering_trader", 0xFF2244AA); // blue robe
        m.put("minecraft:cat", 0xFFFF8800); // orange tabby
        m.put("minecraft:ocelot", 0xFFDDBB44); // golden spotted
        m.put("minecraft:fox", 0xFFFF6600); // vivid orange
        m.put("minecraft:axolotl", 0xFFFF96A0); // pink
        m.put("minecraft:frog", 0xFFDDA020); // warm orange-tan
        m.put("minecraft:tadpole", 0xFF8B6040); // muddy brown
        m.put("minecraft:allay", 0xFF44CCFF); // sky blue
        m.put("minecraft:sniffer", 0xFFAA3322); // brick red
        m.put("minecraft:camel", 0xFFD4A830); // sandy yellow
        m.put("minecraft:armadillo", 0xFFA06030); // earthy orange-brown
        m.put("minecraft:breeze", 0xFF80DDFF); // light breezy cyan
        m.put("minecraft:sheep", 0xFFDDDDDD); // white wool
        m.put("minecraft:cow", 0xFF8B4513); // saddle brown
        m.put("minecraft:mooshroom", 0xFFCC2222); // red mushroom
        m.put("minecraft:pig", 0xFFFF99BB); // pink
        m.put("minecraft:chicken", 0xFFFFDD44); // yellow
        m.put("minecraft:rabbit", 0xFFCC9966); // tan
        m.put("minecraft:horse", 0xFF8B5C2A); // bay brown
        m.put("minecraft:donkey", 0xFF8B7040); // dull tan-brown
        m.put("minecraft:mule", 0xFF5C3010); // dark brown
        m.put("minecraft:parrot", 0xFFCC3311); // red (most iconic)
        m.put("minecraft:dolphin", 0xFF5080A8); // blue-grey
        m.put("minecraft:turtle", 0xFF44AA44); // olive green
        m.put("minecraft:squid", 0xFF224488); // deep blue
        m.put("minecraft:glow_squid", 0xFF00BBAA); // glowing teal
        m.put("minecraft:cod", 0xFFAA7040); // brown-orange
        m.put("minecraft:salmon", 0xFFDD6644); // reddish-orange
        m.put("minecraft:tropical_fish", 0xFFFF8844); // vivid orange
        m.put("minecraft:pufferfish", 0xFFBBCC00); // yellow-green

        BUILTIN_MOB_COLORS = Map.copyOf(m);
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
     * GLOW – vanilla spectral-arrow white outline via isCurrentlyGlowing()
     * override.
     * OFF – no effect.
     *
     * Note: GLOW is not supported for item drops; they use DropMode instead.
     */
    public enum EntityMode {
        RING, GLOW, OFF
    }

    // ── DropMode ───────────────────────────────────────────────────────────────
    /**
     * Restricted mode for item drops: RING or OFF only (no vanilla glow outline).
     */
    public enum DropMode {
        RING, OFF
    }
}
