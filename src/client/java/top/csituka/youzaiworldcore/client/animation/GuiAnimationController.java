package top.csituka.youzaiworldcore.client.animation;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.GuiAnimationMode;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 统一管理客户端页面的进入、退出和切换动画。
 *
 * <p>完全模式下，屏幕退出会先保留当前屏幕并等待退出动画完成，再提交目标屏幕；
 * 这样原版页面和模组页面都能复用同一套动画，不要求逐个重写页面绘制代码。</p>
 */
public final class GuiAnimationController {

    private static final String MODULE = "GuiAnimationController";
    private static final long ENTER_DURATION_MS = 260L;
    private static final long EXIT_DURATION_MS = 200L;
    private static final float MAX_OFFSET_RATIO = 0.08F;

    private static Screen currentScreen;
    private static Screen exitingScreen;
    private static Gui pendingGui;
    private static Screen pendingScreen;
    private static long enterStartedAt;
    private static long exitStartedAt;
    private static boolean transitionPending;
    private static boolean bypassSetScreen;
    private static boolean contentTransformActive;
    private static float contentTransformDisplacement;

    private GuiAnimationController() {
    }

    /** 当前是否完全关闭动画。 */
    public static boolean isDisabled() {
        return getMode() == GuiAnimationMode.OFF;
    }

    /** 当前是否启用完整页面动画。 */
    public static boolean isFull() {
        return getMode() == GuiAnimationMode.FULL;
    }

    /** 当前是否仅保留项目原有的基础动画。 */
    public static boolean isBasic() {
        return getMode() == GuiAnimationMode.BASIC;
    }

    /** 当前是否允许项目原有的局部动画。关闭模式下所有局部动画都必须立即完成。 */
    public static boolean isEnabled() {
        return getMode() != GuiAnimationMode.OFF;
    }

    /** 读取当前配置，配置读取失败时由配置层负责报告并终止。 */
    public static GuiAnimationMode getMode() {
        return ClientExternalSettings.getGuiAnimationMode();
    }

    /** 配置在运行中变化时结束遗留切换，并避免当前页面突然重新播放进入动画。 */
    public static void onModeChanged(GuiAnimationMode mode) {
        if (mode != GuiAnimationMode.FULL && transitionPending) {
            finishPendingTransition();
        }
        enterStartedAt = 0L;
    }

    /**
     * 在 {@code Gui.setScreen} 真正写入字段前拦截页面切换。
     *
     * @return 是否已经接管本次切换，调用方应取消原方法继续执行
     */
    public static boolean interceptScreenChange(Gui gui, Screen newScreen) {
        if (!isFull() || bypassSetScreen) {
            return false;
        }

        Screen oldScreen = gui.screen();
        if (oldScreen == newScreen || oldScreen == null) {
            return false;
        }

        if (transitionPending) {
            pendingGui = gui;
            pendingScreen = newScreen;
            return true;
        }

        pendingGui = gui;
        pendingScreen = newScreen;
        exitingScreen = oldScreen;
        exitStartedAt = System.currentTimeMillis();
        transitionPending = true;
        DebugLogger.info(MODULE, "接管页面切换：%s -> %s",
                oldScreen.getClass().getSimpleName(),
                newScreen == null ? "游戏画面" : newScreen.getClass().getSimpleName());
        return true;
    }

    /** 在屏幕字段写入后登记新的页面进入时间。 */
    public static void onScreenChanged(Screen newScreen) {
        currentScreen = newScreen;
        if (newScreen != null) {
            enterStartedAt = System.currentTimeMillis();
            DebugLogger.debug(MODULE, "页面进入动画开始：%s", newScreen.getClass().getSimpleName());
        }
    }

    /** 在每帧 GUI 提取前提交已经完成退出动画的目标页面。 */
    public static void tick() {
        if (!transitionPending) {
            return;
        }
        if (!isFull()) {
            finishPendingTransition();
            return;
        }
        if (progress(exitStartedAt, EXIT_DURATION_MS) >= 1.0F) {
            finishPendingTransition();
        }
    }

    /**
     * 返回指定页面当前帧的纵向位移。正值表示页面位于正常位置下方：
     * 页面进入时从下方上移到原位，退出时从原位向下移出。
     */
    public static float getDisplacement(Screen screen, int height) {
        if (!isFull() || screen == null) {
            return 0.0F;
        }

        if (transitionPending && screen == exitingScreen) {
            float progress = progress(exitStartedAt, EXIT_DURATION_MS);
            return height * MAX_OFFSET_RATIO * easeInCubic(progress);
        }

        if (screen == currentScreen) {
            float progress = progress(enterStartedAt, ENTER_DURATION_MS);
            return height * MAX_OFFSET_RATIO * (1.0F - easeOutCubic(progress));
        }

        return 0.0F;
    }

    /**
     * 返回当前页面在进入或退出阶段的可见度，供固定遮罩执行淡入淡出。
     */
    public static float getScreenOpacity() {
        if (!isFull() || currentScreen == null) {
            return 1.0F;
        }

        if (transitionPending && currentScreen == exitingScreen) {
            return 1.0F - easeInCubic(progress(exitStartedAt, EXIT_DURATION_MS));
        }

        return easeOutCubic(progress(enterStartedAt, ENTER_DURATION_MS));
    }

    /** 登记当前正在使用位移矩阵提取页面内容，供全屏遮罩抵消该位移。 */
    public static void beginContentTransform(float displacement) {
        contentTransformActive = true;
        contentTransformDisplacement = displacement;
    }

    /** 结束当前页面内容的位移矩阵。 */
    public static void endContentTransform() {
        contentTransformActive = false;
        contentTransformDisplacement = 0.0F;
    }

    /** @return 当前页面内容矩阵的纵向位移；未应用矩阵时返回零 */
    public static float getContentTransformDisplacement() {
        return contentTransformActive ? contentTransformDisplacement : 0.0F;
    }

    /** 页面渲染动画期间的鼠标 Y 偏移，供输入层反向换算使用。 */
    public static double getInputYOffset(Screen screen) {
        if (screen == null) {
            return 0.0D;
        }
        return getDisplacement(screen, screen.height);
    }

    /** 完成延迟切换；只在渲染线程调用。 */
    private static void finishPendingTransition() {
        if (!transitionPending || pendingGui == null) {
            return;
        }

        Gui gui = pendingGui;
        Screen next = pendingScreen;
        transitionPending = false;
        pendingGui = null;
        pendingScreen = null;
        exitingScreen = null;

        boolean oldBypass = bypassSetScreen;
        bypassSetScreen = true;
        try {
            gui.setScreen(next);
        } finally {
            bypassSetScreen = oldBypass;
        }
    }

    private static float progress(long startedAt, long durationMs) {
        if (startedAt <= 0L) {
            return 1.0F;
        }
        return Math.min(1.0F, Math.max(0.0F,
                (System.currentTimeMillis() - startedAt) / (float) durationMs));
    }

    private static float easeInCubic(float value) {
        return value * value * value;
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }
}
