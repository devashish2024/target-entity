package vortex.targetentity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import vortex.targetentity.ModConfig.FilterMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A scrollable checkbox-style screen for populating the item / mob filter
 * lists.
 *
 * <p>
 * Tabs at the top switch between Items and Mobs. When {@link FilterMode#OFF}
 * is active the list is read-only (checkboxes are greyed out and unclickable).
 *
 * <p>
 * Open this screen from {@link TargetEntityModMenu} or a "Filter Lists" button.
 */
@Environment(EnvType.CLIENT)
public class FilterScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int HEADER_H = 62; // area above list
    private static final int FOOTER_H = 40; // area below list
    private static final int ENTRY_H = 18;
    private static final int CB_SIZE = 10; // checkbox square size
    private static final int CB_MARGIN = 5; // checkbox left margin
    private static final int TEXT_MARGIN = CB_MARGIN + CB_SIZE + 4;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    /** true = Items tab active, false = Mobs tab active */
    private boolean showItems = true;

    /** All registry keys for the current tab, after search filtering. */
    private final List<String> filteredList = new ArrayList<>();
    /** All items from the registry (pre-built once). */
    private final List<String> allItems;
    /** All entity-types from the registry (pre-built once). */
    private final List<String> allMobs;

    /** How many pixels the list is scrolled down. */
    private int scrollOffset = 0;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private EditBox searchField;
    private Button tabItemsBtn;
    private Button tabMobsBtn;
    private Button selectAllBtn;
    private Button selectNoneBtn;
    private Button doneBtn;

    public FilterScreen(Screen parent, boolean showItems) {
        super(Component.translatable("target-entity.filter.title"));
        this.parent = parent;
        this.showItems = showItems;

        // Build sorted lists once at construction time
        this.allItems = BuiltInRegistries.ITEM.keySet().stream()
                .map(Identifier::toString)
                .sorted()
                .toList();
        this.allMobs = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(Identifier::toString)
                .sorted()
                .toList();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int cx = width / 2;

        // Search field
        searchField = new EditBox(font, cx - 100, 38, 200, 16,
                Component.translatable("target-entity.filter.search"));
        searchField.setHint(Component.translatable("target-entity.filter.search"));
        searchField.setResponder(s -> rebuildList());
        addRenderableWidget(searchField);

        // Tabs
        tabItemsBtn = Button.builder(Component.translatable("target-entity.filter.tab.items"),
                btn -> {
                    showItems = true;
                    scrollOffset = 0;
                    rebuildList();
                })
                .bounds(cx - 102, 16, 100, 20).build();
        addRenderableWidget(tabItemsBtn);

        tabMobsBtn = Button.builder(Component.translatable("target-entity.filter.tab.mobs"),
                btn -> {
                    showItems = false;
                    scrollOffset = 0;
                    rebuildList();
                })
                .bounds(cx + 2, 16, 100, 20).build();
        addRenderableWidget(tabMobsBtn);

        // Footer
        int footerY = height - FOOTER_H + 10;
        selectAllBtn = Button.builder(Component.translatable("target-entity.filter.select_all"),
                btn -> setAllChecked(true))
                .bounds(cx - 155, footerY, 100, 20).build();
        addRenderableWidget(selectAllBtn);

        selectNoneBtn = Button.builder(Component.translatable("target-entity.filter.select_none"),
                btn -> setAllChecked(false))
                .bounds(cx - 50, footerY, 100, 20).build();
        addRenderableWidget(selectNoneBtn);

        doneBtn = Button.builder(Component.translatable("target-entity.filter.done"),
                btn -> close())
                .bounds(cx + 55, footerY, 100, 20).build();
        addRenderableWidget(doneBtn);

        rebuildList();
    }

    @Override
    public void onClose() {
        ModConfig.save();
        minecraft.setScreen(parent);
    }

    private void close() {
        onClose();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // List data
    // ────────────────────────────────────────────────────────────────────────────

    private void rebuildList() {
        filteredList.clear();
        String query = searchField == null ? "" : searchField.getValue().toLowerCase();
        List<String> source = showItems ? allItems : allMobs;
        for (String key : source) {
            if (query.isEmpty() || key.contains(query)) {
                filteredList.add(key);
            }
        }
        // clamp scroll
        int maxScroll = Math.max(0, filteredList.size() * ENTRY_H - listHeight());
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private Set<String> activeFilter() {
        ModConfig cfg = ModConfig.get();
        return showItems ? cfg.itemFilterList : cfg.mobFilterList;
    }

    private void setAllChecked(boolean checked) {
        if (ModConfig.get().filterMode == FilterMode.OFF)
            return;
        Set<String> filter = activeFilter();
        if (checked) {
            filter.addAll(filteredList);
        } else {
            filter.removeAll(filteredList);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Rendering
    // ────────────────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Title
        context.centeredText(font, title, width / 2, 4, 0xFFFFFFFF);

        // Render registered widgets (tabs, search field, buttons)
        super.extractRenderState(context, mouseX, mouseY, delta);

        // Dividers
        context.fill(0, HEADER_H - 1, width, HEADER_H, 0xFF555555);
        context.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0xFF555555);

        // List
        renderList(context, mouseX, mouseY);

        // Tab indicator — 2px highlight under the active tab button
        int cx = width / 2;
        context.fill(cx - 102, 36, cx - 2, 38,
                showItems ? 0xFFFFFFFF : 0xFF888888);
        context.fill(cx + 2, 36, cx + 102, 38,
                showItems ? 0xFF888888 : 0xFFFFFFFF);

        // "OFF mode" banner
        if (ModConfig.get().filterMode == FilterMode.OFF) {
            String msg = "§7Filter mode is OFF — enable it in Settings to allow toggling";
            context.centeredText(font, Component.literal(msg),
                    width / 2, HEADER_H + 6, 0xFFAAAAAA);
        }
    }

    private void renderList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int listTop = HEADER_H;
        int listBottom = height - FOOTER_H;
        int listH = listBottom - listTop;
        boolean disabled = ModConfig.get().filterMode == FilterMode.OFF;
        Set<String> filter = activeFilter();

        // Scissor / clip: only render entries in the list area
        // We just check y bounds manually for text to avoid Scissor complexity
        int firstIdx = scrollOffset / ENTRY_H;
        int lastIdx = Math.min(filteredList.size() - 1,
                firstIdx + (listH / ENTRY_H) + 1);

        for (int i = firstIdx; i <= lastIdx; i++) {
            String key = filteredList.get(i);
            int entryY = listTop + i * ENTRY_H - scrollOffset;
            if (entryY + ENTRY_H <= listTop || entryY >= listBottom)
                continue;

            boolean checked = filter.contains(key);
            boolean hovering = mouseX >= CB_MARGIN && mouseX < width - CB_MARGIN
                    && mouseY >= entryY && mouseY < entryY + ENTRY_H && !disabled;

            // Row highlight
            if (hovering) {
                context.fill(0, entryY, width, entryY + ENTRY_H, 0x22FFFFFF);
            }

            // Checkbox box
            int cbX = CB_MARGIN;
            int cbY = entryY + (ENTRY_H - CB_SIZE) / 2;
            int cbColor = disabled ? 0xFF555555 : 0xFF888888;
            int checkColor = disabled ? 0xFF666666 : 0xFFFFFFFF;
            context.fill(cbX, cbY, cbX + CB_SIZE, cbY + CB_SIZE, cbColor);
            if (checked) {
                context.fill(cbX + 2, cbY + 2, cbX + CB_SIZE - 2, cbY + CB_SIZE - 2, checkColor);
            }

            // Entry key text
            int textColor = disabled ? 0xFF888888 : (checked ? 0xFFFFFFFF : 0xFFCCCCCC);
            context.text(font, key, TEXT_MARGIN, entryY + (ENTRY_H - 8) / 2, textColor, false);
        }

        // Scroll bar (if content overflows)
        int totalH = filteredList.size() * ENTRY_H;
        if (totalH > listH) {
            int barH = Math.max(20, listH * listH / totalH);
            int barY = listTop + scrollOffset * (listH - barH) / Math.max(1, totalH - listH);
            int barX = width - 4;
            context.fill(barX, listTop, barX + 3, listBottom, 0xFF333333);
            context.fill(barX, barY, barX + 3, barY + barH, 0xFFAAAAAA);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Input
    // ────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        // Let widgets handle first (buttons, search field)
        boolean handled = super.mouseClicked(click, doubled);
        if (handled)
            return true;

        // Check list click
        if (ModConfig.get().filterMode == FilterMode.OFF)
            return false;
        int listTop = HEADER_H;
        int listBottom = height - FOOTER_H;
        if (mouseY < listTop || mouseY >= listBottom)
            return false;

        int idx = (mouseY - listTop + scrollOffset) / ENTRY_H;
        if (idx < 0 || idx >= filteredList.size())
            return false;

        String key = filteredList.get(idx);
        Set<String> filter = activeFilter();
        if (filter.contains(key)) {
            filter.remove(key);
        } else {
            filter.add(key);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listTop = HEADER_H;
        int listBottom = height - FOOTER_H;
        if (mouseY >= listTop && mouseY < listBottom) {
            int dir = (int) Math.signum(verticalAmount);
            if (dir != 0) {
                int maxScroll = Math.max(0, filteredList.size() * ENTRY_H - listHeight());
                scrollOffset = Math.max(0, Math.min(maxScroll,
                        scrollOffset - dir * ENTRY_H * 3));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(event);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────────

    private int listHeight() {
        return height - HEADER_H - FOOTER_H;
    }
}
