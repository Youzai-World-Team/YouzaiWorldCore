package top.csituka.youzaiworldcore.client.screen.options;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

/** 读取原版页面生成的控件与数据，保留原有回调；不修改原版页面的布局或渲染方法。 */
final class SettingsWidgets {
    private SettingsWidgets() { }

    static List<AbstractWidget> flatten(Screen screen) {
        List<AbstractWidget> widgets = new ArrayList<>();
        Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(screen, widgets, visited);
        return widgets;
    }

    /** 读取原版选项与控件的对应关系，用于同屏去重及预设改变后的值同步。 */
    static Map<AbstractWidget, OptionInstance<?>> options(Screen screen) {
        Map<AbstractWidget, OptionInstance<?>> result = new IdentityHashMap<>();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof OptionsList list)) continue;
            for (Object row : list.children()) {
                // Entry 在 InnerClasses 属性中为 protected，使用其已核验的类型名识别，兼容派生行。
                Class<?> type = row.getClass();
                while (type != null && !type.getName().equals("net.minecraft.client.gui.components.OptionsList$Entry")) {
                    type = type.getSuperclass();
                }
                if (type == null) continue;
                for (Object value : read(row, "children", List.class)) {
                    if (value instanceof OptionsList.OptionInstanceWidget binding && binding.optionInstance() != null) {
                        result.put(binding.widget(), binding.optionInstance());
                    }
                }
            }
        }
        return result;
    }

    static AbstractWidget focused(GuiEventListener listener) {
        while (listener instanceof ContainerEventHandler container && container.getFocused() != null) {
            listener = container.getFocused();
        }
        return listener instanceof AbstractWidget widget ? widget : null;
    }

    private static void collect(GuiEventListener listener, List<AbstractWidget> widgets,
                                Set<GuiEventListener> visited) {
        if (!visited.add(listener)) return;
        if (listener instanceof AbstractWidget widget && !widget.visible) return;
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) collect(child, widgets, visited);
        } else if (listener instanceof AbstractWidget widget) {
            widgets.add(widget);
        }
    }

    static void flush(Screen source) {
        for (GuiEventListener child : source.children()) {
            if (child instanceof OptionsList list) list.applyUnsavedChanges();
        }
    }

    /** 统一页只提交仍有待应用值的滑条，避免旧模型或隐藏的重复控件覆盖最新配置。 */
    static void flushPending(List<AbstractWidget> widgets) {
        for (AbstractWidget widget : widgets) {
            if (widget instanceof OptionInstance.OptionInstanceSliderButton<?> slider
                    && read(slider, "delayedApplyAt", Long.class) != null) {
                slider.applyUnsavedValue();
            }
        }
    }

    static boolean key(Component text, String key) {
        if (text.getContents() instanceof TranslatableContents translated && translated.getKey().equals(key)) return true;
        return false;
    }

    static String plainText(FormattedCharSequence line) {
        StringBuilder text = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }

    /** 26.2 未提供公开 getter 的只读数据；字段签名已对本地官方 jar 核验。 */
    static <T> T read(Object object, String name, Class<T> type) {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return type.cast(field.get(object));
            } catch (NoSuchFieldException ignored) {
                // 沿继承链查找，第三方子类也可以使用原版数据模型。
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("无法读取设置页面字段：" + name, exception);
            }
        }
        throw new IllegalStateException("设置页面字段不存在：" + object.getClass().getName() + "." + name);
    }
}
