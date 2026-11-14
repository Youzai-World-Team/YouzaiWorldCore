package top.csituka.youzaiworldcore.client.screen.map;

import org.lwjgl.sdl.SDLScancode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
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

/** 全屏地图：拖动、光标缩放、图层切换、路径点、玩家跟踪。 */
@SuppressWarnings("null")
public final class YzWorldMapScreen extends Screen {
    private static final int CONTEXT_MENU_PADDING = 4;
    private final Screen parent;
    private final MapCanvas canvas = new MapCanvas();
    private String dimension;
    private double centerX, centerZ, scale = 1;
    private boolean follow = true, dragging;
    private int left, top, mapWidth, mapHeight;
    private MapView displayed;
    private int unit, inset;
    private int zoomTop, zoomBottom, infoX, infoY, infoWidth;
    private boolean zoomDragging;
    private MapSettingsPanel settingsPanel;
    private MapDetailPanel detailPanel;
    private MapOverlayPanel overlayPanel;
    private TransparentButton contextMenu;
    private final MapPanelAnimation panelAnimation = new MapPanelAnimation();
    private final List<TransparentButton> layerControls = new ArrayList<>();
    private List<Integer> exploredLayers = List.of();
    private Integer exploredHeight;
    private MapLayer overviewLayer;
    private int layerPage;
    private final List<AbstractWidget> controls = new ArrayList<>();
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
        closeContextMenu();
        panelAnimation.layout(width, height);
        clearFocus(); clearWidgets(); controls.clear(); displayed = null;
        dragging = false; zoomDragging = false;
        left = 0; top = 0; mapWidth = Math.max(1, width); mapHeight = Math.max(1, height);
        unit = Math.clamp(Math.min(width / 40, height / 25), 18, 36);
        inset = Math.max(6, unit / 2);
        int right = width - inset - unit;
        icon(right, inset, MapIconButton.Icon.CLOSE, "done", this::onClose);

        // 缩放轨道在小窗口中收短，为底部文字入口留出空间。
        int zoomY = Math.max(inset + unit + 8, height / 4);
        zoomTop = zoomY + unit + 10;
        zoomBottom = Math.max(zoomTop + 12, Math.min(height * 2 / 3, height - inset - 26 - unit * 2 - 26));
        icon(inset, zoomY, MapIconButton.Icon.PLUS, "zoom_in", () -> zoom(scale * 1.25));
        icon(inset, zoomBottom + 10, MapIconButton.Icon.MINUS, "zoom_out", () -> zoom(scale / 1.25));
        icon(inset, height - inset - 26 - unit - 8, MapIconButton.Icon.CENTER, "recenter", this::recenter);
        int entryWidth = Math.min(110, (width - inset * 2 - 24) / 5);
        int entryY = height - inset - 26;
        String[] entryKeys = { "settings", "waypoints", "players", "radar", "dimension" };
        Runnable[] entryActions = { this::openSettings, this::openWaypoints,
                () -> openOverlay(new MapPlayersPanel(this)), () -> openOverlay(new MapRadarPanel(this)), this::openDimensions };
        for (int i = 0; i < entryKeys.length; i++) {
            button(inset + i * (entryWidth + 6), entryY, entryWidth, MapTexts.text(entryKeys[i]), entryActions[i]).setHeight(26);
        }

        layerControls.clear();
        exploredLayers = availableLayers();
        if (exploredHeight != null && !exploredLayers.contains(exploredHeight)) exploredHeight = null;
        rebuildLayerControls();

        infoWidth = Math.min(260, Math.max(100, width / 3));
        infoX = right - infoWidth - 8;
        // 坐标卡片放在底部入口上方，避免窄屏遮挡。
        infoY = entryY - 48;
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
        closeContextMenu();
        if (settingsPanel != null) { closeSettings(); return; }
        closeDetails(); closeOverlay(overlayPanel);
        dragging = false; zoomDragging = false;
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
        dimension = target; follow = false; exploredHeight = null;
        overviewLayer = null; canvas.close(); init();
        DebugLogger.debug("WorldMap", "地图切换维度：%s", target);
    }

    private void openDimensions() { openDetails(new MapDetailPanel(this)); }

    void openPoint(top.csituka.youzaiworldcore.map.MapWaypoint point, boolean creating) {
        openDetails(new MapDetailPanel(this, point, creating));
    }

    private void openDetails(MapDetailPanel detail) {
        closeContextMenu();
        closeSettings(); closeDetails(); closeOverlay(overlayPanel);
        dragging = false; zoomDragging = false;
        detailPanel = detail; detail.layout(width, height); addWidget(detail);
        panelAnimation.show(detail);
        clearFocus(); setFocused(detail);
        DebugLogger.debug("WorldMap", "打开地图详情界面");
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
        dragging = false; zoomDragging = false;
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
        exploredHeight = null; overviewLayer = null;
        MapClient.track(null);
        double span = (MapMobRadar.radius() * 2 + 1) * 16.0;
        scale = Math.clamp(Math.min(mapWidth, mapHeight) * 0.8 / span, 0.125, 16.0);
        status = Component.empty(); closeOverlay(overlayPanel); canvas.close(); init();
    }

    void locateRadar(MapClient.Radar target, boolean track) {
        dimension = target.dimension(); centerX = target.x(); centerZ = target.z(); follow = track; exploredHeight = null; overviewLayer = null;
        MapClient.track(track ? target.id() : null);
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
        if (contextMenu != null) return List.of(contextMenu);
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

    private void recenter() {
        follow = true; MapClient.track(null); exploredHeight = null; overviewLayer = null;
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
        if (!hint.getString().isEmpty()) {
            int hintWidth = Math.min(font.width(hint), width - 2 * (inset + unit + 12));
            YzuiTheme.card(g, (width - hintWidth) / 2 - 6, inset + 28, hintWidth + 12, 22);
            YzuiTheme.label(g, font, hint, (width - hintWidth) / 2, inset + 35, hintWidth, YzuiTheme.text(), false);
        }
        boolean covered = overOverlay(mx, my);
        super.extractRenderState(g, covered ? -100 : mx, covered ? -100 : my, delta);
        boolean overMenu = overContextMenu(mx, my);
        panelAnimation.draw(g, overMenu ? -100 : mx, overMenu ? -100 : my, delta);
        if (contextMenu != null) {
            g.nextStratum();
            YzuiTheme.card(g, contextMenu.getX() - CONTEXT_MENU_PADDING, contextMenu.getY() - CONTEXT_MENU_PADDING,
                    contextMenu.getWidth() + CONTEXT_MENU_PADDING * 2, contextMenu.getHeight() + CONTEXT_MENU_PADDING * 2);
            contextMenu.extractRenderState(g, mx, my, delta);
        }
    }

    private boolean inside(double x, double y) {
        return MapShapes.contains(shown(), x - left, y - top, 0, 0) && !overControls(x, y);
    }
    /** 覆盖页使用完整面板区域拦截命中，空白处也不能触发底层悬停提示。 */
    private boolean overOverlay(double x, double y) {
        return overContextMenu(x, y)
                || overlayPanel != null && overlayPanel.isMouseOver(x, y)
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

    // ===== 地图右键菜单 =====

    /** 保存右键位置，只有点击菜单项后才打开新建路径点面板。 */
    private void openContextMenu(double mouseX, double mouseY, MapVertex hit) {
        closeContextMenu(); clearFocus();
        dragging = false; zoomDragging = false; setDragging(false);
        String targetDimension = dimension;
        int x = (int) hit.x(), z = (int) hit.z();
        var layer = currentLayer();
        var tile = MapClient.cache().get(new MapTileKey(targetDimension, layer, currentHeight(layer),
                Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        int pixel = MapTileKey.pixelIndex(x, z);
        int y = Math.clamp(tile != null && tile.heights()[pixel] != MapTile.VOID_HEIGHT
                ? tile.heights()[pixel] + 1 : playerHeight(), -4096, 4095);
        Component label = MapTexts.text("new_point");
        int menuWidth = Math.min(Math.max(140, font.width(label) + 20), Math.max(16, width - 16));
        int menuHeight = 24;
        int menuX = Math.clamp((int) mouseX + CONTEXT_MENU_PADDING, CONTEXT_MENU_PADDING,
                Math.max(CONTEXT_MENU_PADDING, width - menuWidth - CONTEXT_MENU_PADDING));
        int menuY = Math.clamp((int) mouseY + CONTEXT_MENU_PADDING, CONTEXT_MENU_PADDING,
                Math.max(CONTEXT_MENU_PADDING, height - menuHeight - CONTEXT_MENU_PADDING));
        contextMenu = addWidget(new TransparentButton(menuX, menuY, menuWidth, menuHeight, label, () -> {
            DebugLogger.debug("WorldMap", "从右键菜单新建路径点：%s X=%d Y=%d Z=%d", targetDimension, x, y, z);
            openPoint(MapClient.newPoint(targetDimension, x, y, z), true);
        }));
        contextMenu.setTextLeftAligned(true);
        contextMenu.setTooltip(Tooltip.create(label));
        setFocused(contextMenu);
        DebugLogger.debug("WorldMap", "打开地图右键菜单：%s X=%d Y=%d Z=%d", targetDimension, x, y, z);
    }

    /** 菜单底板的留白也拦截鼠标，避免点击穿透到地图或覆盖面板。 */
    private boolean overContextMenu(double x, double y) {
        return contextMenu != null && x >= contextMenu.getX() - CONTEXT_MENU_PADDING
                && x < contextMenu.getRight() + CONTEXT_MENU_PADDING
                && y >= contextMenu.getY() - CONTEXT_MENU_PADDING
                && y < contextMenu.getBottom() + CONTEXT_MENU_PADDING;
    }

    private void closeContextMenu() {
        if (contextMenu == null) return;
        removeWidget(contextMenu); contextMenu = null; setDragging(false);
        DebugLogger.debug("WorldMap", "关闭地图右键菜单");
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean actual) {
        if (contextMenu != null) {
            if (overContextMenu(event.x(), event.y())) {
                contextMenu.mouseClicked(event, actual); return true;
            }
            closeContextMenu();
            if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return true;
        }
        if (overlayPanel != null && overlayPanel.isMouseOver(event.x(), event.y())) {
            var overlay = overlayPanel; clearFocus(); setFocused(overlay); setDragging(event.button() == InputConstants.MOUSE_BUTTON_LEFT);
            overlay.mouseClicked(event, actual); return true;
        }
        if (overlayPanel != null) clearFocus();
        if (detailPanel != null && detailPanel.isMouseOver(event.x(), event.y())) {
            var overlay = detailPanel;
            clearFocus(); setFocused(overlay); setDragging(event.button() == InputConstants.MOUSE_BUTTON_LEFT);
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
        if (overZoom(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { zoomDragging = true; slideZoom(event.y()); return true; }
        if (overControls(event.x(), event.y())) return true;
        if (!inside(event.x(), event.y())) return false;
        var hit = world(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            openContextMenu(event.x(), event.y(), hit); return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        for (var point : MapClient.waypoints()) {
            if (!MapClient.visibleWaypoint(point) || !MapSettings.markersVisible()) continue;
            var position = point.projected(dimension, MapSettings.enabled(MapSettings.Toggle.PORTAL_PROJECTION));
            if (position != null && Math.hypot(position.x() - hit.x(), position.z() - hit.z()) * shown().scale() < 10) {
                openPoint(point, false); return true;
            }
        }
        for (var radar : MapClient.radar()) if (MapSettings.markersVisible() && radar.dimension().equals(dimension)
                && Math.hypot(radar.x() - hit.x(), radar.z() - hit.z()) * shown().scale() < 8) {
            MapClient.track(radar.id()); follow = true; status = MapTexts.text("tracking", radar.name()); return true;
        }
        follow = false; dragging = true;
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (contextMenu != null) return true;
        if (zoomDragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { slideZoom(event.y()); return true; }
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseDragged(event, dx, dy);
        centerX = Math.clamp(centerX - dx / scale, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        centerZ = Math.clamp(centerZ - dy / scale, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && zoomDragging) { zoomDragging = false; return true; }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private int playerHeight() { var player = Minecraft.getInstance().player; return player == null ? 64 : player.getBlockY(); }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (contextMenu != null) {
            if (!overContextMenu(x, y)) closeContextMenu();
            return true;
        }
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
        if (contextMenu != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) closeContextMenu();
            else if (MapClient.OPEN.matches(event)) onClose();
            else contextMenu.keyPressed(event);
            return true;
        }
        if (overlayPanel != null && event.key() == InputConstants.KEY_ESCAPE) return overlayPanel.keyPressed(event);
        if (overlayPanel != null && getFocused() == overlayPanel && event.key() != InputConstants.KEY_TAB) {
            overlayPanel.keyPressed(event); return true;
        }
        if (detailPanel != null && event.key() == InputConstants.KEY_ESCAPE) return detailPanel.keyPressed(event);
        if (detailPanel != null && getFocused() == detailPanel && event.key() != InputConstants.KEY_TAB) {
            detailPanel.keyPressed(event); return true;
        }
        if (settingsPanel != null && event.key() == InputConstants.KEY_ESCAPE) return settingsPanel.keyPressed(event);
        if (event.key() == InputConstants.KEY_ESCAPE || MapClient.OPEN.matches(event)) { onClose(); return true; }
        if (event.key() == InputConstants.KEY_EQUALS || event.key() == InputConstants.KEY_ADD) { zoom(scale * 1.25); return true; }
        if (event.key() == InputConstants.KEY_MINUS || event.key() == SDLScancode.SDL_SCANCODE_KP_MINUS) { zoom(scale / 1.25); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (MapSettings.enabled(MapSettings.Toggle.REMEMBER_VIEW)) MapPersonalData.rememberView(dimension, centerX, centerZ, scale);
        MapClient.suppressShortcuts(); Minecraft.getInstance().gui.setScreen(parent);
    }
    @Override public void removed() { closeContextMenu(); panelAnimation.finish(); canvas.close(); displayed = null; zoomDragging = false; dragging = false; super.removed(); }
}
