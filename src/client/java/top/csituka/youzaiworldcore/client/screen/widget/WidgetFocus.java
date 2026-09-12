package top.csituka.youzaiworldcore.client.screen.widget;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;

/** 为手动渲染的表单补齐 Tab / Shift+Tab 焦点遍历，不接触输入框中的文本。 */
public final class WidgetFocus {
    private WidgetFocus() { }

    public static boolean keyPressed(KeyEvent event, List<? extends AbstractWidget> widgets) {
        if (event.key() == 258) {
            List<? extends AbstractWidget> available = widgets.stream().filter(w -> w.active && w.visible).toList();
            if (available.isEmpty()) return true;
            int current = -1;
            for (int i = 0; i < available.size(); i++) if (available.get(i).isFocused()) current = i;
            int next = current < 0 ? (event.hasShiftDown() ? available.size() - 1 : 0)
                    : Math.floorMod(current + (event.hasShiftDown() ? -1 : 1), available.size());
            for (AbstractWidget widget : widgets) widget.setFocused(widget == available.get(next));
            return true;
        }
        for (AbstractWidget widget : widgets) {
            if (!(widget instanceof net.minecraft.client.gui.components.EditBox)
                    && widget.active && widget.visible && widget.isFocused() && widget.keyPressed(event)) return true;
        }
        return false;
    }

    public static void mouseFocus(double x, double y, List<? extends AbstractWidget> widgets) {
        for (AbstractWidget widget : widgets) widget.setFocused(widget.active && widget.visible && widget.isMouseOver(x, y));
    }
}
