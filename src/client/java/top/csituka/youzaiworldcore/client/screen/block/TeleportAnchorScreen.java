package top.csituka.youzaiworldcore.client.screen.block;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.effect.TeleportFovEffect;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.item.tool.TeleportStoneItem;
import top.csituka.youzaiworldcore.item.tool.WarpScrollItem;
import top.csituka.youzaiworldcore.network.TeleportAnchorDeletePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload.EntryType;
import top.csituka.youzaiworldcore.network.TeleportAnchorRenamePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorReorderPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 传送锚点选择界面。
 * <p>
 * 使用官网薄荷色 MD3 卡片；搜索、列表和操作区随窗口高度自适应。
 * 底部常驻显示两个按钮：「传送」+「编辑」（或「返回」）。
 * 点击「编辑」后右侧展开「重命名」+「移除」两个贴图按钮，原「编辑」变为「返回」。
 * 状态切换通过 {@link #editMode} 字段管理，UI 重建时根据状态决定显示哪些按钮。
 * 鼠标悬停提示使用 vanilla {@link Tooltip} 机制。
 * 当前打开的传送锚点（玩家正在右键的那个）在名称前显示定位图标。
 * 列表可见行数按窗口高度计算，超过可见范围时启用滚轮滚动。
 * <p>
 * 列表入口类型由 {@link TeleportAnchorListPayload#entryType} 决定，用于显示不同
 * 的代价信息（传送锚点方块 / 传送石 / 传送卷轴），并控制客户端发送传送包前的预判。
 */
@SuppressWarnings("null")
public class TeleportAnchorScreen extends Screen {

    private int panelWidth;
    private int panelHeight;
    private int visibleRows;
    private String renameDraft;
    private static final int PANEL_PADDING = 16;
    private static final int ITEM_HEIGHT = 32;
    private static final int ITEM_GAP = 6;
    private static final int TITLE_HEIGHT = 40;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_HEIGHT = 28;
    private static final int ICON_BUTTON_SIZE = 28;

    /** 标题栏右上角关闭按钮的尺寸（与其它菜单的关闭按钮一致）。 */
    private static final int CLOSE_BUTTON_SIZE = 24;

    /** 列表区域最多同时显示的条目数，超出时启用滚动。 */
    private static final int MAX_VISIBLE_ITEMS = 10;

    /** 编辑模式下按钮之间的紧凑间距。 */
    private static final int EDIT_GAP = 4;

    /** 当前锚点定位图标的尺寸（与条目高度对齐）。 */
    private static final int LOCATION_ICON_SIZE = 14;

    private static int buttonTextColor() { return YzuiTheme.text(); }

    /** 空列表提示文本的颜色（灰白，区别于正常条目）。 */
    private static int emptyHintColor() { return YzuiTheme.textMuted(); }

    /** 条目右侧距离文本的颜色。 */
    private static int distanceColor() { return YzuiTheme.textMuted(); }

    /** 底部消耗信息文本的颜色。 */
    private static int costTextColor() { return YzuiTheme.textMuted(); }

    /** 消耗不足时的文本颜色（橙红）。 */
    private static int costInsufficientColor() { return YzuiTheme.error(); }

    /** 消耗信息每行占用的高度。 */
    private static final int COST_LINE_HEIGHT = 11;

    /** 底部按钮与第一行消耗信息之间的间距。 */
    private static final int COST_INFO_TOP_GAP = 4;

    /** 耐久消耗行内嵌的传送石图标尺寸。 */
    private static final int STONE_ICON_SIZE = 10;

    /** 卷轴消耗行内嵌的传送卷轴图标尺寸。 */
    private static final int SCROLL_ICON_SIZE = 10;

    /** 同维度传送消耗的经验等级，与服务端 {@code ModNetworking} 中的口径保持一致。 */
    private static final int XP_COST_SAME_DIMENSION = 1;

    /** 跨维度传送消耗的经验等级。 */
    private static final int XP_COST_CROSS_DIMENSION = 2;

    /**
     * 距离单位对应的语言键，逐级换算：m → km → Mm → Gm，Gm 之后不再进位。
     */
    private static final String[] DISTANCE_UNIT_KEYS = {
            "screen.youzaiworldcore.teleport_anchor.distance_m",
            "screen.youzaiworldcore.teleport_anchor.distance_km",
            "screen.youzaiworldcore.teleport_anchor.distance_mm",
            "screen.youzaiworldcore.teleport_anchor.distance_gm",
    };

    /** 条目右侧距离文本与条目右边缘的间距。 */
    private static final int DISTANCE_PADDING = 6;

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
    private static final Identifier STONE_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/item/teleport_stone.png");
    private static final Identifier SCROLL_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/item/warp_scroll.png");

    private final List<TeleportAnchorData> points;

    @Nullable
    private final BlockPos currentAnchorPos;
    @Nullable
    private final ResourceKey<Level> currentAnchorDim;

    /**
     * 入口类型，决定本次列表的代价信息与预判逻辑：
     * <ul>
     *   <li>{@link EntryType#ANCHOR}：纯锚点入口，仅扣经验 + 维度池/锚点本身校验</li>
     *   <li>{@link EntryType#STONE}：传送石入口，额外扣耐久 + 60 秒物品冷却</li>
     *   <li>{@link EntryType#SCROLL}：传送卷轴入口，额外消耗 1 张卷轴 + 120 秒物品冷却，不查耐久/经验</li>
     * </ul>
     */
    private final EntryType entryType;

    /**
     * 本次列表由传送石/卷轴打开时，玩家握持该物品的那只手；null 表示走的是传送锚点方块入口。
     */
    @Nullable
    private final InteractionHand entryHand;

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

    /**
     * 当前实际渲染的条目列表（搜索模式下为过滤后的子集，否则即 points 本身）。
     * 渲染层按可见按钮下标定位条目时必须用它而不是 points，否则搜索状态下会错位。
     */
    private List<TeleportAnchorData> displayedPoints = List.of();

    /**
     * 玩家一个可用传送锚点都没有：列表区域改为占一行高度并居中显示空列表提示文本。
     * 仅在 {@code points} 本身为空时为 true；搜索无结果不算（那是过滤结果，不是没有锚点）。
     */
    private boolean showEmptyHint = false;

    /** 列表区域顶部 Y 坐标，供绘制空列表提示时定位。 */
    private int listTopY;

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
    @Nullable
    private TransparentButton closeButton;
    private EditBox renameEditBox;

    private int panelX;
    private int panelY;
    private int listBottomY;
    private int actionsY;

    public TeleportAnchorScreen(List<TeleportAnchorData> points,
                                 @Nullable BlockPos currentAnchorPos,
                                 @Nullable ResourceKey<Level> currentAnchorDim,
                                 EntryType entryType,
                                 @Nullable InteractionHand entryHand) {
        super(Component.translatable("screen.youzaiworldcore.teleport_anchor.title"));
        this.points = new ArrayList<>(points);
        this.currentAnchorPos = currentAnchorPos;
        this.currentAnchorDim = currentAnchorDim;
        this.entryType = entryType;
        this.entryHand = entryHand;
    }

    @Override
    protected void init() {
        super.init();
        if (renameMode && renameEditBox != null) renameDraft = renameEditBox.getValue();
        pointButtons.clear();
        renameConfirmButtons.clear();
        teleportButton = copyButton = editToggleButton = renameButton = removeButton = null;
        moveUpButton = moveDownButton = searchButton = null;
        closeButton = null;
        renameEditBox = searchBox = null;

        String query = searchQuery.toLowerCase(Locale.ROOT);
        displayedPoints = searchMode && !query.isEmpty()
                ? points.stream().filter(p -> p.name().toLowerCase(Locale.ROOT).contains(query)).toList()
                : points;
        displayPointCount = displayedPoints.size();
        panelWidth = Math.min(520, width - 40);
        int searchHeight = searchMode ? 32 : 0;
        int actionsHeight = renameMode ? 60 : confirmingDelete ? 56 : BUTTON_HEIGHT + costInfoHeight();
        int fixedHeight = TITLE_HEIGHT + searchHeight + PANEL_PADDING * 3 + actionsHeight;
        visibleRows = Math.clamp((height - 32 - fixedHeight + ITEM_GAP) / (ITEM_HEIGHT + ITEM_GAP),
                1, MAX_VISIBLE_ITEMS);
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, displayPointCount - visibleRows));
        int visibleCount = Math.min(displayPointCount, visibleRows);
        showEmptyHint = displayPointCount == 0;
        int listHeight = Math.max(1, visibleCount) * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP;
        panelHeight = fixedHeight + listHeight;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        listTopY = panelY + TITLE_HEIGHT + PANEL_PADDING + searchHeight;
        listBottomY = listTopY + listHeight;
        actionsY = listBottomY + PANEL_PADDING + (confirmingDelete ? 28 : 0);

        closeButton = new TransparentButton(panelX + panelWidth - PANEL_PADDING - CLOSE_BUTTON_SIZE,
                panelY + 8, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                Component.translatable("youzaiworldcore.message.gui.close_button"), this::onClose);
        closeButton.setBackgroundVisible(false);
        addRenderableWidget(closeButton);

        if (searchMode) {
            searchBox = new EditBox(font, panelX + PANEL_PADDING, panelY + TITLE_HEIGHT + PANEL_PADDING,
                    panelWidth - PANEL_PADDING * 2, 24,
                    Component.translatable("screen.youzaiworldcore.teleport_anchor.search_hint"));
            searchBox.setMaxLength(32);
            searchBox.setHint(Component.translatable("screen.youzaiworldcore.teleport_anchor.search_hint"));
            searchBox.setValue(searchQuery);
            searchBox.active = !renameMode && !confirmingDelete;
            searchBox.setResponder(text -> {
                searchQuery = text;
                scrollOffset = 0;
                rebuildWidgets();
            });
            addRenderableWidget(searchBox);
            if (searchBox.active) setFocused(searchBox);
        }

        for (int i = 0; i < visibleCount; i++) {
            TeleportAnchorData point = displayedPoints.get(scrollOffset + i);
            int pointIndex = points.indexOf(point);
            boolean selected = pointIndex == selectedIndex;
            TransparentButton button = new TransparentButton(panelX + PANEL_PADDING,
                    listTopY + i * (ITEM_HEIGHT + ITEM_GAP), panelWidth - PANEL_PADDING * 2, ITEM_HEIGHT,
                    Component.literal(point.name()), () -> { if (!selected) selectPoint(pointIndex); });
            button.setStyle(selected ? YzuiTheme.ButtonStyle.TONAL : YzuiTheme.ButtonStyle.TEXT);
            button.setTextLeftAligned(true);
            button.setTextInsets(isCurrentAnchor(point) ? 26 : 10,
                    Math.min(panelWidth / 3, font.width(formatDistance(point)) + 16));
            button.setTooltip(Tooltip.create(Component.literal(point.name())));
            button.active = !renameMode && !confirmingDelete;
            pointButtons.add(button);
            addRenderableWidget(button);
        }
        if (confirmingDelete) buildDeleteConfirmUI();
        else if (renameMode) buildRenameUI();
        else buildBottomButtons();
    }

    @Override
    public void rebuildWidgets() {
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
        if (index < 0 || index >= points.size()) return;
        int displayIndex = displayedPoints.indexOf(points.get(index));
        if (displayIndex < 0) return;
        if (displayIndex < scrollOffset) scrollOffset = displayIndex;
        else if (displayIndex >= scrollOffset + visibleRows) scrollOffset = displayIndex - visibleRows + 1;
    }

    private void enterEditMode() {
        if (editMode) return;
        editMode = true;
        editModeProgress = GuiAnimationController.isEnabled() ? 0f : 1f;
        rebuildWidgets();
    }

    private void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        editModeProgress = GuiAnimationController.isEnabled() ? 1f : 0f;
        rebuildWidgets();
    }

    /** 构建底部常驻的"传送"按钮和"编辑/返回"按钮；编辑模式下额外构建重命名/移除按钮。 */
    private void buildBottomButtons() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < points.size();
        boolean isCurrentAnchor = hasSelection && isCurrentAnchor(points.get(selectedIndex));

        // 消耗预校验：经验等级 / 传送石耐久 / 卷轴数量任一不足都置灰传送按钮，
        // 避免白播一段传送 FOV 动画后才被服务端拒绝
        boolean affordable = !hasSelection || canAfford(points.get(selectedIndex));

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
                        // 消耗不足则什么都不做（按钮本应已置灰，这里兜底防止界面打开期间数值变化）
                        if (!canAfford(point)) {
                            return;
                        }
                        // 启动 FOV 动画，数据包在放大到顶后自动发送
                        TeleportFovEffect.startTeleport(point.pos(), point.dimension());
                        Minecraft.getInstance().setScreenAndShow(null);
                    }
                });
        teleportButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
        teleportButton.active = hasSelection && !isCurrentAnchor && affordable;

        addRenderableWidget(teleportButton);

        // 编辑/返回按钮（贴图，已含文字，不显示额外 label）
        int editX = panelX + panelWidth - PANEL_PADDING - ICON_BUTTON_SIZE;
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

        addRenderableWidget(editToggleButton);

        // 编辑模式下显示重命名 + 移除 + 上下移动按钮（X 偏移基于动画进度实现平滑过渡）
        if (editMode) {
            // progress 0->1：按钮从右侧滑入，最终停在返回按钮左侧
            int expandOffset = ICON_BUTTON_SIZE + EDIT_GAP;
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
                            renameDraft = null;
                            renameMode = true;
                        }
                        rebuildWidgets();
                    });
            renameButton.active = hasSelection;

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

            addRenderableWidget(moveDownButton);
        }

        // 搜索按钮（一个可用锚点都没有时无从搜索，直接置灰）
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
        searchButton.active = !points.isEmpty();

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

        addRenderableWidget(copyButton);
    }

    /**
     * 按玩家当前的等级、传送石耐久、卷轴数量刷新传送按钮的可用状态。
     * <p>
     * 每帧调用一次：界面打开期间这些数值可能变化（捡起经验球、别处扣经验、扔掉卷轴），
     * 只在重建 UI 时判定会让按钮状态过期。
     */
    private void refreshTeleportAffordability() {
        if (teleportButton == null) {
            return;
        }
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < points.size();
        if (!hasSelection) {
            return;
        }
        TeleportAnchorData point = points.get(selectedIndex);
        boolean active = !isCurrentAnchor(point) && canAfford(point);
        teleportButton.active = active;
        teleportButton.setExternalAlpha(1f);
    }

    /**
     * 客户端预判本次传送的所有消耗是否都付得起：
     * <ul>
     *   <li>经验等级（同维度 1 / 跨维度 2）</li>
     *   <li>{@link EntryType#STONE} 入口额外检查传送石耐久</li>
     *   <li>{@link EntryType#SCROLL} 入口额外检查卷轴数量 ≥ 1（与耐久/XP 无关）</li>
     * </ul>
     * <p>
     * 这只是提前拦截，真正的扣费与校验仍在服务端；创造模式三项都免除，直接放行。
     */
    private boolean canAfford(TeleportAnchorData point) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getAbilities().instabuild) {
            return true;
        }
        // 卷轴入口完全免经验（一次性成本已由卷轴本体的消耗承担）
        if (entryType != EntryType.SCROLL && player.experienceLevel < xpCostFor(point)) {
            return false;
        }
        if (entryType == EntryType.STONE) {
            ItemStack stone = findStoneStack(player);
            if (stone.isEmpty()) {
                return false;
            }
            return remainingDurability(stone) >= TeleportStoneItem.computeDurabilityCost(player, point);
        }
        if (entryType == EntryType.SCROLL) {
            ItemStack scroll = findScrollStack(player);
            if (scroll.isEmpty()) {
                return false;
            }
            return WarpScrollItem.canAffordScroll(player, scroll);
        }
        return true;
    }

    /**
     * 找到本次列表所对应的那把传送石。
     * <p>
     * 优先取打开列表时记录的那只手，找不到时再看另一只手——与服务端结算耐久时的兜底顺序一致。
     *
     * @return 对应的传送石；两只手都没有时返回 {@link ItemStack#EMPTY}
     */
    private ItemStack findStoneStack(net.minecraft.client.player.LocalPlayer player) {
        if (entryHand == null) {
            return ItemStack.EMPTY;
        }
        ItemStack held = player.getItemInHand(entryHand);
        if (held.getItem() instanceof TeleportStoneItem) {
            return held;
        }
        InteractionHand other = entryHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack alternative = player.getItemInHand(other);
        return alternative.getItem() instanceof TeleportStoneItem ? alternative : ItemStack.EMPTY;
    }

    /**
     * 找到本次列表所对应的那组传送卷轴。
     * <p>
     * 与 {@link #findStoneStack} 同模式：优先取 {@link #entryHand} 那只手的卷轴，再看另一只手。
     *
     * @return 对应的传送卷轴；两只手都没有时返回 {@link ItemStack#EMPTY}
     */
    private ItemStack findScrollStack(net.minecraft.client.player.LocalPlayer player) {
        if (entryHand == null) {
            return ItemStack.EMPTY;
        }
        ItemStack held = player.getItemInHand(entryHand);
        if (held.getItem() instanceof WarpScrollItem) {
            return held;
        }
        InteractionHand other = entryHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack alternative = player.getItemInHand(other);
        return alternative.getItem() instanceof WarpScrollItem ? alternative : ItemStack.EMPTY;
    }

    /** 物品剩余耐久点数。 */
    private static int remainingDurability(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    /** 消耗信息区域预留的高度：无锚点时不预留，否则按入口类型分配行数。 */
    private int costInfoHeight() {
        if (points.isEmpty()) {
            return 0;
        }
        int lines;
        switch (entryType) {
            case STONE -> lines = 2;  // 经验 + 耐久
            case SCROLL -> lines = 1; // 仅卷轴消耗（不扣经验）
            default -> lines = 1;     // ANCHOR：仅经验
        }
        return COST_INFO_TOP_GAP + lines * COST_LINE_HEIGHT;
    }

    /**
     * 在底部按钮下方绘制本次传送的消耗信息。
     * <p>
     * 按 {@link #entryType} 分类绘制：
     * <ul>
     *   <li>{@link EntryType#ANCHOR}：仅经验等级</li>
     *   <li>{@link EntryType#STONE}：经验等级 + 传送石耐久（图嵌数字前）</li>
     *   <li>{@link EntryType#SCROLL}：卷轴消耗（图嵌数字前），不显示经验等级</li>
     * </ul>
     * 任一项不足时该行转为橙红色并追加「不足」标注，与置灰的传送按钮相互印证。
     * 创造模式三项都免除，因此不绘制任何消耗信息。
     */
    private void drawCostInfo(GuiGraphicsExtractor guiGraphics) {
        if (confirmingDelete || renameMode) {
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= points.size()) {
            return;
        }
        TeleportAnchorData point = points.get(selectedIndex);
        if (isCurrentAnchor(point)) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null || player.getAbilities().instabuild) {
            return;
        }

        var font = Minecraft.getInstance().font;
        int x = panelX + PANEL_PADDING;
        int y = actionsY + BUTTON_HEIGHT + COST_INFO_TOP_GAP;

        // 第一行：经验等级消耗（卷轴入口跳过——一次性成本已由卷轴承担）
        if (entryType != EntryType.SCROLL) {
            int xpCost = xpCostFor(point);
            boolean enoughXp = player.experienceLevel >= xpCost;
            String xpText = Component.translatable(
                    "screen.youzaiworldcore.teleport_anchor.cost_xp", xpCost).getString();
            if (!enoughXp) {
                xpText += Component.translatable(
                        "screen.youzaiworldcore.teleport_anchor.cost_insufficient_xp").getString();
            }
            guiGraphics.text(font, xpText, x, y,
                    enoughXp ? costTextColor() : costInsufficientColor(), false);
            y += COST_LINE_HEIGHT;
        }

        // 第二行：入口专属消耗（耐久 / 卷轴），分别绘图标 + 数字
        if (entryType == EntryType.STONE) {
            ItemStack stone = findStoneStack(player);
            int durabilityCost = TeleportStoneItem.computeDurabilityCost(player, point);
            boolean enoughDurability = !stone.isEmpty() && remainingDurability(stone) >= durabilityCost;
            int color = enoughDurability ? costTextColor() : costInsufficientColor();

            String prefix = Component.translatable(
                    "screen.youzaiworldcore.teleport_anchor.cost_durability_prefix").getString();
            String suffix = Component.translatable(
                    "screen.youzaiworldcore.teleport_anchor.cost_durability_suffix", durabilityCost).getString();
            if (!enoughDurability) {
                suffix += Component.translatable(
                        "screen.youzaiworldcore.teleport_anchor.cost_insufficient_durability").getString();
            }

            int cursorX = x;
            guiGraphics.text(font, prefix, cursorX, y, color, false);
            cursorX += font.width(prefix);
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, STONE_ICON,
                    cursorX, y + (font.lineHeight - STONE_ICON_SIZE) / 2 - 1,
                    0, 0, STONE_ICON_SIZE, STONE_ICON_SIZE, STONE_ICON_SIZE, STONE_ICON_SIZE);
            cursorX += STONE_ICON_SIZE;
            guiGraphics.text(font, suffix, cursorX, y, color, false);
        } else if (entryType == EntryType.SCROLL) {
            ItemStack scroll = findScrollStack(player);
            boolean enoughScroll = WarpScrollItem.canAffordScroll(player, scroll);
            int color = enoughScroll ? costTextColor() : costInsufficientColor();

            String prefix = Component.translatable(
                    "screen.youzaiworldcore.teleport_anchor.cost_scroll_prefix").getString();
            String suffix = Component.translatable(
                    "screen.youzaiworldcore.teleport_anchor.cost_scroll_suffix").getString();
            if (!enoughScroll) {
                suffix += Component.translatable(
                        "screen.youzaiworldcore.teleport_anchor.cost_insufficient_scroll").getString();
            }

            int cursorX = x;
            guiGraphics.text(font, prefix, cursorX, y, color, false);
            cursorX += font.width(prefix);
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SCROLL_ICON,
                    cursorX, y + (font.lineHeight - SCROLL_ICON_SIZE) / 2 - 1,
                    0, 0, SCROLL_ICON_SIZE, SCROLL_ICON_SIZE, SCROLL_ICON_SIZE, SCROLL_ICON_SIZE);
            cursorX += SCROLL_ICON_SIZE;
            guiGraphics.text(font, suffix, cursorX, y, color, false);
        }
    }

    /**
     * 传送到指定锚点需要消耗的经验等级：同维度 1 级，跨维度 2 级。
     * <p>
     * 与服务端 {@code ModNetworking} 中 {@code TeleportAnchorTeleportPayload} 处理器的口径一致，
     * 两边同时改动才不会出现「按钮能点但服务端拒绝」的偏差。
     * <p>
     * 卷轴入口完全免经验——该方法仍保持原口径，但调用方会跳过使用。
     */
    private static int xpCostFor(TeleportAnchorData point) {
        var player = Minecraft.getInstance().player;
        if (player != null && point.dimension().equals(player.level().dimension())) {
            return XP_COST_SAME_DIMENSION;
        }
        return XP_COST_CROSS_DIMENSION;
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
        int previousOffset = scrollOffset;
        ensureVisible(newIndex);
        if (scrollOffset != previousOffset) rebuildWidgets();
    }

    private void buildDeleteConfirmUI() {
        int totalBtnWidth = BUTTON_WIDTH * 2 + 8;
        int startX = panelX + (panelWidth - totalBtnWidth) / 2;

        TransparentButton confirmBtn = new TransparentButton(
                startX, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_confirm"),
                this::confirmDeletePoint);
        confirmBtn.setStyle(confirmingDelete ? YzuiTheme.ButtonStyle.DANGER : YzuiTheme.ButtonStyle.FILLED);
        renameConfirmButtons.add(confirmBtn);
        addRenderableWidget(confirmBtn);

        TransparentButton cancelBtn = new TransparentButton(
                startX + BUTTON_WIDTH + 8, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_cancel"),
                () -> {
                    confirmingDelete = false;
                    rebuildWidgets();
                });
        cancelBtn.setTextColor(buttonTextColor());
        renameConfirmButtons.add(cancelBtn);
        addRenderableWidget(cancelBtn);
        setFocused(cancelBtn);
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
        int editBoxY = listBottomY + PANEL_PADDING;
        int editBoxWidth = panelWidth - PANEL_PADDING * 2;
        int editBoxX = panelX + PANEL_PADDING;

        renameEditBox = new EditBox(this.font, editBoxX, editBoxY, editBoxWidth, 24,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_hint"));
        renameEditBox.setValue(renameDraft == null ? point.name() : renameDraft);
        renameEditBox.setMaxLength(128);
        addRenderableWidget(renameEditBox);
        setFocused(renameEditBox);

        int btnY = editBoxY + 32;
        int confirmX = panelX + (panelWidth / 2) - BUTTON_WIDTH - 4;
        int cancelX = panelX + (panelWidth / 2) + 4;

        TransparentButton confirmBtn = new TransparentButton(
                confirmX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_confirm"),
                this::confirmRename);
        confirmBtn.setStyle(confirmingDelete ? YzuiTheme.ButtonStyle.DANGER : YzuiTheme.ButtonStyle.FILLED);
        renameConfirmButtons.add(confirmBtn);
        addRenderableWidget(confirmBtn);

        TransparentButton cancelBtn = new TransparentButton(
                cancelX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_cancel"),
                this::cancelRename);
        cancelBtn.setTextColor(buttonTextColor());
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
        if (renameMode || confirmingDelete) return true;
        if (mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= listTopY && mouseY < listBottomY
                && displayPointCount > visibleRows && scrollY != 0) {
            int next = Math.clamp(scrollOffset + (scrollY < 0 ? 1 : -1), 0, displayPointCount - visibleRows);
            if (next != scrollOffset) {
                scrollOffset = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String formatPointLabel(TeleportAnchorData point) {
        return point.name();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        editModeProgress = !GuiAnimationController.isEnabled() ? (editMode ? 1f : 0f)
                : Math.min(1f, editModeProgress + (YzuiTheme.frosted() ? 0.08f : 0.18f));
        if (!confirmingDelete && !renameMode) refreshTeleportAffordability();
        if (editMode) {
            for (TextureIconButton button : new TextureIconButton[] {renameButton, removeButton, moveUpButton, moveDownButton}) {
                if (button != null) button.setAlpha(editModeProgress);
            }
        }
        YzuiTheme.card(guiGraphics, panelX, panelY, panelWidth, panelHeight);
        YzuiTheme.label(guiGraphics, font, getTitle(), panelX + PANEL_PADDING, panelY + 16,
                panelWidth - PANEL_PADDING * 2 - CLOSE_BUTTON_SIZE - 8, YzuiTheme.text(), false);
        if (confirmingDelete) {
            YzuiTheme.wrapped(guiGraphics, font, Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_hint"),
                    panelX + PANEL_PADDING, listBottomY + PANEL_PADDING, panelWidth - PANEL_PADDING * 2, 2, YzuiTheme.error());
        }
        guiGraphics.enableScissor(panelX + PANEL_PADDING, actionsY + BUTTON_HEIGHT,
                panelX + panelWidth - PANEL_PADDING, panelY + panelHeight);
        drawCostInfo(guiGraphics);
        guiGraphics.disableScissor();
        if (displayPointCount > visibleRows) {
            int x = panelX + panelWidth - 8, trackHeight = listBottomY - listTopY;
            int thumbHeight = Math.max(12, trackHeight * visibleRows / displayPointCount);
            int thumbY = listTopY + (trackHeight - thumbHeight) * scrollOffset / (displayPointCount - visibleRows);
            guiGraphics.fill(x, listTopY, x + 3, listBottomY, YzuiTheme.surfaceHigh());
            guiGraphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, YzuiTheme.outline());
        }
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (showEmptyHint) {
            Component hint = Component.translatable(points.isEmpty()
                    ? "screen.youzaiworldcore.teleport_anchor.empty"
                    : "screen.youzaiworldcore.teleport_anchor.no_results");
            YzuiTheme.wrapped(guiGraphics, font, hint, panelX + PANEL_PADDING, listTopY + 6,
                    panelWidth - PANEL_PADDING * 2, 2, emptyHintColor());
        }
        drawCurrentAnchorIcons(guiGraphics);
        drawEntryDistances(guiGraphics);
    }

    /**
     * 在每个可见条目的右侧绘制该传送锚点距离起点的直线距离。
     * <p>
     * 起点取决于列表是怎么打开的：右键传送锚点方块打开时取<b>该锚点方块</b>（静态，数值不随走动变化），
     * 用传送石或卷轴打开时取<b>玩家当前位置</b>（动态，每帧重算，玩家移动时数字实时变化）——
     * 后者也正是服务端计算传送石耐久消耗时用的口径（卷轴不依赖距离，沿用同一规则即可）。
     * 不同维度之间距离没有可比性，显示「跨维度」。
     */
    private void drawEntryDistances(GuiGraphicsExtractor guiGraphics) {
        var font = Minecraft.getInstance().font;

        for (int i = 0; i < pointButtons.size(); i++) {
            int pointIndex = scrollOffset + i;
            if (pointIndex >= displayedPoints.size()) break;
            TeleportAnchorData point = displayedPoints.get(pointIndex);

            String text = formatDistance(point);
            TransparentButton btn = pointButtons.get(i);
            int textX = btn.getX() + btn.getWidth() - DISTANCE_PADDING - font.width(text);
            int textY = btn.getY() + (ITEM_HEIGHT - font.lineHeight) / 2;
            YzuiTheme.label(guiGraphics, font, Component.literal(text), Math.max(btn.getX() + btn.getWidth() * 2 / 3, textX),
                    textY, Math.min(btn.getWidth() / 3 - DISTANCE_PADDING, font.width(text)), distanceColor(), false);
        }
    }

    /**
     * 格式化单个传送点到起点的距离文本。
     * <p>
     * 1 格方块 = 1 m，取欧氏距离；起点与目标都按锚点落点（方块上表面中心）取值，
     * 玩家起点则直接用玩家坐标。跨维度时距离没有可比性，显示「跨维度」。
     */
    private String formatDistance(TeleportAnchorData point) {
        ResourceKey<Level> originDim;
        double originX;
        double originY;
        double originZ;

        if (currentAnchorPos != null && currentAnchorDim != null) {
            // 传送锚点方块入口：以该锚点为静态起点
            originDim = currentAnchorDim;
            originX = currentAnchorPos.getX() + 0.5;
            originY = currentAnchorPos.getY() + 1.0;
            originZ = currentAnchorPos.getZ() + 0.5;
        } else {
            // 传送石 / 卷轴入口：以玩家当前位置为动态起点
            var player = Minecraft.getInstance().player;
            if (player == null) return "";
            originDim = player.level().dimension();
            originX = player.getX();
            originY = player.getY();
            originZ = player.getZ();
        }

        if (!point.dimension().equals(originDim)) {
            return Component.translatable(
                    "screen.youzaiworldcore.teleport_anchor.cross_dimension").getString();
        }

        BlockPos pos = point.pos();
        double dx = pos.getX() + 0.5 - originX;
        double dy = pos.getY() + 1.0 - originY;
        double dz = pos.getZ() + 0.5 - originZ;
        return formatMetres(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * 把米数格式化成带单位的距离文本：满 1000 就进一级，m → km → Mm → Gm，Gm 之后不再进位。
     * <p>
     * 无论换算到哪一级都固定保留 1 位小数；进位判断按四舍五入到 1 位小数后的值来做，
     * 避免出现 {@code 1000.0m} 这种本该进位却没进位的显示。
     */
    private static String formatMetres(double metres) {
        double value = metres;
        int unit = 0;
        while (unit < DISTANCE_UNIT_KEYS.length - 1 && roundToTenth(value) >= 1000.0) {
            value /= 1000.0;
            unit++;
        }
        String number = String.format(Locale.ROOT, "%.1f", value);
        return Component.translatable(DISTANCE_UNIT_KEYS[unit], number).getString();
    }

    /** 四舍五入到 1 位小数。 */
    private static double roundToTenth(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** 在当前正在打开的传送锚点条目左侧绘制定位图标。 */
    private void drawCurrentAnchorIcons(GuiGraphicsExtractor guiGraphics) {
        for (int i = 0; i < pointButtons.size(); i++) {
            int pointIndex = scrollOffset + i;
            if (pointIndex >= displayedPoints.size()) break;
            TeleportAnchorData point = displayedPoints.get(pointIndex);
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
        // 编辑表单与删除确认优先取消，避免直接关闭整个页面。
        if (keyEvent.key() == 256) {
            if (renameMode) { cancelRename(); return true; }
            if (confirmingDelete) { confirmingDelete = false; rebuildWidgets(); return true; }
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
        // 背景由共用屏幕入口在内容变换之前绘制，避免重复模糊与叠加遮罩。
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 贴图图标按钮：内部用 blit 渲染一张纹理，可选叠加文字。
     * 继承 TransparentButton 以复用主题按钮、悬停与禁用状态。
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
            this.setStyle(YzuiTheme.ButtonStyle.TONAL);
            if (tooltip != null) {
                this.setTooltip(Tooltip.create(tooltip));
            }
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 先绘制主题容器，再用主题文字色着色图标。
            super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
            // 叠加贴图
            if (texture == null) return;
            float vis = Math.min(1f, Math.max(0f, this.getAlpha()));
            if (vis < 0.001f) return;
            int x = this.getX();
            int y = this.getY();
            int size = Math.min(20, Math.min(getWidth(), getHeight()) - 6);
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x + (getWidth() - size) / 2, y + (getHeight() - size) / 2,
                    0, 0, size, size, size, size,
                    YzuiTheme.alpha(active ? YzuiTheme.onPrimaryContainer() : YzuiTheme.textMuted(), vis * (active ? 1f : 0.55f)));
        }
    }
}
