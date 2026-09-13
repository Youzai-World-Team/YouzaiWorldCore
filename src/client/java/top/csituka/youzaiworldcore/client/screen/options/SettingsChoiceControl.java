package top.csituka.youzaiworldcore.client.screen.options;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 设置中心的开关与下拉选择适配；保存、校验和联动仍执行原版控件注册的回调。 */
final class SettingsChoiceControl {
    private SettingsChoiceControl() { }

    static AbstractWidget adapt(AbstractWidget widget) {
        return widget instanceof CycleButton<?> cycle ? adaptCycle(cycle) : widget;
    }

    private static <T> AbstractWidget adaptCycle(CycleButton<T> cycle) {
        Binding<T> binding = new Binding<>(cycle);
        AbstractWidget control = binding.isOnOff() ? new Toggle<>(binding) : new Selection<>(binding);
        binding.control = (BoundControl) control;
        binding.control.synchronize();
        return control;
    }

    static AbstractWidget source(AbstractWidget widget) {
        return widget instanceof BoundControl bound ? bound.binding().source : widget;
    }

    static void synchronize(AbstractWidget widget) {
        if (widget instanceof BoundControl bound) bound.synchronize();
    }

    static String searchText(AbstractWidget widget) {
        // 控件只显示独立的名称，但搜索仍包含原版的当前值。
        return widget instanceof BoundControl bound ? bound.binding().source.createDefaultNarrationMessage().getString()
                : widget.getMessage().getString();
    }

    private interface BoundControl {
        Binding<?> binding();
        void synchronize();
    }

    private static final class Binding<T> {
        private final CycleButton<T> source;
        private final Component name;
        private final CycleButton.ValueListSupplier<T> values;
        private final Function<T, Component> labels;
        private final CycleButton.OnValueChange<T> onChange;
        private final WidgetTooltipHolder tooltipHolder;
        private Tooltip tooltip;
        private BoundControl control;
        private List<T> presented = List.of();

        @SuppressWarnings("unchecked")
        private Binding(CycleButton<T> source) {
            this.source = source;
            name = SettingsWidgets.read(source, "name", Component.class);
            values = SettingsWidgets.read(source, "values", CycleButton.ValueListSupplier.class);
            labels = SettingsWidgets.read(source, "valueStringifier", Function.class);
            onChange = SettingsWidgets.read(source, "onValueChange", CycleButton.OnValueChange.class);
            tooltipHolder = SettingsWidgets.read(source, "tooltip", WidgetTooltipHolder.class);
        }

        private boolean isOnOff() {
            List<T> defaults = values.getDefaultList();
            // 按住/切换、左/右等二选一模式也用下拉框，只有明确的开/关使用开关。
            return defaults.size() == 2 && defaults.stream().anyMatch(value -> isOnOffValue(value, true))
                    && defaults.stream().anyMatch(value -> isOnOffValue(value, false));
        }

        private boolean isOnOffValue(T value, boolean on) {
            return SettingsWidgets.key(labels.apply(value), on ? "options.on" : "options.off");
        }

        private boolean isOn() { return isOnOffValue(source.getValue(), true); }

        private void synchronize(AbstractWidget widget) {
            widget.active = source.active && !values.getSelectedList().isEmpty();
            widget.visible = source.visible;
            widget.setAlpha(source.getAlpha());
            widget.setTabOrderGroup(source.getTabOrderGroup());
            Tooltip current = tooltipHolder.get();
            if (current != tooltip) {
                tooltip = current;
                widget.setTooltip(current);
            }
        }

        private void toggle() {
            boolean next = !isOn();
            for (T value : values.getSelectedList()) {
                if (isOnOffValue(value, next)) {
                    choose(value);
                    return;
                }
            }
            control.synchronize();
        }

        private void select(int index) {
            if (index >= 0 && index < presented.size()) choose(presented.get(index));
        }

        private void choose(T value) {
            try {
                if (!source.active || !source.visible || !values.getSelectedList().contains(value)
                        || Objects.equals(source.getValue(), value)) return;
                // setValue 本身不执行保存回调；直接应用一次目标值，避免循环经过中间选项。
                source.setValue(value);
                onChange.onValueChange(source, value);
                DebugLogger.debug("YzuiSettings", "设置选项：%s → %s", name.getString(), labels.apply(source.getValue()).getString());
            } finally {
                control.synchronize();
            }
        }
    }

    private static final class Toggle<T> extends CheckboxButton implements BoundControl {
        private final Binding<T> binding;

        private Toggle(Binding<T> binding) {
            super(0, 0, 160, 24, binding.name, binding.isOn(), binding::toggle);
            this.binding = binding;
            setWrapMessage(true);
        }

        @Override public Binding<T> binding() { return binding; }
        @Override public void synchronize() {
            binding.synchronize(this);
            setChecked(binding.isOn());
        }
        @Override protected MutableComponent createNarrationMessage() {
            return binding.source.createDefaultNarrationMessage();
        }
    }

    private static final class Selection<T> extends DropdownButton implements BoundControl {
        private final Binding<T> binding;

        private Selection(Binding<T> binding) {
            super(0, 0, 160, 160, 24, binding.name, List.of(), -1, false, binding::select, null);
            this.binding = binding;
        }

        @Override public Binding<T> binding() { return binding; }
        @Override public void synchronize() {
            binding.synchronize(this);
            List<T> available = List.copyOf(binding.values.getSelectedList());
            // 动态可选范围改变时关闭旧菜单，旧条目的索引不能指向另一批值。
            if (!available.equals(binding.presented)) closePopup();
            binding.presented = available;
            updateOptions(available.stream().map(value -> binding.labels.apply(value).getString()).toList(),
                    available.indexOf(binding.source.getValue()), binding.labels.apply(binding.source.getValue()).getString());
            if (!active || !visible) closePopup();
        }
        @Override protected MutableComponent createNarrationMessage() {
            return binding.source.createDefaultNarrationMessage();
        }
    }
}
