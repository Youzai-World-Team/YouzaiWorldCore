package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 替换原版爱心血条为长条状血条（YZUI 组件）。
 *
 * <p>
 * 当 YZUI 启用时，原版的心形血条被替换为固定宽度的长条状进度条，
 * 中间显示 {@code 当前生命值 / 最大生命值} 文字。
 * </p>
 *
 * <p>
 * 手持食物时，血条右侧闪烁显示预估可恢复生命值的叠加层。
 * </p>
 */
@SuppressWarnings("null")
public final class HealthBarRenderer {

    /** 单条血条宽度 */
    public static final int BAR_WIDTH = 85;
    /** 血条高度 */
    public static final int BAR_HEIGHT = 5;
    /** 两血条之间的间隔 */
    public static final int BAR_GAP = 8;
    /** 血条距离屏幕底部的垂直偏移（YZUI 热键栏 24+2=26px，上移 6px 避免重叠） */
    public static final int Y_OFFSET_FROM_BOTTOM = 45;

    private static final int TEXT_OFFSET_ABOVE_BAR = 10;
    private static final int TEXT_SHADOW_OFFSET = 1;
    private static final int BG_COLOR = 0xAA333333;
    private static final int COLOR_ABSORPTION = 0x88FFDD00;

    // === 状态效果指示器 ===
    // 中毒条纹色（垂直条纹，高饱和度紫色）
    private static final int POISON_STRIPE_A = 0x88AA00FF;  // 亮紫
    private static final int POISON_STRIPE_B = 0x445500AA;  // 暗紫
    // 凋零条纹色（水平条纹，灰黑）
    private static final int WITHER_STRIPE_A = 0x88555555;  // 中灰
    private static final int WITHER_STRIPE_B = 0x44111111;  // 近黑

    private static final String LOG_TAG = "HealthBarRenderer";

    private HealthBarRenderer() {
    }

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

        // === 1. 背景（圆角） ===
        fillBarBg(graphics, barX, barY, BG_COLOR);

        // === 2. 血条填充（左侧圆角） ===
        int fillWidth = (int) (fillRatio * BAR_WIDTH);
        if (fillWidth > 0) {
            int barColor = getHealthColor(fillRatio);
            fillBarFill(graphics, barX, barY, fillWidth, barColor);
        }

        // === 3. 吸收效果叠加（半透明，常规矩形不过度处理圆角） ===
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

        // === 5. 状态效果指示器（中毒/凋零时在血条右侧绘制装饰条纹） ===
        int guiTicks = client.gui.hud.getGuiTicks();
        renderEffectIndicator(graphics, barX, barY, player, guiTicks);

        // === 6. 文字 ===
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

    // ===== 圆角绘制工具方法 =====

    /** 圆角半径（px），对于 5px 高的条取 1px 即足够 */
    private static final int RADIUS = 1;

    /**
     * 绘制整条背景（全宽双端圆角），使用默认 BAR_WIDTH。
     */
    public static void fillBarBg(GuiGraphicsExtractor graphics, int x, int y, int color) {
        fillBarBg(graphics, x, y, BAR_WIDTH, color);
    }

    /**
     * 绘制整条背景（全宽双端圆角），指定宽度。
     */
    public static void fillBarBg(GuiGraphicsExtractor graphics, int x, int y, int w, int color) {
        int h = BAR_HEIGHT;
        int r = RADIUS;
        graphics.fill(x + r, y, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + r, y + h - r, color);
        graphics.fill(x + w - r, y + r, x + w, y + h - r, color);
    }

    /**
     * 绘制左对齐的部分填充（左侧圆角，右侧平直；若填充到右边缘则双端圆角），使用默认 BAR_WIDTH。
     */
    public static void fillBarFill(GuiGraphicsExtractor graphics, int x, int y, int fillWidth, int color) {
        fillBarFill(graphics, x, y, fillWidth, BAR_WIDTH, color);
    }

    /**
     * 绘制左对齐的部分填充（左侧圆角，右侧平直；若填充到右边缘则双端圆角），指定总宽度。
     */
    public static void fillBarFill(GuiGraphicsExtractor graphics, int x, int y, int fillWidth, int totalWidth, int color) {
        if (fillWidth <= 0 || totalWidth <= 0) return;
        int h = BAR_HEIGHT;
        int r = RADIUS;
        int actualW = Math.min(fillWidth, totalWidth);
        int rightEdge = x + actualW;

        // 主体（排除左右两端的角落像素列）
        int rightLimit = (actualW >= totalWidth - r) ? rightEdge - r : rightEdge;
        if (rightLimit > x + r) {
            graphics.fill(x + r, y, rightLimit, y + h, color);
        }

        // 左端（填充圆角区域，排除左上/左下角落）
        graphics.fill(x, y + r, x + r, y + h - r, color);

        // 若填充覆盖到右边缘区域，也圆角右端
        if (actualW >= totalWidth - r) {
            graphics.fill(rightEdge - r, y + r, rightEdge, y + h - r, color);
        }
    }

    /**
     * 渲染状态效果指示器：中毒/凋零时在整条血条上覆盖装饰纹理。
     *
     * <p>
     * 中毒效果：亮紫/暗紫交替垂直竖条<br>
     * 凋零效果：中灰/近黑交替水平横条<br>
     * 两者同时存在时同时绘制，形成网格交叉纹理。<br>
     * 纹理覆盖整条血条宽度，半透明不遮挡底层血量信息，
     * 并有缓慢脉冲动画。
     * </p>
     */
    private static void renderEffectIndicator(GuiGraphicsExtractor graphics,
            int barX, int barY, Player player, int guiTicks) {
        boolean hasPoison = player.hasEffect(MobEffects.POISON);
        boolean hasWither = player.hasEffect(MobEffects.WITHER);
        if (!hasPoison && !hasWither) return;

        // 脉冲动画：基于游戏刻的慢速三角波
        int phase = (guiTicks / 2) & 7; // 0~7 循环
        boolean flash = phase < 4;       // 4/8 占空比

        if (hasPoison) {
            // === 中毒：垂直交替竖条 ===
            int stripeA = POISON_STRIPE_A;
            int stripeB = POISON_STRIPE_B;
            for (int x = barX; x < barX + BAR_WIDTH; x += 4) {
                int endX = Math.min(x + 2, barX + BAR_WIDTH);
                if (endX > x) {
                    boolean useBright = ((x - barX) / 2 + (flash ? 0 : 1)) % 2 == 0;
                    graphics.fill(x, barY, endX, barY + BAR_HEIGHT, useBright ? stripeA : stripeB);
                }
            }
        }
        if (hasWither) {
            // === 凋零：水平交替横条 ===
            int stripeA = WITHER_STRIPE_A;
            int stripeB = WITHER_STRIPE_B;
            for (int y = barY; y < barY + BAR_HEIGHT; y += 2) {
                int endY = Math.min(y + 1, barY + BAR_HEIGHT);
                if (endY > y) {
                    boolean useBright = (y - barY + (flash ? 0 : 1)) % 2 == 0;
                    graphics.fill(barX, y, barX + BAR_WIDTH, endY, useBright ? stripeA : stripeB);
                }
            }
        }

        DebugLogger.debug(LOG_TAG,
                "效果指示器: poison=%s, wither=%s, guiTicks=%d, flash=%s",
                hasPoison, hasWither, guiTicks, flash);
    }

    /**
     * 计算手持食物可预估恢复的生命值（参考 AppleSkin 思路的简化版）。
     *
     * <p>
     * 逻辑：若玩家受伤、饥饿值未满（可进食）且预测食物等级 >= 18，则每个超过 18 的食物点最多恢复 1 HP。
     * </p>
     *
     * @param player 当前玩家
     * @return 预估可恢复 HP 数（0 表示无恢复）
     */
    private static int computePredictedHeal(Player player) {
        if (!player.isHurt()) {
            return 0;
        }

        // 饥饿值已满时无法进食，不显示预测
        if (player.getFoodData().getFoodLevel() >= FoodConstants.MAX_FOOD) {
            return 0;
        }

        // 检查是否受毒/凋零效果 — 有则无法自然回血
        if (player.hasEffect(MobEffects.POISON)
                || player.hasEffect(MobEffects.WITHER)) {
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
     * 根据生命值比例计算血条颜色（平滑渐变）。
     * <ul>
     * <li>100% ~ 50%：绿色 → 黄色（R 从 0 线性升到 255，G 恒 255）</li>
     * <li>50% ~ 0%：黄色 → 红色（R 恒 255，G 从 255 线性降到 0）</li>
     * </ul>
     * 50% 处两端连续，无缝过渡。
     */
    public static int getHealthColor(float ratio) {
        int r, g;
        if (ratio > 0.5f) {
            // 高血量：绿色 → 黄色
            // t: 0(50%) → 1(100%)
            float t = (ratio - 0.5f) * 2.0f;
            r = (int) (255 * (1.0f - t));  // 255 → 0
            g = 255;                       // 恒 255
        } else {
            // 低血量：黄色 → 红色
            // t: 0(0%) → 1(50%)
            float t = ratio * 2.0f;
            r = 255;                       // 恒 255
            g = (int) (255 * t);           // 0 → 255
        }
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8);
    }
}
