package top.csituka.youzaiworldcore.client.render;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.MailBaseScreen;

/** 自定义屏幕的适配状态；弱引用不延长关闭屏幕的生命周期，HUD 不参与变换。 */
public final class YzuiViewport {
    private static final Map<Screen, YzuiLayout> LAYOUTS = new WeakHashMap<>();
    private static Screen renderingScreen;

    private YzuiViewport() { }

    /** 在原版写入真实宽高后、子类布局之前调用。邮件继续使用自己的设计视口。 */
    public static void configure(Screen screen, int width, int height) {
        if (!YzuiTheme.isCustomScreen(screen) || screen instanceof MailBaseScreen) return;
        boolean container = screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
        YzuiLayout layout = YzuiLayout.fit(width, height, container ? 380 : 480, container ? 280 : 360);
        LAYOUTS.put(screen, layout);
        screen.width = layout.width();
        screen.height = layout.height();
    }

    public static float scale(Screen screen) {
        YzuiLayout layout = LAYOUTS.get(screen);
        return layout == null ? 1f : layout.scale();
    }

    public static double toLogical(Screen screen, double coordinate) {
        return coordinate / scale(screen);
    }

    public static Screen beginRendering(Screen screen) {
        Screen previous = renderingScreen;
        renderingScreen = screen;
        return previous;
    }
    public static void endRendering() { renderingScreen = null; }
    public static Screen renderingScreen() { return renderingScreen; }
}
