package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
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

    /** 缓存的物品栏快照 */
    private static final ItemStack[] cached = new ItemStack[TOTAL];
    /** 每槽位独立的缓存物品渲染器（避免每帧 27 次模型解析） */
    private static final CachedItemRenderer[] itemRenderers = new CachedItemRenderer[TOTAL];
    private static int lastTimesChanged = -1;
    /**
     * 上次缓存对应的玩家实例（弱引用，避免退出世界后仍持有 {@code LocalPlayer}）。
     * <p>
     * 重生 / 换维度会重建玩家实例，新实例的 {@code timesChanged} 从 0 起算，
     * 有极小概率与上次残留值相同而漏刷新，加一道身份校验兜底。
     * </p>
     */
    private static java.lang.ref.WeakReference<Player> lastPlayerRef =
            new java.lang.ref.WeakReference<>(null);

    static {
        for (int i = 0; i < TOTAL; i++) {
            cached[i] = ItemStack.EMPTY;
            itemRenderers[i] = new CachedItemRenderer();
        }
    }

    private InventoryHudRenderer() {
    }

    /**
     * 使用统一缩放矩阵内的设计坐标绘制物品栏 HUD。
     *
     * @param graphics HUD 绘制上下文
     * @param guiHeight 当前缩放矩阵内的设计坐标高度
     */
    public static void render(GuiGraphicsExtractor graphics, int guiHeight) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        // 脏检测：Inventory.timesChanged 变化时更新缓存
        boolean playerChanged = lastPlayerRef.get() != player;
        if (playerChanged) {
            lastPlayerRef = new java.lang.ref.WeakReference<>(player);
        }
        int nowChanged = player.getInventory().getTimesChanged();
        if (playerChanged || nowChanged != lastTimesChanged) {
            var inventory = player.getInventory();
            for (int i = 0; i < TOTAL; i++)
                cached[i] = inventory.getItem(INVENTORY_START_SLOT + i).copy();
            lastTimesChanged = nowChanged;
        }

        int slotSize = BASE_SLOT_SIZE;
        int slotSpacing = BASE_SLOT_SPACING;
        int itemInset = BASE_ITEM_INSET;
        int padding = BASE_PADDING;

        int gridW = (COLS - 1) * slotSpacing + slotSize;
        int gridH = (ROWS - 1) * slotSpacing + slotSize;
        int panelW = gridW + padding * 2;
        int panelH = gridH + padding * 2;

        int panelX = BASE_LEFT_OFFSET;
        int panelY = guiHeight - panelH - BASE_BOTTOM_OFFSET;
        Font font = client.font;

        RoundedRect.fillOrSquare(graphics, panelX, panelY, panelW, panelH,
                BASE_PANEL_RADIUS, PANEL_BG);

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
                    itemRenderers[ci].render(graphics, stack, ix, iy);
                    graphics.itemDecorations(font, stack, ix, iy);
                    ItemBorderRenderer.renderBorder(graphics, ix, iy, stack);
                }
            }
        }
    }

}
