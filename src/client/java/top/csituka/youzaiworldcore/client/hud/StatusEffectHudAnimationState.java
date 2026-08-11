package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 单个状态效果 HUD 单元的入场、退场与布局滑动状态。
 *
 * <p>实例在效果离开活动列表后仍会短暂保留快照，以便完成收缩淡出；布局从完整行
 * 切换为简略行时，坐标和宽度也会连续插值，避免瞬移。</p>
 */
@SuppressWarnings("null")
final class StatusEffectHudAnimationState {

    static final long ENTER_DURATION_MS = 360L;
    static final long EXIT_DURATION_MS = 280L;
    static final long MOVE_DURATION_MS = 320L;

    private static final String MODULE = "StatusEffectHudAnimationState";
    private static final long INACTIVE = Long.MIN_VALUE;

    private final Holder<MobEffect> effect;
    private MobEffectInstance instance;
    private float x;
    private float y;
    private float width;
    private float height;
    private float moveFromX;
    private float moveFromY;
    private float moveFromWidth;
    private float moveFromHeight;
    private int targetX;
    private int targetY;
    private int targetWidth;
    private int targetHeight;
    private boolean compact;
    private boolean exiting;
    private long enterStartedAt = INACTIVE;
    private long exitStartedAt = INACTIVE;
    private long moveStartedAt = INACTIVE;

    StatusEffectHudAnimationState(Holder<MobEffect> effect,
            MobEffectInstance instance, Target target,
            long nowMillis, boolean animate) {
        this.effect = effect;
        this.instance = instance;
        this.x = target.x();
        this.y = target.y();
        this.width = target.width();
        this.height = target.height();
        this.targetX = target.x();
        this.targetY = target.y();
        this.targetWidth = target.width();
        this.targetHeight = target.height();
        this.compact = target.compact();
        if (animate) {
            enterStartedAt = nowMillis;
            trace("启动状态效果拉伸淡入动画: " + effect.getRegisteredName());
        }
    }

    Holder<MobEffect> effect() {
        return effect;
    }

    MobEffectInstance instance() {
        return instance;
    }

    boolean compact() {
        return compact;
    }

    boolean exiting() {
        return exiting;
    }

    int x() {
        return Math.round(x);
    }

    int y() {
        return Math.round(y);
    }

    int width() {
        return Math.max(1, Math.round(width));
    }

    int height() {
        return Math.max(1, Math.round(height));
    }

    void synchronize(MobEffectInstance current, Target target,
            long nowMillis, boolean animate) {
        advance(nowMillis);
        instance = current;

        if (exiting) {
            exiting = false;
            exitStartedAt = INACTIVE;
            if (animate) {
                enterStartedAt = nowMillis;
            }
        }

        boolean targetChanged = targetX != target.x()
                || targetY != target.y()
                || targetWidth != target.width()
                || targetHeight != target.height();
        compact = target.compact();
        if (!targetChanged) {
            return;
        }

        moveFromX = x;
        moveFromY = y;
        moveFromWidth = width;
        moveFromHeight = height;
        targetX = target.x();
        targetY = target.y();
        targetWidth = target.width();
        targetHeight = target.height();
        if (animate) {
            moveStartedAt = nowMillis;
            trace("启动状态效果布局滑动动画: " + effect.getRegisteredName());
        } else {
            moveStartedAt = INACTIVE;
            snapToTarget();
        }
    }

    void beginExit(long nowMillis) {
        if (exiting) {
            return;
        }
        advance(nowMillis);
        instance = new MobEffectInstance(instance);
        exiting = true;
        enterStartedAt = INACTIVE;
        exitStartedAt = nowMillis;
        trace("启动状态效果收缩淡出动画: " + effect.getRegisteredName());
    }

    void advance(long nowMillis) {
        if (moveStartedAt == INACTIVE) {
            return;
        }
        float progress = progress(moveStartedAt, MOVE_DURATION_MS, nowMillis);
        float eased = easeInOutCubic(progress);
        x = lerp(moveFromX, targetX, eased);
        y = lerp(moveFromY, targetY, eased);
        width = lerp(moveFromWidth, targetWidth, eased);
        height = lerp(moveFromHeight, targetHeight, eased);
        if (progress >= 1.0f) {
            moveStartedAt = INACTIVE;
            snapToTarget();
        }
    }

    boolean finished(long nowMillis) {
        return exiting
                && progress(exitStartedAt, EXIT_DURATION_MS, nowMillis) >= 1.0f;
    }

    float alpha(long nowMillis) {
        if (exiting) {
            float progress = progress(exitStartedAt, EXIT_DURATION_MS, nowMillis);
            return 1.0f - easeInCubic(progress);
        }
        float progress = progress(enterStartedAt, ENTER_DURATION_MS, nowMillis);
        return progress >= 1.0f ? 1.0f : easeOutCubic(progress);
    }

    void pushTransform(GuiGraphicsExtractor graphics, long nowMillis) {
        float scaleX = 1.0f;
        float scaleY = 1.0f;
        if (exiting) {
            float progress = easeInCubic(
                    progress(exitStartedAt, EXIT_DURATION_MS, nowMillis));
            scaleX = 1.0f - progress * 0.88f;
            scaleY = 1.0f - progress * 0.48f;
        } else {
            float progress = progress(enterStartedAt, ENTER_DURATION_MS, nowMillis);
            if (progress < 1.0f) {
                scaleX = 0.16f + 0.84f * easeOutBack(progress);
                scaleY = 0.74f + 0.26f * easeOutCubic(progress);
            }
        }

        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scaleX, scaleY);
        graphics.pose().translate(-centerX, -centerY);
    }

    void popTransform(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    private void snapToTarget() {
        x = targetX;
        y = targetY;
        width = targetWidth;
        height = targetHeight;
    }

    private static float progress(long startedAt, long duration, long nowMillis) {
        if (startedAt == INACTIVE) {
            return 1.0f;
        }
        return Math.clamp((nowMillis - startedAt) / (float) duration, 0.0f, 1.0f);
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    private static float easeInCubic(float value) {
        return value * value * value;
    }

    private static float easeOutCubic(float value) {
        float shifted = 1.0f - value;
        return 1.0f - shifted * shifted * shifted;
    }

    private static float easeInOutCubic(float value) {
        return value < 0.5f
                ? 4.0f * value * value * value
                : 1.0f - (float) Math.pow(-2.0f * value + 2.0f, 3.0) / 2.0f;
    }

    private static float easeOutBack(float value) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float shifted = value - 1.0f;
        return 1.0f + c3 * shifted * shifted * shifted
                + c1 * shifted * shifted;
    }

    private static void trace(String message) {
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(MODULE, message);
        }
    }

    record Target(int x, int y, int width, int height, boolean compact) {
    }
}
