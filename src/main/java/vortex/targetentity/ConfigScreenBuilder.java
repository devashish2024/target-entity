package vortex.targetentity;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import vortex.targetentity.ModConfig.DropMode;
import vortex.targetentity.ModConfig.EntityMode;
import vortex.targetentity.ModConfig.FilterMode;

/**
 * Builds the Cloth Config settings screen for Target Entity.
 */
@Environment(EnvType.CLIENT)
public final class ConfigScreenBuilder {

    public static Screen build(Screen parent) {
        ModConfig cfg = ModConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("target-entity.config.title"))
                .setSavingRunnable(ModConfig::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ---- Ring ----------------------------------------------------------
        ConfigCategory ring = builder.getOrCreateCategory(
                Component.translatable("target-entity.config.category.ring"));

        ring.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.ring_enabled"), cfg.ringEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.ringEnabled = v)
                .build());

        // Ring intensity slider: 0–100 → stored as 0.0–1.0
        ring.addEntry(eb.startIntSlider(
                Component.translatable("target-entity.config.ring_intensity"),
                Math.round(cfg.ringIntensity * 100), 0, 100)
                .setDefaultValue(75)
                .setTextGetter(v -> Component.literal(v + "%"))
                .setSaveConsumer(v -> cfg.ringIntensity = v / 100f)
                .build());

        // Ring duration slider: 0 = infinite, 1–300 = seconds
        ring.addEntry(eb.startIntSlider(
                Component.translatable("target-entity.config.ring_duration"),
                cfg.ringDurationSeconds, 0, 300)
                .setDefaultValue(0)
                .setTextGetter(v -> v == 0
                        ? Component.translatable("target-entity.config.duration.infinite")
                        : Component.literal(v + "s"))
                .setSaveConsumer(v -> cfg.ringDurationSeconds = v)
                .build());

        // ---- Glow Effect ---------------------------------------------------
        ConfigCategory glowEffect = builder.getOrCreateCategory(
                Component.translatable("target-entity.config.category.glow_effect"));

        glowEffect.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.glow_effect_enabled"), cfg.glowEffectEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.glowEffectEnabled = v)
                .build());

        // Glow effect duration slider: 0 = infinite, 1–300 = seconds
        glowEffect.addEntry(eb.startIntSlider(
                Component.translatable("target-entity.config.glow_duration"),
                cfg.glowDurationSeconds, 0, 300)
                .setDefaultValue(0)
                .setTextGetter(v -> v == 0
                        ? Component.translatable("target-entity.config.duration.infinite")
                        : Component.literal(v + "s"))
                .setSaveConsumer(v -> cfg.glowDurationSeconds = v)
                .build());

        // ---- Entity modes ------------------------------------------------
        // Drops: RING or OFF only (no vanilla glow outline for item entities).
        // Players / Mobs: RING, GLOW (vanilla outline), or OFF.
        // "Always Glow" bypasses hit-tracking; ignored when the mode is OFF.
        ConfigCategory entities = builder.getOrCreateCategory(
                Component.translatable("target-entity.config.category.entities"));

        entities.addEntry(eb.startEnumSelector(
                Component.translatable("target-entity.config.drop_mode"),
                DropMode.class, cfg.dropMode)
                .setDefaultValue(DropMode.RING)
                .setEnumNameProvider(m -> switch ((DropMode) m) {
                    case RING -> Component.translatable("target-entity.config.mode.ring")
                            .withStyle(ChatFormatting.GREEN);
                    case OFF  -> Component.translatable("target-entity.config.mode.off")
                            .withStyle(ChatFormatting.GRAY);
                })
                .setSaveConsumer(v -> cfg.dropMode = v)
                .build());

        entities.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.always_glow_players"), cfg.alwaysGlowPlayers)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("target-entity.config.always_glow_players.tooltip"))
                .setSaveConsumer(v -> cfg.alwaysGlowPlayers = v)
                .build());

        entities.addEntry(eb.startEnumSelector(
                Component.translatable("target-entity.config.player_mode"),
                EntityMode.class, cfg.playerMode)
                .setDefaultValue(EntityMode.RING)
                .setEnumNameProvider(m -> switch ((EntityMode) m) {
                    case RING -> Component.translatable("target-entity.config.mode.ring")
                            .withStyle(ChatFormatting.GREEN);
                    case GLOW -> Component.translatable("target-entity.config.mode.glow")
                            .withStyle(ChatFormatting.AQUA);
                    case OFF  -> Component.translatable("target-entity.config.mode.off")
                            .withStyle(ChatFormatting.GRAY);
                })
                .setSaveConsumer(v -> cfg.playerMode = v)
                .build());

        entities.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.always_glow_mobs"), cfg.alwaysGlowMobs)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("target-entity.config.always_glow_mobs.tooltip"))
                .setSaveConsumer(v -> cfg.alwaysGlowMobs = v)
                .build());

        entities.addEntry(eb.startEnumSelector(
                Component.translatable("target-entity.config.mob_mode"),
                EntityMode.class, cfg.mobMode)
                .setDefaultValue(EntityMode.RING)
                .setEnumNameProvider(m -> switch ((EntityMode) m) {
                    case RING -> Component.translatable("target-entity.config.mode.ring")
                            .withStyle(ChatFormatting.GREEN);
                    case GLOW -> Component.translatable("target-entity.config.mode.glow")
                            .withStyle(ChatFormatting.AQUA);
                    case OFF  -> Component.translatable("target-entity.config.mode.off")
                            .withStyle(ChatFormatting.GRAY);
                })
                .setSaveConsumer(v -> cfg.mobMode = v)
                .build());

        // ---- Colours -------------------------------------------------------
        ConfigCategory colours = builder.getOrCreateCategory(
                Component.translatable("target-entity.config.category.colors"));

        // Auto-color toggle for drops (derives ring color from item name/rarity)
        colours.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.auto_color_drops"), cfg.autoColorDrops)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("target-entity.config.auto_color_drops.tooltip"))
                .setSaveConsumer(v -> cfg.autoColorDrops = v)
                .build());

        colours.addEntry(eb.startColorField(
                Component.translatable("target-entity.config.color_drops"),
                cfg.colorDrops & 0x00FFFFFF) // cloth-config colour is RGB, no alpha
                .setDefaultValue(0xFFFFFF)
                .setSaveConsumer(v -> cfg.colorDrops = 0xFF000000 | v)
                .build());

        colours.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.auto_color_players"), cfg.autoColorPlayers)
                .setDefaultValue(true)
                .setTooltip(
                        Component.translatable("target-entity.config.auto_color_players.tooltip"),
                        Component.translatable("target-entity.config.color_players.glow_note"))
                .setSaveConsumer(v -> cfg.autoColorPlayers = v)
                .build());

        colours.addEntry(eb.startColorField(
                Component.translatable("target-entity.config.color_players"),
                cfg.colorPlayers & 0x00FFFFFF)
                .setDefaultValue(0x55FFFF)
                .setTooltip(Component.translatable("target-entity.config.color_players.glow_note"))
                .setSaveConsumer(v -> cfg.colorPlayers = 0xFF000000 | v)
                .build());

        colours.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.auto_color_mobs"), cfg.autoColorMobs)
                .setDefaultValue(true)
                .setTooltip(
                        Component.translatable("target-entity.config.auto_color_mobs.tooltip"),
                        Component.translatable("target-entity.config.color_mobs.glow_note"))
                .setSaveConsumer(v -> cfg.autoColorMobs = v)
                .build());

        colours.addEntry(eb.startColorField(
                Component.translatable("target-entity.config.color_mobs"),
                cfg.colorMobs & 0x00FFFFFF)
                .setDefaultValue(0x55FF55)
                .setTooltip(Component.translatable("target-entity.config.color_mobs.glow_note"))
                .setSaveConsumer(v -> cfg.colorMobs = 0xFF000000 | v)
                .build());

        // ---- Filter --------------------------------------------------------
        ConfigCategory filter = builder.getOrCreateCategory(
                Component.translatable("target-entity.config.category.filter"));

        filter.addEntry(eb.startEnumSelector(
                Component.translatable("target-entity.config.filter_mode"),
                FilterMode.class,
                cfg.filterMode)
                .setDefaultValue(FilterMode.OFF)
                .setEnumNameProvider(m -> Component.translatable(
                        "target-entity.config.filter_mode." + m.name().toLowerCase()))
                .setSaveConsumer(v -> cfg.filterMode = v)
                .build());

        // Buttons to open item / mob filter screens inline within the settings.
        // parentCapture stores the current ClothConfig screen once it's initialized,
        // so FilterScreen can navigate back to it on close.
        final Screen[] parentCapture = {parent};
        builder.setAfterInitConsumer(s -> parentCapture[0] = s);

        filter.addEntry(new ButtonConfigEntry(
                Component.translatable("target-entity.config.filter_items"),
                () -> {
                    ModConfig.save();
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new FilterScreen(parentCapture[0], true));
                }));

        filter.addEntry(new ButtonConfigEntry(
                Component.translatable("target-entity.config.filter_mobs"),
                () -> {
                    ModConfig.save();
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new FilterScreen(parentCapture[0], false));
                }));

        return builder.build();
    }

    private ConfigScreenBuilder() {
    }
}
