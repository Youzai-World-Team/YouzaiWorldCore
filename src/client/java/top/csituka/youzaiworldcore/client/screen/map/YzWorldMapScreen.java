package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapCanvas;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapMobRadar;
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

/** 全屏地图：拖动、光标缩放、图层切换、路径点、玩家跟踪、绘图。 */
@SuppressWarnings("null")
public final class YzWorldMapScreen extends Screen {
    enum Tool { SELECT, PEN, LINE, RECTANGLE, ELLIPSE, LABEL }
    static final int[] COLORS = {0xFF79BDEB, 0xFFE78682, 0xFFE8BF77, 0xFF87CDA3, 0xFFC19CDD, 0xFFFFFFFF};
    private final Screen parent;
    private final MapCanvas canvas = new MapCanvas();
    private String dimension;
    private double centerX, centerZ, scale = 1;
    private boolean follow = true, dragging;
    private int left, top, mapWidth, mapHeight;
    private int drawingColor = COLORS[0];
    private MapView displayed;
    private int unit, inset;
    private int zoomTop, zoomBottom, infoX, infoY, infoWidth;
    private boolean zoomDragging;
    private MapSettingsPanel settingsPanel;
    private MapDetailPanel detailPanel;
    private MapOverlayPanel overlayPanel;
    private final MapPanelAnimation panelAnimation = new MapPanelAnimation();
    private final List<TransparentButton> layerControls = new ArrayList<>();
    private List<Integer> exploredLayers = List.of();
    private Integer exploredHeight;
    private MapLayer overviewLayer;
    private int layerPage;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private Tool tool = Tool.SELECT;
    private UUID selected;
    private MapDrawing moving;
    private MapVertex start;
    private List<MapVertex> stroke = new ArrayList<>();
    private Component status = Component.empty();

    public YzWorldMapScreen(Screen parent) {
        super(MapTexts.text("title")); this.parent = parent; dimension = MapClient.dimension();
        var player = Minecraft.getInstance().player;
        if (player != null) { centerX = player.getX(); centerZ = player.getZ(); }
        var previous = MapPersonalData.lastView();
        if (MapSettings.enabled(MapSettings.Toggle.REMEMBER_VIEW) && previous != null && MapClient.dimensions().contains(previous.dimension())) {
            dimension = previous.dimension(); centerX = previous.x(); centerZ = previous.z(); scale = previous.zoom(); follow = false;
        }
    }
    public YzWorldMapScreen(Screen parent, String dimension, double x, double z) {
        this(parent); this.dimension = dimension; centerX = x; centerZ = z; follow = false;
    }

    // ===== 蓝图布局：全幅底图、两侧悬浮控制与底部入口 =====

    @Override protected void init() {
        panelAnimation.layout(width, height);
        clearFocus(); clearWidgets(); controls.clear(); displayed = null;
        dragging = false; zoomDragging = false; moving = null; stroke.clear();
        left = 0; top = 0; mapWidth = Math.max(1, width); mapHeight = Math.max(1, height);
        unit = Math.clamp(Math.min(width / 40, height / 25), 18, 36);
        inset = Math.max(6, unit / 2);
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) selected = null;
        int right = width - inset - unit, bottom = height - inset - unit;
        icon(inset, inset, MapIconButton.Icon.MAP, "title", this::openDrawings);
        icon(right, inset, MapIconButton.Icon.CLOSE, "done", this::onClose);

        // 缩放轨道在小窗口中收短，为底部两个入口留出空间。
        int zoomY = Math.max(inset + unit + 8, height / 4);
        zoomTop = zoomY + unit + 10;
        zoomBottom = Math.max(zoomTop + 12, Math.min(height * 2 / 3, bottom - unit * 2 - 24));
        icon(inset, zoomY, MapIconButton.Icon.PLUS, "zoom_in", () -> zoom(scale * 1.25));
        icon(inset, zoomBottom + 10, MapIconButton.Icon.MINUS, "zoom_out", () -> zoom(scale / 1.25));
        icon(inset, bottom - unit - 6, MapIconButton.Icon.CENTER, "recenter", this::recenter);
        icon(inset, bottom, MapIconButton.Icon.SETTINGS, "settings", this::openSettings);
        icon(inset + (unit + 8) * 2, bottom, MapIconButton.Icon.PIN, "waypoints", this::openWaypoints);
        icon(inset + (unit + 8) * 3, bottom, MapIconButton.Icon.PLAYERS, "players", () -> openOverlay(new MapPlayersPanel(this)));
        icon(inset + (unit + 8) * 4, bottom, MapIconButton.Icon.RADAR, "radar", () -> openOverlay(new MapRadarPanel(this)));
        icon(inset + unit + 8, bottom, MapIconButton.Icon.PEN, "drawing_tools", this::openDrawings);

        layerControls.clear();
        exploredLayers = availableLayers();
        if (exploredHeight != null && !exploredLayers.contains(exploredHeight)) exploredHeight = null;
        rebuildLayerControls();
        icon(right, bottom, MapIconButton.Icon.MAP, "dimension", this::openDimensions);
        infoWidth = Math.min(260, Math.max(100, width / 3));
        infoX = right - infoWidth - 8;
        infoY = bottom + unit - 40;
        // 窄屏底部入口与坐标卡片错行，避免相互遮挡。
        if (infoX < inset + (unit + 8) * 6) infoY = bottom - 50;
        if (settingsPanel != null) { settingsPanel.layout(width, height); addWidget(settingsPanel); panelAnimation.show(settingsPanel); }
        if (detailPanel != null) { detailPanel.layout(width, height); addWidget(detailPanel); panelAnimation.show(detailPanel); }
        if (overlayPanel != null) { overlayPanel.layout(width, height); addWidget(overlayPanel); panelAnimation.show(overlayPanel); }
        DebugLogger.debug("WorldMap", "全屏地图布局：%dx%d", width, height);
    }

    /** 非地图入口也直接打开带左侧覆盖设置的地图。 */
    public static YzWorldMapScreen withSettings(Screen parent) {
        var map = new YzWorldMapScreen(parent);
        map.settingsPanel = new MapSettingsPanel(map);
        return map;
    }

    private void openSettings() {
        if (settingsPanel != null) { closeSettings(); return; }
        closeDetails(); closeOverlay(overlayPanel);
        dragging = false; zoomDragging = false; moving = null; stroke.clear();
        settingsPanel = new MapSettingsPanel(this);
        settingsPanel.layout(width, height); addWidget(settingsPanel);
        panelAnimation.show(settingsPanel);
        clearFocus(); setFocused(settingsPanel);
    }

    /** 只移除覆盖控件，保留当前地图画布、位置与缩放。 */
    void closeSettings() {
        if (settingsPanel == null) return;
        clearFocus(); panelAnimation.hide(settingsPanel); removeWidget(settingsPanel); settingsPanel = null;
    }

    String mapDimension() { return dimension; }

    void switchDimension(String target) {
        dimension = target; follow = false; selected = null; exploredHeight = null;
        overviewLayer = null; canvas.close(); init();
        DebugLogger.debug("WorldMap", "地图切换维度：%s", target);
    }

    private void openDimensions() { openDetails(new MapDetailPanel(this)); }

    void openPoint(top.csituka.youzaiworldcore.map.MapWaypoint point, boolean creating) {
        openDetails(new MapDetailPanel(this, point, creating));
    }

    private void openDetails(MapDetailPanel detail) {
        closeSettings(); closeDetails(); closeOverlay(overlayPanel);
        dragging = false; zoomDragging = false; moving = null; stroke.clear();
        detailPanel = detail; detail.layout(width, height); addWidget(detail);
        panelAnimation.show(detail);
        clearFocus(); setFocused(detail);
        DebugLogger.debug("WorldMap", "打开地图右侧覆盖面板");
    }

    /** 快捷键和命令直接打开地图中的路径点覆盖列表。 */
    public static YzWorldMapScreen withWaypoints(Screen parent) {
        var map = new YzWorldMapScreen(parent); map.overlayPanel = new MapWaypointPanel(map); return map;
    }
    /** 独立快捷键也进入地图内的新建或详情面板。 */
    public static YzWorldMapScreen withPoint(Screen parent, top.csituka.youzaiworldcore.map.MapWaypoint point, boolean creating) {
        var map = new YzWorldMapScreen(parent, point.dimension(), point.x(), point.z());
        map.detailPanel = new MapDetailPanel(map, point, creating); return map;
    }
    private void openWaypoints() { openOverlay(new MapWaypointPanel(this)); }
    private void openOverlay(MapOverlayPanel overlay) {
        closeSettings(); closeDetails(); closeOverlay(overlayPanel);
        init();
        dragging = false; zoomDragging = false; moving = null; stroke.clear();
        overlayPanel = overlay; overlay.layout(width, height); addWidget(overlay);
        panelAnimation.show(overlay);
        clearFocus(); setFocused(overlay);
    }
    void closeOverlay(MapOverlayPanel overlay) {
        if (overlay == null || overlayPanel != overlay) return;
        clearFocus(); panelAnimation.hide(overlayPanel); removeWidget(overlayPanel); overlayPanel = null;
    }
    void locatePoint(top.csituka.youzaiworldcore.map.MapWaypoint point) {
        dimension = point.dimension(); centerX = point.x(); centerZ = point.z(); follow = false; exploredHeight = null; overviewLayer = null;
        closeOverlay(overlayPanel); canvas.close(); init();
    }
    /** 展示整片模拟范围；生物种类选择不绑定某个 UUID，也不改变小地图缩放偏好。 */
    void showMobRadar() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        dimension = MapClient.dimension(); centerX = player.getX(); centerZ = player.getZ(); follow = false;
        exploredHeight = null; overviewLayer = null; tool = Tool.SELECT;
        MapClient.track(null);
        double span = (MapMobRadar.radius() * 2 + 1) * 16.0;
        scale = Math.clamp(Math.min(mapWidth, mapHeight) * 0.8 / span, 0.125, 16.0);
        status = Component.empty(); closeOverlay(overlayPanel); canvas.close(); init();
    }

    void locateRadar(MapClient.Radar target, boolean track) {
        dimension = target.dimension(); centerX = target.x(); centerZ = target.z(); follow = track; exploredHeight = null; overviewLayer = null;
        MapClient.track(track ? target.id() : null);
        tool = Tool.SELECT;
        status = track ? MapTexts.text("tracking", target.name()) : Component.empty();
        closeOverlay(overlayPanel); canvas.close(); init();
    }

    boolean isDetails(MapDetailPanel detail) { return detailPanel == detail; }

    void closeDetails() {
        if (detailPanel == null) return;
        clearFocus(); panelAnimation.hide(detailPanel); removeWidget(detailPanel); detailPanel = null;
    }

    /** 被覆盖的地图按钮不参与键盘焦点和鼠标命中。 */
    @Override public List<? extends GuiEventListener> children() {
        return super.children().stream().filter(child -> !(child instanceof AbstractWidget widget)
                || !panelAnimation.coversClosing(widget)
                && (settingsPanel == null || !settingsPanel.covers(widget))
                && (detailPanel == null || !detailPanel.covers(widget))
                && (overlayPanel == null || !overlayPanel.covers(widget))).toList();
    }

    private MapIconButton icon(int x, int y, MapIconButton.Icon icon, String key, Runnable action) {
        var button = addRenderableWidget(new MapIconButton(x, y, unit, MapTexts.text(key), icon, action));
        controls.add(button); return button;
    }

    private TransparentButton button(int x, int y, int w, Component text, Runnable action) {
        var button = addRenderableWidget(new TransparentButton(x, y, Math.max(16, w), 22, text, action));
        button.setTooltip(Tooltip.create(text)); controls.add(button); return button;
    }

    /** 只在玩家当前区块且地图仍以该区块为中心时提供已探索层。 */
    private List<Integer> availableLayers() {
        var player = Minecraft.getInstance().player;
        if (player == null || !dimension.equals(MapClient.dimension())) return List.of();
        int x = Math.floorDiv(player.getBlockX(), 16), z = Math.floorDiv(player.getBlockZ(), 16);
        if (Math.floorDiv(bounded(centerX), 16) != x || Math.floorDiv(bounded(centerZ), 16) != z) return List.of();
        return MapPersonalData.exploredHeights(dimension, x, z);
    }

    private MapLayer currentLayer() {
        if (exploredHeight != null) return MapLayer.CAVE;
        if (overviewLayer != null) return overviewLayer;
        var layer = MapClient.layer(dimension);
        if (layer == MapLayer.FIXED || layer == MapLayer.CAVE && !exploredLayers.contains(MapClient.height(dimension, layer))) return MapLayer.SURFACE;
        return layer;
    }

    private int currentHeight(MapLayer layer) {
        return exploredHeight != null && layer == MapLayer.CAVE ? exploredHeight : MapClient.height(dimension, layer);
    }

    private void rebuildLayerControls() {
        for (var control : layerControls) { removeWidget(control); controls.remove(control); }
        layerControls.clear();
        if (exploredLayers.isEmpty()) return;
        int right = width - inset - unit;
        int visible = Math.max(1, Math.min(5, (height - 2 * inset - 4 * unit - 32) / (unit + 3)));
        int pages = Math.max(1, Math.ceilDiv(exploredLayers.size(), visible));
        layerPage = Math.clamp(layerPage, 0, pages - 1);
        int count = Math.min(visible, exploredLayers.size() - layerPage * visible);
        int y = (height - (count + (pages > 1 ? 3 : 1)) * (unit + 3)) / 2;
        addLayerControl(right, y, Component.literal("≋"), MapTexts.text("layer.surface"), exploredHeight == null && currentLayer() == MapLayer.SURFACE,
                () -> { exploredHeight = null; overviewLayer = MapLayer.SURFACE; canvas.close(); rebuildLayerControls(); });
        for (int i = 0; i < count; i++) {
            int index = layerPage * visible + i, elevation = exploredLayers.get(index);
            Component name = MapTexts.text("explored_floor", index + 1, elevation);
            addLayerControl(right, y + (i + 1) * (unit + 3), Component.literal(Integer.toString(elevation)), name,
                    currentLayer() == MapLayer.CAVE && currentHeight(MapLayer.CAVE) == elevation, () -> {
                        exploredHeight = elevation; overviewLayer = null; canvas.close(); rebuildLayerControls();
                        DebugLogger.debug("WorldMap", "查看已探索地下层：%s Y=%d", dimension, elevation);
                    });
        }
        if (pages > 1) {
            addLayerControl(right, y + (count + 1) * (unit + 3), Component.literal("↑"), MapTexts.text("previous"), false,
                    () -> { layerPage = Math.floorMod(layerPage - 1, pages); rebuildLayerControls(); });
            addLayerControl(right, y + (count + 2) * (unit + 3), Component.literal("↓"), MapTexts.text("next"), false,
                    () -> { layerPage = (layerPage + 1) % pages; rebuildLayerControls(); });
        }
    }

    private void addLayerControl(int x, int y, Component label, Component tooltip, boolean selected, Runnable action) {
        int buttonWidth = Math.max(unit, font.width(label) + 4);
        var control = button(x + unit - buttonWidth, y, buttonWidth, label, action);
        control.setHeight(unit); control.setTextInsets(0, 0); control.setTooltip(Tooltip.create(tooltip));
        if (selected) control.setStyle(YzuiTheme.ButtonStyle.FILLED);
        layerControls.add(control);
    }

    @Override public void tick() {
        if (overlayPanel != null) overlayPanel.tick();
        var available = availableLayers();
        if (!available.equals(exploredLayers)) {
            exploredLayers = available; layerPage = 0;
            if (exploredHeight != null && !available.contains(exploredHeight)) { exploredHeight = null; canvas.close(); }
            rebuildLayerControls();
        }
    }

    private void openDrawings() {
        if (overlayPanel instanceof MapDrawingPanel) closeOverlay(overlayPanel);
        else openOverlay(new MapDrawingPanel(this));
    }

    Tool drawingTool() { return tool; }
    int drawingColor() { return drawingColor; }
    void setDrawingColor(int value) { drawingColor = value | 0xFF000000; }

    /** 从工具管理器进入画布；绘图开关关闭时一并开启，保证新标注可见。 */
    void chooseDrawingTool(Tool value) {
        tool = value; selected = null; status = Component.empty();
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) MapSettings.toggle(MapSettings.Toggle.DRAWINGS);
        closeOverlay(overlayPanel);
        DebugLogger.debug("WorldMap", "选择绘图工具：%s", value);
    }

    /** 定位完整标注并进入选择模式，随后可以在地图中拖动。 */
    void locateDrawing(MapDrawing drawing) {
        double minX = drawing.vertices().stream().mapToDouble(MapVertex::x).min().orElse(0);
        double maxX = drawing.vertices().stream().mapToDouble(MapVertex::x).max().orElse(0);
        double minZ = drawing.vertices().stream().mapToDouble(MapVertex::z).min().orElse(0);
        double maxZ = drawing.vertices().stream().mapToDouble(MapVertex::z).max().orElse(0);
        dimension = drawing.dimension(); centerX = (minX + maxX) / 2; centerZ = (minZ + maxZ) / 2;
        scale = Math.clamp(Math.min(mapWidth * 0.65 / Math.max(32, maxX - minX), mapHeight * 0.65 / Math.max(32, maxZ - minZ)), 0.125, 16);
        follow = false; MapClient.track(null); tool = Tool.SELECT; selected = drawing.id();
        exploredHeight = null; overviewLayer = null; status = Component.empty();
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) MapSettings.toggle(MapSettings.Toggle.DRAWINGS);
        closeOverlay(overlayPanel); canvas.close(); init();
    }

    MapLayer drawingLayer() { return overviewLayer == null ? MapLayer.AUTO : overviewLayer; }
    void cycleDrawingLayer() {
        exploredHeight = null;
        overviewLayer = overviewLayer == null ? MapLayer.SURFACE : overviewLayer == MapLayer.SURFACE ? MapLayer.ROOF : null;
        canvas.close(); rebuildLayerControls();
    }

    private void recenter() {
        follow = true; MapClient.track(null); exploredHeight = null; overviewLayer = null;
        if (!dimension.equals(MapClient.dimension())) { selected = null; }
        dimension = MapClient.dimension();
        var player = Minecraft.getInstance().player;
        if (player != null) { centerX = player.getX(); centerZ = player.getZ(); }
        status = Component.empty(); canvas.close(); init();
        DebugLogger.debug("WorldMap", "地图返回自身位置");
    }

    private MapView view() { return new MapView(centerX, centerZ, scale, 0, mapWidth, mapHeight); }
    // 输入可能在同一渲染帧内连续到达，始终从最新视口计算，不能沿用上一帧缩放中心。
    private MapView shown() { return view(); }

    /** 主线程 Tick 读取当前可视区域，服务端只返回已记录的区块。 */
    public MapViewRequestPayload subscription() {
        var view = view(); var a = view.world(0, 0); var b = view.world(mapWidth, mapHeight); var layer = currentLayer();
        return MapClient.viewRequest(dimension, layer, currentHeight(layer), a.x(), a.y(), b.x(), b.y());
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { YzuiTheme.backdrop(g); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        panelAnimation.update();
        var player = Minecraft.getInstance().player;
        if (follow && player != null) {
            var tracked = MapClient.tracked(dimension);
            if (tracked != null) { centerX = tracked.x(); centerZ = tracked.z(); }
            else if (MapClient.trackedId() == null && dimension.equals(MapClient.dimension())) { centerX = player.getX(); centerZ = player.getZ(); }
        }
        var layer = currentLayer();
        displayed = canvas.drawInteractive(g, view(), dimension, layer, currentHeight(layer), left, top, Minecraft.getInstance().getWindow().getGuiScale());
        MapRenderer.overlay(g, displayed, dimension, left, top, 0, 1, true);
        g.enableScissor(left, top, left + mapWidth, top + mapHeight);
        var selectedDrawing = selectedDrawing();
        if (selectedDrawing != null) MapRenderer.drawing(g, displayed, left, top, 0, selectedDrawing, 1, true);
        if (moving != null) MapRenderer.drawing(g, displayed, left, top, 0, moved(), 1, true);
        if (stroke.size() >= 2) {
            var kind = drawingKind();
            if (kind != null) MapRenderer.drawing(g, displayed, left, top, 0, new MapDrawing(new UUID(0, 0), dimension, kind, stroke, drawingColor, ""), 1, true);
        }
        g.disableScissor();
        g.nextStratum();
        // 信息与工具浮在地形上；鼠标悬停信息不覆盖玩家坐标。
        int positionX = bounded(shown().centerX()), positionZ = bounded(shown().centerZ());
        var positionTile = MapClient.cache().get(new MapTileKey(dimension, layer, currentHeight(layer),
                Math.floorDiv(positionX, 16), Math.floorDiv(positionZ, 16)));
        int positionPixel = MapTileKey.pixelIndex(positionX, positionZ);
        String elevationAtCenter = positionTile == null || positionTile.heights()[positionPixel] == MapTile.VOID_HEIGHT
                ? "?" : Short.toString(positionTile.heights()[positionPixel]);
        String coordinates = "X: " + positionX + "  Y: " + elevationAtCenter + "  Z: " + positionZ;
        YzuiTheme.card(g, infoX, infoY, infoWidth, 40);
        YzuiTheme.label(g, font, Component.literal(coordinates), infoX + 7, infoY + 7, infoWidth - 14, YzuiTheme.text(), false);
        Component location = MapTexts.text("layer." + layer.name().toLowerCase(Locale.ROOT)).copy()
                .append(layer.hasHeight() ? " Y " + currentHeight(layer) : "").append(" / ").append(MapTexts.dimension(dimension));
        if (positionTile != null) location = location.copy().append(" / ").append(MapTexts.biome(positionTile.biome(positionPixel)));
        YzuiTheme.label(g, font, location, infoX + 7, infoY + 24, infoWidth - 14, YzuiTheme.textMuted(), false);
        int railX = inset + unit / 2;
        YzuiTheme.card(g, inset, zoomTop - 5, unit, zoomBottom - zoomTop + 10);
        g.fill(railX - 1, zoomTop, railX + 1, zoomBottom, YzuiTheme.outline());
        int thumb = zoomBottom - (int) Math.round(Math.log(scale / 0.125) / Math.log(128) * (zoomBottom - zoomTop));
        g.fill(railX - 5, thumb - 3, railX + 5, thumb + 3, YzuiTheme.primary());
        if (overZoom(mx, my)) g.setTooltipForNextFrame(MapTexts.text("fullscreen_zoom", String.format(Locale.ROOT, "%.2f", scale)), mx, my);
        if (inside(mx, my)) {
            var world = shown().world(mx - left, my - top); int x = bounded(world.x()), z = bounded(world.y());
            var tile = MapClient.cache().get(new MapTileKey(dimension, layer, currentHeight(layer), Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
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
        if (!hint.getString().isEmpty()) {
            int hintWidth = Math.min(font.width(hint), width - 2 * (inset + unit + 12));
            YzuiTheme.card(g, (width - hintWidth) / 2 - 6, inset + 28, hintWidth + 12, 22);
            YzuiTheme.label(g, font, hint, (width - hintWidth) / 2, inset + 35, hintWidth, YzuiTheme.text(), false);
        }
        boolean covered = overOverlay(mx, my);
        super.extractRenderState(g, covered ? -100 : mx, covered ? -100 : my, delta);
        panelAnimation.draw(g, mx, my, delta);
    }

    private boolean inside(double x, double y) {
        return MapShapes.contains(shown(), x - left, y - top, 0, 0) && !overControls(x, y);
    }
    /** 覆盖页使用完整面板区域拦截命中，空白处也不能触发底层悬停提示。 */
    private boolean overOverlay(double x, double y) {
        return overlayPanel != null && overlayPanel.isMouseOver(x, y)
                || detailPanel != null && detailPanel.isMouseOver(x, y)
                || settingsPanel != null && settingsPanel.isMouseOver(x, y)
                || panelAnimation.isClosingOver(x, y);
    }
    // 缩放条自行绘制，不经过普通按钮的鼠标屏蔽，所有命中入口在此统一排除覆盖区域。
    private boolean overZoom(double x, double y) {
        return !overOverlay(x, y) && x >= inset && x < inset + unit && y >= zoomTop - 5 && y <= zoomBottom + 5;
    }
    private boolean overControls(double x, double y) {
        return overOverlay(x, y) || controls.stream().anyMatch(widget -> widget.visible && widget.isMouseOver(x, y)) || overZoom(x, y)
                || x >= infoX && x < infoX + infoWidth && y >= infoY && y < infoY + 40;
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
        if (overlayPanel != null && overlayPanel.isMouseOver(event.x(), event.y())) {
            var overlay = overlayPanel; clearFocus(); setFocused(overlay); setDragging(event.button() == 0);
            overlay.mouseClicked(event, actual); return true;
        }
        if (overlayPanel != null) clearFocus();
        if (detailPanel != null && detailPanel.isMouseOver(event.x(), event.y())) {
            var overlay = detailPanel;
            clearFocus(); setFocused(overlay); setDragging(event.button() == 0);
            overlay.mouseClicked(event, actual); return true;
        }
        if (detailPanel != null) clearFocus();
        if (settingsPanel != null && settingsPanel.isMouseOver(event.x(), event.y())) {
            var overlay = settingsPanel;
            clearFocus(); setFocused(overlay);
            overlay.mouseClicked(event, actual); return true;
        }
        if (panelAnimation.isClosingOver(event.x(), event.y())) return true;
        if (super.mouseClicked(event, actual)) return true;
        if (overZoom(event.x(), event.y()) && event.button() == 0) { zoomDragging = true; slideZoom(event.y()); return true; }
        if (overControls(event.x(), event.y())) return true;
        if (!inside(event.x(), event.y())) return false;
        var hit = world(event.x(), event.y());
        if (event.button() == 1) {
            for (var point : MapClient.waypoints()) {
                if (!MapClient.visibleWaypoint(point) || !MapSettings.enabled(MapSettings.Toggle.MARKER_ICONS)) continue;
                var position = point.projected(dimension, MapSettings.enabled(MapSettings.Toggle.PORTAL_PROJECTION));
                if (position != null && Math.hypot(position.x() - hit.x(), position.z() - hit.z()) * shown().scale() < 10) {
                    openPoint(point, false); return true;
                }
            }
            var drawing = hitDrawing(event.x() - left, event.y() - top);
            if (drawing != null && drawing.kind() == MapDrawing.Kind.LABEL) {
                Minecraft.getInstance().gui.setScreen(new MapTextScreen(this, "tool.label", drawing.label(), 96,
                        value -> MapPersonalData.putDrawing(new MapDrawing(drawing.id(), dimension, drawing.kind(), drawing.vertices(), drawing.color(), value)))); return true;
            }
            var layer = currentLayer(); int y = playerHeight();
            var tile = MapClient.cache().get(new MapTileKey(dimension, layer, currentHeight(layer), Math.floorDiv((int) hit.x(), 16), Math.floorDiv((int) hit.z(), 16)));
            if (tile != null && tile.heights()[MapTileKey.pixelIndex((int) hit.x(), (int) hit.z())] != MapTile.VOID_HEIGHT) y = tile.heights()[MapTileKey.pixelIndex((int) hit.x(), (int) hit.z())] + 1;
            openPoint(MapClient.newPoint(dimension, (int) hit.x(), Math.clamp(y, -4096, 4095), (int) hit.z()), true); return true;
        }
        if (event.button() != 0) return false;
        if (tool == Tool.SELECT) for (var point : MapClient.waypoints()) {
            if (!MapClient.visibleWaypoint(point) || !MapSettings.enabled(MapSettings.Toggle.MARKER_ICONS)) continue;
            var position = point.projected(dimension, MapSettings.enabled(MapSettings.Toggle.PORTAL_PROJECTION));
            if (position != null && Math.hypot(position.x() - hit.x(), position.z() - hit.z()) * shown().scale() < 10) {
                openPoint(point, false); return true;
            }
        }
        if (tool == Tool.LABEL) {
            Minecraft.getInstance().gui.setScreen(new MapTextScreen(this, "tool.label", "", 96, value -> save(new MapDrawing(UUID.randomUUID(), dimension, MapDrawing.Kind.LABEL, List.of(hit), drawingColor, value)))); return true;
        }
        if (tool == Tool.SELECT) {
            for (var radar : MapClient.radar()) if (MapSettings.enabled(MapSettings.Toggle.MARKER_ICONS) && radar.dimension().equals(dimension)
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
            else if (tool != Tool.SELECT && stroke.size() > 1) save(new MapDrawing(UUID.randomUUID(), dimension, drawingKind(), stroke, drawingColor, ""));
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
        return switch (tool) { case PEN -> MapDrawing.Kind.PEN; case LINE -> MapDrawing.Kind.LINE; case RECTANGLE -> MapDrawing.Kind.RECTANGLE; case ELLIPSE -> MapDrawing.Kind.ELLIPSE; case LABEL -> MapDrawing.Kind.LABEL; case SELECT -> null; };
    }
    MapDrawing selectedDrawing() {
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) return null;
        return MapPersonalData.drawings().stream().filter(d -> d.id().equals(selected) && d.dimension().equals(dimension)).findFirst().orElse(null);
    }
    private MapDrawing hitDrawing(double x, double y) {
        if (!MapSettings.enabled(MapSettings.Toggle.DRAWINGS)) return null;
        return MapPersonalData.drawings().reversed().stream().filter(d -> d.dimension().equals(dimension) && MapShapes.distance(d, shown(), x, y) <= 8).findFirst().orElse(null);
    }
    private void save(MapDrawing drawing) { if (!MapPersonalData.putDrawing(drawing)) status = MapTexts.text("limit"); else selected = drawing.id(); }
    private void deleteDrawing() { if (selected != null) MapPersonalData.removeDrawing(selected); selected = null; }
    private int playerHeight() { var player = Minecraft.getInstance().player; return player == null ? 64 : player.getBlockY(); }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (overlayPanel != null && overlayPanel.isMouseOver(x, y)) { overlayPanel.mouseScrolled(x, y, horizontal, vertical); return true; }
        if (detailPanel != null && detailPanel.isMouseOver(x, y)) return detailPanel.mouseScrolled(x, y, horizontal, vertical);
        if (settingsPanel != null && settingsPanel.isMouseOver(x, y)) {
            settingsPanel.mouseScrolled(x, y, horizontal, vertical); return true;
        }
        if (panelAnimation.isClosingOver(x, y)) return true;
        if (vertical == 0) return false;
        if (overZoom(x, y)) { zoom(scale * Math.pow(1.25, vertical)); return true; }
        if (!inside(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        var zoomed = shown().zoomAt(x - left, y - top, Math.clamp(scale * Math.pow(1.25, vertical), 0.125, 16));
        scale = zoomed.scale(); centerX = Math.clamp(zoomed.centerX(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        centerZ = Math.clamp(zoomed.centerZ(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT); follow = false; return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (overlayPanel != null && event.key() == GLFW.GLFW_KEY_ESCAPE) return overlayPanel.keyPressed(event);
        if (overlayPanel != null && getFocused() == overlayPanel && event.key() != GLFW.GLFW_KEY_TAB) {
            overlayPanel.keyPressed(event); return true;
        }
        if (detailPanel != null && event.key() == GLFW.GLFW_KEY_ESCAPE) return detailPanel.keyPressed(event);
        if (detailPanel != null && getFocused() == detailPanel && event.key() != GLFW.GLFW_KEY_TAB) {
            detailPanel.keyPressed(event); return true;
        }
        if (settingsPanel != null && event.key() == GLFW.GLFW_KEY_ESCAPE) return settingsPanel.keyPressed(event);
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
    @Override public void onClose() {
        if (MapSettings.enabled(MapSettings.Toggle.REMEMBER_VIEW)) MapPersonalData.rememberView(dimension, centerX, centerZ, scale);
        MapClient.suppressShortcuts(); Minecraft.getInstance().gui.setScreen(parent);
    }
    @Override public void removed() { panelAnimation.finish(); canvas.close(); displayed = null; zoomDragging = false; dragging = false; moving = null; stroke.clear(); super.removed(); }
}
