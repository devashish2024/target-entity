package vortex.targetentity;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Provides the Target Entity config + filter screens to Mod Menu.
 *
 * <p>
 * Opens a small hub screen with two buttons:
 * <ul>
 * <li>Settings → Cloth Config screen</li>
 * <li>Filter Lists → {@link FilterScreen}</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class TargetEntityModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new HubScreen(parent);
    }

    /** Two-button hub that routes to either the settings or filter screen. */
    @Environment(EnvType.CLIENT)
    static final class HubScreen extends Screen {

        private final Screen parent;

        HubScreen(Screen parent) {
            super(Component.translatable("target-entity.hub.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int cy = height / 2;

            addRenderableWidget(Button.builder(
                    Component.translatable("target-entity.hub.settings"),
                    btn -> minecraft.setScreen(ConfigScreenBuilder.build(this)))
                    .bounds(cx - 102, cy - 12, 100, 20).build());

            addRenderableWidget(Button.builder(
                    Component.translatable("target-entity.hub.filters"),
                    btn -> minecraft.setScreen(new FilterScreen(this)))
                    .bounds(cx + 2, cy - 12, 100, 20).build());

            addRenderableWidget(Button.builder(
                    Component.translatable("gui.back"),
                    btn -> onClose())
                    .bounds(cx - 50, cy + 16, 100, 20).build());
        }

        @Override
        public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context,
                int mouseX, int mouseY, float delta) {
            context.centeredText(font, title, width / 2, height / 2 - 35, 0xFFFFFFFF);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }
}
