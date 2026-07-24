package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.mixin.client.food.FoodDataExhaustionAccessor;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 饥饿值条渲染器。
 *
 * <p>替换原版鸡腿图标为长条状进度条，显示当前饥饿值 / 最大饥饿值，
 * 条内叠加饱和度显示（橙色半透明+白色高亮线），消耗度显示（灰底色透过食物填充）。
 * 手持食物时根据游戏刻交替闪烁显示预测恢复值。</p>
 *
 * <p>与 {@link HealthBarRenderer} 搭配使用，位于其右侧。</p>
 */
@SuppressWarnings("null")
public final class FoodBarRenderer {

    private static final int TEXT_OFFSET_ABOVE_BAR = 10;
    private static final int TEXT_SHADOW_OFFSET = 1;
    private static final int BG_COLOR = 0xAA333333;
    /** 消耗度底色（浅灰色，绘制在食物填充下方） */
    private static final int COLOR_EXHAUSTION = 0x88AAAAAA;
    /** 饱和度叠加层（橙色，半透明） */
    private static final int COLOR_SATURATION = 0x66FFAA44;
    /** 饱和度白色高亮线（1px 在饱和度区域顶部） */
    private static final int COLOR_SAT_HIGHLIGHT = 0xCCFFFFFF;
    /** 最大消耗度 */
    private static final float MAX_EXHAUSTION = 4.0f;

    private static final String LOG_TAG = "FoodBarRenderer";

    // === 闪烁系统（参考 AppleSkin：每 tick 更新三角形波） ===
    private static float unclampedFlashAlpha = 0f;
    /** 当前闪烁透明度（0.0 ~ 0.65），供 HealthBarRenderer 共用 */
    public static float flashAlpha = 0f;
    private static byte alphaDir = 1;
    private static int lastGuiTicks = -1;

    private FoodBarRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, int barX, int barY) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;

        if (player == null || !player.isAlive()) {
            return;
        }

        int bw = HealthBarRenderer.BAR_WIDTH;
        int bh = HealthBarRenderer.BAR_HEIGHT;

        // === 获取食物数据 ===
        FoodData foodData = player.getFoodData();
        int foodLevel = foodData.getFoodLevel();
        float saturation = foodData.getSaturationLevel();
        float exhaustion = ((FoodDataExhaustionAccessor) foodData).youzaiworldcore$getExhaustionLevel();
        int maxFood = FoodConstants.MAX_FOOD;
        if (maxFood <= 0) return;

        // === 检测手持食物 ===
        ItemStack heldItem = findHeldFood(player);
        FoodProperties foodProps = (heldItem != null)
                ? heldItem.getComponents().getOrDefault(DataComponents.FOOD, null)
                : null;

        // === 闪烁更新 ===
        Hud hud = client.gui.hud;
        int guiTicks = hud.getGuiTicks();
        boolean hasFood = foodProps != null;
        boolean canPredict = hasFood && foodLevel < maxFood; // 饥饿值满了不再显示预测
        updateFlash(guiTicks, canPredict);

        // === 计算预测显示值（始终计算，无论闪烁相位） ===
        int displayFood = foodLevel;
        float displaySaturation = saturation;
        boolean isPredicted = false;
        int predictedNutrition = 0;

        if (canPredict) {
            predictedNutrition = foodProps.nutrition();
            displayFood = Math.min(maxFood, foodLevel + predictedNutrition);
            displaySaturation = saturation + foodProps.saturation();
            isPredicted = true;
        }

        float fillRatio = Math.min(1.0f, Math.max(0.0f, (float) displayFood / maxFood));
        float satRatio  = Math.min(1.0f, displaySaturation / maxFood);
        float exhRatio  = Math.min(1.0f, Math.max(0.0f, exhaustion / MAX_EXHAUSTION));

        DebugLogger.debug(LOG_TAG,
                "food=%d, sat=%.1f, exh=%.2f, fill=%.2f, flash=%.2f",
                displayFood, displaySaturation, exhaustion, fillRatio, flashAlpha);

        // ===== 渲染层级（从下到上） =====
        //   1. 背景
        //   2. 消耗度底色（灰色 — 透过食物填充可见）
        //   3. 食物填充（棕色）
        //   4. 饱和度叠加（橙色 + 白色高亮线）
        //   5. 文字

        // === 1. 背景 ===
        graphics.fill(barX, barY, barX + bw, barY + bh, BG_COLOR);

        // === 2. 消耗度底色（绘制在食物填充下方，从右侧延伸） ===
        //    参考 AppleSkin：底色在食物条背后，宽度 = exhaustionRatio * BAR_WIDTH
        //    从右向左延伸（消耗度越高，灰色区域越大），食物填充覆盖其上，
        //    消耗度通过填充的半透明底色隐约可见。
        if (exhRatio > 0.01f) {
            int exhWidth = (int) (exhRatio * bw);
            int exhStartX = barX + bw - exhWidth;
            if (exhStartX < barX) exhStartX = barX;
            graphics.fill(exhStartX, barY, barX + bw, barY + bh, COLOR_EXHAUSTION);
        }

        // === 3. 食物填充（棕色） ===
        int fillWidth = (int) (fillRatio * bw);
        int barColor = getFoodColor(fillRatio);
        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + bh, barColor);
        }

        // === 4. 饱和度叠加（橙色半透明 + 白色高亮线） ===
        if (satRatio > 0.01f) {
            int satWidth = (int) (satRatio * bw);
            if (satWidth > fillWidth) satWidth = fillWidth;
            if (satWidth > 0) {
                int satStart = barX + fillWidth - satWidth;
                if (satStart < barX) satStart = barX;
                int actualWidth = fillWidth - (satStart - barX);
                if (actualWidth > 0) {
                    // 橙色半透明层
                    graphics.fill(satStart, barY, satStart + actualWidth, barY + bh, COLOR_SATURATION);
                    // 白色高亮线（1px 在区域顶部）
                    graphics.fill(satStart, barY, satStart + actualWidth, barY + 1, COLOR_SAT_HIGHLIGHT);
                }
            }
        }

        // === 5. 上方文字 ===
        Font font = client.font;
        String text;
        int textColor;

        if (isPredicted) {
            // 预测模式：始终显示绿色闪烁文字（脉冲不切换回白色），alpha 随 flashAlpha 波动
            int alpha = Math.min(255, Math.max(60, (int) ((flashAlpha * 0.6f + 0.4f) * 255)));
            text = String.format("%d(+%d)/%d", foodLevel, predictedNutrition, maxFood);
            textColor = (alpha << 24) | 0x88FF88;
            drawText(graphics, font, text, barX, barY, textColor, true);
        } else {
            text = String.format("%d/%d", displayFood, maxFood);
            drawText(graphics, font, text, barX, barY, 0xFFFFFFFF, false);
        }
    }

    // ===== 闪烁系统 =====

    public static void updateFlash(int guiTicks, boolean hasFood) {
        if (!hasFood) {
            unclampedFlashAlpha = 0f;
            flashAlpha = 0f;
            alphaDir = 1;
            lastGuiTicks = -1;
            return;
        }
        if (guiTicks == lastGuiTicks) return;
        lastGuiTicks = guiTicks;

        unclampedFlashAlpha += alphaDir * 0.125f;
        if (unclampedFlashAlpha >= 1.5f)      alphaDir = -1;
        else if (unclampedFlashAlpha <= -0.5f) alphaDir = 1;
        flashAlpha = Math.max(0f, Math.min(1f, unclampedFlashAlpha)) * 0.65f;
    }

    public static boolean flashActive(boolean hasFood) {
        return hasFood && flashAlpha > 0.01f;
    }

    // ===== 工具方法 =====

    static ItemStack findHeldFood(Player player) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty()) {
            if (main.getComponents().getOrDefault(DataComponents.FOOD, null) != null) return main;
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty()) {
            if (off.getComponents().getOrDefault(DataComponents.FOOD, null) != null) return off;
        }
        return null;
    }

    /** 根据饥饿值比例计算条颜色（棕色系） */
    private static int getFoodColor(float ratio) {
        int r, g, b;
        if (ratio > 0.5f) {
            float t = (ratio - 0.5f) * 2.0f;
            r = (int) (0x8B + (0xD4 - 0x8B) * t);
            g = (int) (0x45 + (0xA0 - 0x45) * t);
            b = (int) (0x13 + (0x17 - 0x13) * t);
        } else {
            float t = ratio * 2.0f;
            r = (int) (0x55 + (0x8B - 0x55) * t);
            g = (int) (0x20 + (0x45 - 0x20) * t);
            b = (int) (0x0A + (0x13 - 0x0A) * t);
        }
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static void drawText(GuiGraphicsExtractor graphics, Font font,
                                  String text, int barX, int barY, int color, boolean alwaysShadow) {
        int textWidth = font.width(text);
        int textX = barX + (HealthBarRenderer.BAR_WIDTH - textWidth) / 2;
        int textY = barY - TEXT_OFFSET_ABOVE_BAR;
        if (alwaysShadow || (color & 0xFF000000) != 0) {
            graphics.text(font, text, textX + TEXT_SHADOW_OFFSET, textY + TEXT_SHADOW_OFFSET,
                    0xFF000000, false);
        }
        graphics.text(font, text, textX, textY, color, false);
    }
}
