package vortex.targetentity;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

        // ---- General -------------------------------------------------------
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("target-entity.config.category.general"));

        general.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.glow_enabled"), cfg.glowEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.glowEnabled = v)
                .build());

        // Glow intensity slider: 0–100 → stored as 0.0–1.0
        general.addEntry(eb.startIntSlider(
                Component.translatable("target-entity.config.glow_intensity"),
                Math.round(cfg.glowIntensity * 100), 0, 100)
                .setDefaultValue(75)
                .setTextGetter(v -> Component.literal(v + "%"))
                .setSaveConsumer(v -> cfg.glowIntensity = v / 100f)
                .build());

        // Duration slider: 0 = infinite, 1–300 = seconds
        general.addEntry(eb.startIntSlider(
                Component.translatable("target-entity.config.glow_duration"),
                cfg.glowDurationSeconds, 0, 300)
                .setDefaultValue(0)
                .setTextGetter(v -> v == 0
                        ? Component.translatable("target-entity.config.glow_duration.infinite")
                        : Component.literal(v + "s"))
                .setSaveConsumer(v -> cfg.glowDurationSeconds = v)
                .build());

        // ---- Entity toggles ------------------------------------------------
        ConfigCategory entities = builder.getOrCreateCategory(
                Component.translatable("target-entity.config.category.entities"));

        entities.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.glow_drops"), cfg.glowDrops)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.glowDrops = v)
                .build());

        entities.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.glow_players"), cfg.glowPlayers)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.glowPlayers = v)
                .build());

        entities.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.glow_mobs"), cfg.glowMobs)
                .setDefaultValue(true)
                .setSaveConsumer(v -> cfg.glowMobs = v)
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
                .setTooltip(Component.translatable("target-entity.config.auto_color_players.tooltip"))
                .setSaveConsumer(v -> cfg.autoColorPlayers = v)
                .build());

        colours.addEntry(eb.startColorField(
                Component.translatable("target-entity.config.color_players"),
                cfg.colorPlayers & 0x00FFFFFF)
                .setDefaultValue(0x55FFFF)
                .setSaveConsumer(v -> cfg.colorPlayers = 0xFF000000 | v)
                .build());

        colours.addEntry(eb.startBooleanToggle(
                Component.translatable("target-entity.config.auto_color_mobs"), cfg.autoColorMobs)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("target-entity.config.auto_color_mobs.tooltip"))
                .setSaveConsumer(v -> cfg.autoColorMobs = v)
                .build());

        colours.addEntry(eb.startColorField(
                Component.translatable("target-entity.config.color_mobs"),
                cfg.colorMobs & 0x00FFFFFF)
                .setDefaultValue(0x55FF55)
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
