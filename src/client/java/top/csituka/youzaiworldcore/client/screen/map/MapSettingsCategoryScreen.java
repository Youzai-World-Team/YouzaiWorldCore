package top.csituka.youzaiworldcore.client.screen.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapRenderer;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.YzHudSettingsScreen;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 按用途分组的分页设置菜单；信息与生物类别支持分别多选。 */
final class MapSettingsCategoryScreen extends MapScreen {
    enum Category {
        APPEARANCE("map_appearance"), INFORMATION("map_information"), MOBS("map_mobs"),
        TERRAIN("map_terrain"), NAVIGATION("map_navigation");
        final String key;
        Category(String key) { this.key = key; }
    }
    private record Option(String key, Supplier<Component> value, Runnable action, MapSettings.Toggle toggle) { }
    private final Category category;
    private int page, pages;
    MapSettingsCategoryScreen(Screen parent, Category category) {
        super(parent, category.key); this.category = category;
        DebugLogger.debug("MapSettings", "打开地图分类设置：%s", category);
    }
    @Override protected void init() {
        super.init(); var options = new ArrayList<Option>();
        switch (category) {
            case APPEARANCE -> {
                option(options, "shape", () -> name("shape.", MapSettings.shape()), () -> MapSettings.setShape(next(MapSettings.Shape.values(), MapSettings.shape())));
                option(options, "size", () -> {
                    int design = MapSettings.size(), displayed = MapRenderer.layoutSize();
                    return Component.literal(design == displayed ? Integer.toString(design) : design + " → " + displayed);
                }, () -> MapSettings.setSize(MapSettings.size() >= 240 ? 80 : MapSettings.size() + 16));
                option(options, "zoom", () -> Component.literal(String.format(Locale.ROOT, "%.2f×", MapSettings.zoom())),
                        () -> MapSettings.setZoom(MapSettings.zoom() >= 8 ? 0.25 : MapSettings.zoom() * 2));
                toggles(options, MapSettings.Toggle.ROTATE);
                options.add(new Option("hud_layout", null, () -> Minecraft.getInstance().gui.setScreen(new YzHudSettingsScreen(this)), null));
            }
            case INFORMATION -> toggles(options, MapSettings.Toggle.COORDINATES, MapSettings.Toggle.BIOME_LABEL, MapSettings.Toggle.LAYER_LABEL);
            case MOBS -> toggles(options, MapSettings.Toggle.RADAR_FRIENDLY, MapSettings.Toggle.RADAR_HOSTILE, MapSettings.Toggle.RADAR_OTHER);
            case TERRAIN -> {
                option(options, "layer", () -> name("layer.", MapSettings.layer()), MapClient::cycleLayer);
                option(options, "fixed_height", () -> Component.literal(Integer.toString(MapSettings.fixedHeight())), () ->
                        Minecraft.getInstance().gui.setScreen(new MapTextScreen(this, "fixed_height", Integer.toString(MapSettings.fixedHeight()), 5,
                                value -> { try { int y = Integer.parseInt(value); return y >= -4096 && y <= 4095; } catch (NumberFormatException ignored) { return false; } },
                                value -> MapSettings.setFixedHeight(Integer.parseInt(value)))));
                option(options, "overlay", () -> name("overlay.", MapSettings.overlay()), () -> MapSettings.setOverlay(next(MapSettings.Overlay.values(), MapSettings.overlay())));
                toggles(options, MapSettings.Toggle.GRID, MapSettings.Toggle.EXACT_LOAD_LEVEL);
            }
            case NAVIGATION -> {
                toggles(options, MapSettings.Toggle.PORTAL_PROJECTION);
                option(options, "death_limit", () -> Component.literal(Integer.toString(MapSettings.deathLimit())),
                        () -> MapSettings.setDeathLimit(MapSettings.deathLimit() >= 50 ? 0 : MapSettings.deathLimit() + 5));
                option(options, "waypoint_distance", () -> Component.literal(MapSettings.waypointDistance() + "m"),
                        () -> MapSettings.setWaypointDistance(MapSettings.waypointDistance() >= 100000 ? 1000 : MapSettings.waypointDistance() * 2));
            }
        }
        int columns = panelWidth >= 450 ? 2 : 1, count = Math.max(1, (panelHeight - 98) / 28) * columns;
        pages = Math.max(1, Math.ceilDiv(options.size(), count)); page = Math.clamp(page, 0, pages - 1);
        int cell = (panelWidth - 24 - (columns - 1) * 6) / columns;
        for (int i = page * count; i < Math.min(options.size(), (page + 1) * count); i++) {
            var option = options.get(i); int slot = i - page * count;
            Component label = option.value() == null ? MapTexts.text(option.key())
                    : MapTexts.text("setting_value", MapTexts.text(option.key()), option.value().get());
            var control = button(panelX + 12 + slot % columns * (cell + 6), panelY + 42 + slot / columns * 28, cell, label,
                    () -> { option.action().run(); if (Minecraft.getInstance().gui.screen() == this) init(); });
            control.setTooltip(Tooltip.create(label));
            if (option.toggle() != null) control.setStyle(MapSettings.enabled(option.toggle()) ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
        }
        int footer = panelY + panelHeight - 34;
        button(panelX + 12, footer, 30, Component.literal("‹"), () -> { page--; init(); }).active = page > 0;
        button(panelX + panelWidth - 42, footer, 30, Component.literal("›"), () -> { page++; init(); }).active = page < pages - 1;
        button(panelX + 48, footer, panelWidth - 96, MapTexts.text("done"), this::onClose);
    }
    private static void option(List<Option> list, String key, Supplier<Component> value, Runnable action) {
        list.add(new Option(key, value, action, null));
    }
    private static void toggles(List<Option> list, MapSettings.Toggle... toggles) {
        for (var toggle : toggles) list.add(new Option("option." + toggle.key(), () -> MapTexts.text(MapSettings.enabled(toggle) ? "on" : "off"),
                () -> MapSettings.toggle(toggle), toggle));
    }
    private static Component name(String prefix, Enum<?> value) { return MapTexts.text(prefix + value.name().toLowerCase(Locale.ROOT)); }
    private static <T extends Enum<T>> T next(T[] values, T value) { return values[(value.ordinal() + 1) % values.length]; }
    static Component markerDisplayLabel() { return name("marker_display.", MapSettings.markerDisplay()); }
    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        label(g, MapTexts.text("page", page + 1, pages), panelX + 12, panelY + panelHeight - 54, panelWidth - 24);
    }
}
