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
 * 通过 {@code Inventory.getTimesChanged()} 检测物品栏变化，
 * 仅在变化时更新缓存，渲染始终走缓存数据（避免每帧 27 次 getItem() 调用）。
 * </p>
 */
@SuppressWarnings("null")
public final class InventoryHudRenderer {

    private static final float REF_HEIGHT = 360f;
    private static final int BASE_SLOT_SIZE = 18;
    private static final int BASE_SLOT_SPACING = 20;
    private static final int BASE_ITEM_INSET = 1;
    private static final int BASE_PADDING = 3;
    private static final int BASE_PANEL_RADIUS = 6;
    private static final int BASE_BOTTOM_OFFSET = 2;
    private static final int BASE_LEFT_OFFSET = 2;

    private static final int SLOT_EMPTY_COLOR = 0x40FFFFFF;
    private static final int SLOT_FILLED_COLOR = 0x5AFFFFFF;
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int INVENTORY_START_SLOT = 9;
    private static final int TOTAL = COLS * ROWS; // 27

    private static final int[][] CORNER_R6 = buildCornerTable(6);

    /** 缓存的物品栏快照 */
    private static final ItemStack[] cached = new ItemStack[TOTAL];
    private static int lastTimesChanged = -1;

    static {
        for (int i = 0; i < TOTAL; i++)
            cached[i] = ItemStack.EMPTY;
    }

    private InventoryHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        // 脏检测：Inventory.timesChanged 变化时更新缓存
        int nowChanged = player.getInventory().getTimesChanged();
        if (nowChanged != lastTimesChanged) {
            for (int i = 0; i < TOTAL; i++)
                cached[i] = player.getInventory().getItem(INVENTORY_START_SLOT + i).copy();
            lastTimesChanged = nowChanged;
        }

        float s = graphics.guiHeight() / REF_HEIGHT;
        int slotSize = rnd(BASE_SLOT_SIZE, s);
        int slotSpacing = rnd(BASE_SLOT_SPACING, s);
        int itemInset = rnd(BASE_ITEM_INSET, s);
        int padding = rnd(BASE_PADDING, s);

        int gridW = (COLS - 1) * slotSpacing + slotSize;
        int gridH = (ROWS - 1) * slotSpacing + slotSize;
        int panelW = gridW + padding * 2;
        int panelH = gridH + padding * 2;

        int sh = graphics.guiHeight();
        int panelX = rnd(BASE_LEFT_OFFSET, s);
        int panelY = sh - panelH - rnd(BASE_BOTTOM_OFFSET, s);
        Font font = client.font;

        fillRounded(graphics, panelX, panelY, panelW, panelH,
                rnd(BASE_PANEL_RADIUS, s), PANEL_BG, CORNER_R6);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int ci = row * COLS + col;
                int slotX = panelX + padding + col * slotSpacing;
                int slotY = panelY + padding + row * slotSpacing;

                ItemStack stack = cached[ci];
                int slotBg = stack.isEmpty() ? SLOT_EMPTY_COLOR : SLOT_FILLED_COLOR;
                graphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, slotBg);

                if (!stack.isEmpty()) {
                    int ix = slotX + itemInset;
                    int iy = slotY + itemInset;
                    graphics.item(stack, ix, iy);
                    graphics.itemDecorations(font, stack, ix, iy);
                    ItemBorderRenderer.renderBorder(graphics, ix, iy, stack);
                }
            }
        }
    }

    private static int rnd(float base, float scale) {
        return Math.round(base * scale);
    }

    private static int[][] buildCornerTable(int r) {
        int count = 0;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++)
                if (i * i + j * j < r * r)
                    count++;
        int[][] tbl = new int[count][2];
        int idx = 0;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++)
                if (i * i + j * j < r * r)
                    tbl[idx++] = new int[] { i, j };
        return tbl;
    }

    private static void fillRounded(GuiGraphicsExtractor g,
            int x, int y, int w, int h, int r, int color, int[][] corners) {
        if (w > r * 2) {
            g.fill(x + r, y, x + w - r, y + h, color);
        } else {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int[] c : corners) {
            int i = c[0], j = c[1];
            g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
            g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
            g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
            g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
        }
    }
}
