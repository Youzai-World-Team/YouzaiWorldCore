package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 装备栏与物品栏共用的槽位动画状态。
 *
 * <p>负责检测物品出现、消失、显示数量增加和耐久修复，并提供对应的
 * 缩放、遮罩、高光与低耐久摇晃参数。每个 HUD 槽位应持有独立实例。</p>
 */
public final class HudSlotAnimationState {

    private static final String MODULE = "HudSlotAnimationState";

    public static final long ENTER_DURATION_MS = 320L;
    public static final long EXIT_DURATION_MS = 260L;
    public static final long COUNT_PULSE_DURATION_MS = 420L;
    public static final long REPAIR_PULSE_DURATION_MS = 520L;

    private static final long INACTIVE = Long.MIN_VALUE;

    private final CachedItemRenderer outgoingRenderer = new CachedItemRenderer();
    private ItemStack observedStack = ItemStack.EMPTY;
    private ItemStack observedSource = ItemStack.EMPTY;
    private ItemStack outgoingStack = ItemStack.EMPTY;
    private int observedDisplayCount;
    private int outgoingDisplayCount;
    private int observedDamage;
    private long enterStartedAt = INACTIVE;
    private long exitStartedAt = INACTIVE;
    private long countPulseStartedAt = INACTIVE;
    private long repairPulseStartedAt = INACTIVE;
    private boolean initialized;

    /**
     * 同步槽位当前状态并在变化时启动动画。
     *
     * @param stack 当前物品
     * @param displayCount HUD 实际显示的数量
     * @param nowMillis 当前动画时间戳（毫秒）
     * @param animate 是否为本次变化播放动画
     */
    public void synchronize(ItemStack stack, int displayCount,
            long nowMillis, boolean animate) {
        ItemStack current = stack == null ? ItemStack.EMPTY : stack;
        if (!initialized || !animate) {
            observedStack = current.copy();
            observedSource = current;
            observedDisplayCount = displayCount;
            observedDamage = current.isEmpty() ? 0 : current.getDamageValue();
            outgoingStack = ItemStack.EMPTY;
            clearTimelines();
            initialized = true;
            return;
        }

        boolean wasEmpty = observedStack.isEmpty();
        boolean isEmpty = current.isEmpty();
        boolean sameItem = !wasEmpty && !isEmpty && observedStack.is(current.getItem());

        if (wasEmpty && !isEmpty) {
            beginEnter(nowMillis);
        } else if (!wasEmpty && isEmpty) {
            beginExit(nowMillis);
        } else if (!wasEmpty && !isEmpty && !sameItem) {
            beginExit(nowMillis);
            beginEnter(nowMillis);
        } else if (sameItem) {
            if (displayCount > observedDisplayCount) {
                countPulseStartedAt = nowMillis;
                trace("启动数量增加脉冲动画");
            }
            int damage = current.getDamageValue();
            if (current.isDamageableItem() && damage < observedDamage) {
                repairPulseStartedAt = nowMillis;
                trace("启动耐久修复脉冲动画");
            }
        }

        int currentDamage = current.isEmpty() ? 0 : current.getDamageValue();
        if (current != observedSource
                || wasEmpty != isEmpty
                || current.getCount() != observedStack.getCount()
                || currentDamage != observedDamage) {
            observedStack = current.copy();
            observedSource = current;
        }
        observedDisplayCount = displayCount;
        observedDamage = currentDamage;
    }

    /** 返回仍处于退场时间轴内的旧物品，动画结束后返回空。 */
    public ItemStack outgoingStack(long nowMillis) {
        if (outgoingStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (progress(exitStartedAt, EXIT_DURATION_MS, nowMillis) >= 1.0f) {
            outgoingStack = ItemStack.EMPTY;
            exitStartedAt = INACTIVE;
            return ItemStack.EMPTY;
        }
        return outgoingStack;
    }

    /** 返回退场物品专用的缓存模型渲染器。 */
    public CachedItemRenderer outgoingRenderer() {
        return outgoingRenderer;
    }

    /** 返回退场快照对应的 HUD 显示数量。 */
    public int outgoingDisplayCount() {
        return outgoingDisplayCount;
    }

    /**
     * 压入当前物品的缩放、数量脉冲与低耐久摇晃矩阵。
     */
    public void pushCurrentTransform(GuiGraphicsExtractor graphics, ItemStack stack,
            float centerX, float centerY, long nowMillis) {
        float scale = currentScale(nowMillis);
        float danger = durabilityDanger(stack);
        float shakeX = 0.0f;
        float shakeY = 0.0f;
        if (danger > 0.0f) {
            double phase = nowMillis * (0.035 + danger * 0.025);
            shakeX = (float) Math.sin(phase) * (0.45f + danger * 1.35f);
            shakeY = (float) Math.cos(phase * 1.7) * danger * 0.45f;
        }
        pushTransform(graphics, centerX, centerY, scale, scale, shakeX, shakeY);
    }

    /** 压入旧物品的收缩退场矩阵。 */
    public void pushOutgoingTransform(GuiGraphicsExtractor graphics,
            float centerX, float centerY, long nowMillis) {
        float p = easeInCubic(progress(exitStartedAt, EXIT_DURATION_MS, nowMillis));
        float scale = 1.0f - p * 0.78f;
        pushTransform(graphics, centerX, centerY, scale, scale, 0.0f, p * 2.0f);
    }

    /** 弹出由本类压入的物品动画矩阵。 */
    public void popTransform(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    /** 绘制物品出现、数量增加、修复和低耐久警告叠加层。 */
    public void drawCurrentOverlay(GuiGraphicsExtractor graphics, ItemStack stack,
            int x, int y, int width, int height, long nowMillis) {
        float enter = progress(enterStartedAt, ENTER_DURATION_MS, nowMillis);
        if (enter < 1.0f) {
            float flicker = 0.55f + 0.45f
                    * Math.abs((float) Math.sin(enter * Math.PI * 3.0));
            int alpha = Math.round((1.0f - enter) * flicker * 155.0f);
            graphics.fill(x, y, x + width, y + height,
                    YzHudLayout.applyOpacity(ARGB.color(alpha, 255, 255, 255)));
        }

        float countPulse = progress(countPulseStartedAt, COUNT_PULSE_DURATION_MS, nowMillis);
        if (countPulse < 1.0f) {
            int alpha = Math.round((float) Math.sin(countPulse * Math.PI) * 105.0f);
            graphics.fill(x, y, x + width, y + height,
                    YzHudLayout.applyOpacity(ARGB.color(alpha, 125, 255, 145)));
        }

        float repairPulse = progress(repairPulseStartedAt, REPAIR_PULSE_DURATION_MS, nowMillis);
        if (repairPulse < 1.0f) {
            int alpha = Math.round((float) Math.sin(repairPulse * Math.PI) * 85.0f);
            graphics.fill(x, y, x + width, y + height,
                    YzHudLayout.applyOpacity(ARGB.color(alpha, 100, 235, 255)));
        }

        float danger = durabilityDanger(stack);
        if (danger > 0.0f) {
            float pulse = 0.45f + 0.55f
                    * Math.abs((float) Math.sin(nowMillis * 0.008));
            int alpha = Math.round((18.0f + danger * 42.0f) * pulse);
            graphics.fill(x, y, x + width, y + height,
                    YzHudLayout.applyOpacity(ARGB.color(alpha, 255, 55, 55)));
        }
    }

    /** 绘制退场物品逐渐与槽位背景融合的遮罩。 */
    public void drawOutgoingOverlay(GuiGraphicsExtractor graphics,
            int x, int y, int width, int height, long nowMillis) {
        float p = progress(exitStartedAt, EXIT_DURATION_MS, nowMillis);
        int alpha = Math.round(easeInCubic(p) * 190.0f);
        graphics.fill(x, y, x + width, y + height,
                YzHudLayout.applyOpacity(ARGB.color(alpha, 255, 255, 255)));
    }

    private void beginExit(long nowMillis) {
        outgoingStack = observedStack.copy();
        outgoingDisplayCount = observedDisplayCount;
        outgoingRenderer.invalidate();
        exitStartedAt = nowMillis;
        trace("启动物品退场动画");
    }

    private void beginEnter(long nowMillis) {
        enterStartedAt = nowMillis;
        trace("启动物品入场动画");
    }

    private void clearTimelines() {
        enterStartedAt = INACTIVE;
        exitStartedAt = INACTIVE;
        countPulseStartedAt = INACTIVE;
        repairPulseStartedAt = INACTIVE;
    }

    private static void trace(String message) {
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(MODULE, message);
        }
    }

    private float currentScale(long nowMillis) {
        float scale = 1.0f;
        float enter = progress(enterStartedAt, ENTER_DURATION_MS, nowMillis);
        if (enter < 1.0f) {
            scale *= 0.58f + 0.42f * easeOutBack(enter);
        }
        float countPulse = progress(countPulseStartedAt, COUNT_PULSE_DURATION_MS, nowMillis);
        if (countPulse < 1.0f) {
            scale *= 1.0f + (float) Math.sin(countPulse * Math.PI) * 0.16f;
        }
        float repairPulse = progress(repairPulseStartedAt, REPAIR_PULSE_DURATION_MS, nowMillis);
        if (repairPulse < 1.0f) {
            scale *= 1.0f + (float) Math.sin(repairPulse * Math.PI) * 0.08f;
        }
        return scale;
    }

    private static float durabilityDanger(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return 0.0f;
        }
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return 0.0f;
        }
        int remaining = Math.max(0, maxDamage - stack.getDamageValue());
        float ratio = remaining / (float) maxDamage;
        if (ratio > 0.10f) {
            return 0.0f;
        }
        return 0.15f + 0.85f * (1.0f - ratio / 0.10f);
    }

    private static void pushTransform(GuiGraphicsExtractor graphics,
            float centerX, float centerY, float scaleX, float scaleY,
            float offsetX, float offsetY) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX + offsetX, centerY + offsetY);
        graphics.pose().scale(scaleX, scaleY);
        graphics.pose().translate(-centerX, -centerY);
    }

    private static float progress(long startedAt, long duration, long nowMillis) {
        if (startedAt == INACTIVE) {
            return 1.0f;
        }
        return Math.clamp((nowMillis - startedAt) / (float) duration, 0.0f, 1.0f);
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float shifted = t - 1.0f;
        return 1.0f + c3 * shifted * shifted * shifted
                + c1 * shifted * shifted;
    }
}
