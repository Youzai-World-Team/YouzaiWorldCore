package top.csituka.youzaiworldcore.client.screen.widget;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 为手动渲染的表单补齐 Tab / Shift+Tab 焦点遍历，不接触输入框中的文本。 */
public final class WidgetFocus {
    private WidgetFocus() {
    }

    @SuppressWarnings("null")
    public static boolean keyPressed(KeyEvent event, List<? extends AbstractWidget> widgets) {
        if (event.key() == InputConstants.KEY_TAB) {
            @SuppressWarnings("null")
            List<? extends AbstractWidget> available = widgets.stream().filter(w -> w.active && w.visible).toList();
            if (available.isEmpty())
                return true;
            int current = -1;
            for (int i = 0; i < available.size(); i++)
                if (available.get(i).isFocused())
                    current = i;
            int next = current < 0 ? (event.hasShiftDown() ? available.size() - 1 : 0)
                    : Math.floorMod(current + (event.hasShiftDown() ? -1 : 1), available.size());
            for (AbstractWidget widget : widgets)
                widget.setFocused(widget == available.get(next));
            return true;
        }
        for (AbstractWidget widget : widgets) {
            if (!(widget instanceof net.minecraft.client.gui.components.EditBox)
                    && widget.active && widget.visible && widget.isFocused() && widget.keyPressed(event))
                return true;
        }
        return false;
    }

    @SuppressWarnings("null")
    public static void mouseFocus(double x, double y, List<? extends AbstractWidget> widgets) {
        for (AbstractWidget widget : widgets)
            widget.setFocused(widget.active && widget.visible && widget.isMouseOver(x, y));
    }

    /** 菜单卡片按实际位置用方向键移动；表单和下拉框仍自行处理方向键。 */
    @SuppressWarnings("null")
    public static boolean moveInGrid(KeyEvent event, List<? extends AbstractWidget> widgets) {
        int dx = switch (event.key()) {
            case InputConstants.KEY_LEFT -> -1;
            case InputConstants.KEY_RIGHT -> 1;
            default -> 0;
        };
        int dy = switch (event.key()) {
            case InputConstants.KEY_UP -> -1;
            case InputConstants.KEY_DOWN -> 1;
            default -> 0;
        };
        if (dx == 0 && dy == 0)
            return false;
        @SuppressWarnings("null")
        var available = widgets.stream().filter(widget -> widget.active && widget.visible).toList();
        @SuppressWarnings("null")
        var current = available.stream().filter(AbstractWidget::isFocused).findFirst().orElse(null);
        if (current instanceof net.minecraft.client.gui.components.EditBox)
            return false;
        if (available.isEmpty())
            return true;
        if (current == null) {
            for (AbstractWidget widget : widgets)
                widget.setFocused(widget == available.getFirst());
            return true;
        }
        double centerX = current.getX() + current.getWidth() / 2.0;
        double centerY = current.getY() + current.getHeight() / 2.0;
        AbstractWidget next = null;
        double best = Double.POSITIVE_INFINITY;
        boolean bestAligned = false;
        for (AbstractWidget candidate : available) {
            @SuppressWarnings("null")
            double x = candidate.getX() + candidate.getWidth() / 2.0 - centerX;
            double y = candidate.getY() + candidate.getHeight() / 2.0 - centerY;
            double forward = dx == 0 ? y * dy : x * dx;
            if (candidate == current || forward <= 0)
                continue;
            boolean aligned = dx == 0
                    ? candidate.getX() < current.getRight() && candidate.getRight() > current.getX()
                    : candidate.getY() < current.getBottom() && candidate.getBottom() > current.getY();
            double sideways = dx == 0 ? x : y;
            double distance = forward * forward + sideways * sideways * 4;
            if (next == null || (aligned && !bestAligned) || (aligned == bestAligned && distance < best)) {
                next = candidate;
                best = distance;
                bestAligned = aligned;
            }
        }
        if (next != null) {
            for (AbstractWidget widget : widgets)
                widget.setFocused(widget == next);
            DebugLogger.debug("YzuiMenu", "方向键移动焦点：%s", next.getMessage().getString());
        }
        return true;
    }
}
