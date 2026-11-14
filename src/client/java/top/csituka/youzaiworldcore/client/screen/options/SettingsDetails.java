package top.csituka.youzaiworldcore.client.screen.options;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.config.YzuiThemeMode;
import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.YzHudSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.map.YzWorldMapScreen;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 设置中心与地图子页共用真实设置定义；浏览、重排和同步不写配置。 */
final class SettingsDetails {
    enum MapGroup { GENERAL, APPEARANCE, INFORMATION, MOBS, TERRAIN, NAVIGATION }

    private SettingsDetails() { }

    static void appearance(List<SettingsList.Row> rows) {
        choice(rows, Component.translatable("screen.youzaiworldcore.appearance.theme"), YzuiThemeMode.values(),
                ClientExternalSettings::getYzuiTheme, ClientExternalSettings::setYzuiTheme,
                value -> Component.translatable("screen.youzaiworldcore.appearance.theme." + value.name().toLowerCase(Locale.ROOT)));
        choice(rows, Component.translatable("screen.youzaiworldcore.appearance.effects"), YzuiVisualStyle.values(),
                ClientExternalSettings::getYzuiVisualStyle, ClientExternalSettings::setYzuiVisualStyle,
                value -> Component.translatable("screen.youzaiworldcore.appearance.style." + value.name().toLowerCase(Locale.ROOT)));
        for (YzuiVisualStyle style : YzuiVisualStyle.values()) {
            rows.getLast().addSearchTerms(Component.translatable("screen.youzaiworldcore.appearance.style."
                    + style.name().toLowerCase(Locale.ROOT) + ".description").getString());
        }
    }

    static Component mapGroupTitle(MapGroup group) {
        return group == MapGroup.GENERAL ? YzuiOptionsScreen.text("map.general")
                : MapTexts.text("map_" + group.name().toLowerCase(Locale.ROOT));
    }

    static void map(List<SettingsList.Row> rows, Screen parent, MapGroup only) {
        if (only == null) {
            rows.add(SettingsList.Row.widget(new TransparentButton(0, 0, 160, 24,
                    YzuiOptionsScreen.text("map.preview"), () -> Minecraft.getInstance().gui.setScreen(new YzWorldMapScreen(parent)))));
        }
        for (MapGroup group : MapGroup.values()) {
            if (only != null && only != group) continue;
            rows.add(SettingsList.Row.subheading(mapGroupTitle(group)));
            switch (group) {
                case GENERAL -> {
                    toggles(rows, MapSettings.Toggle.MINIMAP, MapSettings.Toggle.WAYPOINTS, MapSettings.Toggle.ANCHORS,
                            MapSettings.Toggle.RADAR_PLAYERS, MapSettings.Toggle.WAYPOINT_HUD, MapSettings.Toggle.REMEMBER_VIEW);
                    choice(rows, MapTexts.text("marker_display"), MapSettings.MarkerDisplay.values(),
                            MapSettings::markerDisplay, MapSettings::setMarkerDisplay, value -> name("marker_display.", value));
                }
                case APPEARANCE -> {
                    choice(rows, MapTexts.text("shape"), MapSettings.Shape.values(), MapSettings::shape, MapSettings::setShape,
                            value -> name("shape.", value));
                    slider(rows, "size", MapSettings::size, value -> MapSettings.setSize((int) value), 80, 240, false, 0);
                    slider(rows, "zoom", MapSettings::zoom, MapSettings::setZoom, 0.25, 8, true, 2);
                    toggles(rows, MapSettings.Toggle.ROTATE);
                    rows.add(SettingsList.Row.controls(MapTexts.text("hud_layout"), YzuiOptionsScreen.text("hud.save_hint"),
                            new TransparentButton(0, 0, 160, 24, YzuiOptionsScreen.text("open"),
                                    () -> Minecraft.getInstance().gui.setScreen(new YzHudSettingsScreen(parent)))));
                }
                case INFORMATION -> toggles(rows, MapSettings.Toggle.COORDINATES, MapSettings.Toggle.BIOME_LABEL, MapSettings.Toggle.LAYER_LABEL);
                case MOBS -> toggles(rows, MapSettings.Toggle.RADAR_FRIENDLY, MapSettings.Toggle.RADAR_HOSTILE, MapSettings.Toggle.RADAR_OTHER);
                case TERRAIN -> {
                    choice(rows, MapTexts.text("layer"), MapLayer.values(), MapSettings::layer, MapClient::selectLayer,
                            value -> name("layer.", value));
                    number(rows, "fixed_height", MapSettings::fixedHeight, MapSettings::setFixedHeight, -4096, 4095);
                    choice(rows, MapTexts.text("overlay"), MapSettings.Overlay.values(), MapSettings::overlay, MapSettings::setOverlay,
                            value -> name("overlay.", value));
                    toggles(rows, MapSettings.Toggle.GRID, MapSettings.Toggle.EXACT_LOAD_LEVEL);
                }
                case NAVIGATION -> {
                    toggles(rows, MapSettings.Toggle.PORTAL_PROJECTION);
                    slider(rows, "death_limit", MapSettings::deathLimit, value -> MapSettings.setDeathLimit((int) value), 0, 50, false, 0);
                    number(rows, "waypoint_distance", MapSettings::waypointDistance, MapSettings::setWaypointDistance, 64, 100000);
                }
            }
        }
    }

    private static Component name(String prefix, Enum<?> value) { return MapTexts.text(prefix + value.name().toLowerCase(Locale.ROOT)); }

    private static void toggles(List<SettingsList.Row> rows, MapSettings.Toggle... options) {
        for (MapSettings.Toggle option : options) {
            Component label = MapTexts.text("option." + option.key());
            var control = new CheckboxButton(0, 0, 160, 24, label, MapSettings.enabled(option), () -> MapSettings.toggle(option)) {
                @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
                    setChecked(MapSettings.enabled(option));
                    super.extractWidgetRenderState(g, x, y, tick);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, label.copy().append(": ").append(MapTexts.text(MapSettings.enabled(option) ? "on" : "off")));
                }
            };
            rows.add(SettingsList.Row.widget(control));
        }
    }

    private static <T extends Enum<T>> void choice(List<SettingsList.Row> rows, Component label, T[] values,
            Supplier<T> getter, Consumer<T> setter, java.util.function.Function<T, Component> names) {
        List<T> choices = List.of(values);
        List<String> labels = choices.stream().map(value -> names.apply(value).getString()).toList();
        var control = new DropdownButton(0, 0, 180, 180, 24, label, labels, choices.indexOf(getter.get()), false,
                index -> { setter.accept(choices.get(index)); DebugLogger.debug("YzuiSettings", "设置 %s：%s", label.getString(), labels.get(index)); }, null) {
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
                updateOptions(labels, choices.indexOf(getter.get()), names.apply(getter.get()).getString());
                super.extractWidgetRenderState(g, x, y, tick);
            }
        };
        SettingsList.Row row = SettingsList.Row.widget(control);
        row.addSearchTerms(String.join(" ", labels));
        rows.add(row);
    }

    private static void slider(List<SettingsList.Row> rows, String key, DoubleSupplier getter, DoubleConsumer setter,
            double min, double max, boolean logarithmic, int digits) {
        rows.add(SettingsList.Row.widget(new NumberSlider(MapTexts.text(key), getter, setter, min, max, logarithmic, digits)));
    }

    private static final class NumberSlider extends AbstractSliderButton {
        private final Component label;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final double min, max;
        private final boolean logarithmic;
        private final int digits;

        NumberSlider(Component label, DoubleSupplier getter, DoubleConsumer setter, double min, double max, boolean logarithmic, int digits) {
            super(0, 0, 180, 24, label, logarithmic ? Math.log(getter.getAsDouble() / min) / Math.log(max / min)
                    : (getter.getAsDouble() - min) / (max - min));
            this.label = label; this.getter = getter; this.setter = setter;
            this.min = min; this.max = max; this.logarithmic = logarithmic; this.digits = digits;
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(MapTexts.text("setting_value", label, Component.literal(String.format(Locale.ROOT, "%." + digits + "f", getter.getAsDouble()))));
        }

        @Override protected void applyValue() {
            double selected = logarithmic ? min * Math.pow(max / min, value) : min + value * (max - min);
            double factor = Math.pow(10, digits);
            setter.accept(Math.clamp(Math.round(selected * factor) / factor, min, max));
        }

        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
            if (!isFocused()) value = logarithmic ? Math.log(getter.getAsDouble() / min) / Math.log(max / min)
                    : (getter.getAsDouble() - min) / (max - min);
            updateMessage();
            super.extractWidgetRenderState(g, x, y, tick);
        }
    }

    private static void number(List<SettingsList.Row> rows, String key, IntSupplier getter, IntConsumer setter, int min, int max) {
        rows.add(SettingsList.Row.controls(MapTexts.text(key), Component.empty(), new NumberField(MapTexts.text(key), getter, setter, min, max)));
    }

    /** 数值按回车或失焦提交；非法/未完成输入保留原配置，离开输入框时恢复有效值。 */
    private static final class NumberField extends EditBox {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int min, max;
        private String baseline;

        NumberField(Component label, IntSupplier getter, IntConsumer setter, int min, int max) {
            super(Minecraft.getInstance().font, 0, 0, 180, 24, label);
            this.getter = getter; this.setter = setter; this.min = min; this.max = max;
            setMaxLength(11);
            baseline = Integer.toString(getter.getAsInt()); setValue(baseline);
            setTooltip(Tooltip.create(YzuiOptionsScreen.text("number.hint", min, max)));
        }

        private void commit() {
            if (!getValue().equals(baseline)) {
                try {
                    int selected = Integer.parseInt(getValue().strip());
                    if (selected >= min && selected <= max && selected != getter.getAsInt()) setter.accept(selected);
                } catch (NumberFormatException ignored) { /* 空白或负号等未完成输入不写入配置。 */ }
            }
            baseline = Integer.toString(getter.getAsInt()); setValue(baseline);
        }

        @Override public void setFocused(boolean focused) { if (!focused && isFocused()) commit(); super.setFocused(focused); }
        @Override public boolean keyPressed(KeyEvent event) {
            if (isFocused() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) { commit(); return true; }
            return super.keyPressed(event);
        }
    }

    static void commitNumbers(List<AbstractWidget> widgets) {
        for (AbstractWidget widget : widgets) if (widget instanceof NumberField field) field.commit();
    }
}
