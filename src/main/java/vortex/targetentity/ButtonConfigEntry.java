package vortex.targetentity;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * A Cloth Config list entry that renders a single centred MC {@link Button}.
 * Use this in filter categories to open custom filter screens from within the
 * Cloth Config settings screen.
 */
@Environment(EnvType.CLIENT)
public final class ButtonConfigEntry extends AbstractConfigListEntry<Void> {

    private final Button button;

    public ButtonConfigEntry(Component label, Runnable action) {
        super(label, false);
        this.button = Button.builder(label, btn -> action.run())
                .size(200, 20)
                .build();
    }

    // ── AbstractConfigEntry / ValueHolder ────────────────────────────────────

    @Override public Void getValue()                  { return null; }
    @Override public Optional<Void> getDefaultValue() { return Optional.empty(); }
    @Override public boolean isEdited()               { return false; }

    // ── DynamicEntryListWidget$Entry ─────────────────────────────────────────

    @Override
    public int getItemHeight() { return 28; }

    /**
     * Parameters follow the Cloth Config / MC AbstractSelectionList convention:
     * (context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta)
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor context,
                                   int index, int y, int x,
                                   int entryWidth, int entryHeight,
                                   int mouseX, int mouseY,
                                   boolean isHovered, float delta) {
        int btnW = Math.min(200, entryWidth - 24);
        button.setWidth(btnW);
        button.setX(x + (entryWidth - btnW) / 2);
        button.setY(y + 4);
        button.extractRenderState(context, mouseX, mouseY, delta);
    }

    // ── ContainerEventHandler (ElementEntry) ─────────────────────────────────

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }
}
