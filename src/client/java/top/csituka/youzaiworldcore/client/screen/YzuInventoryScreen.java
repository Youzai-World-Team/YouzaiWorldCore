package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YZUI 生存模式物品栏屏幕。
 * <p>
 * 替代原版 InventoryScreen，YZUI 开启时使用白底圆角风格渲染面板与槽位，
 * 关闭时回退原版。保留 AbstractRecipeBookScreen 的配方书交互、容器拖拽
 * 与合成等全部原版状态与逻辑，仅替换渲染层。
 * <p>
 * 槽位布局同原版（InventoryMenu 固定坐标），仅外观变更为圆角矩形。
 */
public class YzuInventoryScreen extends AbstractRecipeBookScreen<InventoryMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuInventoryScreen");

    // ========== YZUI 配色常量 ==========
    private static final int PANEL_BG         = 0x80FFFFFF;
    private static final int SLOT_COLOR       = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x60FFFFFF;
    private static final int CRAFT_RESULT_BG  = 0x60FFFFFF;

    private static final int PANEL_RADIUS = 6;
    private static final int SLOT_RADIUS  = 3;
    private static final int SLOT_SIZE    = 16;

    /** 玩家模型渲染区域（相对 leftPos/topPos） */
    private static final int ENTITY_X = 26;
    private static final int ENTITY_Y = 8;
    private static final int ENTITY_W = 75;
    private static final int ENTITY_H = 78;
    private static final int ENTITY_SIZE = 30;
    private static final float ENTITY_Y_OFFSET = 0.0625f;

    private float xMouse;
    private float yMouse;

    // ========== 构造 ==========

    public YzuInventoryScreen(Player player) {
        super(
                player.inventoryMenu,
                new CraftingRecipeBookComponent(player.inventoryMenu),
                player.getInventory(),
                Component.translatable("container.crafting")
        );
        LOGGER.debug("YzuInventoryScreen created for player: {}", player.getName().getString());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        // 必须在 super.init() 之前设置 leftPos/topPos，因为 super.init()
        // 会调用 initButton() → getRecipeBookButtonPosition()，需要正确的坐标。
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuInventoryScreen.init() — leftPos={} topPos={} imageWidth={} imageHeight={}",
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    // ========== 渲染管线（覆盖各个阶段，由 super.extractRenderState 统一调度） ==========

    /**
     * 面板背景 + 玩家模型。
     * 由 AbstractContainerScreen.extractRenderState → this.extractBackground() 调用。
     */
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1) YZUI 半透明圆角面板
        fillRoundedRect(guiGraphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                PANEL_RADIUS, PANEL_BG);

        // 2) 玩家模型（使用原版静态方法，鼠标跟随旋转）
        var player = this.minecraft.player;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    guiGraphics,
                    this.leftPos + ENTITY_X,
                    this.topPos  + ENTITY_Y,
                    this.leftPos + ENTITY_W,
                    this.topPos  + ENTITY_H,
                    ENTITY_SIZE,
                    ENTITY_Y_OFFSET,
                    this.xMouse,
                    this.yMouse,
                    player
            );
        }
    }

    /**
     * 由 super.extractRenderState 核心流程调用。我们在这里插入 YZUI 槽位背景绘制，
     * 然后委托 super 渲染物品图标（含 quickCraft 动画、悬浮高亮等）。
     */
    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // 1) YZUI 槽位背景
        drawSlotBackgrounds(guiGraphics, mouseX, mouseY);

        // 2) 物品渲染（super 会调用 extractSlot 逐个绘制）
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    /**
     * 槽位物品渲染 — 不覆盖，使用原版全部逻辑（含 quick craft 数量分配）。
     * 空槽位的 noItemIcon 精灵虽会显示（YZUI 背景之上），但优先保证 quick craft 功能正常。
     * 如需完全隐藏 noItemIcon，后续可单独用 @Redirect Mixin 处理 {@code getNoItemIcon()} 调用。
     */
    // extractSlot 不覆盖 — 完全使用 AbstractContainerScreen.extractSlot

    /**
     * 记录鼠标位置供模型跟随，其余委托 super。
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        this.xMouse = (float) mouseX;
        this.yMouse = (float) mouseY;
    }

    // ========== 槽位背景绘制 ==========

    private void drawSlotBackgrounds(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // 注意：extractSlots 在 extractContents 内被调用，
        // 此时 Graphics 上下文已由 pose().translate(leftPos, topPos) 平移，
        // 因此 slot.x / slot.y 是容器相对坐标，直接使用即可。
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) continue;

            int slotX = slot.x;
            int slotY = slot.y;

            boolean isHovered = mouseX >= slotX + this.leftPos && mouseX < slotX + this.leftPos + SLOT_SIZE
                    && mouseY >= slotY + this.topPos && mouseY < slotY + this.topPos + SLOT_SIZE;
            int color = isHovered ? SLOT_HOVER_COLOR : SLOT_COLOR;

            fillRoundedRect(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, color);
        }

        // 合成输出槽额外高亮
        if (!this.menu.slots.isEmpty()) {
            Slot resultSlot = this.menu.slots.get(0);
            if (resultSlot.isActive()) {
                fillRoundedRect(guiGraphics,
                        resultSlot.x,
                        resultSlot.y,
                        SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, CRAFT_RESULT_BG);
            }
        }
    }

    // ========== 配方书 ==========

    @Override
    protected @NonNull ScreenPosition getRecipeBookButtonPosition() {
        // 配方书按钮：容器右上角，与原本位置一致 (leftPos + 104, height/2 - 22)
        return new ScreenPosition(this.leftPos + 104, this.height / 2 - 22);
    }

    @Override
    protected void onRecipeBookButtonClick() {
        LOGGER.debug("Recipe book button toggled");
        super.onRecipeBookButtonClick();
    }

    // ========== 标签（纯 YZUI 不显示 vanilla 文字） ==========

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op
    }

    // ========== 杂项 ==========

    @Override
    public boolean showsActiveEffects() {
        return false;
    }

    @Override
    protected boolean isBiggerResultSlot() {
        return false;
    }

    // ========== 工具方法 ==========

    private static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }
}
