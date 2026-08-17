package top.csituka.youzaiworldcore.client.screen.widget;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * 为屏幕预览控件覆盖全局 YZUI 样式选择。
 * <p>采用弱引用保存控件，界面关闭后不会阻止预览控件被回收。</p>
 */
public final class YzuiStyleOverride {

    public static final int STYLE_INHERIT = 0;
    public static final int STYLE_VANILLA = 1;
    public static final int STYLE_YZUI = 2;

    private static final Map<AbstractWidget, Integer> OVERRIDES = new WeakHashMap<>();

    private YzuiStyleOverride() {
    }

    /** 为指定控件设置预览样式。 */
    public static void set(AbstractWidget widget, int styleOverride) {
        if (styleOverride == STYLE_INHERIT) {
            OVERRIDES.remove(widget);
        } else {
            OVERRIDES.put(widget, styleOverride);
        }
    }

    /** @return 指定控件的样式覆盖模式 */
    public static int get(AbstractWidget widget) {
        return OVERRIDES.getOrDefault(widget, STYLE_INHERIT);
    }
}
