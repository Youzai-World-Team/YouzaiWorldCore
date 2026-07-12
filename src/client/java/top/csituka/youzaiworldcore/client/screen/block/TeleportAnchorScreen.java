package top.csituka.youzaiworldcore.client.screen.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.network.TeleportAnchorDeletePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorRenamePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorTeleportPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * 传送锚点选择界面。
 * <p>
 * 使用 TransparentButton 实现半透明白底圆角按钮，与 LoginScreen 风格统一。
 * 底部常驻显示「传送」按钮；「重命名」通过 Shift+左键、「删除」通过 Ctrl+左键触发。
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

    /** 列表区域最多同时显示的条目数，超出时启用滚动。 */
    private static final int MAX_VISIBLE_ITEMS = 8;

    /** 当前锚点定位图标的尺寸（与条目高度对齐）。 */
    private static final int LOCATION_ICON_SIZE = 14;
    private static final int LOCATION_ICON_GAP = 6;

    private static final int HIGHLIGHT_BG = 0x40FFFFFF;
    private static final int BUTTON_TEXT_COLOR = 0xFFFFFF;

    private static final Identifier LOCATION_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/teleport_location.png");

    private final List<TeleportAnchorData> points;
    @Nullable
    private final BlockPos currentAnchorPos;
    @Nullable
    private final ResourceKey<Level> currentAnchorDim;

    private int selectedIndex = -1;
    private boolean renameMode = false;
    private boolean confirmingDelete = false;

    /** 滚动偏移（列表顶部显示的第几个条目）。 */
    private int scrollOffset = 0;

    // UI 组件
    private final List<TransparentButton> pointButtons = new ArrayList<>();
    private final List<TransparentButton> actionButtons = new ArrayList<>();
    private EditBox renameEditBox;

    private int panelX;
    private int panelY;
    private int listBottomY;

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
        this.actionButtons.clear();
        this.renameEditBox = null;

        // 限制滚动偏移在有效范围内
        int maxScroll = Math.max(0, points.size() - MAX_VISIBLE_ITEMS);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        int visibleCount = Math.min(points.size(), MAX_VISIBLE_ITEMS);
        int listHeight = Math.max(0, visibleCount * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP);

        // 底部区域高度：始终预留"传送"按钮的常驻高度
        int actionsHeight;
        if (confirmingDelete) {
            actionsHeight = BUTTON_HEIGHT + ACTIONS_Y_OFFSET;
        } else if (renameMode) {
            actionsHeight = 40;
        } else {
            // 常驻显示传送按钮
            actionsHeight = BUTTON_HEIGHT + ACTIONS_Y_OFFSET;
        }

        int totalHeight = TITLE_HEIGHT + PANEL_PADDING + listHeight + PANEL_PADDING + actionsHeight;

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - totalHeight) / 2;

        // 构建列表条目按钮
        int buttonY = panelY + TITLE_HEIGHT + PANEL_PADDING;
        for (int i = 0; i < visibleCount; i++) {
            int pointIndex = scrollOffset + i;
            TeleportAnchorData point = points.get(pointIndex);
            boolean isSelected = (pointIndex == selectedIndex);
            boolean isCurrentAnchor = isCurrentAnchor(point);

            String label = formatPointLabel(point, isCurrentAnchor);
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
            // 常驻显示传送按钮
            buildTeleportButton();
        }
    }

    @Override
    public void rebuildWidgets() {
        this.pointButtons.clear();
        this.actionButtons.clear();
        this.renameEditBox = null;
        super.rebuildWidgets();
    }

    /** 判断指定传送点是否是当前打开的锚点。 */
    private boolean isCurrentAnchor(TeleportAnchorData point) {
        return currentAnchorPos != null && currentAnchorDim != null
                && point.pos().equals(currentAnchorPos)
                && point.dimension().equals(currentAnchorDim);
    }

    private void buildTeleportButton() {
        int actionsY = listBottomY + PANEL_PADDING + ACTIONS_Y_OFFSET;
        int startX = panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < points.size();
        boolean isCurrentAnchor = hasSelection && isCurrentAnchor(points.get(selectedIndex));

        TransparentButton teleportBtn = new TransparentButton(
                startX, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.teleport"),
                () -> {
                    TeleportAnchorData point = points.get(selectedIndex);
                    ClientPlayNetworking.send(new TeleportAnchorTeleportPayload(point.pos(), point.dimension()));
                    Minecraft.getInstance().setScreenAndShow(null);
                });
        teleportBtn.setTextColor(BUTTON_TEXT_COLOR);
        teleportBtn.active = hasSelection && !isCurrentAnchor;
        if (!teleportBtn.active) teleportBtn.setExternalAlpha(0.3f);
        actionButtons.add(teleportBtn);
        addRenderableWidget(teleportBtn);
    }

    private void buildDeleteConfirmUI() {
        int actionsY = listBottomY + PANEL_PADDING + ACTIONS_Y_OFFSET;
        int totalBtnWidth = BUTTON_WIDTH * 2 + 8;
        int startX = panelX + (PANEL_WIDTH - totalBtnWidth) / 2;

        TransparentButton confirmBtn = new TransparentButton(
                startX, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_confirm"),
                this::confirmDeletePoint);
        confirmBtn.setTextColor(BUTTON_TEXT_COLOR);
        actionButtons.add(confirmBtn);
        addRenderableWidget(confirmBtn);

        TransparentButton cancelBtn = new TransparentButton(
                startX + BUTTON_WIDTH + 8, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_cancel"),
                () -> {
                    confirmingDelete = false;
                    rebuildWidgets();
                });
        cancelBtn.setTextColor(BUTTON_TEXT_COLOR);
        actionButtons.add(cancelBtn);
        addRenderableWidget(cancelBtn);
    }

    private void confirmDeletePoint() {
        TeleportAnchorData point = points.get(selectedIndex);
        ClientPlayNetworking.send(new TeleportAnchorDeletePayload(point.pos(), point.dimension()));
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
        actionButtons.add(confirmBtn);
        addRenderableWidget(confirmBtn);

        TransparentButton cancelBtn = new TransparentButton(
                cancelX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_cancel"),
                this::cancelRename);
        cancelBtn.setTextColor(BUTTON_TEXT_COLOR);
        actionButtons.add(cancelBtn);
        addRenderableWidget(cancelBtn);
    }

    private void confirmRename() {
        if (renameEditBox != null && selectedIndex >= 0 && selectedIndex < points.size()) {
            String newName = renameEditBox.getValue().trim();
            if (!newName.isEmpty()) {
                TeleportAnchorData old = points.get(selectedIndex);
                points.set(selectedIndex, new TeleportAnchorData(old.pos(), old.dimension(), newName));
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

    private void selectPoint(int index) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (points.size() > MAX_VISIBLE_ITEMS) {
            int maxScroll = points.size() - MAX_VISIBLE_ITEMS;
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

    private static String formatPointLabel(TeleportAnchorData point, boolean isCurrentAnchor) {
        String dimDisplay = switch (point.dimension().identifier().getPath()) {
            case "overworld" -> "主世界";
            case "the_nether" -> "下界";
            case "the_end" -> "末地";
            default -> point.dimension().identifier().getPath();
        };
        // 当前锚点保留空 prefix（图标在渲染时单独绘制）；普通锚点保持纯文本
        return point.name() + " (" + dimDisplay + " @ "
                + point.pos().getX() + ", " + point.pos().getY() + ", " + point.pos().getZ() + ")";
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
        if (points.size() > MAX_VISIBLE_ITEMS) {
            int scrollbarX = panelX + PANEL_WIDTH - 4;
            int scrollbarY = panelY + TITLE_HEIGHT + PANEL_PADDING;
            int scrollbarHeight = MAX_VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP;
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollbarHeight, 0x40FFFFFF);

            int maxScroll = points.size() - MAX_VISIBLE_ITEMS;
            if (maxScroll > 0) {
                int thumbHeight = Math.max(10, scrollbarHeight * MAX_VISIBLE_ITEMS / points.size());
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
        // R 键：重命名选中项
        if (keyEvent.key() == 82 && selectedIndex >= 0 && selectedIndex < points.size() && !renameMode && !confirmingDelete) {
            renameMode = true;
            rebuildWidgets();
            return true;
        }
        // Delete 键：删除选中项（弹出确认）
        if (keyEvent.key() == 261 && selectedIndex >= 0 && selectedIndex < points.size() && !renameMode && !confirmingDelete) {
            confirmingDelete = true;
            rebuildWidgets();
            return true;
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
}
