package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapCanvas;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapExport;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapRenderer;
import top.csituka.youzaiworldcore.client.map.MapShapes;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapView;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapDrawing;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.map.MapTile;
import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.map.MapVertex;
import top.csituka.youzaiworldcore.network.MapSessionPayload;
import top.csituka.youzaiworldcore.network.MapViewRequestPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 全屏地图：拖动、光标缩放、图层切换、路径点、玩家跟踪、绘图与选区导出。 */
@SuppressWarnings("null")
public final class YzWorldMapScreen extends Screen {
    private enum Panel { NONE, TOOLS, PLAYERS, RADAR, DIMENSIONS }
    private enum Tool { SELECT, PEN, LINE, RECTANGLE, ELLIPSE, LABEL, AREA }
    private static final int[] COLORS = {0xFF79BDEB, 0xFFE78682, 0xFFE8BF77, 0xFF87CDA3, 0xFFC19CDD, 0xFFFFFFFF};
    private final Screen parent;
    private final MapCanvas canvas = new MapCanvas();
    private String dimension;
    private double centerX, centerZ, scale = 1;
    private boolean follow = true, dragging;
    private int left, top, mapWidth, mapHeight, color;
    private MapView displayed;
    private Panel panel = Panel.NONE;
    private int unit, inset, panelX, panelY, panelWidth, panelHeight, page;
    private int zoomTop, zoomBottom, infoX, infoY, infoWidth;
    private boolean zoomDragging;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private Tool tool = Tool.SELECT;
    private UUID selected;
    private MapDrawing moving;
    private MapVertex start;
    private List<MapVertex> stroke = new ArrayList<>();
    private MapExport.Area area;
    private Component status = Component.empty();

    public YzWorldMapScreen(Screen parent) {
        super(MapTexts.text("title")); this.parent = parent; dimension = MapClient.dimension();
        var player = Minecraft.getInstance().player;
        if (player != null) { centerX = player.getX(); centerZ = player.getZ(); }
    }
    public YzWorldMapScreen(Screen parent, String dimension, double x, double z) {
        this(parent); this.dimension = dimension; centerX = x; centerZ = z; follow = false;
    }

    // ===== 蓝图布局：全幅底图、两侧悬浮控制与底部入口 =====

    @Override protected void init() {
        clearFocus(); clearWidgets(); controls.clear(); displayed = null;
        dragging = false; zoomDragging = false; moving = null; stroke.clear();
        left = 0; top = 0; mapWidth = Math.max(1, width); mapHeight = Math.max(1, height);
        unit = Math.clamp(Math.min(width / 40, height / 25), 18, 36);
        inset = Math.max(6, unit / 2);
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) selected = null;
        int right = width - inset - unit, bottom = height - inset - unit;
        icon(inset, inset, MapIconButton.Icon.MAP, "title", () -> togglePanel(Panel.TOOLS));
        icon(right, inset, MapIconButton.Icon.CLOSE, "done", this::onClose);

        // 缩放轨道在小窗口中收短，为底部两个入口留出空间。
        int zoomY = Math.max(inset + unit + 8, height / 4);
        zoomTop = zoomY + unit + 10;
        zoomBottom = Math.max(zoomTop + 12, Math.min(height * 2 / 3, bottom - unit * 2 - 24));
        icon(inset, zoomY, MapIconButton.Icon.PLUS, "zoom_in", () -> zoom(scale * 1.25));
        icon(inset, zoomBottom + 10, MapIconButton.Icon.MINUS, "zoom_out", () -> zoom(scale / 1.25));
        icon(inset, bottom - unit - 6, MapIconButton.Icon.CENTER, "recenter", this::recenter);
        icon(inset, bottom, MapIconButton.Icon.SETTINGS, "settings", () -> Minecraft.getInstance().gui.setScreen(new MapSettingsScreen(this)));
        icon(inset + (unit + 8) * 2, bottom, MapIconButton.Icon.PIN, "waypoints", () -> Minecraft.getInstance().gui.setScreen(new MapWaypointListScreen(this)));
        icon(inset + (unit + 8) * 3, bottom, MapIconButton.Icon.PLAYERS, "players", () -> togglePanel(Panel.PLAYERS));
        icon(inset + (unit + 8) * 4, bottom, MapIconButton.Icon.RADAR, "radar", () -> togglePanel(Panel.RADAR));
        icon(inset + unit + 8, bottom, MapIconButton.Icon.PEN, "drawing_tools", () -> togglePanel(Panel.TOOLS));

        int layerY = Math.max(inset + unit + 8, (height - 5 * (unit + 3)) / 2);
        layerButton(right, layerY, Component.literal("≋"), MapLayer.SURFACE, 0);
        int[] slices = {53, 23, -7, -37};
        for (int i = 0; i < slices.length; i++) layerButton(right, layerY + (i + 1) * (unit + 3), Component.literal(Integer.toString(slices[i])), MapLayer.FIXED, slices[i]);
        icon(right, bottom, MapIconButton.Icon.MAP, "dimension", () -> togglePanel(Panel.DIMENSIONS));
        infoWidth = Math.min(260, Math.max(100, width / 3));
        infoX = right - infoWidth - 8;
        infoY = bottom + unit - 40;
        // 窄屏底部入口与坐标卡片错行，避免相互遮挡。
        if (infoX < inset + (unit + 8) * 6) infoY = bottom - 50;
        buildPanel();
        DebugLogger.debug("WorldMap", "全屏地图布局：%dx%d，面板=%s", width, height, panel);
    }

    private MapIconButton icon(int x, int y, MapIconButton.Icon icon, String key, Runnable action) {
        var button = addRenderableWidget(new MapIconButton(x, y, unit, MapTexts.text(key), icon, action));
        controls.add(button); return button;
    }

    private TransparentButton button(int x, int y, int w, Component text, Runnable action) {
        var button = addRenderableWidget(new TransparentButton(x, y, Math.max(16, w), 22, text, action));
        button.setTooltip(Tooltip.create(text)); controls.add(button); return button;
    }

    private void layerButton(int x, int y, Component label, MapLayer layer, int elevation) {
        var button = button(x, y, unit, label, () -> {
            MapSettings.setLayer(layer);
            if (layer.hasHeight()) MapSettings.setFixedHeight(elevation);
            canvas.close(); init();
            DebugLogger.debug("WorldMap", "切换图层：%s，高度=%d", layer, elevation);
        });
        button.setHeight(unit);
        button.setTextInsets(0, 0);
        button.setTooltip(Tooltip.create(MapTexts.text("layer." + layer.name().toLowerCase(Locale.ROOT))
                .copy().append(layer.hasHeight() ? " Y " + elevation : "")));
        if (MapSettings.layer() == layer && (!layer.hasHeight() || MapSettings.fixedHeight() == elevation)) button.setStyle(YzuiTheme.ButtonStyle.FILLED);
    }

    private void togglePanel(Panel next) {
        panel = panel == next ? Panel.NONE : next; page = 0; init();
    }

    private void recenter() {
        follow = true; MapClient.track(null);
        if (!dimension.equals(MapClient.dimension())) { selected = null; area = null; }
        dimension = MapClient.dimension();
        var player = Minecraft.getInstance().player;
        if (player != null) { centerX = player.getX(); centerZ = player.getZ(); }
        status = Component.empty(); canvas.close(); init();
        DebugLogger.debug("WorldMap", "地图返回自身位置");
    }

    private void buildPanel() {
        if (panel == Panel.NONE) { panelWidth = 0; panelHeight = 0; return; }
        panelWidth = Math.min(300, width - 2 * (inset + unit + 8));
        panelHeight = Math.min(190, height - 2 * inset - unit - 12);
        panelX = panel == Panel.DIMENSIONS ? width - inset - unit - 8 - panelWidth : inset + unit + 8;
        panelY = height - inset - unit - 8 - panelHeight;
        int x = panelX + 8, y = panelY + 26, w = panelWidth - 16;
        if (panel == Panel.TOOLS) {
            int cell = (w - 12) / 4;
            for (var value : Tool.values()) {
                int i = value.ordinal();
                button(x + (i % 4) * (cell + 4), y + (i / 4) * 26, cell,
                        MapTexts.text("tool." + value.name().toLowerCase(Locale.ROOT)), () -> { tool = value; panel = Panel.NONE; init(); })
                        .setStyle(tool == value ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
            }
            button(x + 3 * (cell + 4), y + 26, cell, MapTexts.text("color"), () -> {
                color = (color + 1) % COLORS.length;
                var drawing = selectedDrawing();
                if (drawing != null) MapPersonalData.putDrawing(new MapDrawing(drawing.id(), dimension, drawing.kind(), drawing.vertices(), COLORS[color], drawing.label()));
            }).setTextColor(() -> COLORS[color]);
            button(x, y + 52, cell, MapTexts.text("undo"), MapPersonalData::undo);
            button(x + cell + 4, y + 52, cell, MapTexts.text("redo"), MapPersonalData::redo);
            button(x + (cell + 4) * 2, y + 52, cell, MapTexts.text("delete"), this::deleteDrawing);
            button(x + (cell + 4) * 3, y + 52, cell, MapTexts.text("export"), () -> {
                var layer = MapClient.layer(dimension);
                Minecraft.getInstance().gui.setScreen(new MapExportScreen(this, dimension, layer, MapClient.height(dimension, layer), area == null ? visibleArea() : area));
            });
            button(x, y + 78, w, MapTexts.text("overlay." + MapSettings.overlay().name().toLowerCase(Locale.ROOT)), () -> {
                var values = MapSettings.Overlay.values(); MapSettings.setOverlay(values[(MapSettings.overlay().ordinal() + 1) % values.length]); init();
            });
            button(x, y + 104, w, MapTexts.text("layer." + MapSettings.layer().name().toLowerCase(Locale.ROOT)), () -> { MapClient.cycleLayer(); init(); });
        } else if (panel == Panel.RADAR) {
            var options = new MapSettings.Toggle[] {MapSettings.Toggle.RADAR_PLAYERS, MapSettings.Toggle.RADAR_HOSTILE, MapSettings.Toggle.RADAR_FRIENDLY, MapSettings.Toggle.RADAR_OTHER, MapSettings.Toggle.RADAR_ICONS};
            for (int i = 0; i < options.length; i++) {
                var option = options[i];
                button(x, y + i * 26, w, MapTexts.text("setting_value", MapTexts.text("option." + option.key()),
                        MapTexts.text(MapSettings.enabled(option) ? "on" : "off")), () -> { MapSettings.toggle(option); init(); });
            }
        } else {
            var players = MapClient.radar().stream().filter(MapClient.Radar::player).toList();
            var dimensions = MapClient.dimensions();
            int count = panel == Panel.PLAYERS ? players.size() : dimensions.size();
            int rows = Math.max(1, (panelHeight - 82) / 26);
            int pages = Math.max(1, (count + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
            for (int i = 0; i < rows && page * rows + i < count; i++) {
                int index = page * rows + i;
                if (panel == Panel.PLAYERS) {
                    var target = players.get(index);
                    button(x, y + i * 26, w, Component.literal(target.name()).append(" · ").append(MapTexts.dimension(target.dimension())), () -> {
                        MapClient.track(target.id());
                        if (!dimension.equals(target.dimension())) { selected = null; area = null; }
                        dimension = target.dimension(); centerX = target.x(); centerZ = target.z(); follow = true;
                        status = MapTexts.text("tracking", target.name()); panel = Panel.NONE; canvas.close(); init();
                        DebugLogger.debug("WorldMap", "跟踪玩家：%s", target.name());
                    });
                } else {
                    String target = dimensions.get(index);
                    button(x, y + i * 26, w, MapTexts.dimension(target), () -> {
                        dimension = target; follow = false; selected = null; area = null; panel = Panel.NONE; canvas.close(); init();
                    }).setStyle(target.equals(dimension) ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
                }
            }
            int footer = panelY + panelHeight - 52;
            button(x, footer, (w - 4) / 2, MapTexts.text("previous"), () -> { page--; init(); }).active = page > 0;
            button(x + (w + 4) / 2, footer, (w - 4) / 2, MapTexts.text("next"), () -> { page++; init(); }).active = page + 1 < pages;
            if (panel == Panel.PLAYERS) button(x, footer + 26, w, MapTexts.text("stop_navigation"), () -> {
                MapClient.navigate(null); MapClient.track(null); follow = false; status = Component.empty(); panel = Panel.NONE; init();
            });
        }
    }

    private MapView view() { return new MapView(centerX, centerZ, scale, 0, mapWidth, mapHeight); }
    private MapView shown() { return displayed == null ? view() : displayed; }

    /** 主线程 Tick 读取当前可视区域，服务端只返回已记录的区块。 */
    public MapViewRequestPayload subscription() {
        var area = visibleArea(); var layer = MapClient.layer(dimension);
        return MapClient.viewRequest(dimension, layer, MapClient.height(dimension, layer), area.minX(), area.minZ(), area.maxX(), area.maxZ());
    }

    private MapExport.Area visibleArea() {
        var view = view(); var a = view.world(0, 0); var b = view.world(mapWidth, mapHeight);
        return MapExport.Area.of(a.x(), a.y(), b.x(), b.y());
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { YzuiTheme.backdrop(g); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var player = Minecraft.getInstance().player;
        if (follow && player != null) {
            var tracked = MapClient.tracked(dimension);
            if (tracked != null) { centerX = tracked.x(); centerZ = tracked.z(); }
            else if (MapClient.trackedId() == null && dimension.equals(MapClient.dimension())) { centerX = player.getX(); centerZ = player.getZ(); }
        }
        var layer = MapClient.layer(dimension);
        displayed = canvas.draw(g, view(), dimension, layer, MapClient.height(dimension, layer), left, top, 0, 1, Minecraft.getInstance().getWindow().getGuiScale());
        MapRenderer.overlay(g, displayed, dimension, left, top, 0, 1, true);
        g.enableScissor(left, top, left + mapWidth, top + mapHeight);
        var selectedDrawing = selectedDrawing();
        if (selectedDrawing != null) MapRenderer.drawing(g, displayed, left, top, 0, selectedDrawing, 1, true);
        if (moving != null) MapRenderer.drawing(g, displayed, left, top, 0, moved(), 1, true);
        if (stroke.size() >= 2) {
            var kind = tool == Tool.AREA ? MapDrawing.Kind.RECTANGLE : drawingKind();
            if (kind != null) MapRenderer.drawing(g, displayed, left, top, 0, new MapDrawing(new UUID(0, 0), dimension, kind, stroke, COLORS[color], ""), 1, true);
        }
        if (area != null) MapRenderer.drawing(g, displayed, left, top, 0,
                new MapDrawing(new UUID(0, 1), dimension, MapDrawing.Kind.RECTANGLE,
                        List.of(new MapVertex(area.minX(), area.minZ()), new MapVertex(area.maxX(), area.maxZ())), YzuiTheme.primary(), ""), 0.8f, true);
        g.disableScissor();
        g.nextStratum();
        // 信息与工具浮在地形上；鼠标悬停信息不覆盖玩家坐标。
        int positionX = bounded(shown().centerX()), positionZ = bounded(shown().centerZ());
        var positionTile = MapClient.cache().get(new MapTileKey(dimension, layer, MapClient.height(dimension, layer),
                Math.floorDiv(positionX, 16), Math.floorDiv(positionZ, 16)));
        int positionPixel = MapTileKey.pixelIndex(positionX, positionZ);
        String elevationAtCenter = positionTile == null || positionTile.heights()[positionPixel] == MapTile.VOID_HEIGHT
                ? "?" : Short.toString(positionTile.heights()[positionPixel]);
        String coordinates = "X: " + positionX + "  Y: " + elevationAtCenter + "  Z: " + positionZ;
        YzuiTheme.card(g, infoX, infoY, infoWidth, 40);
        YzuiTheme.label(g, font, Component.literal(coordinates), infoX + 7, infoY + 7, infoWidth - 14, YzuiTheme.text(), false);
        Component location = MapTexts.text("layer." + layer.name().toLowerCase(Locale.ROOT)).copy()
                .append(layer.hasHeight() ? " Y " + MapClient.height(dimension, layer) : "").append(" / ").append(MapTexts.dimension(dimension));
        if (positionTile != null) location = location.copy().append(" / ").append(MapTexts.biome(positionTile.biome(positionPixel)));
        YzuiTheme.label(g, font, location, infoX + 7, infoY + 24, infoWidth - 14, YzuiTheme.textMuted(), false);
        int railX = inset + unit / 2;
        YzuiTheme.card(g, inset, zoomTop - 5, unit, zoomBottom - zoomTop + 10);
        g.fill(railX - 1, zoomTop, railX + 1, zoomBottom, YzuiTheme.outline());
        int thumb = zoomBottom - (int) Math.round(Math.log(scale / 0.125) / Math.log(128) * (zoomBottom - zoomTop));
        g.fill(railX - 5, thumb - 3, railX + 5, thumb + 3, YzuiTheme.primary());
        if (overZoom(mx, my)) g.setTooltipForNextFrame(MapTexts.text("fullscreen_zoom", String.format(Locale.ROOT, "%.2f", scale)), mx, my);
        if (panel != Panel.NONE) {
            YzuiTheme.card(g, panelX, panelY, panelWidth, panelHeight);
            String key = switch (panel) { case TOOLS -> "drawing_tools"; case PLAYERS -> "players"; case RADAR -> "radar"; default -> "dimension"; };
            YzuiTheme.label(g, font, MapTexts.text(key), panelX + 8, panelY + 9, panelWidth - 16, YzuiTheme.primary(), false);
            if (panel == Panel.PLAYERS && MapClient.radar().stream().noneMatch(MapClient.Radar::player))
                YzuiTheme.label(g, font, MapTexts.text("no_visible_players"), panelX + 8, panelY + 34, panelWidth - 16, YzuiTheme.textMuted(), false);
        }
        if (panel == Panel.NONE && inside(mx, my)) {
            var world = shown().world(mx - left, my - top); int x = bounded(world.x()), z = bounded(world.y());
            var tile = MapClient.cache().get(new MapTileKey(dimension, layer, MapClient.height(dimension, layer), Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            int pixel = MapTileKey.pixelIndex(x, z);
            String elevation = tile == null || tile.heights()[pixel] == MapTile.VOID_HEIGHT ? "?" : Short.toString(tile.heights()[pixel]);
            Component detail = Component.literal("X: " + x + "  Y: " + elevation + "  Z: " + z + " · ")
                    .append(tile == null ? MapTexts.text("unknown") : MapTexts.biome(tile.biome(pixel)));
            int available = Math.max(1, width - 2 * (inset + unit + 12));
            int textWidth = Math.min(available, font.width(detail));
            int textX = (width - textWidth) / 2;
            YzuiTheme.card(g, textX - 6, inset, textWidth + 12, 22);
            YzuiTheme.label(g, font, detail, textX, inset + 7, textWidth, YzuiTheme.text(), false);
        }
        Component hint = status;
        if (MapSettings.overlay() == MapSettings.Overlay.LOAD_STATE) hint = MapTexts.text(MapClient.session() != null
                && MapClient.session().allows(MapSessionPayload.LOAD_STATE) ? "load_legend" : "load_unavailable");
        else if (tool != Tool.SELECT) hint = MapTexts.text("tool." + tool.name().toLowerCase(Locale.ROOT));
        if (!hint.getString().isEmpty() && panel == Panel.NONE) {
            int hintWidth = Math.min(font.width(hint), width - 2 * (inset + unit + 12));
            YzuiTheme.card(g, (width - hintWidth) / 2 - 6, inset + 28, hintWidth + 12, 22);
            YzuiTheme.label(g, font, hint, (width - hintWidth) / 2, inset + 35, hintWidth, YzuiTheme.text(), false);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    private boolean inside(double x, double y) {
        return MapShapes.contains(shown(), x - left, y - top, 0, 0) && !overControls(x, y);
    }
    private boolean overZoom(double x, double y) { return x >= inset && x < inset + unit && y >= zoomTop - 5 && y <= zoomBottom + 5; }
    private boolean overControls(double x, double y) {
        return controls.stream().anyMatch(widget -> widget.visible && widget.isMouseOver(x, y)) || overZoom(x, y)
                || x >= infoX && x < infoX + infoWidth && y >= infoY && y < infoY + 40
                || panel != Panel.NONE && x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight;
    }
    private void zoom(double value) {
        double previous = scale; scale = Math.clamp(value, 0.125, 16); displayed = null;
        DebugLogger.debug("WorldMap", "地图缩放：%.3f → %.3f", previous, scale);
    }
    private void slideZoom(double y) {
        zoom(0.125 * Math.pow(128, Math.clamp((zoomBottom - y) / (zoomBottom - zoomTop), 0, 1)));
    }
    private static int bounded(double value) { return (int) Math.clamp(Math.floor(value), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); }
    private MapVertex world(double x, double y) { var point = shown().world(x - left, y - top); return new MapVertex(bounded(point.x()), bounded(point.y())); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (super.mouseClicked(event, actual)) return true;
        if (overZoom(event.x(), event.y()) && event.button() == 0) { zoomDragging = true; slideZoom(event.y()); return true; }
        if (overControls(event.x(), event.y())) return true;
        if (panel != Panel.NONE) { panel = Panel.NONE; init(); return true; }
        if (!inside(event.x(), event.y())) return false;
        var hit = world(event.x(), event.y());
        if (event.button() == 1) {
            if (MapSettings.enabled(MapSettings.Toggle.WAYPOINTS)) for (var point : MapClient.waypoints()) {
                if (!point.enabled()) continue;
                var position = point.projected(dimension, MapSettings.enabled(MapSettings.Toggle.PORTAL_PROJECTION));
                if (position != null && Math.hypot(position.x() - hit.x(), position.z() - hit.z()) * shown().scale() < 10) {
                    Minecraft.getInstance().gui.setScreen(new MapWaypointActionsScreen(this, point)); return true;
                }
            }
            var drawing = hitDrawing(event.x() - left, event.y() - top);
            if (drawing != null && drawing.kind() == MapDrawing.Kind.LABEL) {
                Minecraft.getInstance().gui.setScreen(new MapTextScreen(this, "tool.label", drawing.label(), 96,
                        value -> MapPersonalData.putDrawing(new MapDrawing(drawing.id(), dimension, drawing.kind(), drawing.vertices(), drawing.color(), value)))); return true;
            }
            var layer = MapClient.layer(dimension); int y = playerHeight();
            var tile = MapClient.cache().get(new MapTileKey(dimension, layer, MapClient.height(dimension, layer), Math.floorDiv((int) hit.x(), 16), Math.floorDiv((int) hit.z(), 16)));
            if (tile != null && tile.heights()[MapTileKey.pixelIndex((int) hit.x(), (int) hit.z())] != MapTile.VOID_HEIGHT) y = tile.heights()[MapTileKey.pixelIndex((int) hit.x(), (int) hit.z())] + 1;
            Minecraft.getInstance().gui.setScreen(new MapWaypointEditScreen(this, MapClient.newPoint(dimension, (int) hit.x(), Math.clamp(y, -4096, 4095), (int) hit.z()), false)); return true;
        }
        if (event.button() != 0) return false;
        if (tool == Tool.SELECT && MapSettings.enabled(MapSettings.Toggle.WAYPOINTS)) for (var point : MapClient.waypoints()) {
            if (!point.enabled()) continue;
            var position = point.projected(dimension, MapSettings.enabled(MapSettings.Toggle.PORTAL_PROJECTION));
            if (position != null && Math.hypot(position.x() - hit.x(), position.z() - hit.z()) * shown().scale() < 10) {
                Minecraft.getInstance().gui.setScreen(new MapWaypointActionsScreen(this, point)); return true;
            }
        }
        if (tool == Tool.LABEL) {
            Minecraft.getInstance().gui.setScreen(new MapTextScreen(this, "tool.label", "", 96, value -> save(new MapDrawing(UUID.randomUUID(), dimension, MapDrawing.Kind.LABEL, List.of(hit), COLORS[color], value)))); return true;
        }
        if (tool == Tool.SELECT) {
            for (var radar : MapClient.radar()) if (radar.dimension().equals(dimension)
                    && Math.hypot(radar.x() - hit.x(), radar.z() - hit.z()) * shown().scale() < 8) {
                MapClient.track(radar.id()); follow = true; status = MapTexts.text("tracking", radar.name()); return true;
            }
            var drawing = hitDrawing(event.x() - left, event.y() - top);
            selected = drawing == null ? null : drawing.id(); moving = drawing;
        }
        follow = false; dragging = true; start = hit; stroke = new ArrayList<>(); stroke.add(hit);
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (zoomDragging && event.button() == 0) { slideZoom(event.y()); return true; }
        if (!dragging || event.button() != 0) return super.mouseDragged(event, dx, dy);
        if (tool == Tool.SELECT && moving == null) { centerX = Math.clamp(centerX - dx / scale, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); centerZ = Math.clamp(centerZ - dy / scale, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); return true; }
        MapVertex point = world(event.x(), event.y());
        if (tool == Tool.PEN) {
            if (stroke.size() < MapDrawing.MAX_VERTICES && Math.hypot(point.x() - stroke.getLast().x(), point.z() - stroke.getLast().z()) * scale >= 2) stroke.add(point);
        } else { if (stroke.size() > 1) stroke.removeLast(); stroke.add(point); }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && zoomDragging) { zoomDragging = false; return true; }
        if (event.button() == 0 && dragging) {
            dragging = false;
            if (moving != null && stroke.size() > 1) save(moved());
            else if (tool == Tool.AREA && stroke.size() > 1) area = MapExport.Area.of(start.x(), start.z(), stroke.getLast().x(), stroke.getLast().z());
            else if (tool != Tool.SELECT && stroke.size() > 1) save(new MapDrawing(UUID.randomUUID(), dimension, drawingKind(), stroke, COLORS[color], ""));
            moving = null; stroke.clear(); return true;
        }
        return super.mouseReleased(event);
    }

    private MapDrawing moved() {
        if (moving == null || stroke.size() < 2) return moving;
        double dx = stroke.getLast().x() - start.x(), dz = stroke.getLast().z() - start.z();
        double minX = moving.vertices().stream().mapToDouble(MapVertex::x).min().orElse(0), maxX = moving.vertices().stream().mapToDouble(MapVertex::x).max().orElse(0);
        double minZ = moving.vertices().stream().mapToDouble(MapVertex::z).min().orElse(0), maxZ = moving.vertices().stream().mapToDouble(MapVertex::z).max().orElse(0);
        double shiftX = Math.clamp(dx, -MapTileKey.WORLD_LIMIT - minX, MapTileKey.WORLD_LIMIT - maxX);
        double shiftZ = Math.clamp(dz, -MapTileKey.WORLD_LIMIT - minZ, MapTileKey.WORLD_LIMIT - maxZ);
        return new MapDrawing(moving.id(), dimension, moving.kind(), moving.vertices().stream().map(p -> new MapVertex(p.x() + shiftX, p.z() + shiftZ)).toList(), moving.color(), moving.label());
    }

    private MapDrawing.Kind drawingKind() {
        return switch (tool) { case PEN -> MapDrawing.Kind.PEN; case LINE -> MapDrawing.Kind.LINE; case RECTANGLE, AREA -> MapDrawing.Kind.RECTANGLE; case ELLIPSE -> MapDrawing.Kind.ELLIPSE; case LABEL -> MapDrawing.Kind.LABEL; case SELECT -> null; };
    }
    private MapDrawing selectedDrawing() { return MapPersonalData.drawings().stream().filter(d -> d.id().equals(selected)).findFirst().orElse(null); }
    private MapDrawing hitDrawing(double x, double y) {
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) return null;
        return MapPersonalData.drawings().reversed().stream().filter(d -> d.dimension().equals(dimension) && MapShapes.distance(d, shown(), x, y) <= 8).findFirst().orElse(null);
    }
    private void save(MapDrawing drawing) { if (!MapPersonalData.putDrawing(drawing)) status = MapTexts.text("limit"); else selected = drawing.id(); }
    private void deleteDrawing() { if (selected != null) MapPersonalData.removeDrawing(selected); selected = null; area = null; }
    private int playerHeight() { var player = Minecraft.getInstance().player; return player == null ? 64 : player.getBlockY(); }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return false;
        if (overZoom(x, y)) { zoom(scale * Math.pow(1.25, vertical)); return true; }
        if (!inside(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        var zoomed = shown().zoomAt(x - left, y - top, Math.clamp(scale * Math.pow(1.25, vertical), 0.125, 16));
        scale = zoomed.scale(); centerX = Math.clamp(zoomed.centerX(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        centerZ = Math.clamp(zoomed.centerZ(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); follow = false; return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && panel != Panel.NONE) { panel = Panel.NONE; init(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || MapClient.OPEN.matches(event)) { onClose(); return true; }
        if (event.key() == GLFW.GLFW_KEY_EQUAL || event.key() == GLFW.GLFW_KEY_KP_ADD) { zoom(scale * 1.25); return true; }
        if (event.key() == GLFW.GLFW_KEY_MINUS || event.key() == GLFW.GLFW_KEY_KP_SUBTRACT) { zoom(scale / 1.25); return true; }
        boolean control = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (control && event.key() == GLFW.GLFW_KEY_Z) { if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) MapPersonalData.redo(); else MapPersonalData.undo(); return true; }
        if (control && event.key() == GLFW.GLFW_KEY_Y) { MapPersonalData.redo(); return true; }
        if (event.key() == GLFW.GLFW_KEY_DELETE) { deleteDrawing(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { MapClient.suppressShortcuts(); Minecraft.getInstance().gui.setScreen(parent); }
    @Override public void removed() { canvas.close(); displayed = null; zoomDragging = false; dragging = false; moving = null; stroke.clear(); super.removed(); }
}
