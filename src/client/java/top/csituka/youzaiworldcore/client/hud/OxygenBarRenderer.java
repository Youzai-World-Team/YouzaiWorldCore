package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 氧气值条渲染器。
 *
 * <p>
 * 替换原版气泡图标为长条状进度条，仅在水下 ({@code airSupply < maxAirSupply}) 时显示。
 * 位于食物条正上方（第二行右侧）。
 * </p>
 */
@SuppressWarnings("null")
public final class OxygenBarRenderer {

    private static final int TEXT_OFFSET_ABOVE_BAR = 10;
    private static final int TEXT_SHADOW_OFFSET = 1;
    private static final int BG_COLOR = 0xAA333333;
    /** 氧气填充色（蓝色系） */
    private static final int COLOR_OXYGEN = 0xFF44AAFF;
    /** 氧气不足警告色（红色） */
    private static final int COLOR_OXYGEN_LOW = 0xFFFF4444;
    /** 氧气低阈值（低于此值显示红色） */
    private static final float LOW_OXYGEN_RATIO = 0.25f;

    private static final String LOG_TAG = "OxygenBarRenderer";

    private OxygenBarRenderer() {
    }

    /**
     * 在指定位置渲染自定义长条氧气值条。
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

        int airSupply = player.getAirSupply();
        int maxAirSupply = player.getMaxAirSupply();

        // 只在有氧气消耗时显示（水下）；负值按 0 显示
        if (airSupply >= maxAirSupply) {
            return;
        }
        int displayAir = Math.max(0, airSupply);

        int bw = HealthBarRenderer.BAR_WIDTH;

        float fillRatio = Math.min(1.0f, Math.max(0.0f, (float) displayAir / maxAirSupply));

        // 先判等级：基本类型装箱 + varargs 数组在每帧路径上是白扔的垃圾
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(LOG_TAG,
                    "渲染氧气条: air=%d, max=%d, fill=%.2f, pos=(%d,%d)",
                    displayAir, maxAirSupply, fillRatio, barX, barY);
        }

        // === 1. 背景（圆角） ===
        HealthBarRenderer.fillBarBg(graphics, barX, barY, BG_COLOR);

        // === 2. 填充（左侧圆角；氧气低时显示红色警告） ===
        int fillWidth = (int) (fillRatio * bw);
        if (fillWidth > 0) {
            int color = (fillRatio <= LOW_OXYGEN_RATIO) ? COLOR_OXYGEN_LOW : COLOR_OXYGEN;
            HealthBarRenderer.fillBarFill(graphics, barX, barY, fillWidth, color);
        }

        // === 3. 文字 ===
        Font font = client.font;
        String text = String.format("%d/%d", displayAir, maxAirSupply);
        int textWidth = font.width(text);
        int textX = barX + (bw - textWidth) / 2;
        int textY = barY - TEXT_OFFSET_ABOVE_BAR;
        graphics.text(font, text, textX + TEXT_SHADOW_OFFSET, textY + TEXT_SHADOW_OFFSET,
                0xFF000000, false);
        graphics.text(font, text, textX, textY, 0xFFFFFFFF, false);
    }
}
