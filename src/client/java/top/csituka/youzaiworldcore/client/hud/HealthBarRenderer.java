package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 替换原版爱心血条为长条状血条（YZUI 组件）。
 *
 * <p>当 YZUI 启用时，原版的心形血条被替换为固定宽度的长条状进度条，
 * 中间显示 {@code 当前生命值 / 最大生命值} 文字。</p>
 *
 * <p>手持食物时，血条右侧闪烁显示预估可恢复生命值的叠加层。</p>
 */
@SuppressWarnings("null")
public final class HealthBarRenderer {

    /** 单条血条宽度 */
    public static final int BAR_WIDTH = 85;
    /** 血条高度 */
    public static final int BAR_HEIGHT = 5;
    /** 两血条之间的间隔 */
    public static final int BAR_GAP = 8;
    /** 血条距离屏幕底部的垂直偏移（与原版第一行爱心一致） */
    public static final int Y_OFFSET_FROM_BOTTOM = 39;

    private static final int TEXT_OFFSET_ABOVE_BAR = 10;
    private static final int TEXT_SHADOW_OFFSET = 1;
    private static final int BG_COLOR = 0xAA333333;
    private static final int COLOR_ABSORPTION = 0x88FFDD00;
    /** 预测恢复血量叠加层（绿色，半透明，闪烁时覆盖） */
    private static final int COLOR_PREDICTED_HEAL = 0xAA44FF44;
    private static final String LOG_TAG = "HealthBarRenderer";

    private HealthBarRenderer() {}

    /**
     * 在指定位置渲染自定义长条血条。
     *
     * @param graphics GuiGraphicsExtractor 实例
     * @param barX     血条左上角 X 坐标
     * @param barY     血条左上角 Y 坐标
     */
    public static void render(GuiGraphicsExtractor graphics, int barX, int barY) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;

        if (player == null || !player.isAlive()) {
            return;
        }

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float absorption = player.getAbsorptionAmount();

        if (maxHealth <= 0) {
            return;
        }

        // 显示值统一用 Math.ceil 取整：血量 > 0 时至少显示 1（修复中毒导致的小数血量显示为 0）
        int displayHealth = (int) Math.ceil(health);
        int displayMax = (int) Math.ceil(maxHealth);
        int displayAbsorb = (int) Math.ceil(absorption);

        float fillRatio = Math.min(1.0f, Math.max(0.0f, health / maxHealth));

        DebugLogger.debug(LOG_TAG,
                "渲染血条: health=%.1f, max=%.1f, absorb=%.1f, fill=%.2f, pos=(%d,%d)",
                health, maxHealth, absorption, fillRatio, barX, barY);

        // === 1. 背景 ===
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BG_COLOR);

        // === 2. 血条填充 ===
        int fillWidth = (int) (fillRatio * BAR_WIDTH);
        if (fillWidth > 0) {
            int barColor = getHealthColor(fillRatio);
            graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, barColor);
        }

        // === 3. 吸收效果叠加 ===
        if (absorption > 0.0f) {
            float combinedRatio = Math.min(1.0f, (health + absorption) / maxHealth);
            int absorbWidth = (int) (combinedRatio * BAR_WIDTH);
            if (absorbWidth > fillWidth) {
                graphics.fill(barX + fillWidth, barY, barX + absorbWidth, barY + BAR_HEIGHT, COLOR_ABSORPTION);
            }
        }

        // === 4. 手持食物预测血量叠加 ===
        int predictedHeal = computePredictedHeal(player);
        boolean showHealOverlay = predictedHeal > 0;

        if (showHealOverlay) {
            float predictedHealth = Math.min(maxHealth, health + predictedHeal);
            float predictedRatio = predictedHealth / maxHealth;
            int predictedWidth = (int) (predictedRatio * BAR_WIDTH);

            // 从当前填充末端到预测值末端绘制绿色半透明叠加（始终显示，脉冲式闪烁）
            int overlayStart = barX + fillWidth;
            int overlayEnd = Math.min(barX + predictedWidth, barX + BAR_WIDTH);
            if (overlayEnd > overlayStart) {
                float flashAlpha = FoodBarRenderer.flashAlpha;
                int a = Math.min(255, Math.max(40, (int) ((flashAlpha * 0.6f + 0.4f) * 255))); // 始终有基础可见度
                int overlayColor = (a << 24) | 0x44FF44;
                graphics.fill(overlayStart, barY, overlayEnd, barY + BAR_HEIGHT, overlayColor);
            }

            DebugLogger.debug(LOG_TAG,
                    "预测血量: predictedHeal=%d, predictedHealth=%.1f, overlay=(%d,%d)-(%d,%d), flashAlpha=%.2f",
                    predictedHeal, predictedHealth, overlayStart, barY,
                    Math.min(barX + predictedWidth, barX + BAR_WIDTH), barY + BAR_HEIGHT,
                    FoodBarRenderer.flashAlpha);
        }

        // === 5. 文字 ===
        Font font = client.font;
        String text;

        if (showHealOverlay) {
            // 预测模式：始终显示绿色闪烁文字（脉冲，不切换回白色）
            int alpha = Math.min(255, Math.max(60, (int) ((FoodBarRenderer.flashAlpha * 0.6f + 0.4f) * 255)));
            text = String.format("%d(+%d)/%d", displayHealth, predictedHeal, displayMax);
            int textColor = (alpha << 24) | 0x88FF88;
            drawTextWithAlpha(graphics, font, text, barX, barY, textColor);
        } else {
            // 普通模式
            if (absorption > 0.0f) {
                // 有吸收效果时直接合并显示：如 "30/20"（不拼接 +<吸收值>）
                int totalHealth = Math.min(displayHealth + displayAbsorb, displayMax * 10); // safety cap
                text = String.format("%d/%d", totalHealth, displayMax);
            } else {
                text = String.format("%d/%d", displayHealth, displayMax);
            }
            drawTextCentered(graphics, font, text, barX, barY);
        }
    }

    /**
     * 计算手持食物可预估恢复的生命值（参考 AppleSkin 思路的简化版）。
     *
     * <p>逻辑：若玩家受伤且预测食物等级 >= 18，则每个超过 18 的食物点最多恢复 1 HP。</p>
     *
     * @param player 当前玩家
     * @return 预估可恢复 HP 数（0 表示无恢复）
     */
    private static int computePredictedHeal(Player player) {
        if (!player.isHurt()) {
            return 0;
        }

        // 检查是否受毒/凋零效果 — 有则无法自然回血
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) {
            return 0;
        }

        ItemStack heldItem = FoodBarRenderer.findHeldFood(player);
        if (heldItem == null) {
            return 0;
        }

        FoodProperties foodProps = heldItem.getComponents().getOrDefault(DataComponents.FOOD, null);
        if (foodProps == null) {
            return 0;
        }

        int foodLevel = player.getFoodData().getFoodLevel();
        int nutrition = foodProps.nutrition();
        int maxFood = FoodConstants.MAX_FOOD;
        int predictedFood = Math.min(maxFood, foodLevel + nutrition);

        // 食物 >= 18 时开始回血，每个超过 18 的食物点最多恢复 1 HP
        if (predictedFood < 18) {
            return 0;
        }

        int foodOverThreshold = Math.max(0, predictedFood - 18);
        int maxRegen = (int) Math.min(
                Math.ceil(player.getMaxHealth() - player.getHealth()),
                foodOverThreshold);
        return Math.max(0, maxRegen);
    }

    /** 在血条上方居中绘制文字，带阴影。 */
    public static void drawTextCentered(GuiGraphicsExtractor graphics, Font font,
                                         String text, int barX, int barY) {
        int textWidth = font.width(text);
        int textX = barX + (BAR_WIDTH - textWidth) / 2;
        int textY = barY - TEXT_OFFSET_ABOVE_BAR;
        graphics.text(font, text, textX + TEXT_SHADOW_OFFSET, textY + TEXT_SHADOW_OFFSET,
                0xFF000000, false);
        graphics.text(font, text, textX, textY, 0xFFFFFFFF, false);
    }

    /** 在血条上方居中绘制带透明度的文字。 */
    private static void drawTextWithAlpha(GuiGraphicsExtractor graphics, Font font,
                                           String text, int barX, int barY, int color) {
        int textWidth = font.width(text);
        int textX = barX + (BAR_WIDTH - textWidth) / 2;
        int textY = barY - TEXT_OFFSET_ABOVE_BAR;
        // 阴影始终全不透明
        graphics.text(font, text, textX + TEXT_SHADOW_OFFSET, textY + TEXT_SHADOW_OFFSET,
                0xFF000000, false);
        graphics.text(font, text, textX, textY, color, false);
    }

    /**
     * 根据生命值比例计算血条颜色。
     * <ul>
     *   <li>100% ~ 50%：绿色渐变到黄色</li>
     *   <li>50% ~ 0%：黄色渐变到红色</li>
     * </ul>
     */
    public static int getHealthColor(float ratio) {
        int r, g;
        if (ratio > 0.5f) {
            float t = (ratio - 0.5f) * 2.0f;
            r = (int) (0x44 + (0xFF - 0x44) * t);
            g = 0xFF;
        } else {
            float t = ratio * 2.0f;
            r = 0xFF;
            g = (int) (0xD7 * t);
        }
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8);
    }
}
