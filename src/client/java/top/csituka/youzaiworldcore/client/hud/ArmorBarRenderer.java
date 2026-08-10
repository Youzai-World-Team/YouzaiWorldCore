package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 盔甲值条渲染器。
 *
 * <p>
 * 替换原版盔甲图标为长条状进度条，仅在 {@code armorValue > 0} 时显示。
 * 位于血条正上方（第二行左侧）。
 * </p>
 */
@SuppressWarnings("null")
public final class ArmorBarRenderer {

    private static final int TEXT_OFFSET_ABOVE_BAR = 10;
    private static final int TEXT_SHADOW_OFFSET = 1;
    private static final int BG_COLOR = 0xAA333333;
    /** 盔甲填充色（钢蓝色系） */
    private static final int COLOR_ARMOR = 0xFF6A8FBF;
    /** 满盔甲模拟参考值（原版最多 20 点 = 10 个图标） */
    private static final float ARMOR_REFERENCE = 20.0f;

    private static final String LOG_TAG = "ArmorBarRenderer";

    private ArmorBarRenderer() {
    }

    /**
     * 在指定位置渲染自定义长条盔甲值条。
     *
     * @param graphics GuiGraphicsExtractor 实例
     * @param barX     条左上角 X 坐标
     * @param barY     条左上角 Y 坐标
     */
    public static void render(GuiGraphicsExtractor graphics, int barX, int barY) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;

        if (player == null || !player.isAlive()) {
            return;
        }

        int armor = player.getArmorValue();
        if (armor <= 0) {
            return;
        }

        int bw = HealthBarRenderer.BAR_WIDTH;

        float fillRatio = Math.min(1.0f, armor / ARMOR_REFERENCE);

        // 先判等级：基本类型装箱 + varargs 数组在每帧路径上是白扔的垃圾
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(LOG_TAG,
                    "渲染盔甲条: armor=%d, fill=%.2f, pos=(%d,%d)", armor, fillRatio, barX, barY);
        }

        // === 1. 背景（圆角） ===
        HealthBarRenderer.fillBarBg(graphics, barX, barY, BG_COLOR);

        // === 2. 填充（左侧圆角） ===
        int fillWidth = (int) (fillRatio * bw);
        if (fillWidth > 0) {
            HealthBarRenderer.fillBarFill(graphics, barX, barY, fillWidth, COLOR_ARMOR);
        }

        // === 3. 文字 ===
        Font font = client.font;
        String text = String.valueOf(armor);
        int textWidth = font.width(text);
        int textX = barX + (bw - textWidth) / 2;
        int textY = barY - TEXT_OFFSET_ABOVE_BAR;
        graphics.text(font, text, textX + TEXT_SHADOW_OFFSET, textY + TEXT_SHADOW_OFFSET,
                0xFF000000, false);
        graphics.text(font, text, textX, textY, 0xFFFFFFFF, false);
    }
}
