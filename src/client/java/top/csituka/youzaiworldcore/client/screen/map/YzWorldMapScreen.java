package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
    private enum Tool { SELECT, PEN, LINE, RECTANGLE, ELLIPSE, LABEL, AREA }
    private static final int[] COLORS = {0xFF79BDEB, 0xFFE78682, 0xFFE8BF77, 0xFF87CDA3, 0xFFC19CDD, 0xFFFFFFFF};
    private final Screen parent;
    private final MapCanvas canvas = new MapCanvas();
    private String dimension;
    private double centerX, centerZ, scale = 1;
    private boolean follow = true, dragging;
    private int left, top, mapWidth, mapHeight, color;
    private MapView displayed;
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

    @Override protected void init() {
        clearWidgets(); left = 10; top = 66; mapWidth = Math.max(20, width - 20); mapHeight = Math.max(20, height - 137);
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) selected = null;
        int w = (width - 32) / 4;
        button(10, 10, w, MapTexts.dimension(dimension), () -> {
            var dimensions = MapClient.dimensions(); int next = (dimensions.indexOf(dimension) + 1) % dimensions.size();
            dimension = dimensions.get(next); follow = false; selected = null; area = null; canvas.close(); init();
        });
        button(14 + w, 10, w, MapTexts.text("layer." + MapSettings.layer().name().toLowerCase(Locale.ROOT)), () -> { MapClient.cycleLayer(); init(); });
        button(18 + w * 2, 10, w, MapTexts.text("overlay." + MapSettings.overlay().name().toLowerCase(Locale.ROOT)), () -> {
            var values = MapSettings.Overlay.values(); MapSettings.setOverlay(values[(MapSettings.overlay().ordinal() + 1) % values.length]); init();
        });
        button(22 + w * 3, 10, w, MapTexts.text("settings"), () -> Minecraft.getInstance().gui.setScreen(new MapSettingsScreen(this)));
        button(10, 36, w, MapTexts.text("waypoints"), () -> Minecraft.getInstance().gui.setScreen(new MapWaypointListScreen(this)));
        button(14 + w, 36, w, MapTexts.text("recenter"), () -> { follow = true; MapClient.track(null); dimension = MapClient.dimension(); init(); });
        button(18 + w * 2, 36, w, MapTexts.text("stop_navigation"), () -> { MapClient.navigate(null); MapClient.track(null); follow = false; });
        button(22 + w * 3, 36, w, MapTexts.text("done"), this::onClose);
        int footer = height - 33, narrow = (width - 44) / 7;
        button(10, footer, narrow * 2 + 4, MapTexts.text("tool." + tool.name().toLowerCase(Locale.ROOT)), () -> {
            tool = Tool.values()[(tool.ordinal() + 1) % Tool.values().length]; dragging = false; stroke.clear(); moving = null; init();
        });
        button(18 + narrow * 2, footer, narrow, MapTexts.text("color"), () -> {
            color = (color + 1) % COLORS.length;
            var drawing = selectedDrawing();
            if (drawing != null) MapPersonalData.putDrawing(new MapDrawing(drawing.id(), dimension, drawing.kind(), drawing.vertices(), COLORS[color], drawing.label()));
        }).setTextColor(() -> COLORS[color]);
        button(22 + narrow * 3, footer, narrow, MapTexts.text("undo"), MapPersonalData::undo);
        button(26 + narrow * 4, footer, narrow, MapTexts.text("redo"), MapPersonalData::redo);
        button(30 + narrow * 5, footer, narrow, MapTexts.text("delete"), this::deleteDrawing);
        button(34 + narrow * 6, footer, narrow, MapTexts.text("export"), () -> {
            var layer = MapClient.layer(dimension);
            Minecraft.getInstance().gui.setScreen(new MapExportScreen(this, dimension, layer, MapClient.height(dimension, layer), area == null ? visibleArea() : area));
        });
    }

    private TransparentButton button(int x, int y, int w, Component text, Runnable action) {
        return addRenderableWidget(new TransparentButton(x, y, Math.max(16, w), 22, text, action));
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
        YzuiTheme.card(g, left - 3, top - 3, mapWidth + 6, mapHeight + 6);
        var layer = MapClient.layer(dimension);
        displayed = canvas.draw(g, view(), dimension, layer, MapClient.height(dimension, layer), left, top, 10, 1, 1);
        MapRenderer.overlay(g, displayed, dimension, left, top, 10, 1, true);
        g.enableScissor(left, top, left + mapWidth, top + mapHeight);
        var selectedDrawing = selectedDrawing();
        if (selectedDrawing != null) MapRenderer.drawing(g, displayed, left, top, 10, selectedDrawing, 1, true);
        if (moving != null) MapRenderer.drawing(g, displayed, left, top, 10, moved(), 1, true);
        if (stroke.size() >= 2) {
            var kind = tool == Tool.AREA ? MapDrawing.Kind.RECTANGLE : drawingKind();
            if (kind != null) MapRenderer.drawing(g, displayed, left, top, 10, new MapDrawing(new UUID(0, 0), dimension, kind, stroke, COLORS[color], ""), 1, true);
        }
        if (area != null) MapRenderer.drawing(g, displayed, left, top, 10,
                new MapDrawing(new UUID(0, 1), dimension, MapDrawing.Kind.RECTANGLE,
                        List.of(new MapVertex(area.minX(), area.minZ()), new MapVertex(area.maxX(), area.maxZ())), YzuiTheme.primary(), ""), 0.8f, true);
        g.disableScissor();
        YzuiTheme.border(g, left - 1, top - 1, mapWidth + 2, mapHeight + 2, 11, YzuiTheme.alpha(YzuiTheme.primary(), 0.65f));
        Component detail = status.getString().isEmpty() ? MapTexts.text("map_hint") : status;
        if (inside(mx, my)) {
            var world = shown().world(mx - left, my - top); int x = bounded(world.x()), z = bounded(world.y());
            var tile = MapClient.cache().get(new MapTileKey(dimension, layer, MapClient.height(dimension, layer), Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            int pixel = MapTileKey.pixelIndex(x, z);
            String elevation = tile == null || tile.heights()[pixel] == MapTile.VOID_HEIGHT ? "?" : Short.toString(tile.heights()[pixel]);
            detail = Component.literal(x + " / " + elevation + " / " + z + " · ")
                    .append(tile == null ? MapTexts.text("unknown") : MapTexts.biome(tile.biome(pixel)));
        }
        YzuiTheme.label(g, font, detail, left + 4, height - 63, mapWidth - 8, YzuiTheme.text(), false);
        Component secondary = MapTexts.text("map_status", MapClient.cache().size(), MapClient.receivedTiles(), String.format(Locale.ROOT, "%.2f", scale));
        if (MapSettings.overlay() == MapSettings.Overlay.LOAD_STATE) secondary = MapTexts.text(MapClient.session() != null
                && MapClient.session().allows(MapSessionPayload.LOAD_STATE) ? "load_legend" : "load_unavailable");
        YzuiTheme.label(g, font, secondary,
                left + 4, height - 49, mapWidth - 8, YzuiTheme.textMuted(), false);
        super.extractRenderState(g, mx, my, delta);
    }

    private boolean inside(double x, double y) { return MapShapes.contains(shown(), x - left, y - top, 10, 2); }
    private static int bounded(double value) { return (int) Math.clamp(Math.floor(value), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); }
    private MapVertex world(double x, double y) { var point = shown().world(x - left, y - top); return new MapVertex(bounded(point.x()), bounded(point.y())); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (super.mouseClicked(event, actual)) return true;
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
        if (!dragging || event.button() != 0) return super.mouseDragged(event, dx, dy);
        if (tool == Tool.SELECT && moving == null) { centerX = Math.clamp(centerX - dx / scale, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); centerZ = Math.clamp(centerZ - dy / scale, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); return true; }
        MapVertex point = world(event.x(), event.y());
        if (tool == Tool.PEN) {
            if (stroke.size() < MapDrawing.MAX_VERTICES && Math.hypot(point.x() - stroke.getLast().x(), point.z() - stroke.getLast().z()) * scale >= 2) stroke.add(point);
        } else { if (stroke.size() > 1) stroke.removeLast(); stroke.add(point); }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
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
        if (!inside(x, y) || vertical == 0) return false;
        var zoomed = shown().zoomAt(x - left, y - top, Math.clamp(scale * Math.pow(1.25, vertical), 0.125, 16));
        scale = zoomed.scale(); centerX = Math.clamp(zoomed.centerX(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        centerZ = Math.clamp(zoomed.centerZ(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); follow = false; return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        boolean control = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (control && event.key() == GLFW.GLFW_KEY_Z) { if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) MapPersonalData.redo(); else MapPersonalData.undo(); return true; }
        if (control && event.key() == GLFW.GLFW_KEY_Y) { MapPersonalData.redo(); return true; }
        if (event.key() == GLFW.GLFW_KEY_DELETE) { deleteDrawing(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || MapClient.OPEN.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { MapClient.suppressShortcuts(); Minecraft.getInstance().gui.setScreen(parent); }
    @Override public void removed() { canvas.close(); displayed = null; dragging = false; moving = null; stroke.clear(); super.removed(); }
}
