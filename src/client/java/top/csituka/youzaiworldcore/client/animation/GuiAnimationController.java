package top.csituka.youzaiworldcore.client.animation;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.GuiAnimationMode;
import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;
import top.csituka.youzaiworldcore.client.render.YzuiBackdrop;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 统一管理客户端页面的进入、退出和切换动画。
 *
 * <p>动画覆盖的页面退出时先保留当前屏幕并等待退出动画完成，再提交目标屏幕；
 * 这样原版页面和模组页面都能复用同一套动画，不要求逐个重写页面绘制代码。</p>
 */
public final class GuiAnimationController {

    private static final String MODULE = "GuiAnimationController";

    private static Screen currentScreen;
    private static Screen exitingScreen;
    private static Gui pendingGui;
    private static Screen pendingScreen;
    private static long enterStartedAt;
    private static long exitStartedAt;
    private static boolean transitionPending;
    private static boolean bypassSetScreen;
    private static YzuiMotion.Frame exitStartFrame = YzuiMotion.Frame.IDENTITY;
    private static Screen lastRenderedScreen;
    private static YzuiMotion.Frame lastRenderedFrame = YzuiMotion.Frame.IDENTITY;
    private static ContentState content = new ContentState(YzuiMotion.Frame.IDENTITY, 1f, false);
    // 图片状态稍后才合成到屏幕，保存提取时的透明度；弱引用不保留离开的页面或实体。
    private static final Map<Object, Float> PICTURE_OPACITY = Collections.synchronizedMap(new WeakHashMap<>());

    /** 可嵌套的绘制上下文；背景层和弹窗退出时必须恢复前一层的透明度。 */
    public record ContentState(YzuiMotion.Frame frame, float opacity, boolean background) { }

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

    /** 当前是否仅为模组页面、控件与弹窗启用动画。 */
    public static boolean isBasic() {
        return getMode() == GuiAnimationMode.BASIC;
    }

    /** 基础范围覆盖模组页面，完整范围还覆盖原版页面；两者共用同一套动效。 */
    public static boolean animates(Screen screen) {
        var mode = getMode();
        return screen != null && (mode == GuiAnimationMode.FULL
                || mode == GuiAnimationMode.BASIC && YzuiTheme.isCustomScreen(screen));
    }

    /** 当前是否允许项目原有的局部动画。关闭模式下所有局部动画都必须立即完成。 */
    public static boolean isEnabled() {
        return getMode() != GuiAnimationMode.OFF;
    }

    /** 读取当前配置，配置读取失败时由配置层负责报告并终止。 */
    public static GuiAnimationMode getMode() {
        if (ClientExternalSettings.getYzuiVisualStyle() == YzuiVisualStyle.MINIMAL) {
            return GuiAnimationMode.OFF;
        }
        return ClientExternalSettings.getGuiAnimationMode();
    }

    private static long enterDuration() {
        return YzuiMotion.enterDuration(ClientExternalSettings.getYzuiVisualStyle());
    }

    private static long exitDuration() {
        return YzuiMotion.exitDuration(ClientExternalSettings.getYzuiVisualStyle());
    }

    /** 配置在运行中变化时结束遗留切换，并避免当前页面突然重新播放进入动画。 */
    public static void onModeChanged(GuiAnimationMode mode) {
        if (mode != GuiAnimationMode.FULL && transitionPending) {
            finishPendingTransition();
        }
        enterStartedAt = 0L;
        lastRenderedScreen = null;
    }

    /**
     * 在 {@code Gui.setScreen} 真正写入字段前拦截页面切换。
     *
     * @return 是否已经接管本次切换，调用方应取消原方法继续执行
     */
    public static boolean interceptScreenChange(Gui gui, Screen newScreen) {
        if (isBackgroundRendering()) return true;
        if (!animates(gui.screen()) || bypassSetScreen) {
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

        // 打开子层时父页面仍在背景中，直接让新卡片进入，避免父页面先消失再出现。
        if (YzuiBackdrop.willLayer(oldScreen, newScreen)) return false;

        pendingGui = gui;
        pendingScreen = newScreen;
        exitStartFrame = inputFrame(oldScreen);
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
        boolean returning = YzuiBackdrop.isReturning(currentScreen, newScreen);
        YzuiBackdrop.onScreenChanged(currentScreen, newScreen);
        currentScreen = newScreen;
        lastRenderedScreen = null;
        if (newScreen != null) {
            enterStartedAt = returning ? 0L : System.currentTimeMillis();
            DebugLogger.debug(MODULE, "页面进入动画开始：%s", newScreen.getClass().getSimpleName());
        }
    }

    /** 在每帧 GUI 提取前提交已经完成退出动画的目标页面。 */
    public static void tick() {
        if (!transitionPending) {
            return;
        }
        if (!animates(exitingScreen)) {
            finishPendingTransition();
            return;
        }
        if (progress(exitStartedAt, exitDuration()) >= 1.0F) {
            finishPendingTransition();
        }
    }

    /**
     * 返回页面这一帧的透明度、缩放和纵向位移。进入时轻微上移归位，退出时反向收起。
     */
    public static YzuiMotion.Frame frame(Screen screen) {
        if (!animates(screen) || isBackgroundRendering()) return YzuiMotion.Frame.IDENTITY;
        var style = ClientExternalSettings.getYzuiVisualStyle();
        if (transitionPending && screen == exitingScreen) {
            return YzuiMotion.exit(style, exitStartFrame, progress(exitStartedAt, exitDuration()), screen.height);
        }
        return screen == currentScreen
                ? YzuiMotion.enter(style, progress(enterStartedAt, enterDuration()), screen.height)
                : YzuiMotion.Frame.IDENTITY;
    }

    /**
     * 返回当前页面在进入或退出阶段的可见度，供固定遮罩执行淡入淡出。
     */
    public static float getScreenOpacity() {
        return inputFrame(currentScreen).opacity();
    }

    public static YzuiMotion.Frame sampleFrame(Screen screen) {
        lastRenderedScreen = screen;
        lastRenderedFrame = frame(screen);
        return lastRenderedFrame;
    }

    /** 输入使用刚刚显示的帧，而不是事件处理时重新采样动画，避免低帧率下的命中漂移。 */
    public static YzuiMotion.Frame inputFrame(Screen screen) {
        if (!animates(screen)) return YzuiMotion.Frame.IDENTITY;
        return screen == lastRenderedScreen ? lastRenderedFrame : frame(screen);
    }

    public static ContentState pushContent(YzuiMotion.Frame frame) {
        ContentState previous = content;
        content = new ContentState(frame, previous.opacity() * frame.opacity(), previous.background());
        return previous;
    }

    public static ContentState suspendContent(boolean background) {
        ContentState previous = content;
        content = new ContentState(YzuiMotion.Frame.IDENTITY, 1f, background || previous.background());
        return previous;
    }

    public static void restoreContent(ContentState previous) { content = previous; }
    public static boolean isBackgroundRendering() { return content.background(); }
    public static float contentOpacity() { return content.opacity(); }
    public static int fadeColor(int color) {
        return (Math.round((color >>> 24) * content.opacity()) << 24) | (color & 0xFFFFFF);
    }

    public static boolean isExiting(Screen screen) { return transitionPending && exitingScreen == screen; }

    public static void capturePictureOpacity(Object state) {
        if (content.opacity() < 1f) PICTURE_OPACITY.put(state, content.opacity());
        else PICTURE_OPACITY.remove(state);
    }

    public static float pictureOpacity(Object state) { return PICTURE_OPACITY.getOrDefault(state, 1f); }

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
        return YzuiMotion.progress(System.currentTimeMillis() - startedAt, durationMs);
    }
}
