package vortex.targetentity;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Provides the Target Entity config screen to Mod Menu.
 * Opens the Cloth Config settings screen directly — no hub.
 */
@Environment(EnvType.CLIENT)
public class TargetEntityModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreenBuilder::build;
    }
}

