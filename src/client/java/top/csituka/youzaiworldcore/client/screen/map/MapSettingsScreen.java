package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.screen.YzHudSettingsScreen;
import top.csituka.youzaiworldcore.network.MapActionPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** 地图显示、图层、雷达与隐私设置；设置实时写入客户端统一 map_module。 */
public final class MapSettingsScreen extends MapScreen {
    private record Option(Supplier<Component> label, Runnable action) { }
    private int page, pages;
    public MapSettingsScreen(Screen parent) { super(parent, "settings"); }

    @Override protected void init() {
        super.init();
        var options = new ArrayList<Option>();
        option(options, "shape", () -> name("shape.", MapSettings.shape()), () -> MapSettings.setShape(next(MapSettings.Shape.values(), MapSettings.shape())));
        option(options, "size", () -> Component.literal(Integer.toString(MapSettings.size())), () -> MapSettings.setSize(MapSettings.size() >= 240 ? 80 : MapSettings.size() + 16));
        option(options, "zoom", () -> Component.literal(String.format(Locale.ROOT, "%.2f×", MapSettings.zoom())), () -> MapSettings.setZoom(MapSettings.zoom() >= 8 ? 0.25 : MapSettings.zoom() * 2));
        option(options, "layer", () -> name("layer.", MapSettings.layer()), MapClient::cycleLayer);
        option(options, "overlay", () -> name("overlay.", MapSettings.overlay()), () -> MapSettings.setOverlay(next(MapSettings.Overlay.values(), MapSettings.overlay())));
        option(options, "fixed_height", () -> Component.literal(Integer.toString(MapSettings.fixedHeight())), () -> Minecraft.getInstance().gui.setScreen(new MapTextScreen(this, "fixed_height", Integer.toString(MapSettings.fixedHeight()), 5,
                value -> { try { int y = Integer.parseInt(value); return y >= -4096 && y <= 4095; } catch (NumberFormatException ignored) { return false; } },
                value -> MapSettings.setFixedHeight(Integer.parseInt(value)))));
        option(options, "death_limit", () -> Component.literal(Integer.toString(MapSettings.deathLimit())), () -> MapSettings.setDeathLimit(MapSettings.deathLimit() >= 50 ? 0 : MapSettings.deathLimit() + 5));
        option(options, "waypoint_distance", () -> Component.literal(MapSettings.waypointDistance() + "m"), () -> MapSettings.setWaypointDistance(MapSettings.waypointDistance() >= 100000 ? 1000 : MapSettings.waypointDistance() * 2));
        for (var toggle : MapSettings.Toggle.values()) option(options, "option." + toggle.key(), () -> MapTexts.text(MapSettings.enabled(toggle) ? "on" : "off"), () -> MapSettings.toggle(toggle));
        options.add(new Option(() -> MapTexts.text("hud_layout"), () -> Minecraft.getInstance().gui.setScreen(new YzHudSettingsScreen(this))));
        options.add(new Option(() -> MapTexts.text("hide_position"), () -> position(false)));
        options.add(new Option(() -> MapTexts.text("show_position"), () -> position(true)));
        int columns = panelWidth >= 450 ? 2 : 1;
        int rows = Math.max(1, (panelHeight - 106) / 30), count = rows * columns;
        pages = Math.max(1, (options.size() + count - 1) / count); page = Math.clamp(page, 0, pages - 1);
        int cellWidth = (panelWidth - 28 - (columns - 1) * 8) / columns;
        for (int i = page * count; i < Math.min(options.size(), (page + 1) * count); i++) {
            var option = options.get(i); int slot = i - page * count;
            button(panelX + 14 + slot % columns * (cellWidth + 8), panelY + 42 + slot / columns * 30, cellWidth, option.label.get(), () -> { option.action.run(); if (Minecraft.getInstance().gui.screen() == this) init(); });
        }
        int w = (panelWidth - 40) / 4, y = panelY + panelHeight - 34;
        button(panelX + 12, y, w, MapTexts.text("previous"), () -> { page = Math.max(0, page - 1); init(); }).active = page > 0;
        button(panelX + 16 + w, y, w, MapTexts.text("reset_display"), () -> { MapSettings.writeDefaults(); init(); });
        button(panelX + 20 + w * 2, y, w, MapTexts.text("next"), () -> { page++; init(); }).active = page < pages - 1;
        button(panelX + 24 + w * 3, y, w, MapTexts.text("done"), this::onClose);
    }

    private void position(boolean show) {
        var player = Minecraft.getInstance().player;
        if (player == null) { error = MapTexts.text("server_required"); return; }
        MapClient.send(show ? MapActionPayload.Action.SHOW_POSITION : MapActionPayload.Action.HIDE_POSITION,
                MapClient.newPoint(MapClient.dimension(), player.getBlockX(), player.getBlockY(), player.getBlockZ()));
    }

    private static void option(List<Option> list, String key, Supplier<Component> value, Runnable action) {
        list.add(new Option(() -> MapTexts.text("setting_value", MapTexts.text(key), value.get()), action));
    }
    private static Component name(String prefix, Enum<?> value) { return MapTexts.text(prefix + value.name().toLowerCase(Locale.ROOT)); }
    private static <T extends Enum<T>> T next(T[] values, T value) { return values[(value.ordinal() + 1) % values.length]; }
    @Override protected void content(GuiGraphicsExtractor g, int x, int y, float delta) {
        label(g, MapTexts.text("page", page + 1, pages), panelX + panelWidth - 85, panelY + 14, 72);
        label(g, MapTexts.text("settings_hint"), panelX + 14, panelY + panelHeight - 66, panelWidth - 28);
    }
}
