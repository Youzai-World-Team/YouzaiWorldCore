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
 * <p>在屏幕左下角显示 9×3（共 27 个）物品栏槽位，实时绑定玩家背包
 * （槽位索引 9–35），风格与 YZUI 热键栏保持一致。</p>
 */
@SuppressWarnings("null")
public final class InventoryHudRenderer {

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_RADIUS = 3;
    private static final int SLOT_SPACING = 20;
    private static final int SLOT_EMPTY_COLOR = 0x40FFFFFF;
    private static final int SLOT_FILLED_COLOR = 0x5AFFFFFF;
    private static final int ITEM_INSET = 1;
    private static final int PANEL_PADDING = 3;
    private static final int PANEL_RADIUS = 6;
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_BOTTOM_OFFSET = 2;
    private static final int PANEL_LEFT_OFFSET = 2;

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int INVENTORY_START_SLOT = 9;
    /** 网格宽 = 8×20 + 18 = 178 */
    private static final int GRID_WIDTH = (COLS - 1) * SLOT_SPACING + SLOT_SIZE;
    /** 网格高 = 2×20 + 18 = 58 */
    private static final int GRID_HEIGHT = (ROWS - 1) * SLOT_SPACING + SLOT_SIZE;
    /** 面板宽 = 178 + 2×3 = 184 */
    private static final int PANEL_WIDTH = GRID_WIDTH + PANEL_PADDING * 2;
    /** 面板高 = 58 + 2×3 = 64 */
    private static final int PANEL_HEIGHT = GRID_HEIGHT + PANEL_PADDING * 2;

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
        int panelX = PANEL_LEFT_OFFSET;
        int panelY = sh - PANEL_HEIGHT - PANEL_BOTTOM_OFFSET;
        Font font = client.font;

        // 面板背景
        fillRoundedRect(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                PANEL_RADIUS, PANEL_BG);

        // 9×3 槽位网格
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotIndex = INVENTORY_START_SLOT + row * COLS + col;
                int slotX = panelX + PANEL_PADDING + col * SLOT_SPACING;
                int slotY = panelY + PANEL_PADDING + row * SLOT_SPACING;

                ItemStack stack = player.getInventory().getItem(slotIndex);
                boolean hasItem = !stack.isEmpty();

                int slotBg = hasItem ? SLOT_FILLED_COLOR : SLOT_EMPTY_COLOR;
                fillRoundedRect(graphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                        SLOT_RADIUS, slotBg);

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

    // ===== 圆角矩形 =====

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
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }
}
