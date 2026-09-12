package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import top.csituka.youzaiworldcore.client.inventory.InventoryItemGroupRule;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** 单个分组的草稿编辑器，支持手填标签 ID 或从当前原版/模组分类中选择。 */
@SuppressWarnings("null")
public final class InventoryItemGroupEditScreen extends Screen {

    private static final String KEY = "screen.youzaiworldcore.item_groups.";
    private static final List<String> FIELDS = List.of("group_name", "tab_id", "equivalent_items",
            "contained_items", "non_contained_items");
    private final Screen parent;
    private final Consumer<InventoryItemGroupRule> onSave;
    private String groupName;
    private String tabId;
    private String exact;
    private String included;
    private String excluded;
    private String invalidField;
    private int left;
    private int panelWidth;
    private DropdownButton tabPicker;
    private TransparentButton saveButton;

    /** null 规则表示新增；保存只更新上级页面草稿，不在编辑器内落盘。 */
    public InventoryItemGroupEditScreen(Screen parent, InventoryItemGroupRule rule,
                                        Consumer<InventoryItemGroupRule> onSave) {
        super(Component.translatable(KEY + (rule == null ? "add" : "edit")));
        this.parent = parent;
        this.onSave = onSave;
        groupName = rule == null ? "" : rule.groupName();
        tabId = rule == null ? "minecraft:building_blocks" : rule.tabId();
        exact = rule == null ? "" : String.join(", ", rule.equivalentItems());
        included = rule == null ? "" : String.join(", ", rule.containedItems());
        excluded = rule == null ? "" : String.join(", ", rule.nonContainedItems());
    }

    @Override
    protected void init() {
        panelWidth = Math.min(640, width - 32);
        left = (width - panelWidth) / 2;
        field(0, panelWidth, groupName, value -> groupName = value);
        EditBox tabField = field(1, panelWidth - 34, tabId, value -> tabId = value);
        field(2, panelWidth, exact, value -> exact = value);
        field(3, panelWidth, included, value -> included = value);
        field(4, panelWidth, excluded, value -> excluded = value);

        List<CreativeModeTab> tabs = CreativeModeTabs.allTabs().stream()
                .filter(tab -> tab.getType() == CreativeModeTab.Type.CATEGORY).toList();
        List<String> tabIds = tabs.stream().map(tab -> BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString()).toList();
        List<String> labels = tabs.stream().map(tab -> tab.getDisplayName().getString() + "  ["
                + BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab) + "]").toList();
        tabPicker = new DropdownButton(left + panelWidth - 28, 80, 28, panelWidth, 18, Component.empty(),
                labels, -1, false, index -> tabField.setValue(tabIds.get(index)), null);
        tabPicker.setTooltip(Tooltip.create(text("choose_tab")));
        tabPicker.setCanvasSize(width, height);
        addRenderableWidget(tabPicker);

        int buttonWidth = (panelWidth - 6) / 2;
        saveButton = new TransparentButton(left, height - 28, buttonWidth, 20, text("apply_rule"), this::saveRule);
        addRenderableWidget(saveButton);
        addRenderableWidget(new TransparentButton(left + buttonWidth + 6, height - 28, buttonWidth, 20,
                Component.translatable("gui.cancel"), this::onClose));
    }

    private EditBox field(int row, int fieldWidth, String value, Consumer<String> setter) {
        String key = FIELDS.get(row);
        EditBox edit = new EditBox(font, left, 49 + row * 31, fieldWidth, 18, text(key));
        edit.setMaxLength(row < 2 ? Math.max(256, value.length()) : Math.max(16384, value.length()));
        edit.setValue(value);
        edit.setTooltip(Tooltip.create(text(key + "_hint")));
        edit.setResponder(changed -> {
            setter.accept(changed);
            invalidField = null;
            if (saveButton != null) {
                saveButton.setTooltip(null);
            }
        });
        return addRenderableWidget(edit);
    }

    private void saveRule() {
        InventoryItemGroupRule rule = new InventoryItemGroupRule(groupName.trim(), tabId.trim(),
                split(exact), split(included), split(excluded));
        invalidField = rule.invalidField();
        if (invalidField != null) {
            saveButton.setTooltip(Tooltip.create(text("invalid." + invalidField)));
            DebugLogger.debug("InventoryItemGroupEditScreen", "分组草稿字段校验失败：%s", invalidField);
            return;
        }
        onSave.accept(rule);
        DebugLogger.debug("InventoryItemGroupEditScreen", "已更新分组草稿：%s", rule.groupName());
        onClose();
    }

    private static List<String> split(String value) {
        return Arrays.stream(value.split("[,，]")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        YzuiTheme.label(graphics, font, title, left, 14, panelWidth, YzuiTheme.text(), false);
        for (int i = 0; i < FIELDS.size(); i++) {
            YzuiTheme.label(graphics, font, text(FIELDS.get(i)), left, 38 + i * 31,
                    panelWidth, YzuiTheme.textMuted(), false);
        }
        YzuiTheme.label(graphics, font, text(invalidField == null ? "items_hint" : "invalid." + invalidField),
                left, height - 45, panelWidth, invalidField == null ? YzuiTheme.textMuted() : YzuiTheme.error(), false);
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        tabPicker.renderPopup(graphics, mouseX, mouseY, tick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tabPicker.isOpen()) {
            if (tabPicker.mouseClicked(event, doubleClick)) {
                setFocused(tabPicker);
                return true;
            }
            tabPicker.closePopup();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return tabPicker.mouseScrolled(x, y, horizontal, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return tabPicker.isOpen() && tabPicker.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        // 背景由统一屏幕入口绘制。
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component text(String suffix) {
        return Component.translatable(KEY + suffix);
    }
}
