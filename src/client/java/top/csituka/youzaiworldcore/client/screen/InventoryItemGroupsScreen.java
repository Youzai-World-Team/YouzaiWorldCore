package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.InventoryItemGroupsConfig;
import top.csituka.youzaiworldcore.client.inventory.InventoryItemGroupDefaults;
import top.csituka.youzaiworldcore.client.inventory.InventoryItemGroupRule;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 创造物品分组设置：开关、排序、轮播及分组规则的增删、编辑和优先级调整。
 * <p>页面只修改草稿，点击保存后一次性写入 {@code yzwc/client/global_settings.json}；取消和 Esc 丢弃草稿。</p>
 */
@SuppressWarnings("null")
public final class InventoryItemGroupsScreen extends Screen {

    private static final String KEY = "screen.youzaiworldcore.item_groups.";
    private static final int ROW_HEIGHT = 30;

    private final Screen parent;
    private final List<InventoryItemGroupRule> groups = new ArrayList<>(InventoryItemGroupsConfig.getGroups());
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private boolean enabled = InventoryItemGroupsConfig.isEnabled();
    private boolean preview = InventoryItemGroupsConfig.showItemsInGroup();
    private InventoryItemGroupsConfig.Sort sort = InventoryItemGroupsConfig.getSort();
    private String filter = "";
    private int page;
    private int pageCount = 1;
    private int pageSize = 1;
    private int matches;
    private int left;
    private int panelWidth;
    private int listTop;

    /** 创建独立草稿，返回时保留上级设置页面。 */
    public InventoryItemGroupsScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(700, width - 32);
        left = (width - panelWidth) / 2;
        boolean compact = panelWidth < 520;
        int controlWidth = (panelWidth - (compact ? 6 : 12)) / (compact ? 2 : 3);
        CheckboxButton enableButton = new CheckboxButton(left, 50, controlWidth, 20,
                text("enabled"), enabled, () -> enabled = !enabled);
        enableButton.setTooltip(Tooltip.create(text("enabled_hint")));
        addRenderableWidget(enableButton);
        CheckboxButton previewButton = new CheckboxButton(left + controlWidth + 6, 50, controlWidth, 20,
                text("preview"), preview, () -> preview = !preview);
        previewButton.setTooltip(Tooltip.create(text("preview_hint")));
        addRenderableWidget(previewButton);
        TransparentButton sortButton = new TransparentButton(compact ? left : left + 2 * (controlWidth + 6),
                compact ? 76 : 50, compact ? panelWidth : controlWidth, 20,
                text(sort == InventoryItemGroupsConfig.Sort.DEFAULT ? "sort_default" : "sort_alphabetical"), () -> {
                    sort = sort == InventoryItemGroupsConfig.Sort.DEFAULT
                            ? InventoryItemGroupsConfig.Sort.ALPHABETICAL : InventoryItemGroupsConfig.Sort.DEFAULT;
                    rebuildWidgets();
                });
        sortButton.setTooltip(Tooltip.create(text("sort_hint")));
        addRenderableWidget(sortButton);

        int searchY = compact ? 106 : 80;
        EditBox search = new EditBox(font, left, searchY, panelWidth - 90, 20, text("search"));
        search.setMaxLength(256);
        search.setHint(text("search"));
        search.setValue(filter);
        search.setResponder(value -> {
            filter = value;
            page = 0;
            rebuildRows();
        });
        addRenderableWidget(search);
        addRenderableWidget(new TransparentButton(left + panelWidth - 84, searchY, 84, 20,
                text("add"), () -> openEditor(-1)));

        listTop = searchY + 28;
        pageSize = Math.max(1, (height - listTop - 70) / ROW_HEIGHT);
        rebuildRows();
        int footerWidth = (panelWidth - 12) / 3;
        addRenderableWidget(new TransparentButton(left, height - 28, footerWidth, 20,
                text("save"), this::save));
        addRenderableWidget(new TransparentButton(left + footerWidth + 6, height - 28, footerWidth, 20,
                Component.translatable("gui.cancel"), this::onClose));
        TransparentButton reset = new TransparentButton(left + 2 * (footerWidth + 6), height - 28, footerWidth, 20,
                text("reset"), this::resetDraft);
        reset.setTooltip(Tooltip.create(text("reset_hint")));
        addRenderableWidget(reset);
    }

    private void rebuildRows() {
        if (rowWidgets.contains(getFocused())) {
            setFocused(null);
        }
        rowWidgets.forEach(this::removeWidget);
        rowWidgets.clear();
        String query = filter.trim().toLowerCase(Locale.ROOT);
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            InventoryItemGroupRule rule = groups.get(i);
            String searchable = rule.groupName() + " " + rule.displayName().getString() + " " + rule.tabId();
            if (searchable.toLowerCase(Locale.ROOT).contains(query)) {
                visible.add(i);
            }
        }
        matches = visible.size();
        pageCount = Math.max(1, (matches + pageSize - 1) / pageSize);
        page = Math.clamp(page, 0, pageCount - 1);
        int first = page * pageSize;
        for (int offset = 0; offset < pageSize && first + offset < visible.size(); offset++) {
            int index = visible.get(first + offset);
            InventoryItemGroupRule rule = groups.get(index);
            int y = listTop + offset * ROW_HEIGHT;
            Component label = Component.literal((index + 1) + ". ").append(rule.displayName())
                    .append("  ·  " + rule.tabId());
            TransparentButton edit = new TransparentButton(left, y, panelWidth - 84, 24, label,
                    () -> openEditor(index));
            edit.setTextLeftAligned(true);
            edit.setTooltip(Tooltip.create(label.copy().append("\n").append(text("edit_hint"))));
            row(edit);
            TransparentButton up = new TransparentButton(left + panelWidth - 78, y, 22, 24,
                    Component.literal("↑"), () -> move(index, -1));
            up.active = index > 0;
            up.setTooltip(Tooltip.create(text("up")));
            row(up);
            TransparentButton down = new TransparentButton(left + panelWidth - 52, y, 22, 24,
                    Component.literal("↓"), () -> move(index, 1));
            down.active = index < groups.size() - 1;
            down.setTooltip(Tooltip.create(text("down")));
            row(down);
            TransparentButton remove = new TransparentButton(left + panelWidth - 26, y, 26, 24,
                    Component.literal("×"), () -> {
                        groups.remove(index);
                        rebuildRows();
                    });
            remove.setTooltip(Tooltip.create(text("delete")));
            row(remove);
        }
        TransparentButton previous = new TransparentButton(left, height - 56, 48, 20,
                Component.literal("←"), () -> { page--; rebuildRows(); });
        previous.active = page > 0;
        previous.setTooltip(Tooltip.create(text("previous")));
        row(previous);
        TransparentButton next = new TransparentButton(left + panelWidth - 48, height - 56, 48, 20,
                Component.literal("→"), () -> { page++; rebuildRows(); });
        next.active = page < pageCount - 1;
        next.setTooltip(Tooltip.create(text("next")));
        row(next);
    }

    private void row(AbstractWidget widget) {
        rowWidgets.add(widget);
        addRenderableWidget(widget);
    }

    private void move(int index, int direction) {
        Collections.swap(groups, index, index + direction);
        rebuildRows();
    }

    private void openEditor(int index) {
        minecraft.setScreenAndShow(new InventoryItemGroupEditScreen(this, index < 0 ? null : groups.get(index), rule -> {
            if (index < 0) {
                groups.add(rule);
                filter = "";
                page = (groups.size() - 1) / pageSize;
            } else {
                groups.set(index, rule);
            }
        }));
    }

    private void resetDraft() {
        enabled = InventoryItemGroupsConfig.DEFAULT_ENABLED;
        preview = InventoryItemGroupsConfig.DEFAULT_SHOW_ITEMS_IN_GROUP;
        sort = InventoryItemGroupsConfig.DEFAULT_SORT;
        groups.clear();
        groups.addAll(InventoryItemGroupDefaults.create());
        filter = "";
        page = 0;
        rebuildWidgets();
    }

    private void save() {
        InventoryItemGroupsConfig.apply(enabled, sort, preview, groups);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        YzuiTheme.label(graphics, font, title, left, 14, panelWidth, YzuiTheme.text(), false);
        YzuiTheme.label(graphics, font, text("description"), left, 30, panelWidth, YzuiTheme.textMuted(), false);
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        if (matches == 0) {
            YzuiTheme.label(graphics, font, text("empty"), left, listTop + 8, panelWidth, YzuiTheme.textMuted(), true);
        }
        YzuiTheme.label(graphics, font, Component.translatable(KEY + "page", page + 1, pageCount, matches),
                left + 54, height - 50, panelWidth - 108, YzuiTheme.textMuted(), true);
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
