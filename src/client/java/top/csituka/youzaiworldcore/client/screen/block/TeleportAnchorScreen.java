package top.csituka.youzaiworldcore.client.screen.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.network.TeleportAnchorDeletePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorRenamePayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorTeleportPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/**
 * 传送锚点选择界面。
 * <p>
 * 点击传送点条目将其选中，下方出现「传送」「重命名」「删除」三个功能按钮。
 * 支持选中高亮、重命名输入框、仅从当前玩家列表删除。
 */
public class TeleportAnchorScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_PADDING = 12;
    private static final int ITEM_HEIGHT = 22;
    private static final int ITEM_GAP = 4;
    private static final int TITLE_HEIGHT = 30;
    private static final int ACTIONS_Y_OFFSET = 10;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    private static final int SELECTED_HIGHLIGHT_COLOR = 0x40FFFFFF;

    private final List<TeleportAnchorData> points;
    private int selectedIndex = -1;
    private boolean renameMode = false;
    private boolean confirmingDelete = false;
    private EditBox renameEditBox;

    private int panelX;
    private int panelY;
    private int listBottomY;

    public TeleportAnchorScreen(List<TeleportAnchorData> points) {
        super(Component.translatable("screen.youzaiworldcore.teleport_anchor.title"));
        this.points = points;
    }

    @Override
    protected void init() {
        super.init();

        int listHeight = Math.max(0, points.size() * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP);
        int actionsHeight = (confirmingDelete || !renameMode) ? BUTTON_HEIGHT + ACTIONS_Y_OFFSET : 0;
        int renameHeight = renameMode ? 40 : 0;
        int totalHeight = TITLE_HEIGHT + PANEL_PADDING + listHeight + PANEL_PADDING
                + Math.max(actionsHeight, renameHeight);

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - totalHeight) / 2;

        int buttonY = panelY + TITLE_HEIGHT + PANEL_PADDING;
        for (int i = 0; i < points.size(); i++) {
            int index = i;
            TeleportAnchorData point = points.get(i);

            String label = formatPointLabel(point);
            boolean isSelected = (index == selectedIndex);
            Button btn = Button.builder(Component.literal(label),
                    b -> { if (!isSelected) selectPoint(index); })
                    .bounds(panelX + PANEL_PADDING, buttonY,
                            PANEL_WIDTH - PANEL_PADDING * 2, ITEM_HEIGHT)
                    .build();
            btn.active = !isSelected;
            addRenderableWidget(btn);
            buttonY += ITEM_HEIGHT + ITEM_GAP;
        }
        listBottomY = buttonY - ITEM_GAP;

        // 功能按钮或删除确认或重命名模式
            buildDeleteConfirmUI();
        } else if (!renameMode) {
            buildActionButtons();
        } else {
            buildRenameUI();
        }
    }

    private void buildActionButtons() {
        int actionsY = listBottomY + PANEL_PADDING + ACTIONS_Y_OFFSET;
        int totalBtnWidth = BUTTON_WIDTH * 3 + 8 * 2;
        int startX = panelX + (PANEL_WIDTH - totalBtnWidth) / 2;
        boolean hasSelection = selectedIndex >= 0;

        Button teleportBtn = Button.builder(
                Component.translatable("screen.youzaiworldcore.teleport_anchor.teleport"),
                btn -> {
                    ClientPlayNetworking.send(new TeleportAnchorTeleportPayload(selectedIndex));
                    Minecraft.getInstance().setScreenAndShow(null);
                })
                .bounds(startX, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        teleportBtn.active = hasSelection;
        addRenderableWidget(teleportBtn);

        Button renameBtn = Button.builder(
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename"),
                btn -> {
                    renameMode = true;
                    rebuildWidgets();
                })
                .bounds(startX + BUTTON_WIDTH + 8, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        renameBtn.active = hasSelection;
        addRenderableWidget(renameBtn);

        Button deleteBtn = Button.builder(
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete"),
                btn -> {
                    confirmingDelete = true;
                    rebuildWidgets();
                })
                .bounds(startX + (BUTTON_WIDTH + 8) * 2, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        deleteBtn.active = hasSelection;
        addRenderableWidget(deleteBtn);
    }

    private void buildDeleteConfirmUI() {
        int actionsY = listBottomY + PANEL_PADDING + ACTIONS_Y_OFFSET;
        int totalBtnWidth = BUTTON_WIDTH * 2 + 8;
        int startX = panelX + (PANEL_WIDTH - totalBtnWidth) / 2;

        // 确认按钮
        Button confirmBtn = Button.builder(
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_confirm"),
                btn -> confirmDeletePoint())
                .bounds(startX, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(confirmBtn);

        // 取消按钮
        Button cancelBtn = Button.builder(
                Component.translatable("screen.youzaiworldcore.teleport_anchor.delete_cancel"),
                btn -> {
                    confirmingDelete = false;
                    rebuildWidgets();
                })
                .bounds(startX + BUTTON_WIDTH + 8, actionsY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(cancelBtn);
    }

    private void confirmDeletePoint() {
        ClientPlayNetworking.send(new TeleportAnchorDeletePayload(selectedIndex));
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

        addRenderableWidget(Button.builder(
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_confirm"),
                btn -> confirmRename())
                .bounds(confirmX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.youzaiworldcore.teleport_anchor.rename_cancel"),
                btn -> cancelRename())
                .bounds(cancelX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void confirmRename() {
        if (renameEditBox != null && selectedIndex >= 0 && selectedIndex < points.size()) {
            String newName = renameEditBox.getValue().trim();
            if (!newName.isEmpty()) {
                TeleportAnchorData old = points.get(selectedIndex);
                points.set(selectedIndex, new TeleportAnchorData(old.pos(), old.dimension(), newName));
                ClientPlayNetworking.send(new TeleportAnchorRenamePayload(selectedIndex, newName));
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
        rebuildWidgets();
    }

    private static String formatPointLabel(TeleportAnchorData point) {
        String dimDisplay = switch (point.dimension().identifier().getPath()) {
            case "overworld" -> "主世界";
            case "the_nether" -> "下界";
            case "the_end" -> "末地";
            default -> point.dimension().identifier().getPath();
        };
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

        // 选中高亮：在选中按钮位置绘制半透明矩形
        if (selectedIndex >= 0 && selectedIndex < points.size()) {
            int highlightY = panelY + TITLE_HEIGHT + PANEL_PADDING
                    + selectedIndex * (ITEM_HEIGHT + ITEM_GAP);
            guiGraphics.fill(panelX + PANEL_PADDING, highlightY,
                    panelX + PANEL_WIDTH - PANEL_PADDING, highlightY + ITEM_HEIGHT,
                    SELECTED_HIGHLIGHT_COLOR);
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

        // 渲染子组件（按钮和输入框）
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
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
