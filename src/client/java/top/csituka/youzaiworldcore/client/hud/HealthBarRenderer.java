package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 替换原版爱心血条为长条状血条（YZUI 组件）。
 *
 * <p>当 YZUI 启用时，原版的心形血条（随最大生命值增长而占用更多屏幕空间）被替换为
 * 固定宽度的长条状血条（类似进度条），中间显示 {@code 当前生命值 / 最大生命值} 文字。</p>
 *
 * <p>该组件不随玩家最大生命值变化而改变屏幕占用宽度，
 * 适用于自定义生命值上限较高的服务器场景。</p>
 */
@SuppressWarnings("null")
public final class HealthBarRenderer {

    /** 血条宽度，与经验条 / 物品栏对齐（182 像素） */
    private static final int BAR_WIDTH = 182;

    /** 血条高度 */
    private static final int BAR_HEIGHT = 5;

    /** 血条距离屏幕底部的垂直偏移（与原版第一行爱心一致） */
    private static final int Y_OFFSET_FROM_BOTTOM = 39;

    /** 血条上方的文字偏移（负值 = 向上） */
    private static final int TEXT_OFFSET_ABOVE_BAR = 10;

    /** 文字阴影偏移 */
    private static final int TEXT_SHADOW_OFFSET = 1;

    /** 背景色（暗灰色，半透明） */
    private static final int BG_COLOR = 0xAA333333;

    /** 吸收效果覆盖颜色（浅金色，半透明） */
    private static final int COLOR_ABSORPTION = 0x88FFDD00;

    private static final String LOG_TAG = "HealthBarRenderer";

    private HealthBarRenderer() {}

    /**
     * 渲染自定义长条血条。
     * <p>
     * 仅在 YZUI 启用且玩家存活时渲染。
     * </p>
     *
     * @param graphics GuiGraphicsExtractor 实例
     */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;

        // 玩家不存在或已死亡时跳过（26.2 无 isDead，使用 isAlive）
        if (player == null || !player.isAlive()) {
            return;
        }

        // 获取玩家生命值数据
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float absorption = player.getAbsorptionAmount();

        // 负值保护
        if (maxHealth <= 0) {
            return;
        }

        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();

        // 计算血条位置（居中，底部固定偏移）
        int barX = (sw - BAR_WIDTH) / 2;
        int barY = sh - Y_OFFSET_FROM_BOTTOM;

        // 计算填充比例
        float fillRatio = Math.min(1.0f, Math.max(0.0f, health / maxHealth));

        DebugLogger.debug(LOG_TAG,
                "渲染血条: health=%.1f, maxHealth=%.1f, absorption=%.1f, fillRatio=%.2f, pos=(%d,%d)",
                health, maxHealth, absorption, fillRatio, barX, barY);

        // === 1. 绘制背景 ===
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BG_COLOR);

        // === 2. 绘制血条填充 ===
        int fillWidth = (int) (fillRatio * BAR_WIDTH);
        if (fillWidth > 0) {
            int barColor = getHealthColor(fillRatio);
            graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, barColor);
        }

        // === 3. 绘制吸收效果叠加层（仅在吸收值 > 0 时） ===
        if (absorption > 0.0f) {
            float totalEffectiveHealth = health + absorption;
            float absorbRatio = Math.min(1.0f, totalEffectiveHealth / maxHealth);
            int absorbWidth = (int) (absorbRatio * BAR_WIDTH);
            if (absorbWidth > fillWidth) {
                // 在现有填充之上叠加吸收效果（从 fillWidth 到 absorbWidth）
                graphics.fill(barX + fillWidth, barY, barX + absorbWidth, barY + BAR_HEIGHT, COLOR_ABSORPTION);
            }
        }

        // === 4. 绘制居中文字（当前生命值 / 最大生命值） ===
        Font font = client.font;
        String text;
        if (absorption > 0.0f) {
            // 有吸收效果时显示 "current (+absorb) / max"
            text = String.format("%.0f (+%.0f) / %.0f", health, absorption, maxHealth);
        } else {
            text = String.format("%.0f / %.0f", health, maxHealth);
        }

        int textWidth = font.width(text);
        int textX = barX + (BAR_WIDTH - textWidth) / 2;
        int textY = barY - TEXT_OFFSET_ABOVE_BAR;

        // 文字阴影
        graphics.text(font, text, textX + TEXT_SHADOW_OFFSET, textY + TEXT_SHADOW_OFFSET,
                0xFF000000, false);
        // 文字本体（白色）
        graphics.text(font, text, textX, textY, 0xFFFFFFFF, false);
    }

    /**
     * 根据生命值比例计算血条颜色。
     * <ul>
     *   <li>100% ~ 50%：绿色渐变到黄色</li>
     *   <li>50% ~ 0%：黄色渐变到红色</li>
     * </ul>
     *
     * @param ratio 生命值比例（0.0 ~ 1.0）
     * @return ARGB 颜色值
     */
    private static int getHealthColor(float ratio) {
        int r, g, b = 0;

        if (ratio > 0.5f) {
            // 绿色 → 黄色区间
            float t = (ratio - 0.5f) * 2.0f; // 0.0 ~ 1.0
            r = (int) (0x44 + (0xFF - 0x44) * t);              // 0x44 → 0xFF
            g = 0xFF;                                            // 固定 0xFF
        } else {
            // 黄色 → 红色区间
            float t = ratio * 2.0f;                              // 0.0 ~ 1.0
            r = 0xFF;                                            // 固定 0xFF
            g = (int) (0xD7 * t);                                // 0x00 → 0xD7
        }

        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
