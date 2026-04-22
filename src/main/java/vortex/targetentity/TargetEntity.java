package vortex.targetentity;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class TargetEntity implements ClientModInitializer {
	public static final String MOD_ID = "target-entity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static KeyMapping OPEN_SETTINGS_KEY;

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "target_entity"));

	@Override
	public void onInitializeClient() {
		ModConfig.load();

		OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.target-entity.open_settings",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F8,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SETTINGS_KEY.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(ConfigScreenBuilder.build(null));
				}
			}
		});

		LOGGER.info("[Target Entity] Loaded.");
	}
}
