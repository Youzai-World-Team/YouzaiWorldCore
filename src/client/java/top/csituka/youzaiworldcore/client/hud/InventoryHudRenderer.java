package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;

/**
 * YZUI 物品栏 HUD 渲染器。
 *
 * <p>
 * 在屏幕左下角显示 9×3（共 27 个）物品栏槽位，实时绑定玩家背包
 * （槽位索引 9–35），风格与 YZUI 热键栏保持一致：半透明白色圆角面板、
 * 圆角槽位背景、物品图标及数量。
 * </p>
 *
 * <p>
 * 聊天 Z 序：本 HUD 在聊天组件之前渲染，聊天文字自然覆盖在 HUD 之上。
 * </p>
 */
@SuppressWarnings("null")
public final class InventoryHudRenderer {

    // ===== 槽位常量 =====
    /** 单个槽位尺寸 */
    private static final int SLOT_SIZE = 18;
    /** 槽位圆角半径 */
    private static final int SLOT_RADIUS = 3;
    /** 槽位中心间距（对齐原版 20px 间隔） */
    private static final int SLOT_SPACING = 20;
    /** 空槽位背景色（25% 白色） */
    private static final int SLOT_EMPTY_COLOR = 0x40FFFFFF;
    /** 有物品槽位背景色（35% 白色，略亮于空槽位） */
    private static final int SLOT_FILLED_COLOR = 0x5AFFFFFF;
    /** 物品在槽位内的偏移（将 16×16 物品居中于 18×18 槽位） */
    private static final int ITEM_INSET = 1;

    // ===== 网格常量 =====
    /** 列数 */
    private static final int COLS = 9;
    /** 行数 */
    private static final int ROWS = 3;
    /** 背包起始槽位索引（槽位 9–35 为玩家背包主区域） */
    private static final int INVENTORY_START_SLOT = 9;

    // ===== 面板常量（由网格 + 四边等距 padding 反推） =====
    /** 槽位网格总宽度 = (COLS-1)*SLOT_SPACING + SLOT_SIZE = 8*20 + 18 = 178 */
    private static final int GRID_WIDTH = (COLS - 1) * SLOT_SPACING + SLOT_SIZE;
    /** 槽位网格总高度 = (ROWS-1)*SLOT_SPACING + SLOT_SIZE = 2*20 + 18 = 58 */
    private static final int GRID_HEIGHT = (ROWS - 1) * SLOT_SPACING + SLOT_SIZE;
    /** 四边统一内边距 */
    private static final int PANEL_PADDING = 3;
    /** 面板宽度 = 网格宽度 + 左右 padding */
    private static final int PANEL_WIDTH = GRID_WIDTH + PANEL_PADDING * 2;
    /** 面板高度 = 网格高度 + 上下 padding */
    private static final int PANEL_HEIGHT = GRID_HEIGHT + PANEL_PADDING * 2;
    /** 面板圆角半径 */
    private static final int PANEL_RADIUS = 6;
    /** 面板背景色（50% 白色） */
    private static final int PANEL_BG = 0x80FFFFFF;
    /** 面板距屏幕底部偏移 */
    private static final int PANEL_BOTTOM_OFFSET = 2;
    /** 面板距屏幕左边缘偏移 */
    private static final int PANEL_LEFT_OFFSET = 2;

    private InventoryHudRenderer() {
    }

    /**
     * 渲染 YZUI 风格物品栏 HUD（屏幕左下角 9×3 网格）。
     *
     * @param graphics GuiGraphicsExtractor 实例
     */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        int sh = graphics.guiHeight();

        // === 计算面板位置（左下角） ===
        int panelX = PANEL_LEFT_OFFSET;
        int panelY = sh - PANEL_HEIGHT - PANEL_BOTTOM_OFFSET;

        // === 1. 面板背景 ===
        fillRoundedRect(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                PANEL_RADIUS, PANEL_BG);

        // === 2. 绘制 9×3 槽位网格 ===
        Font font = client.font;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotIndex = INVENTORY_START_SLOT + row * COLS + col;
                int slotX = panelX + PANEL_PADDING + col * SLOT_SPACING;
                int slotY = panelY + PANEL_PADDING + row * SLOT_SPACING;

                ItemStack stack = player.getInventory().getItem(slotIndex);
                boolean hasItem = !stack.isEmpty();

                // 槽位背景
                int slotBg = hasItem ? SLOT_FILLED_COLOR : SLOT_EMPTY_COLOR;
                fillRoundedRect(graphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                        SLOT_RADIUS, slotBg);

                // 物品图标与装饰
                if (hasItem) {
                    graphics.item(stack, slotX + ITEM_INSET, slotY + ITEM_INSET);
                    graphics.itemDecorations(font, stack,
                            slotX + ITEM_INSET, slotY + ITEM_INSET);
                    ItemBorderRenderer.renderBorder(graphics,
                            slotX + ITEM_INSET, slotY + ITEM_INSET, stack);
                }
            }
        }
    }

    // ===== 圆角矩形绘制（与 HotbarRenderer.fillRoundedRect 完全一致） =====

    /**
     * 使用像素填充方式绘制实心圆角矩形。
     *
     * @param g     GuiGraphicsExtractor 实例
     * @param x     左上角 X 坐标
     * @param y     左上角 Y 坐标
     * @param w     宽度
     * @param h     高度
     * @param r     圆角半径
     * @param color ARGB 颜色
     */
    private static void fillRoundedRect(GuiGraphicsExtractor g,
            int x, int y, int w, int h, int r, int color) {
        if (w > r * 2) {
            g.fill(x + r, y, x + w - r, y + h, color);
        } else {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1,
                            x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1,
                            x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j,
                            x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j,
                            x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }
}
