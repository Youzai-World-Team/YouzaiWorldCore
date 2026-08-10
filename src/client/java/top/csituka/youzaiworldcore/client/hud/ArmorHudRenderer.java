package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;

/**
 * YZUI 装备耐久 HUD 渲染器。
 *
 * <p>在屏幕左侧、物品栏 HUD 正上方，竖排显示 4 件盔甲的图标及剩余耐久度百分比。
 * 独立圆角面板，风格与 YZUI 热键栏保持一致。</p>
 */
@SuppressWarnings("null")
public final class ArmorHudRenderer {

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_RADIUS = 3;
    private static final int SLOT_SPACING = 20;
    private static final int SLOT_EMPTY_COLOR = 0x40FFFFFF;
    private static final int SLOT_FILLED_COLOR = 0x5AFFFFFF;
    private static final int ITEM_INSET = 1;
    private static final int PANEL_PADDING = 3;
    private static final int PANEL_RADIUS = 6;
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_LEFT_OFFSET = 2;

    /** 盔甲槽位数（头盔/胸甲/护腿/靴子） */
    private static final int ARMOR_SLOTS = 4;
    /** 盔甲网格高度 = (4-1)*20 + 18 = 78 */
    private static final int GRID_HEIGHT = (ARMOR_SLOTS - 1) * SLOT_SPACING + SLOT_SIZE;
    /** 面板高度 = 网格 + 上下 padding */
    private static final int PANEL_HEIGHT = GRID_HEIGHT + PANEL_PADDING * 2;                // 84
    /** 面板宽度 = padding + 槽位(18) + 间距(4) + 文本区(40) + padding */
    private static final int PANEL_WIDTH = PANEL_PADDING + SLOT_SIZE + 4 + 40 + PANEL_PADDING; // 68
    /**
     * 面板距屏幕底部偏移 = 热键栏高度(24) + 热键栏底部偏移(2) + 间距(4) = 30，
     * 确保面板底部位于热键栏正上方。
     */
    private static final int PANEL_BOTTOM_OFFSET = 30;

    // ===== 耐久文本 =====
    private static final int DURABILITY_COLOR_HIGH = 0xFFFFFFFF;
    private static final int DURABILITY_COLOR_MED  = 0xFFFFFF55;
    private static final int DURABILITY_COLOR_LOW  = 0xFFFF5555;

    private ArmorHudRenderer() {
    }

    /**
     * 渲染装备耐久 HUD。
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

        // 4 件盔甲（头盔→胸甲→护腿→靴子，从上到下）
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            int slotIndex = 39 - i;  // 39=头盔, 38=胸甲, 37=护腿, 36=靴子
            int slotX = panelX + PANEL_PADDING;
            int slotY = panelY + PANEL_PADDING + i * SLOT_SPACING;

            ItemStack stack = player.getInventory().getItem(slotIndex);
            boolean hasItem = !stack.isEmpty();

            int slotBg = hasItem ? SLOT_FILLED_COLOR : SLOT_EMPTY_COLOR;
            fillRoundedRect(graphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, slotBg);

            if (hasItem) {
                graphics.item(stack, slotX + ITEM_INSET, slotY + ITEM_INSET);
                ItemBorderRenderer.renderBorder(graphics,
                        slotX + ITEM_INSET, slotY + ITEM_INSET, stack);

                int maxDamage = stack.getMaxDamage();
                if (maxDamage > 0) {
                    int damage = stack.getDamageValue();
                    int percent = (maxDamage - damage) * 100 / maxDamage;
                    String text = percent + "%";

                    int textColor;
                    if (percent > 60) {
                        textColor = DURABILITY_COLOR_HIGH;
                    } else if (percent > 30) {
                        textColor = DURABILITY_COLOR_MED;
                    } else {
                        textColor = DURABILITY_COLOR_LOW;
                    }

                    int textX = slotX + SLOT_SIZE + 4;
                    int textY = slotY + (SLOT_SIZE - font.lineHeight) / 2;
                    graphics.text(font, text, textX, textY, textColor, true);
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
