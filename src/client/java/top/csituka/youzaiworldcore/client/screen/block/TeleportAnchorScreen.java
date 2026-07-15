package top.csituka.youzaiworldcore.client.screen.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.effect.TeleportFovEffect;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.network.TeleportAnchorDeletePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorRenamePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorReorderPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * 传送锚点选择界面。
 * <p>
 * 使用 TransparentButton 实现半透明白底圆角按钮，与 LoginScreen 风格统一。
 * 底部常驻显示两个按钮：「传送」+「编辑」（或「返回」）。
 * 点击「编辑」后右侧展开「重命名」+「移除」两个贴图按钮，原「编辑」变为「返回」。
 * 状态切换通过 {@link #editMode} 字段管理，UI 重建时根据状态决定显示哪些按钮。
 * 鼠标悬停提示使用 vanilla {@link Tooltip} 机制。
 * 当前打开的传送锚点（玩家正在右键的那个）在名称前显示定位图标。
 * 列表超过 {@link #MAX_VISIBLE_ITEMS} 时启用滚轮滚动。
 */
@SuppressWarnings("null")
public class TeleportAnchorScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_PADDING = 12;
    private static final int ITEM_HEIGHT = 22;
    private static final int ITEM_GAP = 4;
    private static final int TITLE_HEIGHT = 30;
    private static final int ACTIONS_Y_OFFSET = 10;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ICON_BUTTON_SIZE = 20;

    /** 列表区域最多同时显示的条目数，超出时启用滚动。 */
    private static final int MAX_VISIBLE_ITEMS = 8;

    /** 编辑模式下按钮之间的紧凑间距。 */
    private static final int EDIT_GAP = 2;

    /** 当前锚点定位图标的尺寸（与条目高度对齐）。 */
    private static final int LOCATION_ICON_SIZE = 14;

    private static final int HIGHLIGHT_BG = 0x40FFFFFF;
    private static final int BUTTON_TEXT_COLOR = 0xFFFFFF;

    private static final Identifier LOCATION_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_location.png");
    private static final Identifier COPY_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_copy.png");
    private static final Identifier EDIT_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_edit.png");
    private static final Identifier BACK_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_back.png");
    private static final Identifier RENAME_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_rename.png");
    private static final Identifier REMOVE_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_remove.png");
    private static final Identifier MOVE_UP_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_moveup.png");
    private static final Identifier MOVE_DOWN_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_movedown.png");
    private static final Identifier SEARCH_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_search.png");

    private final List<TeleportAnchorData> points;
    @Nullable
    private final BlockPos currentAnchorPos;
    @Nullable
    private final ResourceKey<Level> currentAnchorDim;

    private int selectedIndex = -1;
    private boolean renameMode = false;
    private boolean confirmingDelete = false;

    /**
     * 编辑模式状态：true 时底部右侧展开「重命名」+「移除」按钮，原"编辑"按钮变为"返回"。
     * 切换时调用 {@link #rebuildWidgets()} 重建 UI。
     */
    private boolean editMode = false;

    /** 滚动偏移（列表顶部显示的第几个条目）。 */
    private int scrollOffset = 0;

    /** 平滑过渡进度 0..1：0=未展开，1=完全展开。 */
    private float editModeProgress = 0f;

    /** 搜索模式：true 时显示搜索输入框，列表仅显示名称匹配的传送点。 */
    private boolean searchMode = false;
    private String searchQuery = "";
    private EditBox searchBox;
    /** 当前显示的传送点数量（搜索模式下可能少于 points.size()）。 */
    private int displayPointCount = 0;

    // UI 组件
    private final List<TransparentButton> pointButtons = new ArrayList<>();
    private final List<TransparentButton> renameConfirmButtons = new ArrayList<>();
    @Nullable
    private TextureIconButton teleportButton;
    @Nullable
    private TextureIconButton copyButton;
    @Nullable
    private TextureIconButton editToggleButton;
    @Nullable
    private TextureIconButton renameButton;
    @Nullable
    private TextureIconButton removeButton;
    @Nullable
    private TextureIconButton moveUpButton;
    @Nullable
    private TextureIconButton moveDownButton;
    @Nullable
    private TextureIconButton searchButton;
    private EditBox renameEditBox;

    private int panelX;
    private int panelY;
    private int listBottomY;
    private int actionsY;

    public TeleportAnchorScreen(List<TeleportAnchorData> points,
                                 @Nullable BlockPos currentAnchorPos,
                                 @Nullable ResourceKey<Level> currentAnchorDim) {
        super(Component.translatable("screen.youzaiworldcore.teleport_anchor.title"));
        this.points = points;
        this.currentAnchorPos = currentAnchorPos;
        this.currentAnchorDim = currentAnchorDim;
    }

    @Override
    protected void init() {
        super.init();

        this.pointButtons.clear();
        this.renameConfirmButtons.clear();
        this.teleportButton = null;
        this.copyButton = null;
        this.editToggleButton = null;
        this.renameButton = null;
        this.removeButton = null;
        this.moveUpButton = null;
        this.moveDownButton = null;
        this.searchButton = null;
        this.renameEditBox = null;
        this.searchBox = null;

        // 搜索模式：构建过滤后的列表
        List<TeleportAnchorData> displayPoints = points;
        if (searchMode && searchQuery != null && !searchQuery.isEmpty()) {
            String lower = searchQuery.toLowerCase();
            displayPoints = points.stream()
                    .filter(p -> p.name().toLowerCase().contains(lower))
                    .toList();
        }
        displayPointCount = displayPoints.size();

        // 限制滚动偏移在有效范围内
        int maxScroll = Math.max(0, displayPoints.size() - MAX_VISIBLE_ITEMS);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        int visibleCount = Math.min(displayPoints.size(), MAX_VISIBLE_ITEMS);
        int listHeight = Math.max(0, visibleCount * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP);

        // 搜索框高度
        int searchHeight = searchMode ? (22 + 4) : 0;

        int actionsHeight;
        if (confirmingDelete) {
            actionsHeight = BUTTON_HEIGHT + ACTIONS_Y_OFFSET;
        } else if (renameMode) {
            actionsHeight = 40;
        } else {
            actionsHeight = BUTTON_HEIGHT + ACTIONS_Y_OFFSET;
        }

        int totalHeight = TITLE_HEIGHT + PANEL_PADDING + searchHeight + listHeight + PANEL_PADDING + actionsHeight;

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - totalHeight) / 2;
        actionsY = panelY + TITLE_HEIGHT + PANEL_PADDING + searchHeight + listHeight + PANEL_PADDING + ACTIONS_Y_OFFSET;

        // 搜索模式：构建搜索输入框
        if (searchMode) {
            int searchY = panelY + TITLE_HEIGHT + PANEL_PADDING;
            searchBox = new EditBox(this.font,
                    panelX + PANEL_PADDING, searchY,
                    PANEL_WIDTH - PANEL_PADDING * 2, 18,
                    Component.translatable("screen.youzaiworldcore.teleport_anchor.search_hint"));
            searchBox.setMaxLength(32);
            searchBox.setValue(searchQuery);
            searchBox.setFocused(true);
            searchBox.setResponder(text -> {
                searchQuery = text;
                scrollOffset = 0;
                rebuildWidgets();
            });
            addRenderableWidget(searchBox);
        }

        // 构建列表条目按钮
        int buttonY = panelY + TITLE_HEIGHT + PANEL_PADDING + searchHeight;
        for (int i = 0; i < visibleCount; i++) {
            int displayIndex = scrollOffset + i;
            TeleportAnchorData point = displayPoints.get(displayIndex);
            // 在原始 points 中查找该点的索引
            int pointIndex = points.indexOf(point);
            boolean isSelected = (pointIndex == selectedIndex);

            String label = formatPointLabel(point);
            TransparentButton btn = new TransparentButton(
                    panelX + PANEL_PADDING, buttonY,
                    PANEL_WIDTH - PANEL_PADDING * 2, ITEM_HEIGHT,
                    Component.literal(label),
                    () -> { if (!isSelected) selectPoint(pointIndex); }
            );
            btn.setTextColor(BUTTON_TEXT_COLOR);
            if (isSelected) {
                btn.setExternalAlpha(0.3f);
            }
            btn.active = !isSelected;
            pointButtons.add(btn);
            addRenderableWidget(btn);

            buttonY += ITEM_HEIGHT + ITEM_GAP;
        }
        listBottomY = buttonY - ITEM_GAP;

        // 构建底部操作区域
        if (confirmingDelete) {
            buildDeleteConfirmUI();
        } else if (renameMode) {
            buildRenameUI();
        } else {
            buildBottomButtons();
        }
    }

    @Override
    public void rebuildWidgets() {
        this.pointButtons.clear();
        this.renameConfirmButtons.clear();
        this.teleportButton = null;
        this.copyButton = null;
        this.editToggleButton = null;
        this.renameButton = null;
        this.removeButton = null;
        this.moveUpButton = null;
        this.moveDownButton = null;
        this.renameEditBox = null;
        super.rebuildWidgets();
    }

    /** 判断指定传送点是否是当前打开的锚点。 */
    private boolean isCurrentAnchor(TeleportAnchorData point) {
        return currentAnchorPos != null && currentAnchorDim != null
                && point.pos().equals(currentAnchorPos)
                && point.dimension().equals(currentAnchorDim);
    }

    private void selectPoint(int index) {
        // 切换选中点时先关闭编辑模式，避免旧按钮与新按钮重叠
        if (editMode) {
            editMode = false;
            editModeProgress = 0f;
        }
        selectedIndex = index;
        ensureVisible(index);
        rebuildWidgets();
    }

    /** 滚动列表使指定索引可见。 */
    private void ensureVisible(int index) {
        if (index < scrollOffset) {
            scrollOffset = index;
        } else if (index >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = index - MAX_VISIBLE_ITEMS + 1;
        }
    }

    private void enterEditMode() {
        if (editMode) return;
        editMode = true;
        editModeProgress = 1f;
        rebuildWidgets();
    }

    private void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        editModeProgress = 0f;
        rebuildWidgets();
    }

    /** 构建底部常驻的"传送"按钮和"编辑/返回"按钮；编辑模式下额外构建重命名/移除按钮。 */
    private void buildBottomButtons() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < points.size();
        boolean isCurrentAnchor = hasSelection && isCurrentAnchor(points.get(selectedIndex));

        // 传送按钮（文字 + 半透明白底）
        int teleportX = panelX + PANEL_PADDING;
        teleportButton = new TextureIconButton(
                teleportX, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                null,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.teleport"),
                Component.translatable("screen.youzaiworldcore.teleport_anchor.tooltip_teleport"),
                () -> {
                    if (hasSelection && !isCurrentAnchor) {
                        TeleportAnchorData point = points.get(selectedIndex);
                        // 启动 FOV 动画，数据包在放大到顶后自动发送
                        TeleportFovEffect.startTeleport(point.pos(), point.dimension());
                        Minecraft.getInstance().setScreenAndShow(null);
                    }
                });
        teleportButton.setTextColor(BUTTON_TEXT_COLOR);
        teleportButton.active = hasSelection && !isCurrentAnchor;
        if (!teleportButton.active) teleportButton.setExternalAlpha(0.3f);
        addRenderableWidget(teleportButton);

        // 编辑/返回按钮（贴图，已含文字，不显示额外 label）
        int editX = panelX + PANEL_WIDTH - PANEL_PADDING - ICON_BUTTON_SIZE;
        // 复制按钮默认在编辑按钮左侧；编辑模式展开时再往左移动腾出空间
        int copyX = editX - ICON_BUTTON_SIZE - EDIT_GAP;
        int searchX = copyX - ICON_BUTTON_SIZE - EDIT_GAP;
        Identifier editIcon = editMode ? BACK_ICON : EDIT_ICON;
        Component editTooltip = Component.translatable(editMode
                ? "screen.youzaiworldcore.teleport_anchor.back"
                : "screen.youzaiworldcore.teleport_anchor.tooltip_edit");

        editToggleButton = new TextureIconButton(
                editX, actionsY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                editIcon,
                Component.empty(),
                editTooltip,
                () -> {
                    if (!hasSelection) return;
                    if (editMode) exitEditMode();
                    else enterEditMode();
                });
        editToggleButton.active = hasSelection;
        if (!editToggleButton.active) editToggleButton.setExternalAlpha(0.3f);
        addRenderableWidget(editToggleButton);

        // 编辑模式下显示重命名 + 移除 + 上下移动按钮（X 偏移基于动画进度实现平滑过渡）
        if (editMode) {
            // progress 0->1：按钮从右侧滑入，最终停在返回按钮左侧
            // totalExpand=92 是重命名按钮起点偏移（最右），minOffset=24 是终点偏移（与返回按钮间隔一个按钮+间隙）
            int totalExpand = ICON_BUTTON_SIZE * 4 + EDIT_GAP * 3;
            int minOffset  = ICON_BUTTON_SIZE + EDIT_GAP;
            int expandOffset = minOffset + Math.round((1f - editModeProgress) * (totalExpand - minOffset));
            int renameX = editX - expandOffset;
            int removeX = renameX - ICON_BUTTON_SIZE - EDIT_GAP;
            int moveUpX = removeX - ICON_BUTTON_SIZE - EDIT_GAP;
            int moveDownX = moveUpX - ICON_BUTTON_SIZE - EDIT_GAP;
            // 复制按钮随展开一同左移
            copyX = moveDownX - ICON_BUTTON_SIZE - EDIT_GAP;
            searchX = copyX - ICON_BUTTON_SIZE - EDIT_GAP;

            renameButton = new TextureIconButton(
                    renameX, actionsY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                    RENAME_ICON,
                    Component.empty(),
                    Component.translatable("screen.youzaiworldcore.teleport_anchor.tooltip_rename"),
                    () -> {
                        editMode = false;
                        if (hasSelection) {
                            renameMode = true;
                        }
                        rebuildWidgets();
                    });
            renameButton.active = hasSelection;
            if (!renameButton.active) renameButton.setExternalAlpha(0.3f);
            addRenderableWidget(renameButton);

            removeButton = new TextureIconButton(
                    removeX, actionsY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                    REMOVE_ICON,
                    Component.empty(),
                    Component.translatable("screen.youzaiworldcore.teleport_anchor.tooltip_remove"),
                    () -> {
                        editMode = false;
                        if (hasSelection) {
                            confirmingDelete = true;
                        }
                        rebuildWidgets();
                    });
            removeButton.active = hasSelection;
            if (!removeButton.active) removeButton.setExternalAlpha(0.3f);
            addRenderableWidget(removeButton);

            // 向上移动按钮
            moveUpButton = new TextureIconButton(
                    moveUpX, actionsY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                    MOVE_UP_ICON,
                    Component.empty(),
                    Component.translatable("screen.youzaiworldcore.teleport_anchor.tooltip_move_up"),
                    () -> {
                        if (!hasSelection) return;
                        moveSelectedPoint(-1);
                    });
            // 仅在有选中且不是第一项时可点击
            boolean canMoveUp = hasSelection && selectedIndex > 0;
            moveUpButton.active = canMoveUp;
            if (!moveUpButton.active) moveUpButton.setExternalAlpha(0.3f);
            addRenderableWidget(moveUpButton);

            // 向下移动按钮
            moveDownButton = new TextureIconButton(
                    moveDownX, actionsY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                    MOVE_DOWN_ICON,
                    Component.empty(),
                    Component.translatable("screen.youzaiworldcore.teleport_anchor.tooltip_move_down"),
                    () -> {
                        if (!hasSelection) return;
                        moveSelectedPoint(1);
                    });
            // 仅在有选中且不是最后一项时可点击
            boolean canMoveDown = hasSelection && selectedIndex >= 0 && selectedIndex < points.size() - 1;
            moveDownButton.active = canMoveDown;
            if (!moveDownButton.active) moveDownButton.setExternalAlpha(0.3f);
            addRenderableWidget(moveDownButton);
        }

        // 搜索按钮
        searchButton = new TextureIconButton(
                searchX, actionsY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                SEARCH_ICON,
                Component.empty(),
                Component.translatable("screen.youzaiworldcore.teleport_anchor.tooltip_search"),
                () -> {
                    searchMode = !searchMode;
                    if (!searchMode) {
                        searchQuery = "";
                        scrollOffset = 0;
                    }
                    rebuildWidgets();
                });
        addRenderableWidget(searchButton);

        // 复制坐标按钮（在编辑模式展开逻辑之后创建，确保 X 坐标正确）
        copyButton = new TextureIconButton(
                copyX, actionsY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                COPY_ICON,
                Component.empty(),
                Component.translatable("screen.youzaiworldcore.teleport_anchor.tooltip_copy"),
                () -> {
                    if (!hasSelection) return;
                    TeleportAnchorData point = points.get(selectedIndex);
                    String dim = point.dimension().identifier().toString();
                    String text = dim + " "
                            + point.pos().getX() + " "
                            + point.pos().getY() + " "
                            + point.pos().getZ();
                    Minecraft.getInstance().keyboardHandler.setClipboard(text);
                    var localPlayer = Minecraft.getInstance().player;
                    if (localPlayer != null) {
                        localPlayer.sendSystemMessage(
                                Component.translatable("message.youzaiworldcore.teleport_anchor.copied", text));
                    }
                });
        copyButton.active = hasSelection;
        if (!copyButton.active) copyButton.setExternalAlpha(0.3f);
        addRenderableWidget(copyButton);
    }

    /**
     * 移动当前选中的传送点。
     * @param delta -1 表示向上，+1 表示向下
     */
    private void moveSelectedPoint(int delta) {
        if (selectedIndex < 0 || selectedIndex >= points.size()) return;
        int newIndex = selectedIndex + delta;
        if (newIndex < 0 || newIndex >= points.size()) return;
        // 本地 UI 即时交换
        TeleportAnchorData temp = points.get(selectedIndex);
        points.set(selectedIndex, points.get(newIndex));
        points.set(newIndex, temp);
        // 发送给服务端持久化
        ClientPlayNetworking.send(new TeleportAnchorReorderPayload(selectedIndex, newIndex));
        selectedIndex = newIndex;
        rebuildWidgets();
    }

    private void buildDeleteConfirmUI() {
        int totalBtnWidth = BUTTON_WIDTH * 2 + 8;
        int startX = panelX + (PANEL_WIDTH - totalBtnWidth) / 2;

        TransparentButton confirmBtn = new TransparentButton(
                startX, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_confirm"),
                this::confirmDeletePoint);
        confirmBtn.setTextColor(BUTTON_TEXT_COLOR);
        renameConfirmButtons.add(confirmBtn);
        addRenderableWidget(confirmBtn);

        TransparentButton cancelBtn = new TransparentButton(
                startX + BUTTON_WIDTH + 8, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_cancel"),
                () -> {
                    confirmingDelete = false;
                    rebuildWidgets();
                });
        cancelBtn.setTextColor(BUTTON_TEXT_COLOR);
        renameConfirmButtons.add(cancelBtn);
        addRenderableWidget(cancelBtn);
    }

    private void confirmDeletePoint() {
        TeleportAnchorData point = points.get(selectedIndex);
        ClientPlayNetworking.send(new TeleportAnchorDeletePayload(point.pos(), point.dimension()));

        // 如果删除的是当前打开的锚点，关闭界面
        if (isCurrentAnchor(point)) {
            Minecraft.getInstance().setScreenAndShow(null);
            return;
        }

        points.remove(selectedIndex);
        selectedIndex = -1;
        confirmingDelete = false;
        rebuildWidgets();
    }

    private void buildRenameUI() {
        TeleportAnchorData point = points.get(selectedIndex);
        int editBoxY = listBottomY + PANEL_PADDING + 4;
        int editBoxWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int editBoxX = panelX + PANEL_PADDING;

        renameEditBox = new EditBox(this.font, editBoxX, editBoxY, editBoxWidth, 18,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_hint"));
        renameEditBox.setValue(point.name());
        renameEditBox.setFocused(true);
        addRenderableWidget(renameEditBox);

        int btnY = editBoxY + 24;
        int confirmX = panelX + (PANEL_WIDTH / 2) - BUTTON_WIDTH - 4;
        int cancelX = panelX + (PANEL_WIDTH / 2) + 4;

        TransparentButton confirmBtn = new TransparentButton(
                confirmX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_confirm"),
                this::confirmRename);
        confirmBtn.setTextColor(BUTTON_TEXT_COLOR);
        renameConfirmButtons.add(confirmBtn);
        addRenderableWidget(confirmBtn);

        TransparentButton cancelBtn = new TransparentButton(
                cancelX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_cancel"),
                this::cancelRename);
        cancelBtn.setTextColor(BUTTON_TEXT_COLOR);
        renameConfirmButtons.add(cancelBtn);
        addRenderableWidget(cancelBtn);
    }

    private void confirmRename() {
        if (renameEditBox != null && selectedIndex >= 0 && selectedIndex < points.size()) {
            String newName = renameEditBox.getValue().trim();
            if (!newName.isEmpty()) {
                TeleportAnchorData old = points.get(selectedIndex);
                points.set(selectedIndex, new TeleportAnchorData(old.pos(), old.dimension(), newName, old.poolId()));
                ClientPlayNetworking.send(new TeleportAnchorRenamePayload(old.pos(), old.dimension(), newName));
            }
        }
        renameMode = false;
        rebuildWidgets();
    }

    private void cancelRename() {
        renameMode = false;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (displayPointCount > MAX_VISIBLE_ITEMS) {
            int maxScroll = displayPointCount - MAX_VISIBLE_ITEMS;
            if (scrollY > 0) {
                if (scrollOffset > 0) {
                    scrollOffset--;
                    rebuildWidgets();
                }
            } else if (scrollY < 0) {
                if (scrollOffset < maxScroll) {
                    scrollOffset++;
                    rebuildWidgets();
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String formatPointLabel(TeleportAnchorData point) {
        return point.name();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 半透明黑色背景遮罩
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        // 标题
        var font = Minecraft.getInstance().font;
        String title = this.getTitle().getString();
        int titleWidth = font.width(title);
        guiGraphics.text(font, title, (this.width - titleWidth) / 2, panelY + 10, 0xFFFFFFFF, false);

        // 选中高亮
        if (selectedIndex >= 0 && selectedIndex < points.size()) {
            int relativeIndex = selectedIndex - scrollOffset;
            if (relativeIndex >= 0 && relativeIndex < MAX_VISIBLE_ITEMS) {
                int highlightY = panelY + TITLE_HEIGHT + PANEL_PADDING
                        + relativeIndex * (ITEM_HEIGHT + ITEM_GAP);
                guiGraphics.fill(panelX + PANEL_PADDING, highlightY,
                        panelX + PANEL_WIDTH - PANEL_PADDING, highlightY + ITEM_HEIGHT,
                        HIGHLIGHT_BG);
            }
        }

        // 删除确认提示文字
        if (confirmingDelete) {
            String confirmMsg = Component.translatable(
                    "screen.youzaiworldcore.teleport_anchor.delete_hint").getString();
            int msgWidth = font.width(confirmMsg);
            int msgY = listBottomY + PANEL_PADDING - 2;
            guiGraphics.text(font, confirmMsg,
                    (this.width - msgWidth) / 2, msgY, 0xFFFFAA00, false);
        }

        // 滚动指示器
        if (displayPointCount > MAX_VISIBLE_ITEMS) {
            int scrollbarX = panelX + PANEL_WIDTH - 4;
            int scrollbarY = panelY + TITLE_HEIGHT + PANEL_PADDING + (searchMode ? 26 : 0);
            int scrollbarHeight = MAX_VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP;
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollbarHeight, 0x40FFFFFF);

            int maxScroll = displayPointCount - MAX_VISIBLE_ITEMS;
            if (maxScroll > 0) {
                int thumbHeight = Math.max(10, scrollbarHeight * MAX_VISIBLE_ITEMS / displayPointCount);
                int thumbY = scrollbarY + (scrollbarHeight - thumbHeight) * scrollOffset / maxScroll;
                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0x80FFFFFF);
            }
        }

        // 渲染子组件
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // 在当前锚点条目的名称前叠加定位图标（在按钮之上绘制）
        drawCurrentAnchorIcons(guiGraphics);
    }

    /** 在当前正在打开的传送锚点条目左侧绘制定位图标。 */
    private void drawCurrentAnchorIcons(GuiGraphicsExtractor guiGraphics) {
        for (int i = 0; i < pointButtons.size(); i++) {
            int pointIndex = scrollOffset + i;
            if (pointIndex >= points.size()) break;
            TeleportAnchorData point = points.get(pointIndex);
            if (!isCurrentAnchor(point)) continue;

            TransparentButton btn = pointButtons.get(i);
            int iconX = btn.getX() + 4;
            int iconY = btn.getY() + (ITEM_HEIGHT - LOCATION_ICON_SIZE) / 2;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, LOCATION_ICON,
                    iconX, iconY,
                    0, 0,
                    LOCATION_ICON_SIZE, LOCATION_ICON_SIZE,
                    LOCATION_ICON_SIZE, LOCATION_ICON_SIZE);
            break;
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (renameMode && renameEditBox != null && renameEditBox.isFocused()) {
            if (keyEvent.key() == 257) { // Enter
                confirmRename();
                return true;
            }
            if (renameEditBox.keyPressed(keyEvent)) {
                return true;
            }
        }
        // 搜索模式下转发键盘输入
        if (searchMode && searchBox != null && searchBox.isFocused()) {
            if (searchBox.keyPressed(keyEvent)) {
                return true;
            }
        }
        // ESC 退出搜索或编辑模式
        if (keyEvent.key() == 256) {
            if (searchMode && !renameMode && !confirmingDelete) {
                searchMode = false;
                searchQuery = "";
                scrollOffset = 0;
                rebuildWidgets();
                return true;
            }
            if (editMode && !renameMode && !confirmingDelete) {
                exitEditMode();
                return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent characterEvent) {
        if (renameMode && renameEditBox != null && renameEditBox.isFocused()) {
            if (renameEditBox.charTyped(characterEvent)) {
                return true;
            }
        }
        if (searchMode && searchBox != null && searchBox.isFocused()) {
            if (searchBox.charTyped(characterEvent)) {
                return true;
            }
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean bl) {
        if (renameMode && renameEditBox != null) {
            renameEditBox.setFocused(
                    mouseButtonEvent.x() >= renameEditBox.getX()
                    && mouseButtonEvent.x() < renameEditBox.getX() + renameEditBox.getWidth()
                    && mouseButtonEvent.y() >= renameEditBox.getY()
                    && mouseButtonEvent.y() < renameEditBox.getY() + renameEditBox.getHeight()
            );
        }
        if (searchMode && searchBox != null) {
            searchBox.setFocused(
                    mouseButtonEvent.x() >= searchBox.getX()
                    && mouseButtonEvent.x() < searchBox.getX() + searchBox.getWidth()
                    && mouseButtonEvent.y() >= searchBox.getY()
                    && mouseButtonEvent.y() < searchBox.getY() + searchBox.getHeight()
            );
        }
        return super.mouseClicked(mouseButtonEvent, bl);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制原版背景
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 贴图图标按钮：内部用 blit 渲染一张纹理，可选叠加文字。
     * 继承 TransparentButton 以复用半透明白底、hover lerp、disabled 状态。
     * 使用 vanilla {@link Tooltip} 提供悬停提示。
     */
    @SuppressWarnings("null")
    private static class TextureIconButton extends TransparentButton {
        @Nullable
        private final Identifier texture;

        TextureIconButton(int x, int y, int width, int height,
                          @Nullable Identifier texture,
                          @Nullable Component message,
                          @Nullable Component tooltip,
                          Runnable onPress) {
            super(x, y, width, height, message != null ? message : Component.empty(), onPress);
            this.texture = texture;
            if (texture == null) {
                this.setBackgroundVisible(true);
            } else {
                this.setBackgroundVisible(false);
            }
            this.setTextColor(0xFFFFFF);
            if (tooltip != null) {
                this.setTooltip(Tooltip.create(tooltip));
            }
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 父类渲染（半透明白底 + 文字）
            super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
            // 叠加贴图
            if (texture == null) return;
            float vis = Math.min(1f, Math.max(0f, this.getAlpha()));
            if (vis < 0.001f) return;
            int x = this.getX();
            int y = this.getY();
            int w = this.width;
            int h = this.height;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, 0, 0, w, h, w, h);
            // 不可用时叠加暗色遮罩使贴图变淡
            if (!this.active) {
                guiGraphics.fill(x, y, x + w, y + h, 0x80000000);
            }
        }
    }
}
