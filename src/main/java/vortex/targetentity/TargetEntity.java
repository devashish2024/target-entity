package vortex.targetentity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class TargetEntity implements ClientModInitializer {
	public static final String MOD_ID = "target-entity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ModConfig.load();
		LOGGER.info("[Target Entity] Loaded.");
	}
}
